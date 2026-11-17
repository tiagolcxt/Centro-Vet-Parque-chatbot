package com.clinicavet.assistant.conversation.application;

import com.clinicavet.assistant.conversation.domain.ClinicalPriority;
import com.clinicavet.assistant.conversation.domain.Intent;
import com.clinicavet.assistant.conversation.domain.TurnSignal;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Saída estruturada de um turno no modo principal (ADR-0002).
 * <ul>
 *   <li>v1 (Etapa 2): {@code reply}, {@code intent};</li>
 *   <li>v2 (Etapa 3): {@code knowledgeRefs}, {@code knowledgeGap} (ADR-0008);</li>
 *   <li>v3 (Etapa 4): {@code secondaryIntents} (DA-33), {@code handoffRequested}, {@code possibleEmergency}
 *       (ADR-0007), {@code priority}, {@code signals}, {@code clinicalUncertainty} (ADR-0018);</li>
 *   <li>v4 (Etapa 5): {@code customer} = {@code extracted}, {@code confirmations[]} e {@code appointmentRequest}
 *       (ADR-0010, {@link CustomerUpdate}).</li>
 * </ul>
 * Tudo além do texto é <b>sugestão</b>: o backend decide encaminhamento e prioridade, e só pode acrescentar
 * (nunca remover) encaminhamentos e só elevar a prioridade.
 *
 * @param priority sugestão do modelo; {@code null} se ausente ou fora da lista
 */
public record AssistantTurn(String reply, Intent intent, List<Intent> secondaryIntents, List<String> knowledgeRefs,
                            boolean knowledgeGap, boolean handoffRequested, boolean possibleEmergency,
                            ClinicalPriority priority, Set<TurnSignal> signals, boolean clinicalUncertainty,
                            CustomerUpdate customer) {

    public static final int MAX_SECONDARY_INTENTS = 2;

    public AssistantTurn {
        Objects.requireNonNull(reply, "reply");
        Objects.requireNonNull(intent, "intent");
        secondaryIntents = secondaryIntents == null ? List.of() : secondaryIntents.stream()
                .filter(i -> i != intent).distinct().limit(MAX_SECONDARY_INTENTS).toList();
        knowledgeRefs = List.copyOf(knowledgeRefs);
        signals = signals == null ? Set.of() : Set.copyOf(signals);
        customer = customer == null ? CustomerUpdate.NONE : customer;
    }

    /** Turno v3 (sem dados do tutor). */
    public AssistantTurn(String reply, Intent intent, List<Intent> secondaryIntents, List<String> knowledgeRefs,
                         boolean knowledgeGap, boolean handoffRequested, boolean possibleEmergency,
                         ClinicalPriority priority, Set<TurnSignal> signals, boolean clinicalUncertainty) {
        this(reply, intent, secondaryIntents, knowledgeRefs, knowledgeGap, handoffRequested, possibleEmergency, priority,
                signals, clinicalUncertainty, CustomerUpdate.NONE);
    }

    /** Turno v2 (sem campos da Etapa 4). */
    public AssistantTurn(String reply, Intent intent, List<String> knowledgeRefs, boolean knowledgeGap) {
        this(reply, intent, List.of(), knowledgeRefs, knowledgeGap, false, false, null, Set.of(), false);
    }

    /** Turno sem fontes nem lacuna (ex.: saudação). */
    public static AssistantTurn simple(String reply, Intent intent) {
        return new AssistantTurn(reply, intent, List.of(), false);
    }

    /** A principal e as secundárias. */
    public Set<Intent> allIntents() {
        java.util.EnumSet<Intent> all = java.util.EnumSet.of(intent);
        all.addAll(secondaryIntents);
        return all;
    }
}
