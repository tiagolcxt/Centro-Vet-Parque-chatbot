package com.clinicavet.assistant.conversation.infrastructure.persistence;

import com.clinicavet.assistant.shared.domain.InternalNote;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.TenantId;

import java.time.Instant;
import java.util.UUID;

/** Nota interna de uma conversa (RF-S26). A tabela {@code internal_note} é compartilhada com as de solicitação. */
@Entity(name = "ConversationNote")
@Table(name = "internal_note")
class ConversationNoteEntity {

    @Id
    @Column(name = "id", columnDefinition = "BINARY(16)")
    private UUID id;

    @TenantId
    @Column(name = "clinic_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID clinicId;

    @Column(name = "conversation_id", columnDefinition = "BINARY(16)", updatable = false)
    private UUID conversationId;

    @Column(name = "author_user_id", columnDefinition = "BINARY(16)", updatable = false)
    private UUID authorId;

    @Column(name = "text", length = 2000, nullable = false, updatable = false)
    private String text;

    @Column(name = "created_at", columnDefinition = "DATETIME(6)", nullable = false, updatable = false)
    private Instant createdAt;

    protected ConversationNoteEntity() {
    }

    ConversationNoteEntity(UUID conversationId, InternalNote note) {
        this.id = note.id();
        this.conversationId = conversationId;
        this.authorId = note.authorId();
        this.text = note.text();
        this.createdAt = note.at();
    }

    InternalNote toDomain() {
        return new InternalNote(id, authorId, text, createdAt);
    }
}
