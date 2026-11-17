package com.clinicavet.assistant.conversation.application;

import com.clinicavet.assistant.conversation.application.rules.AlertExpressionDetector;
import com.clinicavet.assistant.conversation.application.rules.ExplicitHumanRequestDetector;
import com.clinicavet.assistant.conversation.domain.AssistantMetadata;
import com.clinicavet.assistant.conversation.domain.ClinicalPriority;
import com.clinicavet.assistant.conversation.domain.Conversation;
import com.clinicavet.assistant.conversation.domain.ConversationId;
import com.clinicavet.assistant.conversation.domain.ConversationRepository;
import com.clinicavet.assistant.conversation.domain.ConversationStatus;
import com.clinicavet.assistant.conversation.domain.HandoffReason;
import com.clinicavet.assistant.conversation.domain.Intent;
import com.clinicavet.assistant.conversation.domain.Message;
import com.clinicavet.assistant.conversation.domain.MessageAuthor;
import com.clinicavet.assistant.conversation.domain.MessageRepository;
import com.clinicavet.assistant.conversation.domain.PriorityPolicy;
import com.clinicavet.assistant.conversation.domain.TurnSignal;
import com.clinicavet.assistant.servicerequest.domain.RequestField;
import com.clinicavet.assistant.servicerequest.domain.RequestStatus;
import com.clinicavet.assistant.servicerequest.domain.RequestType;
import com.clinicavet.assistant.servicerequest.domain.ServiceRequest;
import com.clinicavet.assistant.servicerequest.domain.ServiceRequestRepository;
import com.clinicavet.assistant.shared.domain.ContactIdentity;
import com.clinicavet.assistant.shared.exception.ConcurrentTurnException;
import com.clinicavet.assistant.shared.exception.DomainValidationException;
import com.clinicavet.assistant.shared.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Um turno de conversa (proposta 5.1).
 * <ol>
 *   <li><b>Transação 1:</b> resolve a conversa ativa do contato (expira e abre outra, se preciso), grava a
 *       mensagem do tutor e lê a janela recente. A mensagem fica gravada aconteça o que acontecer depois.</li>
 *   <li><b>Fora de transação:</b> detectores determinísticos (pedido de humano, expressões de alerta), depois
 *       o modelo: <b>modo principal</b> em {@code OPEN} (até 2 chamadas, validação e reconciliação) ou
 *       <b>modo restrito</b> em {@code WAITING_HUMAN} (só sinais, uma chamada, ADR-0007 item 4).</li>
 *   <li><b>Transação 2:</b> confere que ninguém mexeu na conversa (senão {@code 409}, DA-21). Aplica tudo de uma
 *       vez (ADR-0017): prioridade (só sobe), intenção, encaminhamento + {@code ServiceRequest(HUMAN_CONTACT)},
 *       pedido de informação, oferta de atendimento humano e a resposta composta pelo backend.</li>
 * </ol>
 * A conversa-alvo da URL serve para identificar o contato. Como no WhatsApp, a mensagem vai para a conversa
 * ativa do contato, que pode ser outra se a alvo estiver encerrada ou expirada (ADR-0009).
 */
@Service
public class HandleIncomingMessageUseCase {

    private static final Logger log = LoggerFactory.getLogger(HandleIncomingMessageUseCase.class);

    /**
     * @param conversationId  conversa em que a mensagem entrou (pode diferir da URL)
     * @param newConversation a conversa foi aberta por esta mensagem
     * @param fallback        o modelo falhou e a resposta foi composta pelo backend sem o texto dele
     */
    public record Result(ConversationId conversationId, boolean newConversation, String reply,
                         ConversationStatus status, Optional<Intent> intent, boolean fallback,
                         ClinicalPriority priority, Optional<HandoffReason> handoffReason) {
    }

    private record TurnStart(Conversation conversation, boolean newConversation, Message customerMessage,
                             List<Message> recent, Instant at) {

        List<Message> previous() {
            return recent.stream().filter(m -> !m.id().equals(customerMessage.id())).toList();
        }
    }

