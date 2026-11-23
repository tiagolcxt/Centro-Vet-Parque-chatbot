package com.clinicavet.assistant.conversation.application;

import com.clinicavet.assistant.clinicknowledge.domain.Departments;
import com.clinicavet.assistant.conversation.domain.ClinicalPriority;
import com.clinicavet.assistant.conversation.domain.Conversation;
import com.clinicavet.assistant.conversation.domain.ConversationRepository;
import com.clinicavet.assistant.conversation.domain.ConversationStatus;
import com.clinicavet.assistant.conversation.domain.Message;
import com.clinicavet.assistant.conversation.domain.MessageRepository;
import com.clinicavet.assistant.conversation.domain.PriorityChange;
import com.clinicavet.assistant.platform.AccountDisabled;
import com.clinicavet.assistant.platform.AuditLog;
import com.clinicavet.assistant.platform.LiveUpdates;
import com.clinicavet.assistant.platform.SectorScope;
import com.clinicavet.assistant.platform.StaffUser;
import com.clinicavet.assistant.shared.domain.ConversationId;
import com.clinicavet.assistant.shared.domain.InternalNote;
import com.clinicavet.assistant.shared.exception.DomainValidationException;
import com.clinicavet.assistant.shared.exception.InvalidStateTransitionException;
import com.clinicavet.assistant.shared.exception.ResourceNotFoundException;
import com.clinicavet.assistant.team.StaffDirectory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Caixa de entrada da equipe (Etapa S2; RF-S20 a RF-S29; ADR-0007 revisto, DA-47). Não altera solicitações.
 * <ul>
 *   <li><b>Escopo de setor</b> (RN-S11): quem tem "meus setores" vê as conversas encaminhadas a eles, as que assumiu
 *       e, se trabalha no setor padrão (Recepção), as que estão só com o assistente;</li>
 *   <li><b>Assumir</b>: um responsável por vez (RN-S05). O tutor recebe o texto "fulano vai continuar", nunca
 *       "avisei";</li>
 *   <li>fora do escopo = {@code 404}; transição inválida = {@code 409}.</li>
 * </ul>
 */
@Service
public class StaffConversationService {

    /** Uma conversa na caixa de entrada, com o atraso calculado pelo prazo do nível (H10, H11). */
    public record QueueItem(Conversation conversation, Optional<Duration> waiting, boolean overdue) {
    }

    public enum Filter {
        /** Tudo o que a pessoa enxerga. */
        ALL,
        /** Aguardando alguém da equipe (encaminhadas e sem responsável). */
        WAITING,
        /** Assumidas por quem pediu. */
        MINE
    }

    public record Detail(Conversation conversation, List<Message> messages, List<InternalNote> notes,
                         List<PriorityChange> priorityChanges) {
    }

    static final int MAX_INBOX = 300;
    static final int MAX_MESSAGES = 500;

    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final TransactionRunner tx;
    private final HandoffSettings settings;
    private final StaffSettings staffTexts;
    private final Departments departments;
    private final StaffDirectory staff;
    private final AuditLog audit;
    private final LiveUpdates live;
    private final Clock clock;

    StaffConversationService(ConversationRepository conversations, MessageRepository messages, TransactionRunner tx,
                             HandoffSettings settings, StaffSettings staffTexts, Departments departments,
                             StaffDirectory staff, AuditLog audit, LiveUpdates live, Clock clock) {
        this.conversations = conversations;
        this.messages = messages;
        this.tx = tx;
        this.settings = settings;
        this.staffTexts = staffTexts;
        this.departments = departments;
        this.staff = staff;
        this.audit = audit;
        this.live = live;
        this.clock = clock;
    }

    /** Caixa de entrada: mais urgente primeiro e, no mesmo nível, quem espera há mais tempo (H10, RF-S20). */
    public List<QueueItem> inbox(StaffUser user, Filter filter) {
        Instant now = clock.instant();
        Visibility visibility = visibility(user);
        return conversations.findInbox(MAX_INBOX).stream()
                .filter(visibility::sees)
                .filter(c -> switch (filter) {
                    case ALL -> true;
                    case WAITING -> c.status() == ConversationStatus.WAITING_HUMAN;
                    case MINE -> c.assigneeId().map(user.userId()::equals).orElse(false);
                })
                .map(c -> {
                    Optional<Duration> waiting = c.status() == ConversationStatus.WAITING_HUMAN
                            ? c.handoffAt().map(at -> Duration.between(at, now)) : Optional.empty();
                    boolean overdue = waiting.map(w -> w.compareTo(settings.deadlineFor(c.clinicalPriority())) > 0)
                            .orElse(false);
                    return new QueueItem(c, waiting, overdue);
                })
                .toList();
    }

