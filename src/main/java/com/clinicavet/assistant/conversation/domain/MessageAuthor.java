package com.clinicavet.assistant.conversation.domain;

/**
 * Autor da mensagem. {@code STAFF} (mensagens da equipe no mesmo chat) fica para o adaptador
 * WhatsApp (registro de decisões, seção 4).
 */
public enum MessageAuthor {
    CUSTOMER,
    ASSISTANT
}
