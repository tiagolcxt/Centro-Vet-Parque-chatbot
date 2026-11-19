package com.clinicavet.assistant.conversation.infrastructure.persistence;

import com.clinicavet.assistant.conversation.domain.AssistantMetadata;
import com.clinicavet.assistant.conversation.domain.ClinicalPriority;
import com.clinicavet.assistant.shared.domain.ConversationId;
import com.clinicavet.assistant.conversation.domain.Intent;
import com.clinicavet.assistant.conversation.domain.Message;
import com.clinicavet.assistant.conversation.domain.MessageAuthor;
import com.clinicavet.assistant.conversation.domain.MessageId;
import com.clinicavet.assistant.conversation.domain.PrioritySource;
import com.clinicavet.assistant.conversation.domain.TurnSignal;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.TenantId;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
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

    // Clínica dona da linha (ADR-0021): preenchida e filtrada pelo Hibernate a partir do CurrentClinic.
    @TenantId
    @Column(name = "clinic_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID clinicId;

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

    @Column(name = "fallback", nullable = false)
    private boolean fallback;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", columnDefinition = "VARCHAR(30)")
    private ClinicalPriority priority;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority_source", columnDefinition = "VARCHAR(20)")
    private PrioritySource prioritySource;

    /** Lista separada por vírgula (só códigos, sem conteúdo). */
    @Column(name = "signals", length = 300)
    private String signals;

    @Column(name = "secondary_intents", length = 100)
    private String secondaryIntents;

    @Column(name = "staff_user_id", columnDefinition = "BINARY(16)", updatable = false)
    private UUID staffUserId;

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
        entity.staffUserId = message.staffUserId().orElse(null);
        AssistantMetadata meta = message.metadata();
        entity.fallback = meta.fallback();
        entity.priority = meta.priority();
        entity.prioritySource = meta.prioritySource();
        entity.signals = join(meta.signals().stream().sorted().map(Enum::name).toList());
        entity.secondaryIntents = join(meta.secondaryIntents().stream().map(Enum::name).toList());
        entity.isNew = true;
        return entity;
    }

    Message toDomain() {
        AssistantMetadata meta = author != MessageAuthor.ASSISTANT ? AssistantMetadata.NONE
                : new AssistantMetadata(fallback, priority, prioritySource, parseSignals(signals), parseIntents(secondaryIntents));
        return Message.restore(new MessageId(id), new ConversationId(conversationId), author, content, intent, meta,
                createdAt, staffUserId);
    }

    private static String join(List<String> values) {
        return values.isEmpty() ? null : String.join(",", values);
    }

    private static Set<TurnSignal> parseSignals(String raw) {
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(raw.split(",")).map(TurnSignal::valueOf)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(TurnSignal.class)));
    }

    private static List<Intent> parseIntents(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(",")).map(Intent::valueOf).toList();
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
