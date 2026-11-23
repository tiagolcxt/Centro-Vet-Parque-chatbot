package com.clinicavet.assistant.conversation.domain;

import com.clinicavet.assistant.shared.domain.ContactIdentity;
import com.clinicavet.assistant.shared.domain.ConversationId;

/**
 * Uma resposta ao tutor foi gravada (do assistente ou da equipe). Publicado dentro da transação que gravou a mensagem:
 * o canal grava a saída (outbox) na mesma transação, e nada sai se ela for desfeita (ADR-0023, item 5). Respostas
 * dadas pelo celular da clínica não geram o evento: elas já foram entregues.
 */
public record OutgoingMessageRecorded(MessageId messageId, ConversationId conversationId, ContactIdentity contact) {
}
