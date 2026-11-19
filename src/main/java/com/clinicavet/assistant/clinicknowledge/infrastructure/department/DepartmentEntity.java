package com.clinicavet.assistant.clinicknowledge.infrastructure.department;

import com.clinicavet.assistant.clinicknowledge.domain.Departments;
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
@Table(name = "department")
class DepartmentEntity {

    @Id
    @Column(name = "id", columnDefinition = "BINARY(16)")
    private UUID id;

    @TenantId
    @Column(name = "clinic_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID clinicId;

    @Column(name = "name", length = 60, nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", columnDefinition = "VARCHAR(12)", nullable = false)
    private Departments.Kind kind;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    @Column(name = "created_at", columnDefinition = "DATETIME(6)", nullable = false)
    private Instant createdAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected DepartmentEntity() {
    }

    DepartmentEntity(String name, Departments.Kind kind, boolean isDefault, Instant now) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.kind = kind;
        this.active = true;
        this.isDefault = isDefault;
        this.createdAt = now;
    }

    Departments.Department view() {
        return new Departments.Department(id, name, kind, active, isDefault);
    }

    void rename(String newName) {
        this.name = newName;
    }

    void setActive(boolean value) {
        this.active = value;
    }

    UUID id() {
        return id;
    }

    Departments.Kind kind() {
        return kind;
    }

    boolean isDefault() {
        return isDefault;
    }

    boolean active() {
        return active;
    }
}
