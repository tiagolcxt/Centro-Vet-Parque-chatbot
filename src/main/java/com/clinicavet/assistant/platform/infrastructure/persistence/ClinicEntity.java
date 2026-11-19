package com.clinicavet.assistant.platform.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/** A própria clínica (tabela global, sem {@code @TenantId}). Só leitura na S1. */
@Entity
@Table(name = "clinic")
public class ClinicEntity {

    @Id
    @Column(name = "id", columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "name", length = 120, nullable = false)
    private String name;

    @Column(name = "status", length = 12, nullable = false)
    private String status;

    protected ClinicEntity() {
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String status() {
        return status;
    }
}
