package com.clinicavet.assistant.platform.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuditEventJpaRepository extends JpaRepository<AuditEventEntity, UUID> {

    List<AuditEventEntity> findTop100ByOrderByOccurredAtDesc();
}
