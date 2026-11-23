package com.clinicavet.assistant.conversation.infrastructure.persistence;

import com.clinicavet.assistant.conversation.domain.Conversation;
import com.clinicavet.assistant.shared.domain.ConversationId;
import com.clinicavet.assistant.conversation.domain.ConversationRepository;
import com.clinicavet.assistant.conversation.domain.ConversationStatus;
import com.clinicavet.assistant.conversation.domain.Message;
import com.clinicavet.assistant.conversation.domain.MessageAuthor;
import com.clinicavet.assistant.conversation.domain.PriorityChange;
import com.clinicavet.assistant.conversation.domain.MessageRepository;
import com.clinicavet.assistant.shared.domain.ContactIdentity;
import com.clinicavet.assistant.shared.domain.InternalNote;
import com.clinicavet.assistant.tutor.domain.TutorId;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
class JpaConversationRepository implements ConversationRepository {

    private static final EnumSet<ConversationStatus> ACTIVE =
            EnumSet.of(ConversationStatus.OPEN, ConversationStatus.WAITING_HUMAN, ConversationStatus.ASSIGNED);

    private final ConversationJpaRepository jpa;
    private final PriorityChangeJpaRepository priorityChanges;
    private final ConversationNoteJpaRepository notes;

    JpaConversationRepository(ConversationJpaRepository jpa, PriorityChangeJpaRepository priorityChanges,
                              ConversationNoteJpaRepository notes) {
        this.jpa = jpa;
        this.priorityChanges = priorityChanges;
        this.notes = notes;
    }

    @Override
    public List<Conversation> findInbox(int limit) {
        return jpa.findInbox(ACTIVE, PageRequest.of(0, limit)).stream().map(ConversationEntity::toDomain).toList();
    }

    @Override
    public List<Conversation> findByIds(Set<ConversationId> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return jpa.findAllById(ids.stream().map(ConversationId::value).toList()).stream()
                .map(ConversationEntity::toDomain).toList();
    }

    @Override
    public List<Conversation> findByTutor(TutorId tutorId) {
        return jpa.findByTutorIdOrderByStartedAtDesc(tutorId.value()).stream().map(ConversationEntity::toDomain).toList();
    }

    @Override
    public List<Conversation> findAssignedTo(UUID staffUserId) {
        return jpa.findByAssigneeIdAndStatus(staffUserId, ConversationStatus.ASSIGNED).stream()
                .map(ConversationEntity::toDomain).toList();
    }

    @Override
    public void saveNote(ConversationId id, InternalNote note) {
        notes.save(new ConversationNoteEntity(id.value(), note));
    }

    @Override
    public List<InternalNote> findNotes(ConversationId id) {
        return notes.findByConversationIdOrderByCreatedAtAsc(id.value()).stream().map(ConversationNoteEntity::toDomain)
                .toList();
    }

    @Override
    public List<Conversation> findByStatusInQueueOrder(ConversationStatus status, int limit) {
        return jpa.findQueue(status, PageRequest.of(0, limit)).stream().map(ConversationEntity::toDomain).toList();
    }

    @Override
    public void savePriorityChange(PriorityChange change) {
        priorityChanges.saveAndFlush(PriorityChangeEntity.newFrom(change));
    }

    @Override
    public List<PriorityChange> findPriorityChanges(ConversationId id) {
        return priorityChanges.findByConversationIdOrderByChangedAtAsc(id.value()).stream()
                .map(PriorityChangeEntity::toDomain).toList();
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
    private final ConversationJpaRepository conversations;
    private final org.springframework.context.ApplicationEventPublisher events;

    JpaMessageRepository(MessageJpaRepository jpa, ConversationJpaRepository conversations,
                         org.springframework.context.ApplicationEventPublisher events) {
        this.jpa = jpa;
        this.conversations = conversations;
        this.events = events;
    }

    @Override
    public Message save(Message message) {
        Message saved = jpa.saveAndFlush(MessageEntity.newFrom(message)).toDomain();
        if (saved.author() == MessageAuthor.ASSISTANT || saved.author() == MessageAuthor.STAFF) {
            conversations.findById(saved.conversationId().value()).ifPresent(c -> events.publishEvent(
                    new com.clinicavet.assistant.conversation.domain.OutgoingMessageRecorded(saved.id(),
                            saved.conversationId(), c.toDomain().contact())));
        }
        return saved;
    }

    @Override
    public java.util.Optional<Message> findById(com.clinicavet.assistant.conversation.domain.MessageId id) {
        return jpa.findById(id.value()).map(MessageEntity::toDomain);
    }

    @Override
    public java.util.Optional<java.time.Instant> lastCustomerMessageAt(ConversationId conversationId) {
        return jpa.findFirstByConversationIdAndAuthorOrderByCreatedAtDesc(conversationId.value(), MessageAuthor.CUSTOMER)
                .map(MessageEntity::createdAt);
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
