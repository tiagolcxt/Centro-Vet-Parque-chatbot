package com.clinicavet.assistant.conversation.domain;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Metadados de uma mensagem do assistente (Etapa 4): se foi a resposta segura (DA-31), a prioridade
 * efetiva do turno e a origem (ADR-0018, item 8), os sinais e as intenções secundárias (DA-33).
 * Mensagens do tutor não têm metadados.
 */
public record AssistantMetadata(boolean fallback, ClinicalPriority priority, PrioritySource prioritySource,
                                Set<TurnSignal> signals, List<Intent> secondaryIntents) {

    public static final AssistantMetadata NONE = new AssistantMetadata(false, null, null, Set.of(), List.of());

    public AssistantMetadata {
        if ((priority == null) != (prioritySource == null)) {
            throw new IllegalArgumentException("prioridade e origem andam juntas");
        }
        signals = signals == null || signals.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(signals));
        secondaryIntents = secondaryIntents == null ? List.of() : List.copyOf(secondaryIntents);
        Objects.requireNonNull(signals);
    }

    public Optional<ClinicalPriority> priorityValue() {
        return Optional.ofNullable(priority);
    }
}
