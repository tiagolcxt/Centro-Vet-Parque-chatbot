package com.clinicavet.assistant.conversation.domain;

import com.clinicavet.assistant.shared.domain.ConversationId;
import com.clinicavet.assistant.shared.domain.ContactIdentity;
import com.clinicavet.assistant.shared.domain.Timestamps;
import com.clinicavet.assistant.shared.exception.DomainValidationException;
import com.clinicavet.assistant.shared.exception.InvalidStateTransitionException;
import com.clinicavet.assistant.tutor.domain.TutorId;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Um atendimento contínuo de um contato (ADR-0009).
 *
 * <pre>
 *  start ──► OPEN ──handOff──► WAITING_HUMAN
 *             ▲                     │
 *             └──returnToAssistant──┘
 *  OPEN / WAITING_HUMAN ──closeByStaff / expire──► CLOSED (final)
 * </pre>
 *
 * A prioridade clínica é independente do status (ADR-0018, H9): só sobe automaticamente; só a equipe a
 * rebaixa. {@code WAITING_HUMAN} significa "sinalizada para revisão humana", não que alguém foi
 * notificado (ADR-0007).
 */
public final class Conversation {

    private static final String AGGREGATE = "Conversation";

    private final ConversationId id;
    private final ContactIdentity contact;
    private TutorId tutorId;
    private ConversationStatus status;
    private Intent currentIntent;
    private CloseReason closeReason;
    private final Instant startedAt;
    private Instant lastActivityAt;
    private Instant closedAt;
    private ClinicalPriority clinicalPriority;
    private HandoffReason handoffReason;
    private Instant handoffAt;
    private boolean humanOfferMade;
    private final Long version;

    private Conversation(ConversationId id, ContactIdentity contact, TutorId tutorId,
                         ConversationStatus status, Intent currentIntent, CloseReason closeReason,
                         Instant startedAt, Instant lastActivityAt, Instant closedAt,
                         ClinicalPriority clinicalPriority, HandoffReason handoffReason, Instant handoffAt,
                         boolean humanOfferMade, Long version) {
        this.id = Objects.requireNonNull(id, "id");
        this.contact = Objects.requireNonNull(contact, "contact");
        this.tutorId = tutorId;
        this.status = Objects.requireNonNull(status, "status");
        this.currentIntent = currentIntent;
        this.closeReason = closeReason;
        this.startedAt = Timestamps.normalize(startedAt);
        this.lastActivityAt = Timestamps.normalize(lastActivityAt);
        this.closedAt = Timestamps.normalizeNullable(closedAt);
        this.clinicalPriority = Objects.requireNonNull(clinicalPriority, "clinicalPriority");
        this.handoffReason = handoffReason;
        this.handoffAt = Timestamps.normalizeNullable(handoffAt);
        this.humanOfferMade = humanOfferMade;
        this.version = version;
        checkConsistency();
    }

    public static Conversation start(ContactIdentity contact, Instant now) {
        return new Conversation(ConversationId.newId(), contact, null, ConversationStatus.OPEN,
                null, null, now, now, null, ClinicalPriority.INITIAL, null, null, false, null);
    }

    /** Reconstitui a partir da persistência, validando a consistência do estado. */
    public static Conversation restore(ConversationId id, ContactIdentity contact, TutorId tutorId,
                                       ConversationStatus status, Intent currentIntent,
                                       CloseReason closeReason, Instant startedAt,
                                       Instant lastActivityAt, Instant closedAt,
                                       ClinicalPriority clinicalPriority, HandoffReason handoffReason,
                                       Instant handoffAt, boolean humanOfferMade, Long version) {
        return new Conversation(id, contact, tutorId, status, currentIntent, closeReason, startedAt,
                lastActivityAt, closedAt, clinicalPriority, handoffReason, handoffAt, humanOfferMade, version);
    }

    /** Reconstituição sem os campos da Etapa 4 (prioridade inicial, sem encaminhamento). */
    public static Conversation restore(ConversationId id, ContactIdentity contact, TutorId tutorId,
                                       ConversationStatus status, Intent currentIntent,
                                       CloseReason closeReason, Instant startedAt,
                                       Instant lastActivityAt, Instant closedAt, Long version) {
        return restore(id, contact, tutorId, status, currentIntent, closeReason, startedAt, lastActivityAt,
                closedAt, ClinicalPriority.INITIAL, null, null, false, version);
    }

    /** Registra atividade (mensagem recebida ou enviada). Só em conversa ativa. */
    public void registerActivity(Instant at) {
        requireActive("registerActivity");
        Instant normalized = Timestamps.normalize(at);
        if (normalized.isAfter(lastActivityAt)) {
            lastActivityAt = normalized;
        }
    }

    /**
     * Sinaliza a conversa para revisão humana, com o motivo (ADR-0007, item 3). Não notifica ninguém.
     * A solicitação {@code HUMAN_CONTACT} é criada pelo turno, na mesma transação (ADR-0017).
     */
    public void handOff(HandoffReason reason, Instant at) {
        Objects.requireNonNull(reason, "reason");
        requireStatus(ConversationStatus.OPEN, "handOff");
        status = ConversationStatus.WAITING_HUMAN;
        handoffReason = reason;
        handoffAt = Timestamps.normalize(at);
        registerActivity(at);
    }

