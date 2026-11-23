package com.clinicavet.assistant.channel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.TenantId;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Conta do WhatsApp da clínica (uma por clínica). O token fica cifrado ({@link TokenCipher}). */
@Entity
@Table(name = "channel_account")
class ChannelAccountEntity {

    static final String PROVIDER = "WHATSAPP_CLOUD";

    @Id
    @Column(name = "id", columnDefinition = "BINARY(16)")
    private UUID id;

    @TenantId
    @Column(name = "clinic_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID clinicId;

    @Column(name = "provider", length = 20, nullable = false, updatable = false)
    private String provider;

    @Column(name = "phone_number_id", length = 40, nullable = false)
    private String phoneNumberId;

    @Column(name = "display_phone", length = 30)
    private String displayPhone;

    @Column(name = "waba_id", length = 40)
    private String wabaId;

    @Column(name = "access_token_encrypted", length = 2000, nullable = false)
    private String accessTokenEncrypted;

    @Column(name = "status", length = 10, nullable = false)
    private String status;

    @Column(name = "created_at", columnDefinition = "DATETIME(6)", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", columnDefinition = "DATETIME(6)", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected ChannelAccountEntity() {
    }

    ChannelAccountEntity(String phoneNumberId, String encryptedToken, Instant now) {
        this.id = UUID.randomUUID();
        this.provider = PROVIDER;
        this.phoneNumberId = phoneNumberId;
        this.accessTokenEncrypted = encryptedToken;
        this.status = "ACTIVE";
        this.createdAt = now;
        this.updatedAt = now;
    }

    void change(String phoneNumberId, String displayPhone, String wabaId, boolean active, Instant now) {
        this.phoneNumberId = phoneNumberId;
        this.displayPhone = displayPhone;
        this.wabaId = wabaId;
        this.status = active ? "ACTIVE" : "DISABLED";
        this.updatedAt = now;
    }

    void replaceToken(String encryptedToken, Instant now) {
        this.accessTokenEncrypted = encryptedToken;
        this.updatedAt = now;
    }

    UUID id() {
        return id;
    }

    String phoneNumberId() {
        return phoneNumberId;
    }

    Optional<String> displayPhone() {
        return Optional.ofNullable(displayPhone);
    }

    Optional<String> wabaId() {
        return Optional.ofNullable(wabaId);
    }

    String accessTokenEncrypted() {
        return accessTokenEncrypted;
    }

    boolean active() {
        return "ACTIVE".equals(status);
    }

    Instant updatedAt() {
        return updatedAt;
    }
}
