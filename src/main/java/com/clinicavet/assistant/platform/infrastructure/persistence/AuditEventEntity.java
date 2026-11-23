package com.clinicavet.assistant.platform.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.TenantId;

import java.time.Instant;
import java.util.UUID;

/** Evento de auditoria (RF-S07). Só inserção. */
@Entity
@Table(name = "audit_event")
public class AuditEventEntity {

    @Id
    @Column(name = "id", columnDefinition = "BINARY(16)")
    private UUID id;

    @TenantId
    @Column(name = "clinic_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID clinicId;

    @Column(name = "occurred_at", columnDefinition = "DATETIME(6)", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "actor_user_id", columnDefinition = "BINARY(16)", updatable = false)
    private UUID actorUserId;

    @Column(name = "action", length = 40, nullable = false, updatable = false)
    private String action;

    @Column(name = "target_type", length = 30, updatable = false)
    private String targetType;

    @Column(name = "target_id", length = 64, updatable = false)
    private String targetId;

    @Column(name = "summary", length = 500, updatable = false)
    private String summary;

    protected AuditEventEntity() {
    }

    public AuditEventEntity(Instant occurredAt, UUID actorUserId, String action, String targetType, String targetId,
                            String summary) {
        this.id = UUID.randomUUID();
        this.occurredAt = occurredAt;
        this.actorUserId = actorUserId;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.summary = summary;
    }

    public UUID id() {
        return id;
    }

    public Instant occurredAt() {
        return occurredAt;
    }

    public UUID actorUserId() {
        return actorUserId;
    }

    public String action() {
        return action;
    }

    public String targetType() {
        return targetType;
    }

    public String targetId() {
        return targetId;
    }

    public String summary() {
        return summary;
    }
}
