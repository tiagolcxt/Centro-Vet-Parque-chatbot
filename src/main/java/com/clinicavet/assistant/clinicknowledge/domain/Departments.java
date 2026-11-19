package com.clinicavet.assistant.clinicknowledge.domain;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Setores da clínica atual (DA-42, ADR-0020): configuração, não módulos. Exatamente um é o padrão (a Recepção), que
 * recebe o que não tem regra. Setores não são versionados (ADR-0026): renomear vale na hora.
 */
public interface Departments {

    enum Kind {
        RECEPTION, CLINICAL, PETSHOP, OTHER
    }

    record Department(UUID id, String name, Kind kind, boolean active, boolean isDefault) {
    }

    List<Department> list();

    Optional<Department> find(UUID id);

    Department create(String name, Kind kind);

    Department rename(UUID id, String name);

    /** O setor padrão não pode ser desativado. */
    Department setActive(UUID id, boolean active);

    /** O setor padrão (a Recepção): recebe o que não tem regra. */
    Department defaultDepartment();

    /** Ids que existem e estão ativos, dentre os informados. */
    Set<UUID> activeAmong(Set<UUID> ids);

    /** Cria Recepção (padrão), Clínica e Pet shop se a clínica ainda não tem setores. Idempotente. */
    void ensureDefaults();
}
