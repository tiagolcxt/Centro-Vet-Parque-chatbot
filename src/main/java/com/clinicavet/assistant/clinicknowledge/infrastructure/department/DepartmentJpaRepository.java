package com.clinicavet.assistant.clinicknowledge.infrastructure.department;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

interface DepartmentJpaRepository extends JpaRepository<DepartmentEntity, UUID> {

    List<DepartmentEntity> findAllByOrderByNameAsc();

    List<DepartmentEntity> findByIdInAndActiveTrue(Collection<UUID> ids);

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, UUID id);
}
