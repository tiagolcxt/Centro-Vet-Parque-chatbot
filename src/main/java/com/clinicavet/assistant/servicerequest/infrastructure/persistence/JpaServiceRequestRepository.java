package com.clinicavet.assistant.servicerequest.infrastructure.persistence;

import com.clinicavet.assistant.conversation.domain.ConversationId;
import com.clinicavet.assistant.servicerequest.domain.RequestStatus;
import com.clinicavet.assistant.servicerequest.domain.ServiceRequest;
import com.clinicavet.assistant.servicerequest.domain.ServiceRequestId;
import com.clinicavet.assistant.servicerequest.domain.ServiceRequestRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface ServiceRequestJpaRepository extends JpaRepository<ServiceRequestEntity, UUID> {

    List<ServiceRequestEntity> findByStatusOrderByCreatedAtAsc(RequestStatus status);

    List<ServiceRequestEntity> findByConversationIdOrderByCreatedAtAsc(UUID conversationId);
}

@Repository
class JpaServiceRequestRepository implements ServiceRequestRepository {

    private final ServiceRequestJpaRepository jpa;

    JpaServiceRequestRepository(ServiceRequestJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public ServiceRequest save(ServiceRequest request) {
        return jpa.saveAndFlush(ServiceRequestEntity.from(request)).toDomain();
    }

    @Override
    public Optional<ServiceRequest> findById(ServiceRequestId id) {
        return jpa.findById(id.value()).map(ServiceRequestEntity::toDomain);
    }

    @Override
    public List<ServiceRequest> findByStatus(RequestStatus status) {
        return jpa.findByStatusOrderByCreatedAtAsc(status).stream().map(ServiceRequestEntity::toDomain).toList();
    }

    @Override
    public List<ServiceRequest> findByConversation(ConversationId conversationId) {
        return jpa.findByConversationIdOrderByCreatedAtAsc(conversationId.value()).stream()
                .map(ServiceRequestEntity::toDomain).toList();
    }
}
