package com.clinicavet.assistant.servicerequest.infrastructure.persistence;

import com.clinicavet.assistant.conversation.domain.ConversationId;
import com.clinicavet.assistant.servicerequest.domain.RequestOutcome;
import com.clinicavet.assistant.servicerequest.domain.RequestStatus;
import com.clinicavet.assistant.servicerequest.domain.RequestType;
import com.clinicavet.assistant.servicerequest.domain.ServiceRequest;
import com.clinicavet.assistant.servicerequest.domain.ServiceRequestId;
import com.clinicavet.assistant.tutor.domain.PetId;
import com.clinicavet.assistant.tutor.domain.TutorId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

/** Detalhes por tipo em colunas próprias (DA-19); motivo e preferência do agendamento desde a V4 (Etapa 5). */
@Entity
@Table(name = "service_request")
class ServiceRequestEntity {

    @Id
    @Column(name = "id", columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "conversation_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID conversationId;

    @Column(name = "tutor_id", columnDefinition = "BINARY(16)")
    private UUID tutorId;

    @Column(name = "pet_id", columnDefinition = "BINARY(16)")
    private UUID petId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", columnDefinition = "VARCHAR(20)", nullable = false)
    private RequestType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", columnDefinition = "VARCHAR(20)", nullable = false)
    private RequestStatus status;

    @Column(name = "summary", length = 500)
    private String summary;

    @Column(name = "reason", length = 300)
    private String reason;

    @Column(name = "preferred_period", length = 120)
    private String preferredPeriod;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", columnDefinition = "VARCHAR(30)")
    private RequestOutcome outcome;

    @Column(name = "outcome_note", length = 500)
    private String outcomeNote;

    @Column(name = "created_at", columnDefinition = "DATETIME(6)", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", columnDefinition = "DATETIME(6)", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected ServiceRequestEntity() {
    }

    static ServiceRequestEntity from(ServiceRequest request) {
        ServiceRequestEntity entity = new ServiceRequestEntity();
        entity.id = request.id().value();
        entity.conversationId = request.conversationId().value();
        entity.tutorId = request.tutorId().map(TutorId::value).orElse(null);
        entity.petId = request.petId().map(PetId::value).orElse(null);
        entity.type = request.type();
        entity.status = request.status();
        entity.summary = request.summary().orElse(null);
        entity.reason = request.reason().orElse(null);
        entity.preferredPeriod = request.preferredPeriod().orElse(null);
        entity.outcome = request.outcome().orElse(null);
        entity.outcomeNote = request.outcomeNote().orElse(null);
        entity.createdAt = request.createdAt();
        entity.updatedAt = request.updatedAt();
        entity.version = request.version();
        return entity;
    }

    ServiceRequest toDomain() {
        return ServiceRequest.restore(new ServiceRequestId(id), new ConversationId(conversationId),
                tutorId == null ? null : new TutorId(tutorId), petId == null ? null : new PetId(petId),
                type, status, summary, reason, preferredPeriod, outcome, outcomeNote, createdAt, updatedAt, version);
    }
}
