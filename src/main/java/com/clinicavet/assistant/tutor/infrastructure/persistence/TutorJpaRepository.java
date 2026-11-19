package com.clinicavet.assistant.tutor.infrastructure.persistence;

import com.clinicavet.assistant.shared.domain.Channel;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface TutorJpaRepository extends JpaRepository<TutorEntity, UUID> {

    Optional<TutorEntity> findByChannelAndExternalContactId(Channel channel, String externalContactId);

    /** Busca da equipe (RF-S42): nome, telefone, contato ou nome de um pet. */
    @Query("""
            select t from TutorEntity t
            where lower(t.name) like :q or t.phone like :q or t.externalContactId like :q
               or t.id in (select p.tutorId from PetEntity p where lower(p.name) like :q)
            order by t.lastSeenAt desc
            """)
    List<TutorEntity> search(@Param("q") String q, Limit limit);
}

interface PetJpaRepository extends JpaRepository<PetEntity, UUID> {

    List<PetEntity> findByTutorIdOrderByCreatedAtAsc(UUID tutorId);
}
