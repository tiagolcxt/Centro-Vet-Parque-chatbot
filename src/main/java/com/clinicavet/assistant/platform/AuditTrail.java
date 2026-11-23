package com.clinicavet.assistant.platform;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Leitura da auditoria da clínica atual (revisão pré-piloto, E2). Só leitura: não existe operação que altere ou apague
 * um registro. Os filtros vazios são ignorados.
 */
public interface AuditTrail {

    record Entry(UUID id, Instant occurredAt, UUID actorUserId, String action, String targetType, String targetId,
                 String summary) {
    }

    /**
     * @param actionPrefix  ex.: {@code conversation.} ou {@code request.status_changed}
     * @param page          começa em 0
     */
    /** @param withoutLogins deixa de fora entradas no sistema ({@code auth.*}), que são muitas e pouco informativas */
    record Filter(Instant from, Instant to, UUID actorUserId, String actionPrefix, String targetType,
                  String targetId, boolean withoutLogins) {
    }

    record Page(List<Entry> items, int page, int size, long total) {
    }

    Page search(Filter filter, int page, int size);
}
