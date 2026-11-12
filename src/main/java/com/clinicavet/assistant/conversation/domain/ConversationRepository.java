package com.clinicavet.assistant.conversation.domain;

import com.clinicavet.assistant.shared.domain.ContactIdentity;

import java.util.Optional;

public interface ConversationRepository {

    /**
     * Salva e devolve o agregado com a versão atualizada. Uma versão desatualizada causa falha de
     * concorrência (lock otimista, DM-16).
     */
    Conversation save(Conversation conversation);

    Optional<Conversation> findById(ConversationId id);

    /** A conversa ativa ({@code OPEN} ou {@code WAITING_HUMAN}) do contato, se houver (ADR-0009). */
    Optional<Conversation> findActiveByContact(ContactIdentity contact);
}
