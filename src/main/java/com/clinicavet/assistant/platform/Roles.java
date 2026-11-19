package com.clinicavet.assistant.platform;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Perfis da clínica atual (ADR-0024). */
public interface Roles {

    record View(UUID id, String name, SectorScope scope, RoleTemplate template, Set<Permission> permissions) {
    }

    List<View> list();

    Optional<View> find(UUID id);

    View create(String name, SectorScope scope, Set<Permission> permissions);

    View update(UUID id, String name, SectorScope scope, Set<Permission> permissions);

    /** Cria os seis perfis-modelo que faltarem. Idempotente. */
    Map<RoleTemplate, UUID> ensureDefaults();
}
