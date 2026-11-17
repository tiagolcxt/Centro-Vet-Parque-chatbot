package com.clinicavet.assistant.conversation.domain;

/** De onde veio a prioridade efetiva do turno (ADR-0018, item 8). */
public enum PrioritySource {
    /** Sugestão do modelo. */
    MODEL,
    /** Piso determinístico do backend (H5). */
    BACKEND_FLOOR,
    /** Nível que a conversa já tinha: nunca é rebaixado automaticamente (H4). */
    CURRENT_LEVEL
}
