package com.clinicavet.assistant.channel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.TenantId;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Resposta a enviar pelo WhatsApp (outbox, RF-S52). Gravada na mesma transação da mensagem; o despachante envia,
 * tenta de novo com espera crescente e, depois do limite, marca como não enviada para a equipe ver.
 */
@Entity
@Table(name = "outbound_message")
class OutboundMessageEntity {

    enum Status {
        PENDING, SENT, DELIVERED, READ, FAILED;

        /** A Meta pode mandar os avisos fora de ordem: a situação só avança. */
        int rank() {
            return switch (this) {
                case PENDING -> 0;
                case SENT -> 1;
                case DELIVERED -> 2;
                case READ -> 3;
                case FAILED -> 4;
            };
        }
    }

    @Id
    @Column(name = "id", columnDefinition = "BINARY(16)")
    private UUID id;

    @TenantId
    @Column(name = "clinic_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID clinicId;

    @Column(name = "conversation_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID conversationId;

    @Column(name = "message_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID messageId;

    @Column(name = "recipient", length = 128, nullable = false, updatable = false)
    private String recipient;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 10, nullable = false)
    private Status status;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", columnDefinition = "DATETIME(6)", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "wamid", length = 255)
    private String wamid;

    @Column(name = "last_error_code", length = 40)
    private String lastErrorCode;

    @Column(name = "created_at", columnDefinition = "DATETIME(6)", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", columnDefinition = "DATETIME(6)", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected OutboundMessageEntity() {
    }

    OutboundMessageEntity(UUID conversationId, UUID messageId, String recipient, Instant now) {
        this.id = UUID.randomUUID();
        this.conversationId = conversationId;
        this.messageId = messageId;
        this.recipient = recipient;
        this.status = Status.PENDING;
        this.attempts = 0;
        this.nextAttemptAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** Reserva o envio: outro despachante não pega a mesma linha enquanto esta tentativa estiver em curso. */
    void claim(Instant now, Duration lease) {
        this.nextAttemptAt = now.plus(lease);
        this.updatedAt = now;
    }

    void sent(String wamid, Instant now) {
        this.attempts++;
        this.wamid = wamid;
        this.status = Status.SENT;
        this.lastErrorCode = null;
        this.updatedAt = now;
    }

    /** Falha no envio: tenta de novo depois de {@code retryIn}, ou desiste ({@code retryIn} vazio). */
    void failed(String errorCode, java.util.Optional<Duration> retryIn, Instant now) {
        this.attempts++;
        this.lastErrorCode = errorCode;
        this.updatedAt = now;
        if (retryIn.isPresent()) {
            this.nextAttemptAt = now.plus(retryIn.get());
        } else {
            this.status = Status.FAILED;
        }
    }

    /** Aviso de situação vindo do webhook. Devolve se mudou algo. */
    boolean report(Status reported, String errorCode, Instant now) {
        if (reported.rank() <= status.rank() || status == Status.FAILED) {
            return false;
        }
        this.status = reported;
        if (reported == Status.FAILED) {
            this.lastErrorCode = errorCode;
        }
        this.updatedAt = now;
        return true;
    }

    /** A equipe pediu para tentar de novo uma resposta que não foi enviada. */
    void retryNow(Instant now) {
        this.status = Status.PENDING;
        this.attempts = 0;
        this.lastErrorCode = null;
        this.wamid = null;
        this.nextAttemptAt = now;
        this.updatedAt = now;
    }

    UUID id() {
        return id;
    }

    UUID conversationId() {
        return conversationId;
    }

    UUID messageId() {
        return messageId;
    }

    String recipient() {
        return recipient;
    }

    Status status() {
        return status;
    }

    int attempts() {
        return attempts;
    }

    String lastErrorCode() {
        return lastErrorCode;
    }
}
