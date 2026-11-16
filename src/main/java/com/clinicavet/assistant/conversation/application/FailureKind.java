package com.clinicavet.assistant.conversation.application;

/** Por que um turno não produziu resposta utilizável (ADR-0002). */
public enum FailureKind {
    REFUSAL(false),
    TRUNCATED(true),
    INVALID(true),
    /** Texto e metadados se contradizem. As regras entram nas Etapas 3 e 4. */
    INCONSISTENT(true),
    TIMEOUT(true),
    TECHNICAL_TRANSIENT(true),
    TECHNICAL_PERMANENT(false);

    private final boolean retryable;

    FailureKind(boolean retryable) {
        this.retryable = retryable;
    }

    /** Vale a única nova tentativa do turno (DA-28)? Recusa e erro permanente, não. */
    public boolean retryable() {
        return retryable;
    }
}
