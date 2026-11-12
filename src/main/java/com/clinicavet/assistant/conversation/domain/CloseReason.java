package com.clinicavet.assistant.conversation.domain;

public enum CloseReason {
    /** Contato voltou a escrever depois do prazo de inatividade (ADR-0009). */
    EXPIRED,
    /** Encerrada por ação da equipe (ADR-0011). */
    CLOSED_BY_STAFF
}
