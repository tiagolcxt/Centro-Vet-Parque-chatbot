package com.clinicavet.assistant.shared.exception;

/** Valor inválido para um campo do domínio. Mapeada para HTTP 422. */
public class DomainValidationException extends DomainException {

    private final String field;

    public DomainValidationException(String field, String reason) {
        super(field + ": " + reason);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
