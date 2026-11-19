package com.clinicavet.assistant.servicerequest.infrastructure.persistence;

import com.clinicavet.assistant.platform.CurrentClinic;
import com.clinicavet.assistant.shared.domain.ConversationId;
import com.clinicavet.assistant.shared.domain.ContactIdentity;
import com.clinicavet.assistant.servicerequest.domain.RequestStatus;
import com.clinicavet.assistant.servicerequest.domain.ServiceRequest;
import com.clinicavet.assistant.servicerequest.domain.ServiceRequestId;
import com.clinicavet.assistant.servicerequest.domain.ServiceRequestRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface ServiceRequestJpaRepository extends JpaRepository<ServiceRequestEntity, UUID> {

    List<ServiceRequestEntity> findByStatusOrderByCreatedAtAsc(RequestStatus status);

    List<ServiceRequestEntity> findByConversationIdOrderByCreatedAtAsc(UUID conversationId);

    /**
     * DA-37: solicitações em aberto de OUTRAS conversas do mesmo contato, da mais recente para a mais antiga.
     * Consulta nativa: o Hibernate não aplica o @TenantId, então a clínica vai explícita (ADR-0021).
     */
    @Query(value = """
            SELECT sr.* FROM service_request sr
            JOIN conversation c ON c.id = sr.conversation_id
            WHERE sr.clinic_id = :clinic AND c.clinic_id = :clinic
              AND c.channel = :channel AND c.external_contact_id = :externalContactId
              AND sr.conversation_id <> :excluded AND sr.status IN ('PENDING', 'IN_PROGRESS')
            ORDER BY sr.created_at DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<ServiceRequestEntity> findOpenOfContactExcept(@Param("clinic") UUID clinic, @Param("channel") String channel,
                                                       @Param("externalContactId") String externalContactId,
                                                       @Param("excluded") UUID excluded, @Param("limit") int limit);
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

    @Override
    public List<ServiceRequest> findOpenOfContactExcept(ContactIdentity contact, ConversationId excluded, int limit) {
        return jpa.findOpenOfContactExcept(CurrentClinic.require().value(), contact.channel().name(), contact.externalContactId(), excluded.value(), limit)
                .stream().map(ServiceRequestEntity::toDomain).toList();
    }
}
