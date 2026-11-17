package com.clinicavet.assistant.conversation.domain;

import java.util.Optional;
import java.util.Set;

/**
 * Prioridade efetiva de um turno (ADR-0018; hierarquia de prioridades, H3 a H8). Determinística e sem
 * dados comerciais (H1).
 * <ul>
 *   <li><b>Nível do turno</b> = o mais urgente entre a sugestão do modelo e os pisos do backend (H5):
 *       possível emergência → P1; preocupação médica, agravamento, alteração após procedimento ou incerteza
 *       clínica → no máximo P2; acompanhamento de tratamento → no máximo P3; expressão de alerta → P1 ou P2.</li>
 *   <li><b>Nível efetivo</b> = o mais urgente entre o do turno e o atual da conversa. Nunca rebaixa (H4).</li>
 * </ul>
 * Na falha da IA não há sugestão do modelo, mas os pisos determinísticos continuam valendo (H8).
 */
public final class PriorityPolicy {

    /** O que o backend sabe do turno para calcular os pisos. */
    public record Inputs(ClinicalPriority modelSuggestion, Set<Intent> intents, Set<TurnSignal> signals,
                         boolean possibleEmergency, boolean clinicalUncertainty,
                         Optional<ClinicalPriority> alertFloor) {

        public Inputs {
            intents = intents == null ? Set.of() : Set.copyOf(intents);
            signals = signals == null ? Set.of() : Set.copyOf(signals);
            alertFloor = alertFloor == null ? Optional.empty() : alertFloor;
        }

        /** Turno sem nada do modelo (falha): só os pisos determinísticos. */
        public static Inputs deterministicOnly(Optional<ClinicalPriority> alertFloor) {
            return new Inputs(null, Set.of(), Set.of(), false, false, alertFloor);
        }
    }

    /**
     * @param turnLevel nível deste turno (modelo + pisos), sem o nível que a conversa já tinha; é ele que
     *                  decide o encaminhamento por prioridade, para não reencaminhar uma conversa que a equipe
     *                  devolveu sem rebaixar
     * @param effective o que fica na conversa
     */
    public record Assessment(ClinicalPriority turnLevel, ClinicalPriority effective, PrioritySource source) {
    }

    private PriorityPolicy() {
    }

    public static ClinicalPriority floor(Inputs in) {
        ClinicalPriority floor = null;
        if (in.possibleEmergency()) {
            floor = ClinicalPriority.P1_POSSIBLE_EMERGENCY;
        }
        if (in.intents().contains(Intent.MEDICAL_CONCERN) || in.clinicalUncertainty()
                || in.signals().contains(TurnSignal.WORSENING) || in.signals().contains(TurnSignal.POST_PROCEDURE_CHANGE)) {
            floor = ClinicalPriority.mostUrgent(floor, ClinicalPriority.P2_URGENT);
        }
        if (in.intents().contains(Intent.TREATMENT_FOLLOW_UP)) {
            floor = ClinicalPriority.mostUrgent(floor, ClinicalPriority.P3_CLINICAL_FOLLOW_UP);
        }
        return ClinicalPriority.mostUrgent(floor, in.alertFloor().orElse(null));
    }

    public static Assessment assess(ClinicalPriority current, Inputs in) {
        ClinicalPriority floor = floor(in);
        ClinicalPriority turnLevel = ClinicalPriority.mostUrgent(in.modelSuggestion(), floor);
        if (turnLevel == null) {
            turnLevel = ClinicalPriority.INITIAL;
        }
        if (!turnLevel.isMoreUrgentThan(current)) {
            return new Assessment(turnLevel, current, PrioritySource.CURRENT_LEVEL);
        }
        boolean modelWins = in.modelSuggestion() != null && (floor == null || in.modelSuggestion().isMoreUrgentThan(floor));
        PrioritySource source = modelWins ? PrioritySource.MODEL : PrioritySource.BACKEND_FLOOR;
        return new Assessment(turnLevel, turnLevel, source);
    }
}
