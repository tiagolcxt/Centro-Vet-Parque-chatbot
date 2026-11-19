package com.clinicavet.assistant.conversation.domain;

import com.clinicavet.assistant.shared.domain.ConversationId;
import com.clinicavet.assistant.shared.domain.ContactIdentity;

import java.util.List;
import java.util.Optional;

public interface ConversationRepository {

    /**
     * Salva e devolve o agregado com a versão atualizada. Uma versão desatualizada causa falha de
     * concorrência (lock otimista, DM-16).
     */
    Conversation save(Conversation conversation);

    Optional<Conversation> findById(ConversationId id);

    /**
     * Como {@link #findById}, mas trava a linha até o fim da transação (lock pessimista). Usado no
     * início e no fim de um turno para serializar quem altera a mesma conversa (DA-21).
     * Exige transação ativa.
     */
    Optional<Conversation> findByIdForUpdate(ConversationId id);

    /** A conversa ativa ({@code OPEN} ou {@code WAITING_HUMAN}) do contato, se houver (ADR-0009). */
    Optional<Conversation> findActiveByContact(ContactIdentity contact);

    /** Como {@link #findActiveByContact}, travando a linha encontrada. Exige transação ativa. */
    Optional<Conversation> findActiveByContactForUpdate(ContactIdentity contact);

    /**
     * Conversas no status, na ordem da fila da equipe (H10): prioridade mais urgente primeiro e, dentro do
     * nível, a que espera há mais tempo (encaminhamento ou última atividade).
     */
    List<Conversation> findByStatusInQueueOrder(ConversationStatus status, int limit);

    void savePriorityChange(PriorityChange change);

    List<PriorityChange> findPriorityChanges(ConversationId id);
}
