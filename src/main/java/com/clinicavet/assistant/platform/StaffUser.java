package com.clinicavet.assistant.platform;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Funcionário autenticado na requisição atual (ADR-0024). Recarregado do banco a cada requisição: mudança de perfil
 * vale na hora, e conta desativada perde o acesso na requisição seguinte.
 */
public record StaffUser(UUID userId, ClinicId clinic, String email, UUID roleId, SectorScope scope,
                        Set<Permission> permissions) {

    public StaffUser {
        permissions = Set.copyOf(permissions);
    }

    public boolean has(Permission permission) {
        return permissions.contains(permission);
    }

    public static Optional<StaffUser> current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof StaffUser user
                ? Optional.of(user) : Optional.empty();
    }
}
