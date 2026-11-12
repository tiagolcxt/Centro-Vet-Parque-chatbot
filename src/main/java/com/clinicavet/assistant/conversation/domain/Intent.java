package com.clinicavet.assistant.conversation.domain;

import java.util.Locale;

/**
 * Intenção principal de um turno (DM-09). Orienta a conversa, mas não impede mudança de assunto.
 */
public enum Intent {
    GREETING,
    APPOINTMENT_REQUEST,
    SERVICE_INFORMATION,
    PRICE_INQUIRY,
    CLINIC_INFORMATION,
    PET_INFORMATION,
    TREATMENT_FOLLOW_UP,
    MEDICAL_CONCERN,
    COMPLAINT,
    HUMAN_REQUEST,
    OTHER;

    /**
     * Converte um valor externo (ex.: vindo do modelo). Valor ausente ou desconhecido vira
     * {@link #OTHER}: nunca confiamos cegamente na classificação recebida (ADR-0002).
     */
    public static Intent parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return OTHER;
        }
        String normalized = raw.strip().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        for (Intent intent : values()) {
            if (intent.name().equals(normalized)) {
                return intent;
            }
        }
        return OTHER;
    }
}
