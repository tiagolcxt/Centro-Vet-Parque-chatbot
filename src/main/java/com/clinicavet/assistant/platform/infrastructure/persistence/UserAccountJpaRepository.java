package com.clinicavet.assistant.platform.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Entidade global: toda consulta, exceto as de login e convite, recebe a clínica explicitamente. */
public interface UserAccountJpaRepository extends JpaRepository<UserAccountEntity, UUID> {

    Optional<UserAccountEntity> findByEmail(String email);

    Optional<UserAccountEntity> findByInviteTokenHash(String inviteTokenHash);

    Optional<UserAccountEntity> findByIdAndClinicId(UUID id, UUID clinicId);

    List<UserAccountEntity> findByClinicIdOrderByEmailAsc(UUID clinicId);

    long countByClinicId(UUID clinicId);

    boolean existsByEmail(String email);
}
