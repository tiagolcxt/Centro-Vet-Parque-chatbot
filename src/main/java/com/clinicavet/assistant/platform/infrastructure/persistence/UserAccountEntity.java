package com.clinicavet.assistant.platform.infrastructure.persistence;

import com.clinicavet.assistant.platform.Accounts;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

/**
 * Conta de usuário. <b>Entidade global</b>, sem {@code @TenantId}: o login acha a conta pelo e-mail antes de existir
 * clínica na requisição (DA-49). Por isso toda consulta fora do login filtra {@code clinic_id} explicitamente
 * ({@code UserAccountJpaRepository}), e o {@code TenancyRulesTest} lista esta entidade como exceção.
 */
@Entity
@Table(name = "user_account")
public class UserAccountEntity {

    @Id
    @Column(name = "id", columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "clinic_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID clinicId;

    @Column(name = "email", length = 254, nullable = false)
    private String email;

    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Column(name = "role_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID roleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", columnDefinition = "VARCHAR(10)", nullable = false)
    private Accounts.Status status;

    @Column(name = "failed_logins", nullable = false)
    private int failedLogins;

    @Column(name = "locked_until", columnDefinition = "DATETIME(6)")
    private Instant lockedUntil;

    @Column(name = "invite_token_hash", columnDefinition = "CHAR(64)")
    private String inviteTokenHash;

    @Column(name = "invite_expires_at", columnDefinition = "DATETIME(6)")
    private Instant inviteExpiresAt;

    @Column(name = "created_at", columnDefinition = "DATETIME(6)", nullable = false)
    private Instant createdAt;

    @Column(name = "disabled_at", columnDefinition = "DATETIME(6)")
    private Instant disabledAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected UserAccountEntity() {
    }

    public UserAccountEntity(UUID clinicId, String email, UUID roleId, Instant now) {
        this.id = UUID.randomUUID();
        this.clinicId = clinicId;
        this.email = email;
        this.roleId = roleId;
        this.status = Accounts.Status.INVITED;
        this.createdAt = now;
    }

    public Accounts.View view() {
        return new Accounts.View(id, email, roleId, status);
    }

    public void setInvite(String tokenHash, Instant expiresAt) {
        this.inviteTokenHash = tokenHash;
        this.inviteExpiresAt = expiresAt;
    }

    /** Aceite do convite: define a senha, ativa a conta (se ainda convidada) e queima o convite. */
    public void acceptInvite(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
        if (status == Accounts.Status.INVITED) {
            status = Accounts.Status.ACTIVE;
        }
        this.inviteTokenHash = null;
        this.inviteExpiresAt = null;
        this.failedLogins = 0;
        this.lockedUntil = null;
    }

    public void changePassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
    }

    public void loginSucceeded() {
        failedLogins = 0;
        lockedUntil = null;
    }

    public void loginFailed(int maxAttempts, Instant lockUntil) {
        failedLogins++;
        if (failedLogins >= maxAttempts) {
            lockedUntil = lockUntil;
            failedLogins = 0;
        }
    }

    public void disable(Instant now) {
        status = Accounts.Status.DISABLED;
        disabledAt = now;
        inviteTokenHash = null;
        inviteExpiresAt = null;
    }

    public void changeRole(UUID newRoleId) {
        this.roleId = newRoleId;
    }

    public UUID id() {
        return id;
    }

    public UUID clinicId() {
        return clinicId;
    }

    public String email() {
        return email;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public UUID roleId() {
        return roleId;
    }

    public Accounts.Status status() {
        return status;
    }

    public Instant lockedUntil() {
        return lockedUntil;
    }

    public Instant inviteExpiresAt() {
        return inviteExpiresAt;
    }
}
