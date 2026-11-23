package com.clinicavet.assistant.alert.infrastructure;

import com.clinicavet.assistant.alert.ForgottenWork;
import com.clinicavet.assistant.alert.domain.WorkAlert;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.TenantId;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "work_alert")
class WorkAlertEntity {

    @Id
    @Column(name = "id", columnDefinition = "BINARY(16)")
    private UUID id;

    // Clínica dona da linha (ADR-0021): preenchida e filtrada pelo Hibernate a partir do CurrentClinic.
    @TenantId
    @Column(name = "clinic_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID clinicId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", columnDefinition = "VARCHAR(30)", nullable = false, updatable = false)
    private ForgottenWork.Kind kind;

    @Column(name = "subject_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID subjectId;

    @Column(name = "pending_since", columnDefinition = "DATETIME(6)", nullable = false, updatable = false)
    private Instant pendingSince;

    @Column(name = "raised_at", columnDefinition = "DATETIME(6)", nullable = false, updatable = false)
    private Instant raisedAt;

    @Column(name = "escalated_at", columnDefinition = "DATETIME(6)")
    private Instant escalatedAt;

    @Column(name = "acknowledged_by", columnDefinition = "BINARY(16)")
    private UUID acknowledgedBy;

    @Column(name = "acknowledged_at", columnDefinition = "DATETIME(6)")
    private Instant acknowledgedAt;

    @Column(name = "closed_at", columnDefinition = "DATETIME(6)")
    private Instant closedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected WorkAlertEntity() {
    }

    static WorkAlertEntity from(WorkAlert alert) {
        WorkAlertEntity entity = new WorkAlertEntity();
        entity.id = alert.id();
        entity.kind = alert.kind();
        entity.subjectId = alert.subjectId();
        entity.pendingSince = alert.pendingSince();
        entity.raisedAt = alert.raisedAt();
        entity.escalatedAt = alert.escalatedAt().orElse(null);
        entity.acknowledgedBy = alert.acknowledgedBy().orElse(null);
        entity.acknowledgedAt = alert.acknowledgedAt().orElse(null);
        entity.closedAt = alert.closedAt().orElse(null);
        entity.version = alert.version();
        return entity;
    }

    WorkAlert toDomain() {
        return WorkAlert.restore(id, kind, subjectId, pendingSince, raisedAt, escalatedAt, acknowledgedBy,
                acknowledgedAt, closedAt, version);
    }
}
