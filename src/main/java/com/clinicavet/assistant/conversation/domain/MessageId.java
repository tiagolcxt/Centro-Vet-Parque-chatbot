package com.clinicavet.assistant.conversation.domain;

import java.util.Objects;
import java.util.UUID;

public record MessageId(UUID value) {

    public MessageId {
        Objects.requireNonNull(value, "value");
    }

    public static MessageId newId() {
        return new MessageId(UUID.randomUUID());
    }
}