    public Detail detail(StaffUser user, ConversationId id) {
        Conversation conversation = visible(user, id);
        return new Detail(conversation, messages.findPage(id, 0, MAX_MESSAGES), conversations.findNotes(id),
                conversations.findPriorityChanges(id));
    }

    /** RF-S22: assume. O assistente para; o tutor recebe "fulano (setor) vai continuar". */
    public Conversation assume(StaffUser user, ConversationId id) {
        Conversation saved = tx.inTransaction(() -> {
            Conversation conversation = load(user, id);
            if (conversation.status() == ConversationStatus.ASSIGNED
                    && conversation.assigneeId().filter(user.userId()::equals).isPresent()) {
                return conversation;
            }
            if (conversation.status() == ConversationStatus.ASSIGNED) {
                String who = conversation.assigneeId().flatMap(staff::find).map(StaffDirectory.Member::displayName)
                        .orElse("outra pessoa");
                throw new InvalidStateTransitionException("Conversa", ConversationStatus.ASSIGNED,
                        "assumir (já assumida por " + who + ")");
            }
            Instant now = clock.instant();
            conversation.assume(user.userId(), now);
            Conversation result = conversations.save(conversation);
            String name = staff.find(user.userId()).map(StaffDirectory.Member::displayName).orElse("Nossa equipe");
            String department = departmentName(result);
            messages.save(Message.fromAssistant(id, staffTexts.assumed(name, department), null, now));
            audit.record("conversation.assumed", "conversation", id.value(), null);
            return result;
        });
        live.changed(LiveUpdates.Topic.CONVERSATION, id.value());
        return saved;
    }

    /** WhatsApp: a resposta livre vale até 24 horas depois da última mensagem do tutor (ADR-0023, item 6). */
    public static final java.time.Duration REPLY_WINDOW = java.time.Duration.ofHours(24);

    /** Fim da janela de resposta livre; vazio fora do WhatsApp (a API de testes não tem janela). */
    public java.util.Optional<Instant> replyWindowEnd(Conversation conversation) {
        if (conversation.contact().channel() != com.clinicavet.assistant.shared.domain.Channel.WHATSAPP) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(messages.lastCustomerMessageAt(conversation.id())
                .map(at -> at.plus(REPLY_WINDOW)).orElse(Instant.EPOCH));
    }

    /** RF-S23: resposta do funcionário. Exige ter assumido (um responsável por vez, RN-S05). */
    public Message reply(StaffUser user, ConversationId id, String text) {
        if (text == null || text.isBlank()) {
            throw new DomainValidationException("text", "não pode ser vazio");
        }
        Message saved = tx.inTransaction(() -> {
            Conversation conversation = load(user, id);
            if (conversation.status() != ConversationStatus.ASSIGNED
                    || !conversation.assigneeId().map(user.userId()::equals).orElse(false)) {
                throw new InvalidStateTransitionException("Conversa", conversation.status(),
                        "responder (assuma a conversa antes)");
            }
            Instant now = clock.instant();
            if (replyWindowEnd(conversation).map(now::isAfter).orElse(false)) {
                throw new com.clinicavet.assistant.shared.exception.ConflictException(
                        "Passaram 24 horas desde a última mensagem do tutor: o WhatsApp não permite resposta livre. "
                                + "Ligue para o tutor ou espere ele escrever de novo.");
            }
            conversation.registerActivity(now);
            conversations.save(conversation);
            return messages.save(Message.fromStaff(id, text.strip(), user.userId(), now));
        });
        live.changed(LiveUpdates.Topic.CONVERSATION, id.value());
        return saved;
    }

    /** RF-S24: devolve ao assistente, com motivo (vira nota interna). */
    public Conversation returnToAssistant(StaffUser user, ConversationId id, String reason) {
        Conversation saved = tx.inTransaction(() -> {
            Conversation conversation = load(user, id);
            Instant now = clock.instant();
            conversation.returnToAssistant(now);
            Conversation result = conversations.save(conversation);
            if (reason != null && !reason.isBlank()) {
                conversations.saveNote(id, InternalNote.by(user.userId(), "Devolvida ao assistente: " + reason.strip(),
                        now));
            }
            audit.record("conversation.returned", "conversation", id.value(), null);
            return result;
        });
        live.changed(LiveUpdates.Topic.CONVERSATION, id.value());
        return saved;
    }

