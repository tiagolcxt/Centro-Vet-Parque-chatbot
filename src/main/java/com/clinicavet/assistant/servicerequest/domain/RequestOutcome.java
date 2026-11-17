package com.clinicavet.assistant.servicerequest.domain;

/** Desfecho informado pela equipe ao resolver ou cancelar (DA-34). O assistente não o lê no MVP (P-15). */
public enum RequestOutcome {
    SCHEDULED_BY_STAFF,
    INFORMATION_PROVIDED,
    HANDLED_OUTSIDE,
    NO_AVAILABILITY,
    CUSTOMER_GAVE_UP,
    NO_RESPONSE,
    DUPLICATE,
    /** Exige observação. */
    OTHER
}
