package com.clinicavet.assistant.platform.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuditEventJpaRepository extends JpaRepository<AuditEventEntity, UUID> {

    List<AuditEventEntity> findTop100ByOrderByOccurredAtDesc();

    /** Filtros opcionais (nulo = ignora). JPQL: o @TenantId filtra a clínica (ADR-0021). */
    @org.springframework.data.jpa.repository.Query("""
            select e from AuditEventEntity e
            where e.occurredAt >= :from and e.occurredAt < :to
              and (:actor is null or e.actorUserId = :actor)
              and (:action is null or e.action like :action)
              and (:targetType is null or e.targetType = :targetType)
              and (:targetId is null or e.targetId = :targetId)
              and (:withoutLogins = false or e.action not like 'auth.%')
            order by e.occurredAt desc
            """)
    org.springframework.data.domain.Page<AuditEventEntity> search(
            @org.springframework.data.repository.query.Param("from") java.time.Instant from,
            @org.springframework.data.repository.query.Param("to") java.time.Instant to,
            @org.springframework.data.repository.query.Param("actor") UUID actor,
            @org.springframework.data.repository.query.Param("action") String action,
            @org.springframework.data.repository.query.Param("targetType") String targetType,
            @org.springframework.data.repository.query.Param("targetId") String targetId,
            @org.springframework.data.repository.query.Param("withoutLogins") boolean withoutLogins,
            org.springframework.data.domain.Pageable pageable);
}
