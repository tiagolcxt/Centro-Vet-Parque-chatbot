package com.clinicavet.assistant.conversation.domain;

import java.util.Optional;
import java.util.Set;

/**
 * Decide se o turno encaminha a conversa para a equipe ou oferece atendimento humano (ADR-0007, item 2;
 * DA-25, DA-31, DA-33). Determinística. Considera a intenção principal e as secundárias. O backend pode
 * acrescentar encaminhamentos, nunca removê-los.
 * <p>
 * <b>Provisório até P-12 e P-20:</b> preocupação médica e acompanhamento (P3) encaminham direto; sofrimento,
 * confusão, luto e vulnerabilidade financeira só oferecem, salvo junto de conteúdo clínico.
 */
public final class HandoffPolicy {

    /**
     * @param explicitRequest      o detector determinístico reconheceu pedido de humano (DA-30)
     * @param intents              principal e secundárias; vazio se o turno falhou
     * @param modelRequestedHandoff o modelo pediu encaminhamento
     * @param possibleEmergency    o modelo indicou possível emergência
     * @param signals              sinais do modelo
     * @param turnLevel            nível de prioridade deste turno (sem o nível anterior da conversa)
     * @param unproductive         resultado do detector de conversa improdutiva (DA-31)
     * @param failureNeedsHandoff  a falha envolve encaminhamento (texto anunciou transferência, ADR-0002)
     * @param offerAlreadyMade     a oferta já foi feita nesta conversa (uma por conversa)
     */
    public record Inputs(boolean explicitRequest, Set<Intent> intents, boolean modelRequestedHandoff,
                         boolean possibleEmergency, Set<TurnSignal> signals, ClinicalPriority turnLevel,
                         Unproductive unproductive, boolean failureNeedsHandoff, boolean offerAlreadyMade) {

        public Inputs {
            intents = intents == null ? Set.of() : Set.copyOf(intents);
            signals = signals == null ? Set.of() : Set.copyOf(signals);
        }
    }

    public enum Unproductive { NONE, OFFER, HANDOFF }

    public record Decision(Optional<HandoffReason> handoff, boolean offerHuman) {

        public static final Decision NONE = new Decision(Optional.empty(), false);

        public boolean handsOff() {
            return handoff.isPresent();
        }
    }

    private static final Set<TurnSignal> OFFER_SIGNALS = Set.of(TurnSignal.EMOTIONAL_DISTRESS, TurnSignal.CONFUSION,
            TurnSignal.BEREAVEMENT, TurnSignal.FINANCIAL_VULNERABILITY, TurnSignal.SENSITIVE_TOPIC);

    private HandoffPolicy() {
    }

    public static Decision decide(Inputs in) {
        Optional<HandoffReason> reason = reason(in);
        if (reason.isPresent()) {
            return new Decision(reason, false);
        }
        boolean offer = !in.offerAlreadyMade()
                && (in.unproductive() == Unproductive.OFFER
                || in.signals().stream().anyMatch(OFFER_SIGNALS::contains));
        return new Decision(Optional.empty(), offer);
    }

    private static Optional<HandoffReason> reason(Inputs in) {
        ClinicalPriority level = in.turnLevel() == null ? ClinicalPriority.INITIAL : in.turnLevel();
        if (in.possibleEmergency() || level == ClinicalPriority.P1_POSSIBLE_EMERGENCY) {
            return Optional.of(HandoffReason.POSSIBLE_EMERGENCY);
        }
        if (level == ClinicalPriority.P2_URGENT || level == ClinicalPriority.P3_CLINICAL_FOLLOW_UP) {
            return Optional.of(HandoffReason.CLINICAL_PRIORITY);
        }
        if (in.explicitRequest() || in.intents().contains(Intent.HUMAN_REQUEST)) {
            return Optional.of(HandoffReason.EXPLICIT_REQUEST);
        }
        if (in.intents().contains(Intent.COMPLAINT)) {
            return Optional.of(HandoffReason.COMPLAINT);
        }
        if (in.intents().contains(Intent.MEDICAL_CONCERN) || in.intents().contains(Intent.TREATMENT_FOLLOW_UP)) {
            return Optional.of(HandoffReason.CLINICAL);
        }
        if (in.signals().contains(TurnSignal.DISSATISFACTION)) {
            return Optional.of(HandoffReason.DISSATISFACTION);
        }
        if (in.signals().contains(TurnSignal.NEEDS_STAFF_DECISION)) {
            return Optional.of(HandoffReason.STAFF_DECISION);
        }
        if (in.modelRequestedHandoff()) {
            return Optional.of(HandoffReason.MODEL_SIGNAL);
        }
        if (in.unproductive() == Unproductive.HANDOFF) {
            return Optional.of(HandoffReason.UNPRODUCTIVE_CONVERSATION);
        }
        if (in.failureNeedsHandoff()) {
            return Optional.of(HandoffReason.TURN_FAILURE);
        }
        return Optional.empty();
    }
}
