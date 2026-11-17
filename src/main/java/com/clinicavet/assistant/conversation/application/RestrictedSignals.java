package com.clinicavet.assistant.conversation.application;

import com.clinicavet.assistant.conversation.domain.ClinicalPriority;
import com.clinicavet.assistant.conversation.domain.TurnSignal;

import java.util.Set;

/**
 * Saída do modo restrito, usado em {@code WAITING_HUMAN} (ADR-0007, item 4): só sinais, nenhum texto livre.
 * O texto ao tutor é composto pelo backend.
 *
 * @param priority sugestão do modelo; {@code null} se ausente ou fora da lista
 */
public record RestrictedSignals(boolean possibleEmergency, boolean explicitHumanRequest, ClinicalPriority priority,
                                Set<TurnSignal> signals) {

    public RestrictedSignals {
        signals = signals == null ? Set.of() : Set.copyOf(signals);
    }
}
