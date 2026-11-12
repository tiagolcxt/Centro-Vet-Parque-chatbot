package com.clinicavet.assistant.conversation.domain;

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
 *  start ──► OPEN ──waitForHuman──► WAITING_HUMAN
 *             ▲                          │
 *             └────returnToAssistant─────┘
 *  OPEN / WAITING_HUMAN ──closeByStaff / expire──► CLOSED (final)
 * </pre>
 *
 * Detalhes do encaminhamento (motivo, momento) e a prioridade clínica entram na Etapa 4
 * (ADR-0007, ADR-0018).
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
    private final Long version;

    private Conversation(ConversationId id, ContactIdentity contact, TutorId tutorId,
                         ConversationStatus status, Intent currentIntent, CloseReason closeReason,
                         Instant startedAt, Instant lastActivityAt, Instant closedAt, Long version) {
        this.id = Objects.requireNonNull(id, "id");
        this.contact = Objects.requireNonNull(contact, "contact");
        this.tutorId = tutorId;
        this.status = Objects.requireNonNull(status, "status");
        this.currentIntent = currentIntent;
        this.closeReason = closeReason;
        this.startedAt = Timestamps.normalize(startedAt);
        this.lastActivityAt = Timestamps.normalize(lastActivityAt);
        this.closedAt = Timestamps.normalizeNullable(closedAt);
        this.version = version;
        checkClosedConsistency();
    }

    public static Conversation start(ContactIdentity contact, Instant now) {
        return new Conversation(ConversationId.newId(), contact, null, ConversationStatus.OPEN,
                null, null, now, now, null, null);
    }

    /** Reconstitui a partir da persistência, validando a consistência do estado. */
    public static Conversation restore(ConversationId id, ContactIdentity contact, TutorId tutorId,
                                       ConversationStatus status, Intent currentIntent,
                                       CloseReason closeReason, Instant startedAt,
                                       Instant lastActivityAt, Instant closedAt, Long version) {
        return new Conversation(id, contact, tutorId, status, currentIntent, closeReason,
                startedAt, lastActivityAt, closedAt, version);
    }

    /** Registra atividade (mensagem recebida ou enviada). Só em conversa ativa. */
    public void registerActivity(Instant at) {
        requireActive("registerActivity");
        Instant normalized = Timestamps.normalize(at);
        if (normalized.isAfter(lastActivityAt)) {
            lastActivityAt = normalized;
        }
    }

    /** Sinaliza a conversa para revisão humana. Não notifica ninguém (ADR-0007). */
    public void waitForHuman(Instant at) {
        requireStatus(ConversationStatus.OPEN, "waitForHuman");
        status = ConversationStatus.WAITING_HUMAN;
        registerActivity(at);
    }

    /** A equipe devolve a conversa ao assistente (ADR-0011). */
    public void returnToAssistant(Instant at) {
        requireStatus(ConversationStatus.WAITING_HUMAN, "returnToAssistant");
        status = ConversationStatus.OPEN;
        registerActivity(at);
    }

    /** Encerramento pela equipe. Não resolve solicitações (ADR-0011). */
    public void closeByStaff(Instant at) {
        requireActive("closeByStaff");
        close(CloseReason.CLOSED_BY_STAFF, at);
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

    private void checkClosedConsistency() {
        boolean closed = status == ConversationStatus.CLOSED;
        if (closed != (closeReason != null) || closed != (closedAt != null)) {
            throw new IllegalStateException(
                    "conversa inconsistente: status=%s, closeReason=%s, closedAt=%s"
                            .formatted(status, closeReason, closedAt));
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

    /** Versão de concorrência (lock otimista, DM-16/DA-21); {@code null} antes de persistir. */
    public Long version() {
        return version;
    }
}
