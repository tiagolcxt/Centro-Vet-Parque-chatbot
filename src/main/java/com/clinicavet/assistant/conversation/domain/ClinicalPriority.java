package com.clinicavet.assistant.conversation.domain;

/**
 * Prioridade clínica da conversa (ADR-0018, hierarquia de prioridades). Independente do status (H9).
 * P1 é o mais urgente; "elevar" é ir para um número menor. Os critérios clínicos não são do sistema
 * (P-20): a prioridade só ordena e encaminha, nunca diagnostica.
 */
public enum ClinicalPriority {
    P1_POSSIBLE_EMERGENCY,
    P2_URGENT,
    P3_CLINICAL_FOLLOW_UP,
    P4_SERVICE_REQUEST,
    P5_ADMINISTRATIVE,
    P6_RELATIONSHIP;

    /** Conversa nova começa aqui (H4). */
    public static final ClinicalPriority INITIAL = P6_RELATIONSHIP;

    public boolean isMoreUrgentThan(ClinicalPriority other) {
        return ordinal() < other.ordinal();
    }

    public static ClinicalPriority mostUrgent(ClinicalPriority a, ClinicalPriority b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return a.isMoreUrgentThan(b) ? a : b;
    }

    /** P1 ou P2: encaminhamento obrigatório e orientação de emergência na falha (H8, DA-25). */
    public boolean isUrgentOrWorse() {
        return this == P1_POSSIBLE_EMERGENCY || this == P2_URGENT;
    }

    /** Valor externo (do modelo). Desconhecido ou ausente: {@code null}, isto é, sem sugestão. */
    public static ClinicalPriority parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.strip().toUpperCase(java.util.Locale.ROOT);
        for (ClinicalPriority priority : values()) {
            if (priority.name().equals(normalized) || priority.name().startsWith(normalized + "_")) {
                return priority;
            }
        }
        return null;
    }
}
