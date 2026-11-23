package com.clinicavet.assistant.shared.domain;

/**
 * Risco clínico da conversa (D9, revisão pré-piloto 3.2). Só sobe automaticamente; rebaixar exige uma pessoa e fica
 * registrado (RN-S03). Não diagnostica: só ordena e encaminha. Os critérios são da equipe veterinária (P-20).
 */
public enum ClinicalRisk {
    POSSIBLE_EMERGENCY,
    URGENT,
    CLINICAL_FOLLOW_UP,
    /** Avaliado, sem sinal clínico identificado. */
    NONE_IDENTIFIED,
    /** Ainda sem avaliação válida (o modelo falhou ou a conversa acabou de começar). Não é "baixo". */
    NOT_ASSESSED;

    public boolean isUrgentOrWorse() {
        return this == POSSIBLE_EMERGENCY || this == URGENT;
    }
}
