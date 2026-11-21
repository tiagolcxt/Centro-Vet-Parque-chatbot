package com.clinicavet.assistant.conversation.quickreply;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface QuickReplyJpaRepository extends JpaRepository<QuickReplyEntity, UUID> {

    List<QuickReplyEntity> findAllByOrderByTitleAsc();

    boolean existsByTitle(String title);

    boolean existsByTitleAndIdNot(String title, UUID id);

    long count();
}
