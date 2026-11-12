package com.clinicavet.assistant.conversation.domain;

import java.time.Duration;
import java.util.Objects;

/**
 * Prazos de expiração por inatividade, por status (ADR-0009).
 * <p>
 * <b>Os valores são pendentes (P-05)</b>: não há valor padrão. A configuração é obrigatória e,
 * até a decisão, só valores provisórios de desenvolvimento ou de teste são usados.
 */
public record ConversationExpirationPolicy(Duration openTimeout, Duration waitingHumanTimeout) {

    public ConversationExpirationPolicy {
        requirePositive("openTimeout", openTimeout);
        requirePositive("waitingHumanTimeout", waitingHumanTimeout);
    }

    public Duration timeoutFor(ConversationStatus status) {
        return switch (Objects.requireNonNull(status, "status")) {
            case OPEN -> openTimeout;
            case WAITING_HUMAN -> waitingHumanTimeout;
            case CLOSED -> throw new IllegalArgumentException("conversa encerrada não expira");
        };
    }

    private static void requirePositive(String name, Duration value) {
        Objects.requireNonNull(value, name);
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " deve ser positivo");
        }
    }
}
