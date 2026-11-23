package com.clinicavet.assistant.channel;

import com.clinicavet.assistant.platform.ClinicId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Mensagens recebidas pelo webhook ({@code inbound_message}). Por JDBC, com {@code clinic_id} explícito em toda
 * consulta: a gravação acontece antes de haver clínica na execução, e o {@code INSERT IGNORE} sobre
 * {@code (clinic_id, wamid)} é o que descarta o reenvio da Meta.
 */
@Component
class InboundStore {

    record Pending(UUID id, String contact, WebhookEvents.Kind kind, String text, int attempts) {
    }

    record MediaRef(UUID id, UUID messageId, WebhookEvents.Kind kind, String mediaId, String mime, String name) {
    }

    record ContactKey(ClinicId clinic, String contact) {
    }

    private final JdbcTemplate jdbc;

    InboundStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Grava a mensagem; {@code false} se já tinha chegado (mesmo wamid). */
    boolean insert(ClinicId clinic, WebhookEvents.Inbound m, Instant now) {
        Instant sentAt = m.sentAt().equals(Instant.EPOCH) ? now : m.sentAt();
        return jdbc.update("""
                INSERT IGNORE INTO inbound_message (id, clinic_id, wamid, contact, kind, media_id, media_mime, media_name,
                    sent_at, received_at, processed_at, attempts, message_id, payload_text, version)
                VALUES (UUID_TO_BIN(?), UUID_TO_BIN(?), ?, ?, ?, ?, ?, ?, ?, ?, NULL, 0, NULL, ?, 0)
                """, UUID.randomUUID().toString(), clinic.toString(), m.wamid(), m.contact(), m.kind().name(),
                m.mediaId(), cut(m.mediaMime(), 100), cut(m.mediaName(), 255), Utc.of(sentAt),
                Utc.of(now), m.text()) == 1;
    }

    /** A mais antiga ainda não processada do contato, na ordem em que o tutor mandou. */
    Optional<Pending> nextPending(ClinicId clinic, String contact, int maxAttempts) {
        return jdbc.query("""
                SELECT BIN_TO_UUID(id) AS id, contact, kind, payload_text, attempts FROM inbound_message
                WHERE clinic_id = UUID_TO_BIN(?) AND contact = ? AND processed_at IS NULL AND attempts < ?
                ORDER BY sent_at, received_at LIMIT 1
                """, (rs, i) -> new Pending(UUID.fromString(rs.getString("id")), rs.getString("contact"),
                WebhookEvents.Kind.valueOf(rs.getString("kind")), rs.getString("payload_text"), rs.getInt("attempts")),
                clinic.toString(), contact, maxAttempts).stream().findFirst();
    }

    void countAttempt(ClinicId clinic, UUID id) {
        jdbc.update("UPDATE inbound_message SET attempts = attempts + 1, version = version + 1 "
                + "WHERE clinic_id = UUID_TO_BIN(?) AND id = UUID_TO_BIN(?)", clinic.toString(), id.toString());
    }

    /** Marca como processada e apaga o texto daqui: ele já está na conversa (minimização, LGPD). */
    void processed(ClinicId clinic, UUID id, UUID messageId, Instant now) {
        jdbc.update("""
                UPDATE inbound_message SET processed_at = ?, message_id = UUID_TO_BIN(?), payload_text = NULL,
                    version = version + 1
                WHERE clinic_id = UUID_TO_BIN(?) AND id = UUID_TO_BIN(?)
                """, Utc.of(now), messageId == null ? null : messageId.toString(), clinic.toString(),
                id.toString());
    }

    /** Contatos com mensagem parada há mais de {@code olderThan} (a varredura retoma; o processamento falhou ou caiu). */
    List<ContactKey> stalled(Instant olderThan, int maxAttempts) {
        return jdbc.query("""
                SELECT DISTINCT BIN_TO_UUID(clinic_id) AS clinic, contact FROM inbound_message
                WHERE processed_at IS NULL AND attempts < ? AND received_at < ? LIMIT 100
                """, (rs, i) -> new ContactKey(ClinicId.of(rs.getString("clinic")), rs.getString("contact")),
                maxAttempts, Utc.of(olderThan));
    }

    /** Anexos ligados às mensagens informadas, na clínica informada. */
    List<MediaRef> mediaOf(ClinicId clinic, Collection<UUID> messageIds) {
        if (messageIds.isEmpty()) {
            return List.of();
        }
        String marks = String.join(",", java.util.Collections.nCopies(messageIds.size(), "UUID_TO_BIN(?)"));
        Object[] args = new Object[messageIds.size() + 1];
        args[0] = clinic.toString();
        int i = 1;
        for (UUID id : messageIds) {
            args[i++] = id.toString();
        }
        return jdbc.query("SELECT BIN_TO_UUID(id) AS id, BIN_TO_UUID(message_id) AS message_id, kind, media_id, "
                        + "media_mime, media_name FROM inbound_message WHERE clinic_id = UUID_TO_BIN(?) "
                        + "AND media_id IS NOT NULL AND message_id IN (" + marks + ")",
                (rs, n) -> new MediaRef(UUID.fromString(rs.getString("id")), UUID.fromString(rs.getString("message_id")),
                        WebhookEvents.Kind.valueOf(rs.getString("kind")), rs.getString("media_id"),
                        rs.getString("media_mime"), rs.getString("media_name")), args);
    }

    Optional<MediaRef> media(ClinicId clinic, UUID id) {
        return jdbc.query("SELECT BIN_TO_UUID(id) AS id, BIN_TO_UUID(message_id) AS message_id, kind, media_id, "
                        + "media_mime, media_name FROM inbound_message WHERE clinic_id = UUID_TO_BIN(?) "
                        + "AND id = UUID_TO_BIN(?) AND media_id IS NOT NULL",
                (rs, n) -> new MediaRef(UUID.fromString(rs.getString("id")),
                        rs.getString("message_id") == null ? null : UUID.fromString(rs.getString("message_id")),
                        WebhookEvents.Kind.valueOf(rs.getString("kind")), rs.getString("media_id"),
                        rs.getString("media_mime"), rs.getString("media_name")),
                clinic.toString(), id.toString()).stream().findFirst();
    }

    private static String cut(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