    /** RF-S25: transfere para outro setor, com motivo obrigatório (RN-S06, vira nota interna). */
    public Conversation transfer(StaffUser user, ConversationId id, UUID departmentId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new DomainValidationException("reason", "obrigatório ao transferir");
        }
        if (departments.activeAmong(Set.of(departmentId)).isEmpty()) {
            throw new DomainValidationException("departmentId", "setor inexistente ou inativo");
        }
        Conversation saved = tx.inTransaction(() -> {
            Conversation conversation = load(user, id);
            Instant now = clock.instant();
            conversation.transfer(departmentId, now);
            Conversation result = conversations.save(conversation);
            String to = departments.find(departmentId).map(Departments.Department::name).orElse("outro setor");
            conversations.saveNote(id, InternalNote.by(user.userId(), "Transferida para " + to + ": " + reason.strip(),
                    now));
            audit.record("conversation.transferred", "conversation", id.value(), "para " + to);
            return result;
        });
        live.changed(LiveUpdates.Topic.CONVERSATION, id.value());
        return saved;
    }

    /** RF-S26: nota interna. Nunca vai ao tutor nem ao modelo (RN-S10). */
    public InternalNote addNote(StaffUser user, ConversationId id, String text) {
        InternalNote note = tx.inTransaction(() -> {
            load(user, id);
            InternalNote created = InternalNote.by(user.userId(), text, clock.instant());
            conversations.saveNote(id, created);
            return created;
        });
        live.changed(LiveUpdates.Topic.CONVERSATION, id.value());
        return note;
    }

    /** RF-S27. Não resolve solicitações (ADR-0011). */
    public Conversation close(StaffUser user, ConversationId id) {
        Conversation saved = tx.inTransaction(() -> {
            Conversation conversation = load(user, id);
            conversation.closeByStaff(clock.instant());
            Conversation result = conversations.save(conversation);
            audit.record("conversation.closed", "conversation", id.value(), null);
            return result;
        });
        live.changed(LiveUpdates.Topic.CONVERSATION, id.value());
        return saved;
    }

    /** Único caminho de rebaixamento (H4): registra nível anterior, novo, autor e momento. */
    public Conversation changePriority(StaffUser user, ConversationId id, ClinicalPriority priority, String note) {
        Conversation saved = tx.inTransaction(() -> {
            Conversation conversation = load(user, id);
            Instant now = clock.instant();
            ClinicalPriority previous = conversation.changePriorityByStaff(priority);
            Conversation result = conversations.save(conversation);
            conversations.savePriorityChange(PriorityChange.byStaff(id, previous, priority, note, now,
                    user.userId().toString()));
            audit.record("conversation.priority_changed", "conversation", id.value(), previous + " -> " + priority);
            return result;
        });
        live.changed(LiveUpdates.Topic.CONVERSATION, id.value());
        return saved;
    }

    /** RN-S07: conversas assumidas por quem foi desativado voltam a aguardar no mesmo setor. */
    @EventListener
    void onAccountDisabled(AccountDisabled event) {
        Instant now = clock.instant();
        for (Conversation conversation : conversations.findAssignedTo(event.userId())) {
            conversation.releaseAssignee(now);
            conversations.save(conversation);
            live.changed(LiveUpdates.Topic.CONVERSATION, conversation.id().value());
        }
    }

    private String departmentName(Conversation conversation) {
        return conversation.waitingDepartmentId().flatMap(departments::find)
                .orElseGet(departments::defaultDepartment).name();
    }

    private Conversation load(StaffUser user, ConversationId id) {
        Conversation conversation = conversations.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Conversa", id.value()));
        if (!visibility(user).sees(conversation)) {
            throw new ResourceNotFoundException("Conversa", id.value());
        }
        return conversation;
    }

    private Conversation visible(StaffUser user, ConversationId id) {
        Conversation conversation = conversations.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Conversa", id.value()));
        if (!visibility(user).sees(conversation)) {
            throw new ResourceNotFoundException("Conversa", id.value());
        }
        return conversation;
    }

    /** RN-S11. Sem setor de encaminhamento = setor padrão. */
    private Visibility visibility(StaffUser user) {
        if (user.scope() == SectorScope.ALL) {
            return new Visibility(user.userId(), null, null);
        }
        return new Visibility(user.userId(), staff.departmentsOf(user.userId()), departments.defaultDepartment().id());
    }

    private record Visibility(UUID userId, Set<UUID> departments, UUID defaultDepartment) {

        boolean sees(Conversation c) {
            if (departments == null) {
                return true;
            }
            if (c.assigneeId().map(userId::equals).orElse(false)) {
                return true;
            }
            if (c.status() == ConversationStatus.OPEN || c.status() == ConversationStatus.CLOSED) {
                return departments.contains(defaultDepartment);
            }
            return departments.contains(c.waitingDepartmentId().orElse(defaultDepartment));
        }
    }
}
