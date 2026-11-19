package com.clinicavet.assistant.platform;

import java.util.Objects;
import java.util.UUID;

/** Identificador da clínica (tenant, ADR-0021). */
public record ClinicId(UUID value) {

    public ClinicId {
        Objects.requireNonNull(value, "value");
    }

    public static ClinicId of(String value) {
        return new ClinicId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
