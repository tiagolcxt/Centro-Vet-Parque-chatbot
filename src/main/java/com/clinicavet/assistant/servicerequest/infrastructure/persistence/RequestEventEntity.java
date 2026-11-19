package com.clinicavet.assistant.servicerequest.infrastructure.persistence;

import com.clinicavet.assistant.servicerequest.domain.RequestEvent;
import com.clinicavet.assistant.servicerequest.domain.ServiceRequestId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.TenantId;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "service_request_event")
class RequestEventEntity {

    @Id
    @Column(name = "id", columnDefinition = "BINARY(16)")
    private UUID id;

    @TenantId
    @Column(name = "clinic_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID clinicId;

    @Column(name = "request_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID requestId;

    @Column(name = "occurred_at", columnDefinition = "DATETIME(6)", nullable = false, updatable = false)
    private Instant occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", columnDefinition = "VARCHAR(20)", nullable = false, updatable = false)
    private RequestEvent.Type type;

    @Column(name = "actor_user_id", columnDefinition = "BINARY(16)", updatable = false)
    private UUID actorId;

    @Column(name = "from_value", length = 64, updatable = false)
    private String fromValue;

    @Column(name = "to_value", length = 64, updatable = false)
    private String toValue;

    @Column(name = "reason", length = 300, updatable = false)
    private String reason;

    protected RequestEventEntity() {
    }

    static RequestEventEntity from(RequestEvent event) {
        RequestEventEntity entity = new RequestEventEntity();
        entity.id = event.id();
        entity.requestId = event.requestId().value();
        entity.occurredAt = event.at();
        entity.type = event.type();
        entity.actorId = event.actorId();
        entity.fromValue = event.from();
        entity.toValue = event.to();
        entity.reason = event.reason();
        return entity;
    }

    RequestEvent toDomain() {
        return new RequestEvent(id, new ServiceRequestId(requestId), type, actorId, fromValue, toValue, reason,
                occurredAt);
    }
}
