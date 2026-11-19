package com.clinicavet.assistant.tutor.domain;

import com.clinicavet.assistant.shared.domain.ConversationId;
import com.clinicavet.assistant.shared.domain.Timestamps;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Proveniência de <b>um campo</b> do cadastro (DA-35). Guarda o que aconteceu na última conversa que tocou o
 * campo ({@code MENTIONED} ou {@code CONFIRMED}) e o momento da última confirmação.
 * <ul>
 *   <li>valor novo ou alterado: {@code MENTIONED} na conversa atual, e a confirmação anterior deixa de valer;</li>
 *   <li>mesmo valor citado de novo: {@code MENTIONED} na conversa atual, mantendo a data da confirmação anterior
 *       (ela continua servindo para {@code REGISTERED} em conversas futuras); se já está {@code CONFIRMED}
 *       nesta conversa, nada muda;</li>
 *   <li>confirmação (critério de P-08, verificado fora daqui): {@code CONFIRMED} na conversa atual.</li>
 * </ul>
 * Em outra conversa, o status é calculado por {@link #statusFor}: {@code REGISTERED} se houve confirmação dentro
 * da validade, {@code TO_RECONFIRM} nos demais casos (ADR-0010, item 2). Nunca {@code CONFIRMED}.
 */
public final class Provenance {

    private final UUID id;
    private final DataSubject subject;
    private final UUID subjectId;
    private final DataField field;
    private ProvenanceStatus status;
    private ConversationId conversationId;
    private Instant confirmedAt;
    private Instant updatedAt;
    private final Long version;

    private Provenance(UUID id, DataSubject subject, UUID subjectId, DataField field, ProvenanceStatus status,
                       ConversationId conversationId, Instant confirmedAt, Instant updatedAt, Long version) {
        this.id = Objects.requireNonNull(id, "id");
        this.subject = Objects.requireNonNull(subject, "subject");
        this.subjectId = Objects.requireNonNull(subjectId, "subjectId");
        this.field = Objects.requireNonNull(field, "field");
        if (field.subject() != subject) {
            throw new IllegalArgumentException("campo " + field + " não pertence a " + subject);
        }
        this.status = Objects.requireNonNull(status, "status");
        if (!status.currentConversation()) {
            throw new IllegalArgumentException("só MENTIONED e CONFIRMED são gravados");
        }
        if (status == ProvenanceStatus.CONFIRMED && confirmedAt == null) {
            throw new IllegalArgumentException("CONFIRMED exige o momento da confirmação");
        }
        this.conversationId = Objects.requireNonNull(conversationId, "conversationId");
        this.confirmedAt = confirmedAt == null ? null : Timestamps.normalize(confirmedAt);
        this.updatedAt = Timestamps.normalize(updatedAt);
        this.version = version;
    }

    public static Provenance mentioned(DataField field, UUID subjectId, ConversationId conversation, Instant now) {
        return new Provenance(UUID.randomUUID(), field.subject(), subjectId, field, ProvenanceStatus.MENTIONED,
                conversation, null, now, null);
    }

    public static Provenance restore(UUID id, DataSubject subject, UUID subjectId, DataField field,
                                     ProvenanceStatus status, ConversationId conversationId, Instant confirmedAt,
                                     Instant updatedAt, Long version) {
        return new Provenance(id, subject, subjectId, field, status, conversationId, confirmedAt, updatedAt, version);
    }

    /** O tutor citou o campo nesta conversa. {@code valueChanged}: o valor gravado mudou com esta citação. */
    public void mention(ConversationId conversation, boolean valueChanged, Instant now) {
        Objects.requireNonNull(conversation, "conversation");
        if (valueChanged) {
            confirmedAt = null;
        } else if (status == ProvenanceStatus.CONFIRMED && conversation.equals(conversationId)) {
            return;
        }
        status = ProvenanceStatus.MENTIONED;
        conversationId = conversation;
        touch(now);
    }

    /** O tutor confirmou o campo nesta conversa, pelo critério de P-08. */
    public void confirm(ConversationId conversation, Instant now) {
        Objects.requireNonNull(conversation, "conversation");
        status = ProvenanceStatus.CONFIRMED;
        conversationId = conversation;
        confirmedAt = Timestamps.normalize(now);
        touch(now);
    }

    /**
     * Status visto pela conversa {@code current}. Na própria conversa, o gravado. Em outra, {@code REGISTERED} se
     * houve confirmação há no máximo {@code validity}; senão {@code TO_RECONFIRM}.
     */
    public ProvenanceStatus statusFor(ConversationId current, Instant now, Duration validity) {
        if (conversationId.equals(current)) {
            return status;
        }
        if (confirmedAt != null && !now.isAfter(confirmedAt.plus(validity))) {
            return ProvenanceStatus.REGISTERED;
        }
        return ProvenanceStatus.TO_RECONFIRM;
    }

    private void touch(Instant at) {
        Instant normalized = Timestamps.normalize(at);
        if (normalized.isAfter(updatedAt)) {
            updatedAt = normalized;
        }
    }

    public UUID id() {
        return id;
    }

    public DataSubject subject() {
        return subject;
    }

    public UUID subjectId() {
        return subjectId;
    }

    public DataField field() {
        return field;
    }

    public ProvenanceStatus status() {
        return status;
    }

    public ConversationId conversationId() {
        return conversationId;
    }

    public Optional<Instant> confirmedAt() {
        return Optional.ofNullable(confirmedAt);
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public Long version() {
        return version;
    }
}
