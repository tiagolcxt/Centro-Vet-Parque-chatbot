package com.clinicavet.assistant.conversation.application;

import java.util.Objects;

/** Resultado da chamada ao modelo depois da classificação. */
public sealed interface TurnOutcome {

    /** Chamadas feitas ao modelo neste turno (1 ou 2). */
    int attempts();

    record Valid(AssistantTurn turn, int attempts) implements TurnOutcome {

        public Valid {
            Objects.requireNonNull(turn, "turn");
        }
    }

    record Failed(FailureKind kind, int attempts) implements TurnOutcome {

        public Failed {
            Objects.requireNonNull(kind, "kind");
        }
    }
}
