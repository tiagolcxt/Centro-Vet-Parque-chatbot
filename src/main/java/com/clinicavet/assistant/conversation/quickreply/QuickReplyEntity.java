package com.clinicavet.assistant.conversation.quickreply;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.TenantId;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "quick_reply")
class QuickReplyEntity {

    @Id
    @Column(name = "id", columnDefinition = "BINARY(16)")
    private UUID id;

    @TenantId
    @Column(name = "clinic_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID clinicId;

    @Column(name = "title", length = 60, nullable = false)
    private String title;

    @Column(name = "body", length = 2000, nullable = false)
    private String body;

    @Column(name = "created_at", columnDefinition = "DATETIME(6)", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", columnDefinition = "DATETIME(6)", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected QuickReplyEntity() {
    }

    QuickReplyEntity(String title, String body, Instant now) {
        this.id = UUID.randomUUID();
        this.title = title;
        this.body = body;
        this.createdAt = now;
        this.updatedAt = now;
    }

    void change(String title, String body, Instant now) {
        this.title = title;
        this.body = body;
        this.updatedAt = now;
    }

    UUID id() {
        return id;
    }

    QuickReplyController.QuickReply view() {
        return new QuickReplyController.QuickReply(id, title, body, updatedAt);
    }
}