    private sealed interface ModelResult {
        record Main(TurnOutcome outcome) implements ModelResult {
        }

        record Restricted(RestrictedOutcome outcome) implements ModelResult {
        }
    }

    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final ServiceRequestRepository serviceRequests;
    private final ConversationLifecycle lifecycle;
    private final ContextAssembler contextAssembler;
    private final TurnExecutor executor;
    private final TurnDecider decider;
    private final ReplyComposer composer;
    private final ExplicitHumanRequestDetector humanRequest;
    private final AlertExpressionDetector alerts;
    private final TransactionRunner tx;
    private final ConversationSettings settings;
    private final HandoffSettings handoffSettings;
    private final CustomerDataCollector collector;
    private final CustomerDataSettings customerData;
    private final Clock clock;

    HandleIncomingMessageUseCase(ConversationRepository conversations, MessageRepository messages,
                                 ServiceRequestRepository serviceRequests, ConversationLifecycle lifecycle,
                                 ContextAssembler contextAssembler, TurnExecutor executor, TurnDecider decider,
                                 ReplyComposer composer, ExplicitHumanRequestDetector humanRequest,
                                 AlertExpressionDetector alerts, TransactionRunner tx, ConversationSettings settings,
                                 HandoffSettings handoffSettings, CustomerDataCollector collector,
                                 CustomerDataSettings customerData, Clock clock) {
        this.conversations = conversations;
        this.messages = messages;
        this.serviceRequests = serviceRequests;
        this.lifecycle = lifecycle;
        this.contextAssembler = contextAssembler;
        this.executor = executor;
        this.decider = decider;
        this.composer = composer;
        this.humanRequest = humanRequest;
        this.alerts = alerts;
        this.tx = tx;
        this.settings = settings;
        this.handoffSettings = handoffSettings;
        this.collector = collector;
        this.customerData = customerData;
        this.clock = clock;
    }

