package com.clinicavet.assistant.shared.exception;

/** Operação que conflita com o estado atual (ex.: e-mail já usado). Mapeada para HTTP 409. */
public class ConflictException extends DomainException {

    public ConflictException(String message) {
        super(message);
    }
}
