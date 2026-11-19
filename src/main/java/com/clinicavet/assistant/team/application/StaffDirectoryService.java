package com.clinicavet.assistant.team.application;

import com.clinicavet.assistant.platform.Accounts;
import com.clinicavet.assistant.team.StaffDirectory;
import com.clinicavet.assistant.team.infrastructure.EmployeeEntity;
import com.clinicavet.assistant.team.infrastructure.EmployeeJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
class StaffDirectoryService implements StaffDirectory {

    private final EmployeeJpaRepository employees;
    private final Accounts accounts;
    private final com.clinicavet.assistant.clinicknowledge.domain.Departments departments;

    StaffDirectoryService(EmployeeJpaRepository employees, Accounts accounts,
                          com.clinicavet.assistant.clinicknowledge.domain.Departments departments) {
        this.employees = employees;
        this.accounts = accounts;
        this.departments = departments;
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> visibleDepartments(com.clinicavet.assistant.platform.StaffUser user) {
        if (user.scope() == com.clinicavet.assistant.platform.SectorScope.ALL) {
            return departments.list().stream().map(com.clinicavet.assistant.clinicknowledge.domain.Departments.Department::id)
                    .collect(java.util.stream.Collectors.toSet());
        }
        return departmentsOf(user.userId());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Member> find(UUID userId) {
        return employees.findById(userId).map(e -> new Member(e.id(), e.displayName(), e.departmentIds(),
                e.availability() == EmployeeEntity.Availability.AVAILABLE,
                accounts.find(userId).map(a -> a.status() == Accounts.Status.ACTIVE).orElse(false)));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, String> names(Set<UUID> userIds) {
        Map<UUID, String> names = new HashMap<>();
        if (!userIds.isEmpty()) {
            employees.findAllById(userIds).forEach(e -> names.put(e.id(), e.displayName()));
        }
        return names;
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> departmentsOf(UUID userId) {
        return employees.findById(userId).map(EmployeeEntity::departmentIds).orElse(Set.of());
    }
}
