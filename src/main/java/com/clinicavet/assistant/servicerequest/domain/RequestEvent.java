package com.clinicavet.assistant.servicerequest.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Histórico da solicitação (RF-S35): toda mudança de setor, responsável ou estado. {@code actorId} vazio = assistente
 * ou sistema. Transferência exige motivo (RN-S06).
 */
public record RequestEvent(UUID id, ServiceRequestId requestId, Type type, UUID actorId, String from, String to,
                           String reason, Instant at) {

    public static final int MAX_REASON = 300;

    public enum Type {
        ASSIGNED, UNASSIGNED, TRANSFERRED, STATUS_CHANGED
    }

    public RequestEvent {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(at, "at");
        if (type == Type.TRANSFERRED && (reason == null || reason.isBlank())) {
            throw new IllegalArgumentException("transferência exige motivo");
        }
    }

    public static RequestEvent of(ServiceRequestId requestId, Type type, UUID actorId, Object from, Object to,
                                  String reason, Instant at) {
        return new RequestEvent(UUID.randomUUID(), requestId, type, actorId, from == null ? null : from.toString(),
                to == null ? null : to.toString(), reason, at);
    }
}
