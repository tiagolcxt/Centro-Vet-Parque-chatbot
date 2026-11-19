package com.clinicavet.assistant.team.infrastructure;

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

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Funcionário (team): cargo, registro, setores e disponibilidade. Mesma chave da conta (1 para 1). */
@Entity
@Table(name = "employee")
public class EmployeeEntity {

    public enum Availability {
        AVAILABLE, AWAY
    }

    @Id
    @Column(name = "user_account_id", columnDefinition = "BINARY(16)")
    private UUID id;

    @TenantId
    @Column(name = "clinic_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID clinicId;

    @Column(name = "display_name", length = 120, nullable = false)
    private String displayName;

    @Column(name = "job_title", length = 80)
    private String jobTitle;

    @Column(name = "professional_registration", length = 40)
    private String professionalRegistration;

    @Enumerated(EnumType.STRING)
    @Column(name = "availability", columnDefinition = "VARCHAR(10)", nullable = false)
    private Availability availability;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "employee_department", joinColumns = @JoinColumn(name = "employee_id"))
    @Column(name = "department_id", columnDefinition = "BINARY(16)", nullable = false)
    private Set<UUID> departmentIds = new HashSet<>();

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected EmployeeEntity() {
    }

    public EmployeeEntity(UUID userAccountId, String displayName, String jobTitle, String professionalRegistration,
                          Set<UUID> departmentIds) {
        this.id = userAccountId;
        this.availability = Availability.AVAILABLE;
        change(displayName, jobTitle, professionalRegistration, departmentIds);
    }

    public void change(String displayName, String jobTitle, String professionalRegistration, Set<UUID> departments) {
        this.displayName = displayName;
        this.jobTitle = jobTitle;
        this.professionalRegistration = professionalRegistration;
        this.departmentIds = new HashSet<>(departments);
    }

    public void setAvailability(Availability value) {
        this.availability = value;
    }

    public UUID id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public String jobTitle() {
        return jobTitle;
    }

    public String professionalRegistration() {
        return professionalRegistration;
    }

    public Availability availability() {
        return availability;
    }

    public Set<UUID> departmentIds() {
        return Set.copyOf(departmentIds);
    }
}
