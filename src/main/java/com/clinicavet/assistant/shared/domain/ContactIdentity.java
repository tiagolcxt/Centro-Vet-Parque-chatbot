package com.clinicavet.assistant.shared.domain;

import com.clinicavet.assistant.shared.exception.DomainValidationException;

import java.util.Objects;

/**
 * Identidade do contato <b>fornecida pelo canal</b> (ex.: número do WhatsApp), nunca extraída do
 * texto da mensagem (ADR-0001, ADR-0010).
 */
public record ContactIdentity(Channel channel, String externalContactId) {

    public static final int MAX_EXTERNAL_ID_LENGTH = 128;

    public ContactIdentity {
        Objects.requireNonNull(channel, "channel");
        if (externalContactId == null || externalContactId.isBlank()) {
            throw new DomainValidationException("externalContactId", "não pode ser vazio");
        }
        externalContactId = externalContactId.strip();
        if (externalContactId.length() > MAX_EXTERNAL_ID_LENGTH) {
            throw new DomainValidationException("externalContactId",
                    "excede " + MAX_EXTERNAL_ID_LENGTH + " caracteres");
        }
    }
}
