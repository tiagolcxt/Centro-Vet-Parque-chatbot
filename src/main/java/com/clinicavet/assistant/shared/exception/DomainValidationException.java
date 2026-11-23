package com.clinicavet.assistant.shared.exception;

/** Valor inválido para um campo do domínio. Mapeada para HTTP 422. */
public class DomainValidationException extends DomainException {

    private final String field;

    public DomainValidationException(String field, String reason) {
        super(UserText.field(field).isEmpty() ? capitalize(reason) : UserText.field(field) + ": " + reason);
        this.field = field;
    }

    private static String capitalize(String text) {
        return text == null || text.isEmpty() ? "Valor inválido." : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    public String field() {
        return field;
    }
}
