package com.clinicavet.assistant.conversation.domain;

import com.clinicavet.assistant.shared.domain.ConversationId;
import com.clinicavet.assistant.shared.domain.Texts;
import com.clinicavet.assistant.shared.domain.Timestamps;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Registro de uma mudança de prioridade feita pela equipe (H4, ADR-0018 item 4): nível anterior, novo,
 * autor e momento. Até a Etapa 7 o autor é só o canal ({@link #API_KEY_AUTHOR}), sem identidade individual.
 */
public record PriorityChange(UUID id, ConversationId conversationId, ClinicalPriority from, ClinicalPriority to,
                             String changedBy, String note, Instant changedAt) {

    public static final String API_KEY_AUTHOR = "equipe (API Key, sem identidade individual)";
    public static final int MAX_NOTE = 500;

    public PriorityChange {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(conversationId, "conversationId");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        changedBy = Texts.required("changedBy", changedBy, 60);
        note = Texts.optional("note", note, MAX_NOTE);
        changedAt = Timestamps.normalize(changedAt);
    }

    public static PriorityChange byStaff(ConversationId conversationId, ClinicalPriority from, ClinicalPriority to,
                                         String note, Instant at) {
        return new PriorityChange(UUID.randomUUID(), conversationId, from, to, API_KEY_AUTHOR, note, at);
    }

    public Optional<String> noteText() {
        return Optional.ofNullable(note);
    }
}
