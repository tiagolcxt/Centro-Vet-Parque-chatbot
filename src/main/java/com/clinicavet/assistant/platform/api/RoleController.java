package com.clinicavet.assistant.platform.api;

import com.clinicavet.assistant.platform.Permission;
import com.clinicavet.assistant.platform.Roles;
import com.clinicavet.assistant.platform.SectorScope;
import com.clinicavet.assistant.shared.exception.DomainValidationException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Perfis editáveis e catálogo de permissões (RF-S04). Só com {@code team:manage}. */
@RestController
@RequestMapping("/app/api")
@PreAuthorize("hasAuthority('team:manage')")
class RoleController {

    private final Roles roles;

    RoleController(Roles roles) {
        this.roles = roles;
    }

    @GetMapping("/permissions")
    List<String> permissions() {
        return Arrays.stream(Permission.values()).map(Permission::code).toList();
    }

    @GetMapping("/roles")
    List<RoleResponse> list() {
        return roles.list().stream().map(RoleResponse::from).toList();
    }

    @PostMapping("/roles")
    @ResponseStatus(HttpStatus.CREATED)
    RoleResponse create(@Valid @RequestBody RoleRequest body) {
        return RoleResponse.from(roles.create(body.name(), body.sectorScope(), parse(body.permissions())));
    }

    @PutMapping("/roles/{id}")
    RoleResponse update(@PathVariable UUID id, @Valid @RequestBody RoleRequest body) {
        return RoleResponse.from(roles.update(id, body.name(), body.sectorScope(), parse(body.permissions())));
    }

    private static Set<Permission> parse(List<String> codes) {
        Set<Permission> parsed = EnumSet.noneOf(Permission.class);
        for (String code : codes) {
            parsed.add(Permission.fromCode(code).orElseThrow(
                    () -> new DomainValidationException("permissions", "permissão desconhecida: " + code)));
        }
        return parsed;
    }

    record RoleRequest(@NotBlank @Size(max = 60) String name, @NotNull SectorScope sectorScope,
                       @NotNull @Size(max = 50) List<@NotBlank String> permissions) {
    }

    record RoleResponse(UUID id, String name, SectorScope sectorScope, String template, List<String> permissions) {
        static RoleResponse from(Roles.View role) {
            return new RoleResponse(role.id(), role.name(), role.scope(),
                    role.template() == null ? null : role.template().name(),
                    role.permissions().stream().map(Permission::code).sorted().toList());
        }
    }
}
