package com.clinicavet.assistant.clinicknowledge.infrastructure.department;

import com.clinicavet.assistant.clinicknowledge.domain.Departments;
import com.clinicavet.assistant.platform.AuditLog;
import com.clinicavet.assistant.shared.exception.ConflictException;
import com.clinicavet.assistant.shared.exception.DomainValidationException;
import com.clinicavet.assistant.shared.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Setores da clínica atual. O filtro de clínica vem do {@code @TenantId} (ADR-0021). */
@Service
class DepartmentService implements Departments {

    private final DepartmentJpaRepository departments;
    private final AuditLog audit;
    private final Clock clock;

    DepartmentService(DepartmentJpaRepository departments, AuditLog audit, Clock clock) {
        this.departments = departments;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Department> list() {
        return departments.findAllByOrderByNameAsc().stream().map(DepartmentEntity::view).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Department> find(UUID id) {
        return departments.findById(id).map(DepartmentEntity::view);
    }

    @Override
    @Transactional
    public Department create(String name, Kind kind) {
        String clean = validName(name);
        if (departments.existsByName(clean)) {
            throw new ConflictException("Já existe um setor com esse nome.");
        }
        DepartmentEntity created = departments.save(new DepartmentEntity(clean, kind, false, clock.instant()));
        audit.record("department.created", "department", created.id(), "Setor criado: " + clean);
        return created.view();
    }

    @Override
    @Transactional
    public Department rename(UUID id, String name) {
        DepartmentEntity department = own(id);
        String clean = validName(name);
        if (departments.existsByNameAndIdNot(clean, id)) {
            throw new ConflictException("Já existe um setor com esse nome.");
        }
        department.rename(clean);
        audit.record("department.renamed", "department", id, "Setor renomeado: " + clean);
        return department.view();
    }

    @Override
    @Transactional
    public Department setActive(UUID id, boolean active) {
        DepartmentEntity department = own(id);
        if (!active && department.isDefault()) {
            throw new ConflictException("O setor padrão não pode ser desativado.");
        }
        if (department.active() != active) {
            department.setActive(active);
            audit.record(active ? "department.activated" : "department.deactivated", "department", id, null);
        }
        return department.view();
    }

    @Override
    @Transactional(readOnly = true)
    public Department defaultDepartment() {
        return departments.findAll().stream().filter(DepartmentEntity::isDefault).findFirst()
                .map(DepartmentEntity::view)
                .orElseThrow(() -> new IllegalStateException("Clínica sem setor padrão (ClinicDefaults)"));
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> activeAmong(Set<UUID> ids) {
        if (ids.isEmpty()) {
            return Set.of();
        }
        return departments.findByIdInAndActiveTrue(ids).stream().map(DepartmentEntity::id).collect(Collectors.toSet());
    }

    @Override
    @Transactional
    public void ensureDefaults() {
        // Por tipo, não por contagem: a V7 pode ter criado só a Recepção numa clínica antiga.
        List<DepartmentEntity> existing = departments.findAll();
        if (existing.stream().noneMatch(DepartmentEntity::isDefault)) {
            departments.save(new DepartmentEntity("Recepção", Kind.RECEPTION, true, clock.instant()));
        }
        if (existing.stream().noneMatch(d -> d.kind() == Kind.CLINICAL) && !departments.existsByName("Clínica")) {
            departments.save(new DepartmentEntity("Clínica", Kind.CLINICAL, false, clock.instant()));
        }
        if (existing.stream().noneMatch(d -> d.kind() == Kind.PETSHOP) && !departments.existsByName("Pet shop")) {
            departments.save(new DepartmentEntity("Pet shop", Kind.PETSHOP, false, clock.instant()));
        }
    }

    private DepartmentEntity own(UUID id) {
        return departments.findById(id).orElseThrow(() -> new ResourceNotFoundException("Setor", id));
    }

    private static String validName(String name) {
        String clean = name == null ? "" : name.strip();
        if (clean.isEmpty() || clean.length() > 60) {
            throw new DomainValidationException("name", "de 1 a 60 caracteres");
        }
        return clean;
    }
}
