package com.clinicavet.assistant.tutor.domain;

import com.clinicavet.assistant.shared.domain.ContactIdentity;
import com.clinicavet.assistant.shared.domain.Texts;
import com.clinicavet.assistant.shared.domain.Timestamps;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Responsável pelo animal. Guarda apenas dados persistentes do tutor (ADR-0010); motivo de
 * consulta, sintomas e preferências pertencem à solicitação ou à conversa.
 * <p>
 * Proveniência das informações (MENCIONADA, CONFIRMADA...) entra na Etapa 5 (PT-09).
 */
public final class Tutor {

    public static final int MAX_NAME_LENGTH = 120;
    public static final int MAX_PHONE_LENGTH = 30;

    private final TutorId id;
    private final ContactIdentity contact;
    private String name;
    private String phone;
    private final Instant firstSeenAt;
    private Instant lastSeenAt;
    private final Long version;

    private Tutor(TutorId id, ContactIdentity contact, String name, String phone,
                  Instant firstSeenAt, Instant lastSeenAt, Long version) {
        this.id = Objects.requireNonNull(id, "id");
        this.contact = Objects.requireNonNull(contact, "contact");
        this.name = Texts.optional("name", name, MAX_NAME_LENGTH);
        this.phone = Texts.optional("phone", phone, MAX_PHONE_LENGTH);
        this.firstSeenAt = Timestamps.normalize(firstSeenAt);
        this.lastSeenAt = Timestamps.normalize(lastSeenAt);
        this.version = version;
    }

    public static Tutor register(ContactIdentity contact, Instant now) {
        return new Tutor(TutorId.newId(), contact, null, null, now, now, null);
    }

    /** Reconstitui a partir da persistência. */
    public static Tutor restore(TutorId id, ContactIdentity contact, String name, String phone,
                                Instant firstSeenAt, Instant lastSeenAt, Long version) {
        return new Tutor(id, contact, name, phone, firstSeenAt, lastSeenAt, version);
    }

    public void updateName(String name) {
        this.name = Texts.optional("name", name, MAX_NAME_LENGTH);
    }

    public void updatePhone(String phone) {
        this.phone = Texts.optional("phone", phone, MAX_PHONE_LENGTH);
    }

    public void markSeen(Instant at) {
        Instant normalized = Timestamps.normalize(at);
        if (normalized.isAfter(lastSeenAt)) {
            this.lastSeenAt = normalized;
        }
    }

    public TutorId id() {
        return id;
    }

    public ContactIdentity contact() {
        return contact;
    }

    public Optional<String> name() {
        return Optional.ofNullable(name);
    }

    public Optional<String> phone() {
        return Optional.ofNullable(phone);
    }

    public Instant firstSeenAt() {
        return firstSeenAt;
    }

    public Instant lastSeenAt() {
        return lastSeenAt;
    }

    /** Versão de concorrência (lock otimista); {@code null} para tutor ainda não persistido. */
    public Long version() {
        return version;
    }
}
