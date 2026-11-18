package com.clinicavet.assistant.conversation.domain;

import java.util.Optional;
import java.util.Set;

/**
 * Decide o que o turno faz pela equipe (ADR-0007, item 2; DA-25, DA-31, DA-33; <b>DA-40, atendimento
 * resolutivo</b>). Determinística. Considera a intenção principal e as secundárias. Três resultados, nesta ordem:
 * <ol>
 *   <li><b>Transferir</b> ({@link Decision#handoff()}): a conversa vai para a fila da equipe ({@code WAITING_HUMAN}).
 *       Possível emergência, prioridade P1/P2, preocupação médica, pedido explícito de pessoa, reclamação e falha
 *       que envolve encaminhamento.</li>
 *   <li><b>Registrar</b> ({@link Decision#register()}): pedido para a equipe, e a conversa <b>continua com o
 *       assistente</b>. Acompanhamento sem urgência (P3), insatisfação, decisão da equipe, sugestão do modelo,
 *       assunto sensível e conversa improdutiva (3ª ocorrência).</li>
 *   <li><b>Oferecer</b> ({@link Decision#offerHuman()}): sofrimento, confusão, luto, vulnerabilidade financeira e
 *       conversa improdutiva (2ª ocorrência), uma vez por conversa.</li>
 * </ol>
 * Em todos os casos o tutor recebe o próximo passo (ligar para a recepção), composto pelo backend.
 * <p>
 * <b>Provisório até P-12 e P-20</b> (o que transfere, se P3 transfere).
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

    /**
     * @param handoff  motivo da transferência (a conversa vai para {@code WAITING_HUMAN})
     * @param register motivo do pedido registrado para a equipe, sem transferir (vazio se houver transferência)
     */
    public record Decision(Optional<HandoffReason> handoff, Optional<HandoffReason> register, boolean offerHuman) {

        public static final Decision NONE = new Decision(Optional.empty(), Optional.empty(), false);

        public Decision {
            if (handoff.isPresent() && register.isPresent()) {
                throw new IllegalArgumentException("transferir já registra o pedido");
            }
        }

        public boolean handsOff() {
            return handoff.isPresent();
        }

        public boolean registers() {
            return register.isPresent();
        }

        /** A equipe recebe algo deste turno (transferência ou pedido). */
        public boolean reachesStaff() {
            return handsOff() || registers();
        }
    }

    private static final Set<TurnSignal> OFFER_SIGNALS = Set.of(TurnSignal.EMOTIONAL_DISTRESS, TurnSignal.CONFUSION,
            TurnSignal.BEREAVEMENT, TurnSignal.FINANCIAL_VULNERABILITY);

    private HandoffPolicy() {
    }

    public static Decision decide(Inputs in) {
        Optional<HandoffReason> transfer = transfer(in);
        if (transfer.isPresent()) {
            return new Decision(transfer, Optional.empty(), false);
        }
        Optional<HandoffReason> register = register(in);
        boolean offer = register.isEmpty() && !in.offerAlreadyMade()
                && (in.unproductive() == Unproductive.OFFER
                || in.signals().stream().anyMatch(OFFER_SIGNALS::contains));
        return new Decision(Optional.empty(), register, offer);
    }

    private static Optional<HandoffReason> transfer(Inputs in) {
        ClinicalPriority level = in.turnLevel() == null ? ClinicalPriority.INITIAL : in.turnLevel();
        if (in.possibleEmergency() || level == ClinicalPriority.P1_POSSIBLE_EMERGENCY) {
            return Optional.of(HandoffReason.POSSIBLE_EMERGENCY);
        }
        if (level == ClinicalPriority.P2_URGENT) {
            return Optional.of(HandoffReason.CLINICAL_PRIORITY);
        }
        if (in.explicitRequest() || in.intents().contains(Intent.HUMAN_REQUEST)) {
            return Optional.of(HandoffReason.EXPLICIT_REQUEST);
        }
        if (in.intents().contains(Intent.COMPLAINT)) {
            return Optional.of(HandoffReason.COMPLAINT);
        }
        if (in.intents().contains(Intent.MEDICAL_CONCERN)) {
            return Optional.of(HandoffReason.CLINICAL);
        }
        if (in.failureNeedsHandoff()) {
            return Optional.of(HandoffReason.TURN_FAILURE);
        }
        return Optional.empty();
    }

    private static Optional<HandoffReason> register(Inputs in) {
        ClinicalPriority level = in.turnLevel() == null ? ClinicalPriority.INITIAL : in.turnLevel();
        if (level == ClinicalPriority.P3_CLINICAL_FOLLOW_UP || in.intents().contains(Intent.TREATMENT_FOLLOW_UP)) {
            return Optional.of(HandoffReason.CLINICAL);
        }
        if (in.signals().contains(TurnSignal.DISSATISFACTION)) {
            return Optional.of(HandoffReason.DISSATISFACTION);
        }
        if (in.signals().contains(TurnSignal.NEEDS_STAFF_DECISION)) {
            return Optional.of(HandoffReason.STAFF_DECISION);
        }
        if (in.signals().contains(TurnSignal.SENSITIVE_TOPIC)) {
            return Optional.of(HandoffReason.SENSITIVE_TOPIC);
        }
        if (in.modelRequestedHandoff()) {
            return Optional.of(HandoffReason.MODEL_SIGNAL);
        }
        if (in.unproductive() == Unproductive.HANDOFF) {
            return Optional.of(HandoffReason.UNPRODUCTIVE_CONVERSATION);
        }
        return Optional.empty();
    }
}
