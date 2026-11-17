package com.clinicavet.assistant.conversation.infrastructure.persistence;

import com.clinicavet.assistant.conversation.domain.ClinicalPriority;
import com.clinicavet.assistant.conversation.domain.ConversationId;
import com.clinicavet.assistant.conversation.domain.PriorityChange;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.UUID;

/** Registro imutável de mudança de prioridade pela equipe (H4). Só inserido. */
@Entity
@Table(name = "priority_change")
class PriorityChangeEntity implements Persistable<UUID> {

    @Id
    @Column(name = "id", columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "conversation_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID conversationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_priority", columnDefinition = "VARCHAR(30)", nullable = false)
    private ClinicalPriority fromPriority;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_priority", columnDefinition = "VARCHAR(30)", nullable = false)
    private ClinicalPriority toPriority;

    @Column(name = "changed_by", length = 60, nullable = false)
    private String changedBy;

    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "changed_at", columnDefinition = "DATETIME(6)", nullable = false)
    private Instant changedAt;

    @Transient
    private boolean isNew;

    protected PriorityChangeEntity() {
    }

    static PriorityChangeEntity newFrom(PriorityChange change) {
        PriorityChangeEntity entity = new PriorityChangeEntity();
        entity.id = change.id();
        entity.conversationId = change.conversationId().value();
        entity.fromPriority = change.from();
        entity.toPriority = change.to();
        entity.changedBy = change.changedBy();
        entity.note = change.note();
        entity.changedAt = change.changedAt();
        entity.isNew = true;
        return entity;
    }

    PriorityChange toDomain() {
        return new PriorityChange(id, new ConversationId(conversationId), fromPriority, toPriority, changedBy, note,
                changedAt);
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }
}
