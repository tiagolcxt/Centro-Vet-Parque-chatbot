package com.clinicavet.assistant.servicerequest.domain;

import com.clinicavet.assistant.shared.domain.ContactIdentity;
import com.clinicavet.assistant.shared.domain.ConversationId;
import com.clinicavet.assistant.shared.domain.InternalNote;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface ServiceRequestRepository {

    /** Sem setor escolhido, a solicitação vai para o setor padrão da clínica (Etapa S2; regras na S5). */
    ServiceRequest save(ServiceRequest request);

    Optional<ServiceRequest> findById(ServiceRequestId id);

    /** Solicitações no status, da mais antiga para a mais nova. */
    List<ServiceRequest> findByStatus(RequestStatus status);

    List<ServiceRequest> findByConversation(ConversationId conversationId);

    /**
     * Solicitações {@code PENDING} ou {@code IN_PROGRESS} de outras conversas do mesmo contato, da mais recente
     * para a mais antiga, no máximo {@code limit} (DA-37). O contato vem do canal, nunca de texto digitado.
     */
    List<ServiceRequest> findOpenOfContactExcept(ContactIdentity contact, ConversationId excluded, int limit);

    /**
     * Fila de trabalho (RF-S30 a RF-S32), da mais antiga para a mais nova.
     *
     * @param departments setores visíveis; vazio = nenhum
     * @param assignee    só deste responsável, se informado
     * @param unassigned  só sem responsável
     */
    List<ServiceRequest> findQueue(Set<UUID> departments, Set<RequestStatus> statuses, UUID assignee,
                                   boolean unassigned, int limit);

    /** Solicitações em aberto de um responsável (RN-S07). */
    List<ServiceRequest> findOpenAssignedTo(UUID assignee);

    List<ServiceRequest> findByTutor(UUID tutorId);

    void saveEvent(RequestEvent event);

    List<RequestEvent> findEvents(ServiceRequestId id);

    void saveNote(ServiceRequestId id, InternalNote note);

    List<InternalNote> findNotes(ServiceRequestId id);
}
