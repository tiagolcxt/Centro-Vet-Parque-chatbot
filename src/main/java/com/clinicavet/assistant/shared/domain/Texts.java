package com.clinicavet.assistant.shared.domain;

import com.clinicavet.assistant.shared.exception.DomainValidationException;

/** Validação de textos curtos dos agregados. */
public final class Texts {

    private Texts() {
    }

    /** Texto obrigatório: sem espaços nas pontas, não vazio e dentro do limite. */
    public static String required(String field, String value, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new DomainValidationException(field, "não pode ser vazio");
        }
        return withinLimit(field, value.strip(), maxLength);
    }

    /** Texto opcional: vazio vira {@code null}. */
    public static String optional(String field, String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return withinLimit(field, value.strip(), maxLength);
    }

    private static String withinLimit(String field, String value, int maxLength) {
        if (value.length() > maxLength) {
            throw new DomainValidationException(field, "excede " + maxLength + " caracteres");
        }
        return value;
    }
}
