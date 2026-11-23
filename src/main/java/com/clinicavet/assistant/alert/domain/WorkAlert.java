package com.clinicavet.assistant.alert.domain;

import com.clinicavet.assistant.alert.ForgottenWork;
import com.clinicavet.assistant.shared.domain.Timestamps;
import com.clinicavet.assistant.shared.exception.InvalidStateTransitionException;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Alerta de trabalho esquecido (E3, provisório até D4). Estados:
 * <pre>
 * aberto ──(prazo de novo sem "Recebi")──► escalado
 *   │  └──"Recebi"──► recebido ──(prazo de novo, ainda pendente)──► escalado (o "Recebi" deixa de valer)
 *   └──────────── pendência acabou (assumida, encerrada, com responsável) ────────────► fechado
 * </pre>
 * "Recebi" não resolve: só diz que alguém viu. Quem resolve é assumir a conversa ou pegar a solicitação.
 */
public final class WorkAlert {

    private static final String AGGREGATE = "WorkAlert";

    private enum State {
        CLOSED
    }

    private final UUID id;
    private final ForgottenWork.Kind kind;
    private final UUID subjectId;
    private final Instant pendingSince;
    private final Instant raisedAt;
    private Instant escalatedAt;
    private UUID acknowledgedBy;
    private Instant acknowledgedAt;
    private Instant closedAt;
    private final Long version;

    private WorkAlert(UUID id, ForgottenWork.Kind kind, UUID subjectId, Instant pendingSince, Instant raisedAt,
                      Instant escalatedAt, UUID acknowledgedBy, Instant acknowledgedAt, Instant closedAt, Long version) {
        this.id = Objects.requireNonNull(id);
        this.kind = Objects.requireNonNull(kind);
        this.subjectId = Objects.requireNonNull(subjectId);
        this.pendingSince = Timestamps.normalize(pendingSince);
        this.raisedAt = Timestamps.normalize(raisedAt);
        this.escalatedAt = escalatedAt;
        this.acknowledgedBy = acknowledgedBy;
        this.acknowledgedAt = acknowledgedAt;
        this.closedAt = closedAt;
        this.version = version;
    }

    public static WorkAlert raise(ForgottenWork.Kind kind, UUID subjectId, Instant pendingSince, Instant now) {
        return new WorkAlert(UUID.randomUUID(), kind, subjectId, pendingSince, now, null, null, null, null, null);
    }

    public static WorkAlert restore(UUID id, ForgottenWork.Kind kind, UUID subjectId, Instant pendingSince,
                                    Instant raisedAt, Instant escalatedAt, UUID acknowledgedBy, Instant acknowledgedAt,
                                    Instant closedAt, Long version) {
        return new WorkAlert(id, kind, subjectId, pendingSince, raisedAt, escalatedAt, acknowledgedBy, acknowledgedAt,
                closedAt, version);
    }

    /** "Recebi": alguém viu o alerta. Para o som e o escalonamento por mais um prazo. */
    public void acknowledge(UUID userId, Instant now) {
        requireOpen("acknowledge");
        this.acknowledgedBy = Objects.requireNonNull(userId);
        this.acknowledgedAt = Timestamps.normalize(now);
    }

    /**
     * Sem "Recebi" por mais um prazo desde o alerta (ou desde o último "Recebi"), escala: passa a aparecer para toda
     * a equipe. Um "Recebi" que venceu deixa de valer.
     *
     * @return {@code true} se escalou agora
     */
    public boolean escalateIfDue(Duration gap, Instant now) {
        if (!isOpen()) {
            return false;
        }
        Instant reference = acknowledgedAt != null ? acknowledgedAt : raisedAt;
        boolean alreadyEscalated = escalatedAt != null && acknowledgedAt == null;
        if (alreadyEscalated || now.isBefore(reference.plus(gap))) {
            return false;
        }
        escalatedAt = Timestamps.normalize(now);
        acknowledgedBy = null;
        acknowledgedAt = null;
        return true;
    }

    public void close(Instant now) {
        requireOpen("close");
        closedAt = Timestamps.normalize(now);
    }

    private void requireOpen(String action) {
        if (!isOpen()) {
            throw new InvalidStateTransitionException(AGGREGATE, State.CLOSED, action);
        }
    }

    public boolean isOpen() {
        return closedAt == null;
    }

    public boolean isEscalated() {
        return escalatedAt != null;
    }

    public boolean isAcknowledged() {
        return acknowledgedAt != null;
    }

    public UUID id() {
        return id;
    }

    public ForgottenWork.Kind kind() {
        return kind;
    }

    public UUID subjectId() {
        return subjectId;
    }

    public Instant pendingSince() {
        return pendingSince;
    }

    public Instant raisedAt() {
        return raisedAt;
    }

    public Optional<Instant> escalatedAt() {
        return Optional.ofNullable(escalatedAt);
    }

    public Optional<UUID> acknowledgedBy() {
        return Optional.ofNullable(acknowledgedBy);
    }

    public Optional<Instant> acknowledgedAt() {
        return Optional.ofNullable(acknowledgedAt);
    }

    public Optional<Instant> closedAt() {
        return Optional.ofNullable(closedAt);
    }

    public Long version() {
        return version;
    }
}
