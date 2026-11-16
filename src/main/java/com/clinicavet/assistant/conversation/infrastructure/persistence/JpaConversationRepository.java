package com.clinicavet.assistant.conversation.infrastructure.persistence;

import com.clinicavet.assistant.conversation.domain.Conversation;
import com.clinicavet.assistant.conversation.domain.ConversationId;
import com.clinicavet.assistant.conversation.domain.ConversationRepository;
import com.clinicavet.assistant.conversation.domain.ConversationStatus;
import com.clinicavet.assistant.conversation.domain.Message;
import com.clinicavet.assistant.conversation.domain.MessageRepository;
import com.clinicavet.assistant.shared.domain.ContactIdentity;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

@Repository
class JpaConversationRepository implements ConversationRepository {

    private static final EnumSet<ConversationStatus> ACTIVE =
            EnumSet.of(ConversationStatus.OPEN, ConversationStatus.WAITING_HUMAN);

    private final ConversationJpaRepository jpa;

    JpaConversationRepository(ConversationJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Conversation save(Conversation conversation) {
        return jpa.saveAndFlush(ConversationEntity.from(conversation)).toDomain();
    }

    @Override
    public Optional<Conversation> findById(ConversationId id) {
        return jpa.findById(id.value()).map(ConversationEntity::toDomain);
    }

    @Override
    public Optional<Conversation> findByIdForUpdate(ConversationId id) {
        return jpa.findByIdForUpdate(id.value()).map(ConversationEntity::toDomain);
    }

    @Override
    public Optional<Conversation> findActiveByContact(ContactIdentity contact) {
        return single(jpa.findByChannelAndExternalContactIdAndStatusIn(
                contact.channel(), contact.externalContactId(), ACTIVE));
    }

    @Override
    public Optional<Conversation> findActiveByContactForUpdate(ContactIdentity contact) {
        return single(jpa.findActiveForUpdate(contact.channel(), contact.externalContactId(), ACTIVE));
    }

    private static Optional<Conversation> single(List<ConversationEntity> active) {
        if (active.size() > 1) {
            // Invariante de ADR-0009 violada (o índice único da V2 impede). Sem o identificador do
            // contato no log (dado pessoal).
            throw new IllegalStateException("mais de uma conversa ativa para o mesmo contato: " + active.size());
        }
        return active.stream().findFirst().map(ConversationEntity::toDomain);
    }
}

@Repository
class JpaMessageRepository implements MessageRepository {

    private final MessageJpaRepository jpa;

    JpaMessageRepository(MessageJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Message save(Message message) {
        return jpa.saveAndFlush(MessageEntity.newFrom(message)).toDomain();
    }

    @Override
    public List<Message> findLatest(ConversationId conversationId, int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit deve ser positivo");
        }
        List<Message> newestFirst = jpa
                .findByConversationIdOrderByCreatedAtDesc(conversationId.value(), PageRequest.of(0, limit))
                .stream().map(MessageEntity::toDomain).toList();
        List<Message> chronological = new ArrayList<>(newestFirst);
        Collections.reverse(chronological);
        return List.copyOf(chronological);
    }

    @Override
    public List<Message> findPage(ConversationId conversationId, int page, int size) {
        if (page < 0 || size <= 0) {
            throw new IllegalArgumentException("page deve ser >= 0 e size, positivo");
        }
        return jpa.findByConversationIdOrderByCreatedAtAsc(conversationId.value(), PageRequest.of(page, size))
                .stream().map(MessageEntity::toDomain).toList();
    }

    @Override
    public long countByConversation(ConversationId conversationId) {
        return jpa.countByConversationId(conversationId.value());
    }
}
