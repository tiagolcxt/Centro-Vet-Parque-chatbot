package com.clinicavet.assistant.tutor.infrastructure.persistence;

import com.clinicavet.assistant.shared.domain.Channel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface TutorJpaRepository extends JpaRepository<TutorEntity, UUID> {

    Optional<TutorEntity> findByChannelAndExternalContactId(Channel channel, String externalContactId);
}

interface PetJpaRepository extends JpaRepository<PetEntity, UUID> {

    List<PetEntity> findByTutorIdOrderByCreatedAtAsc(UUID tutorId);
}
