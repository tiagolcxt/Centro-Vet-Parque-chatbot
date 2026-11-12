package com.clinicavet.assistant.tutor.domain;

import java.util.Objects;
import java.util.UUID;

public record TutorId(UUID value) {

    public TutorId {
        Objects.requireNonNull(value, "value");
    }

    public static TutorId newId() {
        return new TutorId(UUID.randomUUID());
    }
}
