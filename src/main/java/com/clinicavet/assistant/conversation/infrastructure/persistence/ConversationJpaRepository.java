package com.clinicavet.assistant.conversation.infrastructure.persistence;

import com.clinicavet.assistant.conversation.domain.ConversationStatus;
import com.clinicavet.assistant.shared.domain.Channel;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

interface ConversationJpaRepository extends JpaRepository<ConversationEntity, UUID> {

    List<ConversationEntity> findByChannelAndExternalContactIdAndStatusIn(
            Channel channel, String externalContactId, Collection<ConversationStatus> statuses);
}

interface MessageJpaRepository extends JpaRepository<MessageEntity, UUID> {

    List<MessageEntity> findByConversationIdOrderByCreatedAtDesc(UUID conversationId, Pageable pageable);
}
