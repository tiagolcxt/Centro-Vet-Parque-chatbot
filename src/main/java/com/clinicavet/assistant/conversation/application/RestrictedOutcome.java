package com.clinicavet.assistant.conversation.application;

import java.util.Objects;

/** Resultado do modo restrito (ADR-0007, item 4). Na falha vale o fail-safe; não há nova tentativa (DA-28). */
public sealed interface RestrictedOutcome {

    record Valid(RestrictedSignals signals) implements RestrictedOutcome {

        public Valid {
            Objects.requireNonNull(signals, "signals");
        }
    }

    record Failed(FailureKind kind) implements RestrictedOutcome {

        public Failed {
            Objects.requireNonNull(kind, "kind");
        }
    }
}
