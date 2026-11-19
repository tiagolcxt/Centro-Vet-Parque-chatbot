package com.clinicavet.assistant.servicerequest.domain;

import com.clinicavet.assistant.shared.domain.ConversationId;
import com.clinicavet.assistant.shared.domain.ContactIdentity;

import java.util.List;
import java.util.Optional;

public interface ServiceRequestRepository {

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
}
