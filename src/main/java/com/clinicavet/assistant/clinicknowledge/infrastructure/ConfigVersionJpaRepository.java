package com.clinicavet.assistant.clinicknowledge.infrastructure;

import com.clinicavet.assistant.clinicknowledge.domain.ClinicConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Versões da configuração da clínica atual: o filtro de clínica vem do {@code @TenantId} (ADR-0021). */
interface ConfigVersionJpaRepository extends JpaRepository<ConfigVersionEntity, UUID> {

    Optional<ConfigVersionEntity> findByStatus(ClinicConfig.Status status);

    List<ConfigVersionEntity> findAllByOrderByNumberDesc();

    @Query("select coalesce(max(v.number), 0) from ConfigVersionEntity v")
    int maxNumber();
}
