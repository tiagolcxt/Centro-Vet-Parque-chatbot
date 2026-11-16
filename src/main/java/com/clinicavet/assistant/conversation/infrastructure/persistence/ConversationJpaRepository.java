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

interface MessageJpaRepository extends JpaRepository<MessageEntity, UUID> {

    List<MessageEntity> findByConversationIdOrderByCreatedAtDesc(UUID conversationId, Pageable pageable);

    List<MessageEntity> findByConversationIdOrderByCreatedAtAsc(UUID conversationId, Pageable pageable);

    long countByConversationId(UUID conversationId);
}
