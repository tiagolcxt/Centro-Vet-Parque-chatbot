package com.clinicavet.assistant.platform.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClinicJpaRepository extends JpaRepository<ClinicEntity, UUID> {

    List<ClinicEntity> findByStatus(String status);
}
