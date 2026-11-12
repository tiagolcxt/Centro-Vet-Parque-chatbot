package com.clinicavet.assistant.support;

import com.clinicavet.assistant.shared.domain.Channel;
import com.clinicavet.assistant.shared.domain.ContactIdentity;

import java.time.Instant;
import java.util.UUID;

/** Dados de teste <b>fictícios</b>. Cada teste usa um contato próprio para não depender de limpeza. */
public final class Fixtures {

    /** Instante com nanossegundos, para provar a normalização para microssegundos. */
    public static final Instant T0 = Instant.parse("2026-11-11T13:00:00.123456789Z");

    private Fixtures() {
    }

    public static ContactIdentity newContact() {
        return new ContactIdentity(Channel.API, "ficticio-" + UUID.randomUUID());
    }
}
