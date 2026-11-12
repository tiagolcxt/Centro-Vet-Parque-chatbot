package com.clinicavet.assistant.servicerequest.domain;

import java.util.Objects;
import java.util.UUID;

public record ServiceRequestId(UUID value) {

    public ServiceRequestId {
        Objects.requireNonNull(value, "value");
    }

    public static ServiceRequestId newId() {
        return new ServiceRequestId(UUID.randomUUID());
    }
}
