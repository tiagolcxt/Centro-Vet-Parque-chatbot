package com.clinicavet.assistant.conversation.domain;

/**
 * Autor da mensagem. {@code STAFF}: funcionário respondendo pela aplicação (Etapa S2), com o id da conta.
 */
public enum MessageAuthor {
    CUSTOMER,
    ASSISTANT,
    /** Funcionário respondendo pela aplicação (Etapa S2). */
    STAFF
}