    /** A equipe devolve a conversa ao assistente (ADR-0011). A prioridade não muda (H4). */
    public void returnToAssistant(Instant at) {
        requireStatus(ConversationStatus.WAITING_HUMAN, "returnToAssistant");
        status = ConversationStatus.OPEN;
        handoffReason = null;
        handoffAt = null;
        registerActivity(at);
    }

    /** Encerramento pela equipe. Não resolve solicitações (ADR-0011). */
    public void closeByStaff(Instant at) {
        requireActive("closeByStaff");
        close(CloseReason.CLOSED_BY_STAFF, at);
    }

    /**
     * Eleva a prioridade, se o novo nível for mais urgente. Nunca rebaixa (H4).
     *
     * @return {@code true} se mudou
     */
    public boolean raisePriority(ClinicalPriority candidate) {
        Objects.requireNonNull(candidate, "candidate");
        requireActive("raisePriority");
        if (candidate.isMoreUrgentThan(clinicalPriority)) {
            clinicalPriority = candidate;
            return true;
        }
        return false;
    }

    /**
     * Mudança feita pela equipe: o único caminho de rebaixamento (H4). O registro de quem e quando fica em
     * {@link PriorityChange}.
     *
     * @return o nível anterior
     */
    public ClinicalPriority changePriorityByStaff(ClinicalPriority newPriority) {
        Objects.requireNonNull(newPriority, "newPriority");
        requireActive("changePriority");
        ClinicalPriority previous = clinicalPriority;
        clinicalPriority = newPriority;
        return previous;
    }

    /** A oferta de atendimento humano é feita no máximo uma vez por conversa (DA-31). */
    public void markHumanOfferMade() {
        requireActive("markHumanOfferMade");
        humanOfferMade = true;
    }

    /**
     * A conversa expirou se o contato ficou inativo além do prazo do status atual. Avaliado quando o
     * contato volta a escrever; não há job agendado (ADR-0009).
     */
    public boolean isExpired(Instant now, ConversationExpirationPolicy policy) {
        Objects.requireNonNull(policy, "policy");
        if (!status.isActive()) {
            return false;
        }
        return Timestamps.normalize(now).isAfter(lastActivityAt.plus(policy.timeoutFor(status)));
    }

    /** Encerra por inatividade. Só é permitido se a conversa de fato expirou. */
    public void expire(Instant now, ConversationExpirationPolicy policy) {
        if (!isExpired(now, policy)) {
            throw new InvalidStateTransitionException(AGGREGATE, status, "expire (prazo não vencido)");
        }
        close(CloseReason.EXPIRED, now);
    }

    public void recordIntent(Intent intent) {
        requireActive("recordIntent");
        this.currentIntent = Objects.requireNonNull(intent, "intent");
    }

    /** Vincula o tutor identificado. Uma conversa nunca troca de tutor. */
    public void linkTutor(TutorId tutorId) {
        Objects.requireNonNull(tutorId, "tutorId");
        requireActive("linkTutor");
        if (this.tutorId != null && !this.tutorId.equals(tutorId)) {
            throw new DomainValidationException("tutorId", "a conversa já está vinculada a outro tutor");
        }
        this.tutorId = tutorId;
    }

    private void close(CloseReason reason, Instant at) {
        Instant normalized = Timestamps.normalize(at);
        status = ConversationStatus.CLOSED;
        closeReason = reason;
        closedAt = normalized.isBefore(lastActivityAt) ? lastActivityAt : normalized;
    }

    private void requireActive(String action) {
        if (!status.isActive()) {
            throw new InvalidStateTransitionException(AGGREGATE, status, action);
        }
    }

    private void requireStatus(ConversationStatus expected, String action) {
        if (status != expected) {
            throw new InvalidStateTransitionException(AGGREGATE, status, action);
        }
    }

    private void checkConsistency() {
        boolean closed = status == ConversationStatus.CLOSED;
        if (closed != (closeReason != null) || closed != (closedAt != null)) {
            throw new IllegalStateException(
                    "conversa inconsistente: status=%s, closeReason=%s, closedAt=%s"
                            .formatted(status, closeReason, closedAt));
        }
        if (status == ConversationStatus.WAITING_HUMAN && (handoffReason == null || handoffAt == null)) {
            throw new IllegalStateException("conversa em WAITING_HUMAN sem motivo ou momento do encaminhamento");
        }
    }

    public ConversationId id() {
        return id;
    }

    public ContactIdentity contact() {
        return contact;
    }

    public Optional<TutorId> tutorId() {
        return Optional.ofNullable(tutorId);
    }

    public ConversationStatus status() {
        return status;
    }

    public Optional<Intent> currentIntent() {
        return Optional.ofNullable(currentIntent);
    }

    public Optional<CloseReason> closeReason() {
        return Optional.ofNullable(closeReason);
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Instant lastActivityAt() {
        return lastActivityAt;
    }

    public Optional<Instant> closedAt() {
        return Optional.ofNullable(closedAt);
    }

    public ClinicalPriority clinicalPriority() {
        return clinicalPriority;
    }

    public Optional<HandoffReason> handoffReason() {
        return Optional.ofNullable(handoffReason);
    }

    public Optional<Instant> handoffAt() {
        return Optional.ofNullable(handoffAt);
    }

    public boolean humanOfferMade() {
        return humanOfferMade;
    }

    /** Versão de concorrência (lock otimista, DM-16/DA-21); {@code null} antes de persistir. */
    public Long version() {
        return version;
    }
}
