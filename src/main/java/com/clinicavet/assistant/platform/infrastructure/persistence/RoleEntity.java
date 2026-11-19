package com.clinicavet.assistant.platform.infrastructure.persistence;

import com.clinicavet.assistant.platform.Permission;
import com.clinicavet.assistant.platform.RoleTemplate;
import com.clinicavet.assistant.platform.SectorScope;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.TenantId;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Entity
@Table(name = "role")
public class RoleEntity {

    @Id
    @Column(name = "id", columnDefinition = "BINARY(16)")
    private UUID id;

    @TenantId
    @Column(name = "clinic_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID clinicId;

    @Column(name = "name", length = 60, nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "sector_scope", columnDefinition = "VARCHAR(4)", nullable = false)
    private SectorScope scope;

    @Enumerated(EnumType.STRING)
    @Column(name = "template", columnDefinition = "VARCHAR(20)")
    private RoleTemplate template;

    /** Códigos do catálogo ({@code modulo:acao}); um código que saia do catálogo é ignorado na leitura. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "role_permission", joinColumns = @JoinColumn(name = "role_id"))
    @Column(name = "permission", length = 40, nullable = false)
    private Set<String> permissions = new HashSet<>();

    @Column(name = "created_at", columnDefinition = "DATETIME(6)", nullable = false)
    private Instant createdAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected RoleEntity() {
    }

    public RoleEntity(String name, SectorScope scope, RoleTemplate template, Set<Permission> permissions, Instant now) {
        this.id = UUID.randomUUID();
        this.template = template;
        this.createdAt = now;
        change(name, scope, permissions);
    }

    public void change(String name, SectorScope scope, Set<Permission> permissions) {
        this.name = name;
        this.scope = scope;
        this.permissions = permissions.stream().map(Permission::code).collect(Collectors.toCollection(HashSet::new));
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public SectorScope scope() {
        return scope;
    }

    public RoleTemplate template() {
        return template;
    }

    public Set<Permission> permissions() {
        return permissions.stream().map(Permission::fromCode).flatMap(java.util.Optional::stream)
                .collect(Collectors.toUnmodifiableSet());
    }
}
