package com.clinicavet.assistant.team;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * O que os outros módulos precisam saber da equipe da clínica atual (ADR-0020): setores de cada funcionário, nome de
 * exibição e disponibilidade. O módulo de equipe nunca pergunta nada sobre conversas (briefing 12.3).
 */
public interface StaffDirectory {

    record Member(UUID userId, String displayName, Set<UUID> departmentIds, boolean available, boolean active) {
    }

    Optional<Member> find(UUID userId);

    /** Nomes de exibição, para mostrar autores e responsáveis. Ids desconhecidos ficam de fora. */
    Map<UUID, String> names(Set<UUID> userIds);

    Set<UUID> departmentsOf(UUID userId);

    /** Setores que o funcionário enxerga (RN-S11): todos, se o perfil tem escopo "todos"; senão, os dele. */
    Set<UUID> visibleDepartments(com.clinicavet.assistant.platform.StaffUser user);
}
