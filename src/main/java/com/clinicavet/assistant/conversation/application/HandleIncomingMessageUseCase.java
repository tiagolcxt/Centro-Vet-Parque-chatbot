package com.clinicavet.assistant.conversation.application;

import com.clinicavet.assistant.conversation.application.rules.AlertExpressionDetector;
import com.clinicavet.assistant.conversation.application.rules.ExplicitHumanRequestDetector;
import com.clinicavet.assistant.conversation.domain.AssistantMetadata;
import com.clinicavet.assistant.conversation.domain.ClinicalPriority;
import com.clinicavet.assistant.conversation.domain.Conversation;
import com.clinicavet.assistant.shared.domain.ConversationId;
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
 *       o modelo (até 2 chamadas, validação), também em {@code WAITING_HUMAN} desde a DA-40 (antes, modo restrito
 *       só de sinais); a reconciliação "anuncia transferência" só com a conversa aberta.</li>
 *   <li><b>Transação 2:</b> confere que ninguém mexeu na conversa (senão {@code 409}, DA-21). Aplica tudo de uma
 *       vez (ADR-0017): prioridade (só sobe), intenção, transferência ou pedido registrado ({@code ServiceRequest(HUMAN_CONTACT)},
 *       DA-40), pedido de informação, oferta de atendimento humano e a resposta composta pelo backend, com
 *       o próximo passo ({@link NextStep}: ligar para a recepção).</li>
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
                         ClinicalPriority priority, Optional<HandoffReason> handoffReason,
                         Optional<HandoffReason> staffRequestReason) {

        public Result(ConversationId conversationId, boolean newConversation, String reply, ConversationStatus status,
                      Optional<Intent> intent, boolean fallback, ClinicalPriority priority,
                      Optional<HandoffReason> handoffReason) {
            this(conversationId, newConversation, reply, status, intent, fallback, priority, handoffReason,
                    Optional.empty());
        }
    }

    private record TurnStart(Conversation conversation, boolean newConversation, Message customerMessage,
                             List<Message> recent, Instant at) {

        List<Message> previous() {
            return recent.stream().filter(m -> !m.id().equals(customerMessage.id())).toList();
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
    private final NextStep nextStep;
    private final com.clinicavet.assistant.platform.LiveUpdates live;
    private final Clock clock;
    private DepartmentRouter router;

    /** RF-S37 (Etapa S5). Opcional nos testes de unidade que montam o caso de uso à mão: sem ele, setor padrão. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setRouter(DepartmentRouter router) {
        this.router = router;
    }

    private com.clinicavet.assistant.platform.Clinics clinics;

    /** ADR-0025 (Etapa S5). Sem ele (testes de unidade), nível completo. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setClinics(com.clinicavet.assistant.platform.Clinics clinics) {
        this.clinics = clinics;
    }

    /** Intenções que o nível 2 responde: perguntas sobre a clínica (ADR-0025). */
    private static final Set<Intent> INFORMATIONAL = EnumSet.of(Intent.GREETING, Intent.SERVICE_INFORMATION,
            Intent.PRICE_INQUIRY, Intent.CLINIC_INFORMATION);

    private com.clinicavet.assistant.platform.ActivationLevel level() {
        return clinics == null ? com.clinicavet.assistant.platform.ActivationLevel.FULL
                : com.clinicavet.assistant.platform.CurrentClinic.get().map(clinics::activationLevel)
                .orElse(com.clinicavet.assistant.platform.ActivationLevel.FULL);
    }

    /**
     * ADR-0025: nos níveis 1 e 2 o que o assistente não deve tratar vira encaminhamento para o setor, com o texto de
     * encaminhamento e o próximo passo, sem o texto do modelo. Prioridade e emergência seguem iguais.
     */
    private static boolean forwardOnly(com.clinicavet.assistant.platform.ActivationLevel level, AssistantTurn turn) {
        return switch (level) {
            case ORGANIZE -> true;
            case INFORM -> turn.intent() == null || !INFORMATIONAL.containsAll(turn.allIntents());
            case FULL -> false;
        };
    }

    private static TurnDecider.Decision forwarded(TurnDecider.Decision decision, HandoffReason reason) {
        if (decision.handoff().handsOff()) {
            return new TurnDecider.Decision(decision.priority(), new com.clinicavet.assistant.conversation.domain
                    .HandoffPolicy.Decision(decision.handoff().handoff(), Optional.empty(), false),
                    decision.signals(), decision.explicitRequest());
        }
        return new TurnDecider.Decision(decision.priority(), new com.clinicavet.assistant.conversation.domain
                .HandoffPolicy.Decision(Optional.of(reason), Optional.empty(), false),
                decision.signals(), decision.explicitRequest());
    }

    private static ReplyComposer.Plan forwardPlan(ReplyComposer.Plan p) {
        // explicitRequest = sem o texto do modelo: só encaminhamento e próximo passo (ReplyComposer.composeValid).
        return new ReplyComposer.Plan(p.turnLevel(), p.possibleEmergency(), true, true, false, false, p.nextStep(),
                p.emergencyClosed(), p.department());
    }

    private java.util.UUID route(List<String> refs, String text, Intent intent) {
        return router == null ? null : router.route(refs, text, Optional.ofNullable(intent));
    }

    HandleIncomingMessageUseCase(ConversationRepository conversations, MessageRepository messages,
                                 ServiceRequestRepository serviceRequests, ConversationLifecycle lifecycle,
                                 ContextAssembler contextAssembler, TurnExecutor executor, TurnDecider decider,
                                 ReplyComposer composer, ExplicitHumanRequestDetector humanRequest,
                                 AlertExpressionDetector alerts, TransactionRunner tx, ConversationSettings settings,
                                 HandoffSettings handoffSettings, CustomerDataCollector collector,
                                 CustomerDataSettings customerData, NextStep nextStep,
                                 com.clinicavet.assistant.platform.LiveUpdates live, Clock clock) {
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
        this.nextStep = nextStep;
        this.live = live;
        this.clock = clock;
    }

    /** O turno e a mensagem do tutor que ele gravou (o canal liga a mídia recebida a ela, Etapa S4). */
    public record Turn(Result result, com.clinicavet.assistant.conversation.domain.MessageId customerMessageId) {
    }

    public Result handle(ConversationId target, String text) {
        return handleTurn(target, text).result();
    }

    public Turn handleTurn(ConversationId target, String text) {
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
        // DA-40: também em WAITING_HUMAN o modelo responde (o que é da clínica), com as mesmas validações. A
        // reconciliação "anuncia transferência" só vale com a conversa aberta.
        boolean waiting = conversation.status() == ConversationStatus.WAITING_HUMAN;
        // Etapa S2: assumida por funcionário. O modelo roda só para reavaliar a prioridade (RN-S03); nada é respondido.
        boolean assigned = conversation.status() == ConversationStatus.ASSIGNED;
        TurnOutcome outcome = waiting || assigned
                ? executor.execute(request)
                : executor.execute(request, turn -> decider.reconcile(ctx, turn));

        Result result = tx.inTransaction(() -> finish(start, ctx, outcome, waiting, assigned));
        // RF-S29: as telas da equipe recarregam a conversa (mensagem nova, prioridade, solicitações).
        live.changed(com.clinicavet.assistant.platform.LiveUpdates.Topic.CONVERSATION, result.conversationId().value());
        log.info("Turno concluído [conversationId={}, novaConversa={}, modo={}, falha={}, prioridade={}, "
                        + "encaminhamento={}, pedidoRegistrado={}, pedidoExplicito={}, alerta={}]",
                result.conversationId().value(), result.newConversation(), assigned ? "ASSUMIDA" : waiting ? "ESPERA" : "PRINCIPAL",
                result.fallback(), result.priority(), result.handoffReason().map(Enum::name).orElse("-"),
                result.staffRequestReason().map(Enum::name).orElse("-"),
                ctx.explicitRequest().orElse("-"), ctx.alertFloor().map(Enum::name).orElse("-"));
        return new Turn(result, start.customerMessage().id());
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

    private Result finish(TurnStart start, TurnDecider.Context ctx, TurnOutcome outcome, boolean waiting,
                          boolean assigned) {
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
        if (assigned) {
            return finishAssigned(start, ctx, current, outcome, now);
        }
        return waiting ? finishWaiting(start, ctx, current, outcome, now) : finishMain(start, ctx, current, outcome, now);
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
        java.util.UUID department;
        com.clinicavet.assistant.platform.ActivationLevel level = level();
        switch (outcome) {
            case TurnOutcome.Valid valid -> {
                AssistantTurn turn = valid.turn();
                decision = decider.decideValid(ctx, turn);
                department = route(turn.knowledgeRefs(), customerText, turn.intent());
                boolean forward = forwardOnly(level, turn);
                if (forward) {
                    decision = forwarded(decision, HandoffReason.CLINIC_RULE);
                }
                // Lacuna de informação (ADR-0008) também termina com o próximo passo: o pedido de informação está
                // registrado, e o tutor sabe como falar com a equipe (DA-40).
                // Sem repetir (DA-41): se a resposta anterior já indicou a recepção, lacuna e pedido registrado não
                // repetem o aviso nem o telefone. O pedido continua registrado para a equipe. Transferência, pedido
                // explícito e emergência sempre trazem o próximo passo.
                boolean recentlyGuided = alreadyGaveNextStep(start);
                ReplyComposer.Plan plan = plan(decision, turn.possibleEmergency(), turn.knowledgeGap(), recentlyGuided, false,
                        department);
                if (forward) {
                    plan = forwardPlan(plan);
                }
                reply = composer.composeValid(plan, turn.reply());
                intent = turn.intent();
                secondary = turn.secondaryIntents();
                current.recordIntent(turn.intent());
                if (turn.knowledgeGap()) {
                    recordInformationGap(current, customerText, department, now);
                }
                // Etapa 5: dados do tutor e do pet, proveniência e pedido de agendamento (ADR-0010, DA-35 a DA-37).
                CustomerDataCollector.Outcome collected = collector.collect(current, turn, start.recent(),
                        start.customerMessage(), decision.handoff().handsOff(), now);
                if (collected.appointmentSubmitted() && !plan.emergency() && !forward) {
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
                department = route(List.of(), customerText, null);
                if (level == com.clinicavet.assistant.platform.ActivationLevel.ORGANIZE) {
                    // Nível 1 não conversa: sem resposta segura genérica, só o encaminhamento.
                    decision = forwarded(decision, HandoffReason.CLINIC_RULE);
                }
                reply = composer.composeFailure(plan(decision, false, false, false, true, department), settings.fallbackReply(),
                        current.clinicalPriority());
                failed = true;
            }
        }
        current.raisePriority(decision.priority().effective());
        decision.handoff().handoff().ifPresent(reason -> handOff(current, reason, customerText, department, now));
        // DA-40: pedido registrado para a equipe, e a conversa continua com o assistente.
        decision.handoff().register().ifPresent(reason -> openStaffRequest(current, reason, customerText, department,
                now));
        if (decision.handoff().offerHuman()) {
            current.markHumanOfferMade();
        }
        current.registerActivity(now);
        Conversation saved = conversations.save(current);
        messages.save(Message.fromAssistant(id, reply, intent,
                metadata(failed, decision.priority(), decision.signals(), secondary), now));
        return new Result(id, start.newConversation(), reply, saved.status(), Optional.ofNullable(intent), failed,
                saved.clinicalPriority(), decision.handoff().handoff(), decision.handoff().register());
    }

    /**
     * {@code WAITING_HUMAN} (DA-40): a equipe já tem a conversa. O modelo responde o que é da clínica (horário,
     * serviços, andamento) e os detectores continuam: a prioridade é reavaliada e só sobe; risco põe a orientação
     * primeiro. Nada de nova transferência nem de coleta de agendamento. Falha do modelo: confirmação de recebimento
     * (fail-safe, CT-19).
     */
    private Result finishWaiting(TurnStart start, TurnDecider.Context ctx, Conversation current, TurnOutcome outcome,
                                 Instant now) {
        boolean overdue = current.handoffAt()
                .map(at -> now.isAfter(at.plus(handoffSettings.deadlineFor(current.clinicalPriority()))))
                .orElse(false);
        String previousReply = start.previous().stream().filter(m -> m.author() == MessageAuthor.ASSISTANT)
                .reduce((a, b) -> b).map(Message::content).orElse(null);
        Set<TurnSignal> recorded = EnumSet.noneOf(TurnSignal.class);
        if (ctx.alertFloor().isPresent()) {
            recorded.add(TurnSignal.ALERT_EXPRESSION);
        }
        String step = nextStep.text().orElse(null);
        String closed = nextStep.emergencyWhenClosed().orElse(null);
        PriorityPolicy.Assessment priority;
        String reply;
        Intent intent = null;
        List<Intent> secondary = List.of();
        boolean failed;
        switch (outcome) {
            case TurnOutcome.Valid valid -> {
                AssistantTurn turn = valid.turn();
                priority = PriorityPolicy.assess(current.clinicalPriority(), new PriorityPolicy.Inputs(turn.priority(),
                        turn.allIntents(), turn.signals(), turn.possibleEmergency(), turn.clinicalUncertainty(),
                        ctx.alertFloor()));
                recorded.addAll(turn.signals());
                ReplyComposer.WaitingPlan waitingPlan = new ReplyComposer.WaitingPlan(priority.turnLevel(),
                        turn.possibleEmergency(), overdue, turn.signals(), previousReply, step, closed);
                // ADR-0025: nível 1 só confirma o recebimento; nível 2 responde só o que é da clínica.
                reply = forwardOnly(level(), turn) ? composer.composeWaiting(waitingPlan, false)
                        : composer.composeWaitingValid(waitingPlan, turn.reply());
                intent = turn.intent();
                secondary = turn.secondaryIntents();
                current.recordIntent(turn.intent());
                if (turn.knowledgeGap()) {
                    String text = start.customerMessage().content();
                    recordInformationGap(current, text, route(turn.knowledgeRefs(), text, turn.intent()), now);
                }
                collector.collect(current, turn, start.recent(), start.customerMessage(), true, now);
                failed = false;
            }
            case TurnOutcome.Failed failure -> {
                priority = PriorityPolicy.assess(current.clinicalPriority(),
                        PriorityPolicy.Inputs.deterministicOnly(ctx.alertFloor()));
                reply = composer.composeWaiting(new ReplyComposer.WaitingPlan(priority.turnLevel(), false, overdue,
                        Set.of(), previousReply, step, closed), true);
                failed = true;
            }
        }
        current.raisePriority(priority.effective());
        current.registerActivity(now);
        Conversation saved = conversations.save(current);
        messages.save(Message.fromAssistant(current.id(), reply, intent,
                metadata(failed, priority, recorded, secondary), now));
        return new Result(current.id(), start.newConversation(), reply, saved.status(), Optional.ofNullable(intent),
                failed, saved.clinicalPriority(), saved.handoffReason());
    }

    /**
     * {@code ASSIGNED} (Etapa S2, DA-47): um funcionário está com a conversa. O assistente não responde (resposta
     * {@code null}: nada a enviar ao tutor), mas a prioridade é reavaliada pelo modelo e pelas expressões de alerta e
     * só sobe (RN-S03). A tela da equipe destaca a conversa quando ela sobe (RF-S28).
     */
    private Result finishAssigned(TurnStart start, TurnDecider.Context ctx, Conversation current, TurnOutcome outcome,
                                  Instant now) {
        PriorityPolicy.Assessment priority = switch (outcome) {
            case TurnOutcome.Valid valid -> PriorityPolicy.assess(current.clinicalPriority(),
                    new PriorityPolicy.Inputs(valid.turn().priority(), valid.turn().allIntents(), valid.turn().signals(),
                            valid.turn().possibleEmergency(), valid.turn().clinicalUncertainty(), ctx.alertFloor()));
            case TurnOutcome.Failed failure -> PriorityPolicy.assess(current.clinicalPriority(),
                    PriorityPolicy.Inputs.deterministicOnly(ctx.alertFloor()));
        };
        current.raisePriority(priority.effective());
        current.registerActivity(now);
        Conversation saved = conversations.save(current);
        return new Result(current.id(), start.newConversation(), null, saved.status(), Optional.empty(),
                outcome instanceof TurnOutcome.Failed, saved.clinicalPriority(), saved.handoffReason());
    }

    /** A última resposta do assistente nesta conversa já trouxe o próximo passo de agora? */
    private boolean alreadyGaveNextStep(TurnStart start) {
        Optional<String> step = nextStep.text();
        return step.isPresent() && start.previous().stream()
                .filter(m -> m.author() == MessageAuthor.ASSISTANT)
                .reduce((a, b) -> b)
                .map(m -> m.content().contains(step.get()))
                .orElse(false);
    }

    /**
     * @param recentlyGuided a resposta anterior já trouxe o próximo passo: pedido registrado e lacuna não o repetem
     * @param failure        turno com falha: a resposta segura sempre traz o próximo passo
     */
    private ReplyComposer.Plan plan(TurnDecider.Decision decision, boolean possibleEmergency, boolean knowledgeGap,
                                    boolean recentlyGuided, boolean failure, java.util.UUID department) {
        boolean mustGuide = failure || decision.handoff().handsOff() || decision.explicitRequest() || possibleEmergency;
        boolean register = (decision.handoff().registers() || knowledgeGap) && !recentlyGuided;
        String step = mustGuide || register ? nextStep.text().orElse(null) : null;
        return new ReplyComposer.Plan(decision.priority().turnLevel(), possibleEmergency, decision.explicitRequest(),
                decision.handoff().handsOff(), register, decision.handoff().offerHuman(), step,
                nextStep.emergencyWhenClosed().orElse(null),
                router == null ? null : router.nameFor(decision.priority().turnLevel().isUrgentOrWorse() ? null : department));
    }

    private static AssistantMetadata metadata(boolean failed, PriorityPolicy.Assessment priority, Set<TurnSignal> signals,
                                              List<Intent> secondary) {
        return new AssistantMetadata(failed, priority.effective(), priority.source(), signals, secondary);
    }

    /**
     * Encaminhamento (ADR-0007, item 3): {@code WAITING_HUMAN} com motivo e momento e uma
     * {@code ServiceRequest(HUMAN_CONTACT)} pendente, na mesma transação (ADR-0017). Ninguém é notificado.
     */
    private void handOff(Conversation conversation, HandoffReason reason, String customerText,
                         java.util.UUID department, Instant now) {
        // RF-S61: a conversa espera no setor da regra (sem regra, o padrão). P1/P2 ficam no padrão: a recepção
        // atende primeiro a emergência, qualquer que seja o assunto.
        boolean urgent = conversation.clinicalPriority().isUrgentOrWorse();
        conversation.handOff(reason, now, urgent ? null : department);
        openStaffRequest(conversation, reason, customerText, urgent ? null : department, now);
    }

    /**
     * {@code ServiceRequest(HUMAN_CONTACT)} pendente com o motivo, uma aberta por conversa: na transferência e no
     * pedido registrado sem transferir (DA-40). A equipe vê na listagem; ninguém é notificado.
     */
    private void openStaffRequest(Conversation conversation, HandoffReason reason, String customerText,
                                  java.util.UUID department, Instant now) {
        boolean alreadyOpen = serviceRequests.findByConversation(conversation.id()).stream()
                .anyMatch(r -> r.type() == RequestType.HUMAN_CONTACT && !r.status().isFinal());
        if (!alreadyOpen) {
            ServiceRequest request = ServiceRequest.open(conversation.id(), RequestType.HUMAN_CONTACT, now);
            conversation.tutorId().ifPresent(tutor -> request.attachTutor(tutor, now));
            request.describe(truncate("[" + reason.name() + "] " + customerText), now);
            if (department != null) {
                request.routeTo(department, now);
            }
            request.submit(Set.of(RequestField.SUMMARY), now);
            serviceRequests.save(request);
        }
    }

    /**
     * Lacuna de conhecimento vira pedido de informação para a equipe (ADR-0008, item 4): uma
     * {@code ServiceRequest(INFORMATION)} aberta por conversa. Se já houver uma pendente, a nova dúvida é
     * acrescentada ao resumo. Nenhuma notificação: a equipe vê nas listagens.
     */
    private void recordInformationGap(Conversation conversation, String question, java.util.UUID department,
                                      Instant now) {
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
        if (department != null) {
            request.routeTo(department, now);
        }
        request.submit(Set.of(RequestField.SUMMARY), now);
        serviceRequests.save(request);
    }

    private static String truncate(String text) {
        int max = ServiceRequest.MAX_SUMMARY_LENGTH;
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }
}
