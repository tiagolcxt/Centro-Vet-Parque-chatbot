package com.clinicavet.assistant.shared.exception;

/** Base das exceções de regra de negócio. Sem dependência de framework. */
public abstract class DomainException extends RuntimeException {

    protected DomainException(String message) {
        super(message);
    }
}
