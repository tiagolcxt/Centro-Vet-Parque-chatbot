package com.clinicavet.assistant.conversation.infrastructure.persistence;

import com.clinicavet.assistant.conversation.domain.ConversationId;
import com.clinicavet.assistant.conversation.domain.Intent;
import com.clinicavet.assistant.conversation.domain.Message;
import com.clinicavet.assistant.conversation.domain.MessageAuthor;
import com.clinicavet.assistant.conversation.domain.MessageId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.UUID;

/**
 * Mensagens são imutáveis: sempre inseridas, nunca atualizadas. {@link Persistable} evita o SELECT
 * que o Spring Data faria para decidir entre inserir e mesclar um id atribuído pela aplicação.
 */
@Entity
@Table(name = "message")
class MessageEntity implements Persistable<UUID> {

    @Id
    @Column(name = "id", columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "conversation_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID conversationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "author", columnDefinition = "VARCHAR(20)", nullable = false)
    private MessageAuthor author;

    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "intent", columnDefinition = "VARCHAR(40)")
    private Intent intent;

    @Column(name = "created_at", columnDefinition = "DATETIME(6)", nullable = false)
    private Instant createdAt;

    @jakarta.persistence.Transient
    private boolean isNew;

    protected MessageEntity() {
    }

    static MessageEntity newFrom(Message message) {
        MessageEntity entity = new MessageEntity();
        entity.id = message.id().value();
        entity.conversationId = message.conversationId().value();
        entity.author = message.author();
        entity.content = message.content();
        entity.intent = message.intent().orElse(null);
        entity.createdAt = message.createdAt();
        entity.isNew = true;
        return entity;
    }

    Message toDomain() {
        return Message.restore(new MessageId(id), new ConversationId(conversationId), author, content, intent,
                createdAt);
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
