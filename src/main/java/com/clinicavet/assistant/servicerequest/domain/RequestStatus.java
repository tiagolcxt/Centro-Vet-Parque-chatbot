package com.clinicavet.assistant.servicerequest.domain;

public enum RequestStatus {
    /** Dados ainda em coleta pelo assistente. */
    COLLECTING,
    /** Pronta para a equipe. */
    PENDING,
    IN_PROGRESS,
    RESOLVED,
    CANCELLED;

    public boolean isFinal() {
        return this == RESOLVED || this == CANCELLED;
    }
}
