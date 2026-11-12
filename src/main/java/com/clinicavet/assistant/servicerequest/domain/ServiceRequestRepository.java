package com.clinicavet.assistant.servicerequest.domain;

import com.clinicavet.assistant.conversation.domain.ConversationId;

import java.util.List;
import java.util.Optional;

public interface ServiceRequestRepository {

    ServiceRequest save(ServiceRequest request);

    Optional<ServiceRequest> findById(ServiceRequestId id);

    /** Solicitações no status, da mais antiga para a mais nova. */
    List<ServiceRequest> findByStatus(RequestStatus status);

    List<ServiceRequest> findByConversation(ConversationId conversationId);
}
