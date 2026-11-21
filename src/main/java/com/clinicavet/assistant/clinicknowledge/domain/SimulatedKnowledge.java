package com.clinicavet.assistant.clinicknowledge.domain;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Base usada no simulador de configuração (ADR-0026 item 7): durante {@link #call}, a base injetada no assistente
 * passa a ler o rascunho em vez da versão publicada. Vale só na thread que chama.
 */
public final class SimulatedKnowledge {

    private static final ThreadLocal<ClinicKnowledge> CURRENT = new ThreadLocal<>();

    private SimulatedKnowledge() {
    }

    public static <T> T call(ClinicKnowledge knowledge, Supplier<T> work) {
        Objects.requireNonNull(knowledge, "knowledge");
        ClinicKnowledge previous = CURRENT.get();
        CURRENT.set(knowledge);
        try {
            return work.get();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    public static Optional<ClinicKnowledge> current() {
        return Optional.ofNullable(CURRENT.get());
    }
}
