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
    public Optional<Conversation> findActiveByContact(ContactIdentity contact) {
        List<ConversationEntity> active = jpa.findByChannelAndExternalContactIdAndStatusIn(
                contact.channel(), contact.externalContactId(), ACTIVE);
        if (active.size() > 1) {
            // Invariante de ADR-0009 violada. Sem o identificador do contato no log (dado pessoal).
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
    public List<Message> findLatest(com.clinicavet.assistant.conversation.domain.ConversationId conversationId,
                                    int limit) {
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
}
