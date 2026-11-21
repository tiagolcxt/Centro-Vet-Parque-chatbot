package com.clinicavet.assistant.conversation.application;

import com.clinicavet.assistant.clinicknowledge.domain.ClinicConfig;
import com.clinicavet.assistant.clinicknowledge.domain.ClinicKnowledge;
import com.clinicavet.assistant.clinicknowledge.domain.SimulatedKnowledge;
import com.clinicavet.assistant.conversation.domain.Conversation;
import com.clinicavet.assistant.conversation.domain.Intent;
import com.clinicavet.assistant.conversation.domain.Message;
import com.clinicavet.assistant.conversation.domain.MessageRepository;
import com.clinicavet.assistant.shared.config.SimulatedTime;
import com.clinicavet.assistant.shared.domain.Channel;
import com.clinicavet.assistant.shared.domain.ContactIdentity;
import com.clinicavet.assistant.shared.exception.DomainValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Simulador da configuração (S3-7, ADR-0026 item 7): "o que o assistente responderia com o rascunho?". Roda o mesmo
 * turno do assistente, com a mesma validação, mas:
 * <ul>
 *   <li>a base é o rascunho, não a versão publicada;</li>
 *   <li>o horário pode ser simulado ("sábado às 20h");</li>
 *   <li>tudo acontece numa transação desfeita no fim: nenhuma conversa, mensagem, solicitação ou aviso em tempo real
 *       fica gravado (os avisos só saem depois de um commit).</li>
 * </ul>
 * A conversa anterior vem da tela, para o assistente responder com contexto, e não chama o modelo de novo.
 */
@Service
public class ConfigSimulator {

    public static final int MAX_PREVIOUS = 10;

    public enum From {
        TUTOR, ASSISTANT
    }

    public record Line(From from, String text) {
    }

    public record Result(String reply, String priority, boolean handoff, String handoffReason,
                         boolean staffRequest, String intent, boolean fallback) {
    }

    private final ClinicConfig config;
    private final OpenConversationUseCase open;
    private final HandleIncomingMessageUseCase handle;
    private final MessageRepository messages;
    private final TransactionTemplate rollbackOnly;
    private final Clock clock;

    ConfigSimulator(ClinicConfig config, OpenConversationUseCase open, HandleIncomingMessageUseCase handle,
                    MessageRepository messages, PlatformTransactionManager transactions, Clock clock) {
        this.config = config;
        this.open = open;
        this.handle = handle;
        this.messages = messages;
        this.rollbackOnly = new TransactionTemplate(transactions);
        this.clock = clock;
    }

    /**
     * @param at horário local simulado, no fuso da clínica; {@code null} = agora
     */
    public Result simulate(List<Line> previous, String text, LocalDateTime at) {
        if (previous.size() > MAX_PREVIOUS) {
            throw new DomainValidationException("previous", "até " + MAX_PREVIOUS + " mensagens anteriores");
        }
        ClinicKnowledge draft = config.draftKnowledge();
        Supplier<Result> work = () -> SimulatedKnowledge.call(draft, () -> rollbackOnly.execute(status -> {
            status.setRollbackOnly();
            return run(previous, text);
        }));
        if (at == null) {
            return work.get();
        }
        Instant instant = at.atZone(clock.getZone()).toInstant();
        return SimulatedTime.call(instant, work);
    }

    private Result run(List<Line> previous, String text) {
        ContactIdentity contact = new ContactIdentity(Channel.API, "simulador-" + UUID.randomUUID());
        Conversation conversation = open.open(contact).conversation();
        for (Line line : previous) {
            Instant now = clock.instant();
            messages.save(line.from() == From.TUTOR
                    ? Message.fromCustomer(conversation.id(), line.text(), now)
                    : Message.fromAssistant(conversation.id(), line.text(), Intent.OTHER, now));
        }
        HandleIncomingMessageUseCase.Result result = handle.handle(conversation.id(), text);
        return new Result(result.reply(), result.priority().name(), result.handoffReason().isPresent(),
                result.handoffReason().map(Enum::name).orElse(null), result.staffRequestReason().isPresent(),
                result.intent().map(Enum::name).orElse(null), result.fallback());
    }
}
