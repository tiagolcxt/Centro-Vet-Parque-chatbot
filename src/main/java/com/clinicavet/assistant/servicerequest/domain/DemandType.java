package com.clinicavet.assistant.servicerequest.domain;

/**
 * Tipo da demanda (D9, revisão pré-piloto 3.2): o assunto da solicitação, separado do fluxo ({@link RequestType}) e do
 * risco clínico. Classificado pelo backend a partir da intenção e do serviço; a equipe corrige quando precisar.
 */
public enum DemandType {
    APPOINTMENT,
    CLINICAL_QUESTION,
    ADMINISTRATIVE_INFO,
    COMMERCIAL,
    GROOMING,
    COMPLAINT,
    HUMAN_CONTACT,
    OTHER;

    /** Pouco impacto na ordem da fila quando não há sinal clínico (OperationalPriority). */
    public boolean lowImpact() {
        return this == ADMINISTRATIVE_INFO || this == OTHER;
    }

    /** Valor de quando nada melhor foi classificado: deduzido do fluxo. */
    public static DemandType fallbackFor(RequestType type) {
        return switch (type) {
            case APPOINTMENT -> APPOINTMENT;
            case INFORMATION -> ADMINISTRATIVE_INFO;
            case FOLLOW_UP -> CLINICAL_QUESTION;
            case HUMAN_CONTACT -> HUMAN_CONTACT;
        };
    }
}
