package com.clinicavet.assistant.conversation.domain;

import com.clinicavet.assistant.shared.domain.ConversationId;
import com.clinicavet.assistant.shared.domain.Texts;
import com.clinicavet.assistant.shared.domain.Timestamps;
import com.clinicavet.assistant.shared.exception.DomainValidationException;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Mensagem individual de uma conversa. Imutável. Agregado separado de {@link Conversation}: só a
 * janela recente é carregada (ADR-0004).
 * <p>
 * {@link #MAX_CONTENT_LENGTH} é um limite de armazenamento. O tamanho máximo aceito da mensagem do
 * tutor na entrada é menor (2.000 caracteres, DA-28).
 */
public final class Message {

    public static final int MAX_CONTENT_LENGTH = 10_000;

    private final MessageId id;
    private final ConversationId conversationId;
    private final MessageAuthor author;
    private final String content;
    private final Intent intent;
    private final AssistantMetadata metadata;
    private final Instant createdAt;

    private Message(MessageId id, ConversationId conversationId, MessageAuthor author,
                    String content, Intent intent, AssistantMetadata metadata, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.conversationId = Objects.requireNonNull(conversationId, "conversationId");
        this.author = Objects.requireNonNull(author, "author");
        this.content = Texts.required("content", content, MAX_CONTENT_LENGTH);
        AssistantMetadata meta = metadata == null ? AssistantMetadata.NONE : metadata;
        if (author == MessageAuthor.CUSTOMER && (intent != null || !meta.equals(AssistantMetadata.NONE))) {
            throw new DomainValidationException("intent", "só mensagens do assistente registram intenção e metadados");
        }
        this.intent = intent;
        this.metadata = meta;
        this.createdAt = Timestamps.normalize(createdAt);
    }

    public static Message fromCustomer(ConversationId conversationId, String content, Instant at) {
        return new Message(MessageId.newId(), conversationId, MessageAuthor.CUSTOMER, content, null, null, at);
    }

    public static Message fromAssistant(ConversationId conversationId, String content, Intent intent,
                                        Instant at) {
        return fromAssistant(conversationId, content, intent, AssistantMetadata.NONE, at);
    }

    public static Message fromAssistant(ConversationId conversationId, String content, Intent intent,
                                        AssistantMetadata metadata, Instant at) {
        return new Message(MessageId.newId(), conversationId, MessageAuthor.ASSISTANT, content, intent, metadata, at);
    }

    public static Message restore(MessageId id, ConversationId conversationId, MessageAuthor author,
                                  String content, Intent intent, Instant createdAt) {
        return restore(id, conversationId, author, content, intent, AssistantMetadata.NONE, createdAt);
    }

    public static Message restore(MessageId id, ConversationId conversationId, MessageAuthor author,
                                  String content, Intent intent, AssistantMetadata metadata, Instant createdAt) {
        return new Message(id, conversationId, author, content, intent, metadata, createdAt);
    }

    public MessageId id() {
        return id;
    }

    public ConversationId conversationId() {
        return conversationId;
    }

    public MessageAuthor author() {
        return author;
    }

    public String content() {
        return content;
    }

    public Optional<Intent> intent() {
        return Optional.ofNullable(intent);
    }

    public AssistantMetadata metadata() {
        return metadata;
    }

    /** Foi a resposta segura (turno com falha, DA-31)? */
    public boolean isFallback() {
        return metadata.fallback();
    }

    public Instant createdAt() {
        return createdAt;
    }
}
