package com.clinicavet.assistant.clinicknowledge.infrastructure;

import com.clinicavet.assistant.clinicknowledge.domain.ClinicConfig;
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

/** Uma versão da configuração da clínica (ADR-0026). A coluna gerada {@code live_slot} não é mapeada. */
@Entity
@Table(name = "config_version")
class ConfigVersionEntity {

    @Id
    @Column(name = "id", columnDefinition = "BINARY(16)")
    private UUID id;

    @TenantId
    @Column(name = "clinic_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID clinicId;

    @Column(name = "number", nullable = false, updatable = false)
    private int number;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", columnDefinition = "VARCHAR(10)", nullable = false)
    private ClinicConfig.Status status;

    @Column(name = "document", columnDefinition = "LONGTEXT", nullable = false)
    private String document;

    @Column(name = "note", length = 200)
    private String note;

    @Column(name = "created_at", columnDefinition = "DATETIME(6)", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", columnDefinition = "BINARY(16)", updatable = false)
    private UUID createdBy;

    @Column(name = "updated_at", columnDefinition = "DATETIME(6)", nullable = false)
    private Instant updatedAt;

    @Column(name = "published_at", columnDefinition = "DATETIME(6)")
    private Instant publishedAt;

    @Column(name = "published_by", columnDefinition = "BINARY(16)")
    private UUID publishedBy;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected ConfigVersionEntity() {
    }

    ConfigVersionEntity(int number, String document, UUID author, Instant now) {
        this.id = UUID.randomUUID();
        this.number = number;
        this.status = ClinicConfig.Status.DRAFT;
        this.document = document;
        this.createdAt = now;
        this.createdBy = author;
        this.updatedAt = now;
    }

    void replaceDocument(String document, Instant now) {
        this.document = document;
        this.updatedAt = now;
    }

    void publish(String note, UUID author, Instant now) {
        this.status = ClinicConfig.Status.PUBLISHED;
        this.note = note;
        this.publishedAt = now;
        this.publishedBy = author;
        this.updatedAt = now;
    }

    void archive(Instant now) {
        this.status = ClinicConfig.Status.ARCHIVED;
        this.updatedAt = now;
    }

    UUID id() {
        return id;
    }

    UUID clinicId() {
        return clinicId;
    }

    int number() {
        return number;
    }

    ClinicConfig.Status status() {
        return status;
    }

    String document() {
        return document;
    }

    long revision() {
        return version == null ? 0 : version;
    }

    ClinicConfig.VersionSummary summary() {
        return new ClinicConfig.VersionSummary(id, number, status, note, createdAt, createdBy, updatedAt, publishedAt,
                publishedBy, revision());
    }
}