    public Result handle(ConversationId target, String text) {
        String content = validate(text);
        ContactIdentity contact = conversations.findById(target)
                .orElseThrow(() -> new ResourceNotFoundException("Conversa", target.value()))
                .contact();

        TurnStart start = tx.withRetry(() -> begin(contact, content));
        Conversation conversation = start.conversation();

        // Determinísticos, antes do modelo e independentes dele (ADR-0007 item 1, ADR-0019)
        TurnDecider.Context ctx = new TurnDecider.Context(conversation.clinicalPriority(),
                conversation.humanOfferMade(), start.previous(), content, humanRequest.detect(content),
                alerts.floor(content));

        AssistantRequest request = contextAssembler.assemble(conversation, start.recent(), start.customerMessage(),
                start.at());
        ModelResult modelResult = conversation.status() == ConversationStatus.WAITING_HUMAN
                ? new ModelResult.Restricted(executor.executeRestricted(request))
                : new ModelResult.Main(executor.execute(request, turn -> decider.reconcile(ctx, turn)));

        Result result = tx.inTransaction(() -> finish(start, ctx, modelResult));
        log.info("Turno concluído [conversationId={}, novaConversa={}, modo={}, falha={}, prioridade={}, "
                        + "encaminhamento={}, pedidoExplicito={}, alerta={}]",
                result.conversationId().value(), result.newConversation(),
                modelResult instanceof ModelResult.Restricted ? "RESTRITO" : "PRINCIPAL", result.fallback(),
                result.priority(), result.handoffReason().map(Enum::name).orElse("-"),
                ctx.explicitRequest().orElse("-"), ctx.alertFloor().map(Enum::name).orElse("-"));
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

    private Result finish(TurnStart start, TurnDecider.Context ctx, ModelResult modelResult) {
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
        return switch (modelResult) {
            case ModelResult.Restricted restricted -> finishWaiting(start, ctx, current, restricted.outcome(), now);
            case ModelResult.Main main -> finishMain(start, ctx, current, main.outcome(), now);
        };
    }

    private Result finishMain(TurnStart start, TurnDecider.Context ctx, Conversation current, TurnOutcome outcome,
                              Instant now) {
        ConversationId id = current.id();
        String customerText = start.customerMessage().content();
        TurnDecider.Decision decision;
        String reply;
        Intent intent = null;
        List<Intent> secondary = List.of();
        boolean failed;
        switch (outcome) {
            case TurnOutcome.Valid valid -> {
                AssistantTurn turn = valid.turn();
                decision = decider.decideValid(ctx, turn);
                ReplyComposer.Plan plan = plan(decision, turn.possibleEmergency());
                reply = composer.composeValid(plan, turn.reply());
                intent = turn.intent();
                secondary = turn.secondaryIntents();
                current.recordIntent(turn.intent());
                if (turn.knowledgeGap()) {
                    recordInformationGap(current, customerText, now);
                }
                // Etapa 5: dados do tutor e do pet, proveniência e pedido de agendamento (ADR-0010, DA-35 a DA-37).
                CustomerDataCollector.Outcome collected = collector.collect(current, turn, start.recent(),
                        start.customerMessage(), decision.handoff().handsOff(), now);
                if (collected.appointmentSubmitted() && !plan.emergency()) {
                    // Texto do sistema: o modelo não sabe se o registro deu certo e nunca confirma dia nem horário.
                    reply = reply + "\n\n" + customerData.appointmentRegisteredReply().strip();
                }
                // DA-38: a primeira resposta da conversa se apresenta como assistente virtual. Em P1/P2 não: a
                // orientação vem primeiro (H6).
                if (start.previous().isEmpty() && !plan.emergency() && !plan.turnLevel().isUrgentOrWorse()
                        && !reply.toLowerCase(java.util.Locale.ROOT).contains("assistente virtual")) {
                    reply = settings.firstReplyIntro().strip() + "\n\n" + reply;
                }
                failed = false;
            }
            case TurnOutcome.Failed failure -> {
                // Falha: intenção e coleta não mudam (ADR-0002). Prioridade e encaminhamento ainda podem mudar,
                // porque vêm dos detectores determinísticos (H8, ADR-0007).
                decision = decider.decideFailure(ctx, failure.kind() == FailureKind.TRANSFER_WITHOUT_HANDOFF);
                reply = composer.composeFailure(plan(decision, false), settings.fallbackReply(),
                        current.clinicalPriority());
                failed = true;
            }
        }
        current.raisePriority(decision.priority().effective());
        decision.handoff().handoff().ifPresent(reason -> handOff(current, reason, customerText, now));
        if (decision.handoff().offerHuman()) {
            current.markHumanOfferMade();
        }
        current.registerActivity(now);
        Conversation saved = conversations.save(current);
        messages.save(Message.fromAssistant(id, reply, intent,
                metadata(failed, decision.priority(), decision.signals(), secondary), now));
        return new Result(id, start.newConversation(), reply, saved.status(), Optional.ofNullable(intent), failed,
                saved.clinicalPriority(), decision.handoff().handoff());
    }

    private Result finishWaiting(TurnStart start, TurnDecider.Context ctx, Conversation current,
                                 RestrictedOutcome outcome, Instant now) {
        boolean failed = outcome instanceof RestrictedOutcome.Failed;
        RestrictedSignals signals = outcome instanceof RestrictedOutcome.Valid valid ? valid.signals() : null;
        PriorityPolicy.Inputs inputs = signals == null
                ? PriorityPolicy.Inputs.deterministicOnly(ctx.alertFloor())
                : new PriorityPolicy.Inputs(signals.priority(), Set.of(), signals.signals(), signals.possibleEmergency(),
                false, ctx.alertFloor());
        PriorityPolicy.Assessment priority = PriorityPolicy.assess(current.clinicalPriority(), inputs);
        boolean emergency = signals != null && signals.possibleEmergency();
        current.raisePriority(priority.effective());
        boolean overdue = current.handoffAt()
                .map(at -> now.isAfter(at.plus(handoffSettings.deadlineFor(current.clinicalPriority()))))
                .orElse(false);
        // CT-19: a resposta da espera varia com o sinal e nunca repete a anterior.
        String previousReply = start.previous().stream().filter(m -> m.author() == MessageAuthor.ASSISTANT)
                .reduce((a, b) -> b).map(Message::content).orElse(null);
        String reply = composer.composeWaiting(priority.turnLevel(), emergency, failed, overdue,
                signals == null ? Set.of() : signals.signals(), previousReply);
        current.registerActivity(now);
        Conversation saved = conversations.save(current);
        Set<TurnSignal> recorded = EnumSet.noneOf(TurnSignal.class);
        if (signals != null) {
            recorded.addAll(signals.signals());
        }
        if (ctx.alertFloor().isPresent()) {
            recorded.add(TurnSignal.ALERT_EXPRESSION);
        }
        messages.save(Message.fromAssistant(current.id(), reply, null,
                metadata(failed, priority, recorded, List.of()), now));
        return new Result(current.id(), start.newConversation(), reply, saved.status(), Optional.empty(), failed,
                saved.clinicalPriority(), saved.handoffReason());
    }

    private static ReplyComposer.Plan plan(TurnDecider.Decision decision, boolean possibleEmergency) {
        return new ReplyComposer.Plan(decision.priority().turnLevel(), possibleEmergency, decision.explicitRequest(),
                decision.handoff().handsOff(), decision.handoff().offerHuman());
    }

    private static AssistantMetadata metadata(boolean failed, PriorityPolicy.Assessment priority, Set<TurnSignal> signals,
                                              List<Intent> secondary) {
        return new AssistantMetadata(failed, priority.effective(), priority.source(), signals, secondary);
    }

    /**
     * Encaminhamento (ADR-0007, item 3): {@code WAITING_HUMAN} com motivo e momento e uma
     * {@code ServiceRequest(HUMAN_CONTACT)} pendente, na mesma transação (ADR-0017). Ninguém é notificado.
     */
    private void handOff(Conversation conversation, HandoffReason reason, String customerText, Instant now) {
        conversation.handOff(reason, now);
        boolean alreadyOpen = serviceRequests.findByConversation(conversation.id()).stream()
                .anyMatch(r -> r.type() == RequestType.HUMAN_CONTACT && !r.status().isFinal());
        if (!alreadyOpen) {
            ServiceRequest request = ServiceRequest.open(conversation.id(), RequestType.HUMAN_CONTACT, now);
            conversation.tutorId().ifPresent(tutor -> request.attachTutor(tutor, now));
            request.describe(truncate("[" + reason.name() + "] " + customerText), now);
            request.submit(Set.of(RequestField.SUMMARY), now);
            serviceRequests.save(request);
        }
    }

    /**
     * Lacuna de conhecimento vira pedido de informação para a equipe (ADR-0008, item 4): uma
     * {@code ServiceRequest(INFORMATION)} aberta por conversa. Se já houver uma pendente, a nova dúvida é
     * acrescentada ao resumo. Nenhuma notificação: a equipe vê nas listagens.
     */
    private void recordInformationGap(Conversation conversation, String question, Instant now) {
        ConversationId conversationId = conversation.id();
        Optional<ServiceRequest> open = serviceRequests.findByConversation(conversationId).stream()
                .filter(r -> r.type() == RequestType.INFORMATION)
                .filter(r -> r.status() == RequestStatus.COLLECTING || r.status() == RequestStatus.PENDING)
                .findFirst();
        if (open.isPresent()) {
            ServiceRequest request = open.get();
            String previous = request.summary().orElse("");
            request.describe(truncate(previous.isEmpty() ? question : previous + " | " + question), now);
            serviceRequests.save(request);
            return;
        }
        ServiceRequest request = ServiceRequest.open(conversationId, RequestType.INFORMATION, now);
        conversation.tutorId().ifPresent(tutor -> request.attachTutor(tutor, now));
        request.describe(truncate(question), now);
        request.submit(Set.of(RequestField.SUMMARY), now);
        serviceRequests.save(request);
    }

    private static String truncate(String text) {
        int max = ServiceRequest.MAX_SUMMARY_LENGTH;
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }
}
