package com.clinicavet.assistant.conversation.infrastructure.persistence;

import com.clinicavet.assistant.conversation.domain.ClinicalPriority;
import com.clinicavet.assistant.conversation.domain.CloseReason;
import com.clinicavet.assistant.conversation.domain.Conversation;
import com.clinicavet.assistant.shared.domain.ConversationId;
import com.clinicavet.assistant.conversation.domain.ConversationStatus;
import com.clinicavet.assistant.conversation.domain.HandoffReason;
import com.clinicavet.assistant.conversation.domain.Intent;
import com.clinicavet.assistant.shared.domain.Channel;
import com.clinicavet.assistant.shared.domain.ContactIdentity;
import com.clinicavet.assistant.tutor.domain.TutorId;
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
@Table(name = "conversation")
class ConversationEntity {

    @Id
    @Column(name = "id", columnDefinition = "BINARY(16)")
    private UUID id;

    // Clínica dona da linha (ADR-0021): preenchida e filtrada pelo Hibernate a partir do CurrentClinic.
    @TenantId
    @Column(name = "clinic_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID clinicId;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", columnDefinition = "VARCHAR(20)", nullable = false)
    private Channel channel;

    @Column(name = "external_contact_id", length = 128, nullable = false)
    private String externalContactId;

    @Column(name = "tutor_id", columnDefinition = "BINARY(16)")
    private UUID tutorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", columnDefinition = "VARCHAR(20)", nullable = false)
    private ConversationStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_intent", columnDefinition = "VARCHAR(40)")
    private Intent currentIntent;

    @Enumerated(EnumType.STRING)
    @Column(name = "close_reason", columnDefinition = "VARCHAR(30)")
    private CloseReason closeReason;

    @Column(name = "started_at", columnDefinition = "DATETIME(6)", nullable = false)
    private Instant startedAt;

    @Column(name = "last_activity_at", columnDefinition = "DATETIME(6)", nullable = false)
    private Instant lastActivityAt;

    @Column(name = "closed_at", columnDefinition = "DATETIME(6)")
    private Instant closedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "clinical_priority", columnDefinition = "VARCHAR(30)", nullable = false)
    private ClinicalPriority clinicalPriority;

    @Enumerated(EnumType.STRING)
    @Column(name = "handoff_reason", columnDefinition = "VARCHAR(40)")
    private HandoffReason handoffReason;

    @Column(name = "handoff_at", columnDefinition = "DATETIME(6)")
    private Instant handoffAt;

    @Column(name = "human_offer_made", nullable = false)
    private boolean humanOfferMade;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected ConversationEntity() {
    }

    static ConversationEntity from(Conversation conversation) {
        ConversationEntity entity = new ConversationEntity();
        entity.id = conversation.id().value();
        entity.channel = conversation.contact().channel();
        entity.externalContactId = conversation.contact().externalContactId();
        entity.tutorId = conversation.tutorId().map(TutorId::value).orElse(null);
        entity.status = conversation.status();
        entity.currentIntent = conversation.currentIntent().orElse(null);
        entity.closeReason = conversation.closeReason().orElse(null);
        entity.startedAt = conversation.startedAt();
        entity.lastActivityAt = conversation.lastActivityAt();
        entity.closedAt = conversation.closedAt().orElse(null);
        entity.clinicalPriority = conversation.clinicalPriority();
        entity.handoffReason = conversation.handoffReason().orElse(null);
        entity.handoffAt = conversation.handoffAt().orElse(null);
        entity.humanOfferMade = conversation.humanOfferMade();
        entity.version = conversation.version();
        return entity;
    }

    Conversation toDomain() {
        return Conversation.restore(new ConversationId(id), new ContactIdentity(channel, externalContactId),
                tutorId == null ? null : new TutorId(tutorId), status, currentIntent, closeReason,
                startedAt, lastActivityAt, closedAt, clinicalPriority, handoffReason, handoffAt, humanOfferMade,
                version);
    }
}
