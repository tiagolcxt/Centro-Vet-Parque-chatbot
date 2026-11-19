package com.clinicavet.assistant.team.application;

import com.clinicavet.assistant.clinicknowledge.domain.Departments;
import com.clinicavet.assistant.platform.Accounts;
import com.clinicavet.assistant.platform.AuditLog;
import com.clinicavet.assistant.platform.RoleTemplate;
import com.clinicavet.assistant.platform.Roles;
import com.clinicavet.assistant.platform.StaffUser;
import com.clinicavet.assistant.shared.exception.ConflictException;
import com.clinicavet.assistant.shared.exception.DomainValidationException;
import com.clinicavet.assistant.shared.exception.ResourceNotFoundException;
import com.clinicavet.assistant.team.infrastructure.EmployeeEntity;
import com.clinicavet.assistant.team.infrastructure.EmployeeJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Equipe da clínica atual (RF-S03, RF-S05, RF-S06). Separa identidade (conta, no platform) de cargo, registro,
 * setores e disponibilidade (aqui), como pede o briefing (seção 7).
 */
@Service
public class TeamService {

    public record EmployeeView(UUID id, String displayName, String email, UUID roleId, Accounts.Status status,
                               String jobTitle, String professionalRegistration,
                               EmployeeEntity.Availability availability, Set<UUID> departmentIds) {
    }

    public record EmployeeData(String displayName, String email, UUID roleId, Set<UUID> departmentIds,
                               String jobTitle, String professionalRegistration) {
    }

    public record Invited(EmployeeView employee, Accounts.InviteLink invite) {
    }

    private final EmployeeJpaRepository employees;
    private final Accounts accounts;
    private final Roles roles;
    private final Departments departments;
    private final AuditLog audit;

    TeamService(EmployeeJpaRepository employees, Accounts accounts, Roles roles, Departments departments,
                AuditLog audit) {
        this.employees = employees;
        this.accounts = accounts;
        this.roles = roles;
        this.departments = departments;
        this.audit = audit;
    }

    @Transactional
    public Invited invite(EmployeeData data) {
        Set<UUID> sectors = validDepartments(data.departmentIds());
        Accounts.InviteLink link = accounts.invite(data.email(), data.roleId());
        EmployeeEntity employee = employees.save(new EmployeeEntity(link.userId(), validName(data.displayName()),
                blankToNull(data.jobTitle()), blankToNull(data.professionalRegistration()), sectors));
        return new Invited(view(employee, accounts.find(employee.id()).orElseThrow()), link);
    }

    /**
     * Primeiro administrador de uma clínica sem contas (RF-S01: a clínica é criada pela equipe da plataforma).
     * Garante setores e perfis padrão e convida com o perfil Administrador, em todos os setores.
     */
    @Transactional
    public Invited inviteFirstAdmin(String displayName, String email) {
        if (accounts.count() > 0) {
            throw new ConflictException("A clínica já tem contas. Novos funcionários são convidados pelo administrador.");
        }
        departments.ensureDefaults();
        UUID admin = roles.ensureDefaults().get(RoleTemplate.ADMIN);
        Set<UUID> all = departments.list().stream().map(Departments.Department::id).collect(Collectors.toSet());
        return invite(new EmployeeData(displayName, email, admin, all, "Administrador", null));
    }

    @Transactional
    public EmployeeView update(UUID id, EmployeeData data) {
        EmployeeEntity employee = own(id);
        Accounts.View account = accounts.find(id).orElseThrow(() -> new ResourceNotFoundException("Funcionário", id));
        employee.change(validName(data.displayName()), blankToNull(data.jobTitle()),
                blankToNull(data.professionalRegistration()), validDepartments(data.departmentIds()));
        if (data.roleId() != null && !data.roleId().equals(account.roleId())) {
            accounts.changeRole(id, data.roleId());
        }
        audit.record("employee.updated", "user", id, null);
        return view(employee, accounts.find(id).orElseThrow());
    }

    @Transactional
    public void disable(UUID id, StaffUser by) {
        own(id);
        if (by.userId().equals(id)) {
            throw new ConflictException("Ninguém desativa a própria conta: peça a outro administrador.");
        }
        accounts.disable(id);
    }

    @Transactional
    public Accounts.InviteLink newInvite(UUID id) {
        own(id);
        return accounts.newInvite(id);
    }

    @Transactional
    public EmployeeView setAvailability(UUID id, EmployeeEntity.Availability availability) {
        EmployeeEntity employee = own(id);
        employee.setAvailability(availability);
        return view(employee, accounts.find(id).orElseThrow());
    }

    @Transactional(readOnly = true)
    public List<EmployeeView> list() {
        Map<UUID, Accounts.View> byId = accounts.list().stream()
                .collect(Collectors.toMap(Accounts.View::id, Function.identity()));
        return employees.findAllByOrderByDisplayNameAsc().stream()
                .filter(e -> byId.containsKey(e.id()))
                .map(e -> view(e, byId.get(e.id()))).toList();
    }

    @Transactional(readOnly = true)
    public EmployeeView get(UUID id) {
        return view(own(id), accounts.find(id).orElseThrow(() -> new ResourceNotFoundException("Funcionário", id)));
    }

    private EmployeeEntity own(UUID id) {
        return employees.findById(id).orElseThrow(() -> new ResourceNotFoundException("Funcionário", id));
    }

    private Set<UUID> validDepartments(Set<UUID> ids) {
        Set<UUID> requested = ids == null ? Set.of() : new HashSet<>(ids);
        Set<UUID> valid = departments.activeAmong(requested);
        if (!valid.equals(requested)) {
            throw new DomainValidationException("departmentIds", "setor inexistente ou inativo");
        }
        return valid;
    }

    private static EmployeeView view(EmployeeEntity e, Accounts.View account) {
        return new EmployeeView(e.id(), e.displayName(), account.email(), account.roleId(), account.status(),
                e.jobTitle(), e.professionalRegistration(), e.availability(), e.departmentIds());
    }

    private static String validName(String name) {
        String clean = name == null ? "" : name.strip();
        if (clean.isEmpty() || clean.length() > 120) {
            throw new DomainValidationException("displayName", "de 1 a 120 caracteres");
        }
        return clean;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
