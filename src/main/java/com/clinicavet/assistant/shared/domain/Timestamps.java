package com.clinicavet.assistant.shared.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Normaliza instantes para a precisão armazenada no banco ({@code DATETIME(6)}, microssegundos),
 * para que um agregado salvo e relido seja idêntico ao original.
 */
public final class Timestamps {

    private Timestamps() {
    }

    public static Instant normalize(Instant instant) {
        return Objects.requireNonNull(instant, "instant").truncatedTo(ChronoUnit.MICROS);
    }

    public static Instant normalizeNullable(Instant instant) {
        return instant == null ? null : normalize(instant);
    }
}
