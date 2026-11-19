package com.clinicavet.assistant.platform.application;

import com.clinicavet.assistant.platform.AuditLog;
import com.clinicavet.assistant.platform.Permission;
import com.clinicavet.assistant.platform.RoleTemplate;
import com.clinicavet.assistant.platform.Roles;
import com.clinicavet.assistant.platform.SectorScope;
import com.clinicavet.assistant.platform.infrastructure.persistence.RoleEntity;
import com.clinicavet.assistant.platform.infrastructure.persistence.RoleJpaRepository;
import com.clinicavet.assistant.shared.exception.ConflictException;
import com.clinicavet.assistant.shared.exception.DomainValidationException;
import com.clinicavet.assistant.shared.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Perfis editáveis da clínica atual (ADR-0024). O filtro de clínica vem do {@code @TenantId}. */
@Service
class RoleService implements Roles {

    private final RoleJpaRepository roles;
    private final AuditLog audit;
    private final Clock clock;

    RoleService(RoleJpaRepository roles, AuditLog audit, Clock clock) {
        this.roles = roles;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<View> list() {
        return roles.findAllByOrderByNameAsc().stream().map(RoleService::view).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<View> find(UUID id) {
        return roles.findById(id).map(RoleService::view);
    }

    @Override
    @Transactional
    public View create(String name, SectorScope scope, Set<Permission> permissions) {
        String clean = validName(name);
        if (roles.existsByName(clean)) {
            throw new ConflictException("Já existe um perfil com esse nome.");
        }
        RoleEntity role = roles.save(new RoleEntity(clean, scope, null, permissions, clock.instant()));
        audit.record("role.created", "role", role.id(), "Perfil criado: " + clean);
        return view(role);
    }

    @Override
    @Transactional
    public View update(UUID id, String name, SectorScope scope, Set<Permission> permissions) {
        RoleEntity role = roles.findById(id).orElseThrow(() -> new ResourceNotFoundException("Perfil", id));
        String clean = validName(name);
        if (roles.existsByNameAndIdNot(clean, id)) {
            throw new ConflictException("Já existe um perfil com esse nome.");
        }
        if (role.template() == RoleTemplate.ADMIN && !permissions.contains(Permission.TEAM_MANAGE)) {
            // Sem isso, a clínica pode perder o único caminho para administrar a equipe.
            throw new DomainValidationException("permissions", "o perfil Administrador mantém team:manage");
        }
        role.change(clean, scope, permissions);
        audit.record("role.updated", "role", id, "Perfil alterado: " + clean + " (" + permissions.size() + " permissões)");
        return view(role);
    }

    @Override
    @Transactional
    public Map<RoleTemplate, UUID> ensureDefaults() {
        Map<RoleTemplate, UUID> byTemplate = new EnumMap<>(RoleTemplate.class);
        for (RoleEntity role : roles.findAll()) {
            if (role.template() != null) {
                byTemplate.putIfAbsent(role.template(), role.id());
            }
        }
        for (RoleTemplate template : RoleTemplate.values()) {
            if (!byTemplate.containsKey(template) && !roles.existsByName(template.displayName())) {
                RoleEntity role = roles.save(new RoleEntity(template.displayName(), template.scope(), template,
                        template.permissions(), clock.instant()));
                byTemplate.put(template, role.id());
            }
        }
        return byTemplate;
    }

    private static String validName(String name) {
        String clean = name == null ? "" : name.strip();
        if (clean.isEmpty() || clean.length() > 60) {
            throw new DomainValidationException("name", "de 1 a 60 caracteres");
        }
        return clean;
    }

    private static View view(RoleEntity role) {
        return new View(role.id(), role.name(), role.scope(), role.template(), role.permissions());
    }
}
