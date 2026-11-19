package com.clinicavet.assistant.shared.domain;

import com.clinicavet.assistant.shared.exception.DomainValidationException;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Nota interna da equipe numa conversa ou solicitação (RF-S26, RF-S35). Nunca vai ao tutor nem ao modelo (RN-S10).
 * Só acrescentada, nunca editada.
 */
public record InternalNote(UUID id, UUID authorId, String text, Instant at) {

    public static final int MAX_TEXT = 2000;

    public InternalNote {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(at, "at");
        if (text == null || text.isBlank()) {
            throw new DomainValidationException("text", "não pode ser vazio");
        }
        text = text.strip();
        if (text.length() > MAX_TEXT) {
            throw new DomainValidationException("text", "até " + MAX_TEXT + " caracteres");
        }
    }

    public static InternalNote by(UUID authorId, String text, Instant at) {
        return new InternalNote(UUID.randomUUID(), authorId, text, at);
    }
}
