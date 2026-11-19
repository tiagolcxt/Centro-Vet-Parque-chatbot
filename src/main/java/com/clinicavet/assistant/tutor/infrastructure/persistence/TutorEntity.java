package com.clinicavet.assistant.tutor.infrastructure.persistence;

import com.clinicavet.assistant.shared.domain.Channel;
import com.clinicavet.assistant.shared.domain.ContactIdentity;
import com.clinicavet.assistant.tutor.domain.Tutor;
import com.clinicavet.assistant.tutor.domain.TutorId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.TenantId;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tutor")
class TutorEntity {

    @Id
    @Column(name = "id", columnDefinition = "BINARY(16)")
    private UUID id;

    // Clínica dona da linha (ADR-0021): preenchida e filtrada pelo Hibernate a partir do CurrentClinic.
    @TenantId
    @Column(name = "clinic_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID clinicId;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", columnDefinition = "VARCHAR(20)", nullable = false)
    private Channel channel;

    @Column(name = "external_contact_id", length = 128, nullable = false)
    private String externalContactId;

    @Column(name = "name", length = 120)
    private String name;

    @Column(name = "phone", length = 30)
    private String phone;

    @Column(name = "first_seen_at", columnDefinition = "DATETIME(6)", nullable = false)
    private Instant firstSeenAt;

    @Column(name = "last_seen_at", columnDefinition = "DATETIME(6)", nullable = false)
    private Instant lastSeenAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected TutorEntity() {
    }

    static TutorEntity from(Tutor tutor) {
        TutorEntity entity = new TutorEntity();
        entity.id = tutor.id().value();
        entity.channel = tutor.contact().channel();
        entity.externalContactId = tutor.contact().externalContactId();
        entity.name = tutor.name().orElse(null);
        entity.phone = tutor.phone().orElse(null);
        entity.firstSeenAt = tutor.firstSeenAt();
        entity.lastSeenAt = tutor.lastSeenAt();
        entity.version = tutor.version();
        return entity;
    }

    Tutor toDomain() {
        return Tutor.restore(new TutorId(id), new ContactIdentity(channel, externalContactId), name, phone,
                firstSeenAt, lastSeenAt, version);
    }
}
