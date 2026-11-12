package com.clinicavet.assistant.shared.exception;

/** Transição de estado não permitida pelo ciclo de vida do agregado. Mapeada para HTTP 409. */
public class InvalidStateTransitionException extends DomainException {

    private final String aggregate;
    private final String from;
    private final String action;

    public InvalidStateTransitionException(String aggregate, Enum<?> from, String action) {
        super("%s: '%s' não é permitido a partir de %s".formatted(aggregate, action, from));
        this.aggregate = aggregate;
        this.from = from.name();
        this.action = action;
    }

    public String aggregate() {
        return aggregate;
    }

    public String from() {
        return from;
    }

    public String action() {
        return action;
    }
}
