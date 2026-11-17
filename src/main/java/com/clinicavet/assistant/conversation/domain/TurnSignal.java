package com.clinicavet.assistant.conversation.domain;

import java.util.Locale;
import java.util.Optional;

/**
 * Sinais do turno (hierarquia de prioridades, seção 4; ADR-0018). Os que vêm do modelo são só dicas:
 * o backend decide. Nenhum sinal é usado para finalidade comercial.
 */
public enum TurnSignal {
    EMOTIONAL_DISTRESS,
    DISSATISFACTION,
    CONFUSION,
    FINANCIAL_VULNERABILITY,
    BEREAVEMENT,
    NEEDS_STAFF_DECISION,
    /** Agravamento: piso P2 (H5). */
    WORSENING,
    /** Alteração após procedimento: piso P2 (H5). */
    POST_PROCEDURE_CHANGE,
    /** Detectado pelo backend (DA-31). O modelo não envia este. */
    UNPRODUCTIVE_CONVERSATION,
    /** Correspondência na lista de expressões de alerta (DA-27). Detectado pelo backend. */
    ALERT_EXPRESSION;

    /** Valor externo; desconhecido é ignorado. */
    public static Optional<TurnSignal> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String normalized = raw.strip().toUpperCase(Locale.ROOT);
        for (TurnSignal signal : values()) {
            if (signal.name().equals(normalized)) {
                return Optional.of(signal);
            }
        }
        return Optional.empty();
    }

    /** Sinais que só o backend produz: não aparecem no schema do modelo. */
    public boolean backendOnly() {
        return this == UNPRODUCTIVE_CONVERSATION || this == ALERT_EXPRESSION;
    }
}
