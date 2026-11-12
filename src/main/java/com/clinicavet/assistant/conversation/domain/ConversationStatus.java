package com.clinicavet.assistant.conversation.domain;

/**
 * Estado operacional da conversa (ADR-0009). {@code WAITING_HUMAN} significa "sinalizada para
 * revisão humana", não que alguém foi notificado (ADR-0007).
 */
public enum ConversationStatus {
    OPEN,
    WAITING_HUMAN,
    CLOSED;

    /** Conversa ativa: no máximo uma por contato. */
    public boolean isActive() {
        return this != CLOSED;
    }
}
