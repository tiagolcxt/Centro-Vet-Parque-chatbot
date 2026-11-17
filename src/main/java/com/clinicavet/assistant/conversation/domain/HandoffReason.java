package com.clinicavet.assistant.conversation.domain;

/**
 * Motivo do encaminhamento para a equipe (ADR-0007, itens 2 e 3; DA-25). Quando várias condições valem
 * no mesmo turno, fica a primeira desta ordem.
 */
public enum HandoffReason {
    POSSIBLE_EMERGENCY,
    CLINICAL_PRIORITY,
    EXPLICIT_REQUEST,
    COMPLAINT,
    CLINICAL,
    DISSATISFACTION,
    STAFF_DECISION,
    MODEL_SIGNAL,
    EMOTIONAL_DISTRESS,
    UNPRODUCTIVE_CONVERSATION,
    TURN_FAILURE,
    CLINIC_RULE
}
