package com.clinicavet.assistant.conversation.domain;

/**
 * Motivo do encaminhamento para a equipe (ADR-0007, itens 2 e 3; DA-25; DA-40): da transferência da conversa ou do
 * pedido registrado para a equipe. Quando várias condições valem no mesmo turno, fica a primeira desta ordem.
 * O rótulo é o que a equipe lê (resumo da solicitação, planilha); igual ao da aplicação web ({@code labels.ts}).
 */
public enum HandoffReason {
    POSSIBLE_EMERGENCY("Possível emergência"),
    CLINICAL_PRIORITY("Prioridade clínica"),
    EXPLICIT_REQUEST("Pediu uma pessoa"),
    COMPLAINT("Reclamação"),
    CLINICAL("Assunto clínico"),
    DISSATISFACTION("Insatisfação"),
    STAFF_DECISION("Decisão da equipe"),
    MODEL_SIGNAL("Sinal do assistente"),
    EMOTIONAL_DISTRESS("Sofrimento emocional"),
    UNPRODUCTIVE_CONVERSATION("Conversa sem avanço"),
    TURN_FAILURE("Falha do assistente"),
    CLINIC_RULE("Regra da clínica"),
    /** Assunto sensível (CT-19). Só motivo de pedido registrado: não transfere a conversa. */
    SENSITIVE_TOPIC("Assunto sensível");

    private final String label;

    HandoffReason(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
