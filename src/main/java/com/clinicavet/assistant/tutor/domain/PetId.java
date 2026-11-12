package com.clinicavet.assistant.tutor.domain;

import java.util.Objects;
import java.util.UUID;

public record PetId(UUID value) {

    public PetId {
        Objects.requireNonNull(value, "value");
    }

    public static PetId newId() {
        return new PetId(UUID.randomUUID());
    }
}
