package com.clinicavet.assistant.conversation.infrastructure.persistence;

import com.clinicavet.assistant.conversation.domain.ConversationStatus;
import com.clinicavet.assistant.shared.domain.Channel;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface ConversationJpaRepository extends JpaRepository<ConversationEntity, UUID> {

    List<ConversationEntity> findByChannelAndExternalContactIdAndStatusIn(
            Channel channel, String externalContactId, Collection<ConversationStatus> statuses);

    /** Fila da equipe (H10): prioridade (o nome do enum ordena P1 antes de P6), espera, atividade. */
    @Query("""
            select c from ConversationEntity c where c.status = :status
            order by c.clinicalPriority asc, c.handoffAt asc, c.lastActivityAt asc
            """)
    List<ConversationEntity> findQueue(@Param("status") ConversationStatus status, Pageable pageable);

    /** Caixa de entrada (RF-S20): conversas ativas, a mais urgente e a que espera há mais tempo primeiro. */
    @Query("""
            select c from ConversationEntity c where c.status in :statuses
            order by c.clinicalPriority asc, c.handoffAt asc, c.lastActivityAt asc
            """)
    List<ConversationEntity> findInbox(@Param("statuses") Collection<ConversationStatus> statuses, Pageable pageable);

    List<ConversationEntity> findByTutorIdOrderByStartedAtDesc(UUID tutorId);

    List<ConversationEntity> findByAssigneeIdAndStatus(UUID assigneeId, ConversationStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ConversationEntity c where c.id = :id")
    Optional<ConversationEntity> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select c from ConversationEntity c
            where c.channel = :channel and c.externalContactId = :externalContactId and c.status in :statuses
            """)
    List<ConversationEntity> findActiveForUpdate(@Param("channel") Channel channel,
                                                 @Param("externalContactId") String externalContactId,
                                                 @Param("statuses") Collection<ConversationStatus> statuses);
}

interface ConversationNoteJpaRepository extends JpaRepository<ConversationNoteEntity, UUID> {

    List<ConversationNoteEntity> findByConversationIdOrderByCreatedAtAsc(UUID conversationId);
}

interface PriorityChangeJpaRepository extends JpaRepository<PriorityChangeEntity, UUID> {

    List<PriorityChangeEntity> findByConversationIdOrderByChangedAtAsc(UUID conversationId);
}

interface MessageJpaRepository extends JpaRepository<MessageEntity, UUID> {

    List<MessageEntity> findByConversationIdOrderByCreatedAtDesc(UUID conversationId, Pageable pageable);

    List<MessageEntity> findByConversationIdOrderByCreatedAtAsc(UUID conversationId, Pageable pageable);

    long countByConversationId(UUID conversationId);

    java.util.Optional<MessageEntity> findFirstByConversationIdAndAuthorOrderByCreatedAtDesc(UUID conversationId,
            com.clinicavet.assistant.conversation.domain.MessageAuthor author);
}
