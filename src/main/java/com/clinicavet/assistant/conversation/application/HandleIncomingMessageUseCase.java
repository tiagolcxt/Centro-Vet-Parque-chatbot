package com.clinicavet.assistant.conversation.application;

import com.clinicavet.assistant.conversation.domain.Conversation;
import com.clinicavet.assistant.conversation.domain.ConversationId;
import com.clinicavet.assistant.conversation.domain.ConversationRepository;
import com.clinicavet.assistant.conversation.domain.ConversationStatus;
import com.clinicavet.assistant.conversation.domain.Intent;
import com.clinicavet.assistant.conversation.domain.Message;
import com.clinicavet.assistant.conversation.domain.MessageRepository;
import com.clinicavet.assistant.shared.domain.ContactIdentity;
import com.clinicavet.assistant.shared.exception.ConcurrentTurnException;
import com.clinicavet.assistant.shared.exception.DomainValidationException;
import com.clinicavet.assistant.shared.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Um turno de conversa (proposta 5.1, Etapa 2).
 * <ol>
 *   <li><b>Transação 1:</b> resolve a conversa ativa do contato (expira e abre outra, se preciso),
 *       grava a mensagem do tutor e lê a janela recente. A mensagem fica gravada aconteça o que
 *       acontecer depois.</li>
 *   <li><b>Fora de transação:</b> monta o contexto, chama o modelo (no máximo 2 vezes) e classifica.</li>
 *   <li><b>Transação 2:</b> confere que ninguém mexeu na conversa durante a chamada. Se mexeu, responde
 *       {@code 409} sem resposta, e a mensagem do tutor entra no histórico do turno seguinte (DA-21).
 *       Senão, aplica o turno válido (intenção) ou só grava a resposta segura, sem mudar o estado.</li>
 * </ol>
 * A conversa-alvo da URL serve para identificar o contato. Como no WhatsApp, a mensagem vai para a
 * conversa ativa do contato, que pode ser outra se a alvo estiver encerrada ou expirada (ADR-0009).
 */
@Service
public class HandleIncomingMessageUseCase {

    private static final Logger log = LoggerFactory.getLogger(HandleIncomingMessageUseCase.class);

    /**
     * @param conversationId conversa em que a mensagem entrou (pode diferir da URL)
     * @param newConversation a conversa foi aberta por esta mensagem
     * @param fallback        a resposta é a segura padrão, porque o turno falhou
     */
    public record Result(ConversationId conversationId, boolean newConversation, String reply,
                         ConversationStatus status, Optional<Intent> intent, boolean fallback) {
    }

    private record TurnStart(Conversation conversation, boolean newConversation, Message customerMessage,
                             List<Message> recent, Instant at) {
    }

    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final ConversationLifecycle lifecycle;
    private final ContextAssembler contextAssembler;
    private final TurnExecutor executor;
    private final TransactionRunner tx;
    private final ConversationSettings settings;
    private final Clock clock;

    HandleIncomingMessageUseCase(ConversationRepository conversations, MessageRepository messages,
                                 ConversationLifecycle lifecycle, ContextAssembler contextAssembler,
                                 TurnExecutor executor, TransactionRunner tx, ConversationSettings settings,
                                 Clock clock) {
        this.conversations = conversations;
        this.messages = messages;
        this.lifecycle = lifecycle;
        this.contextAssembler = contextAssembler;
        this.executor = executor;
        this.tx = tx;
        this.settings = settings;
        this.clock = clock;
    }

    public Result handle(ConversationId target, String text) {
        String content = validate(text);
        ContactIdentity contact = conversations.findById(target)
                .orElseThrow(() -> new ResourceNotFoundException("Conversa", target.value()))
                .contact();

        TurnStart start = tx.withRetry(() -> begin(contact, content));

        AssistantRequest request = contextAssembler.assemble(start.conversation(), start.recent(),
                start.customerMessage(), start.at());
        TurnOutcome outcome = executor.execute(request);

        Result result = tx.inTransaction(() -> finish(start, outcome));
        log.info("Turno concluído [conversationId={}, novaConversa={}, resultado={}, tentativas={}]",
                result.conversationId().value(), result.newConversation(),
                result.fallback() ? ((TurnOutcome.Failed) outcome).kind() : "VALID", outcome.attempts());
        return result;
    }

    private String validate(String text) {
        if (text == null || text.isBlank()) {
            throw new DomainValidationException("text", "não pode ser vazio");
        }
        String content = text.strip();
        if (content.length() > settings.maxCustomerMessageLength()) {
            throw new DomainValidationException("text",
                    "excede " + settings.maxCustomerMessageLength() + " caracteres");
        }
        return content;
    }

    private TurnStart begin(ContactIdentity contact, String content) {
        Instant now = clock.instant();
        ConversationLifecycle.Resolved resolved = lifecycle.resolveActive(contact, now);
        Conversation conversation = resolved.conversation();
        Message customer = messages.save(Message.fromCustomer(conversation.id(), content, now));
        conversation.registerActivity(now);
        Conversation saved = conversations.save(conversation);
        List<Message> recent = messages.findLatest(saved.id(), settings.historyMaxMessages());
        return new TurnStart(saved, resolved.started(), customer, recent, now);
    }

    private Result finish(TurnStart start, TurnOutcome outcome) {
        ConversationId id = start.conversation().id();
        Conversation current = conversations.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalStateException("conversa sumiu durante o turno"));
        List<Message> latest = messages.findLatest(id, 1);
        boolean changed = !Objects.equals(current.version(), start.conversation().version())
                || latest.isEmpty()
                || !latest.getLast().id().equals(start.customerMessage().id());
        if (changed) {
            // Outra mensagem ou ação chegou durante a chamada ao modelo. Esta resposta é descartada.
            log.info("Turno descartado por concorrência [conversationId={}]", id.value());
            throw new ConcurrentTurnException();
        }

        Instant now = clock.instant();
        return switch (outcome) {
            case TurnOutcome.Valid valid -> {
                AssistantTurn turn = valid.turn();
                current.recordIntent(turn.intent());
                current.registerActivity(now);
                Conversation saved = conversations.save(current);
                messages.save(Message.fromAssistant(id, turn.reply(), turn.intent(), now));
                yield new Result(id, start.newConversation(), turn.reply(), saved.status(),
                        Optional.of(turn.intent()), false);
            }
            case TurnOutcome.Failed failed -> {
                // Falha: o estado da conversa não muda (ADR-0002). A resposta segura fica no histórico,
                // porque foi o que o tutor recebeu.
                String reply = settings.fallbackReply().strip();
                messages.save(Message.fromAssistant(id, reply, null, now));
                yield new Result(id, start.newConversation(), reply, current.status(), Optional.empty(), true);
            }
        };
    }
}
