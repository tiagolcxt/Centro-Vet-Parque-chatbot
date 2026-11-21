package com.clinicavet.assistant.servicerequest.infrastructure.persistence;

import com.clinicavet.assistant.clinicknowledge.domain.Departments;
import com.clinicavet.assistant.platform.CurrentClinic;
import com.clinicavet.assistant.servicerequest.domain.RequestEvent;
import com.clinicavet.assistant.servicerequest.domain.RequestStatus;
import com.clinicavet.assistant.servicerequest.domain.ServiceRequest;
import com.clinicavet.assistant.servicerequest.domain.ServiceRequestId;
import com.clinicavet.assistant.servicerequest.domain.ServiceRequestRepository;
import com.clinicavet.assistant.shared.domain.ContactIdentity;
import com.clinicavet.assistant.shared.domain.ConversationId;
import com.clinicavet.assistant.shared.domain.InternalNote;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

interface ServiceRequestJpaRepository extends JpaRepository<ServiceRequestEntity, UUID> {

    List<ServiceRequestEntity> findByStatusOrderByCreatedAtAsc(RequestStatus status);

    List<ServiceRequestEntity> findByConversationIdOrderByCreatedAtAsc(UUID conversationId);

    List<ServiceRequestEntity> findByTutorIdOrderByCreatedAtDesc(UUID tutorId);

    List<ServiceRequestEntity> findByAssigneeIdAndStatusIn(UUID assigneeId, Collection<RequestStatus> statuses);

    long countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(java.time.Instant from, java.time.Instant to);

    long countByStatusAndUpdatedAtGreaterThanEqualAndUpdatedAtLessThan(RequestStatus status, java.time.Instant from,
                                                                       java.time.Instant to);

    long countByStatusIn(Collection<RequestStatus> statuses);

    @Query("""
            SELECT r FROM ServiceRequestEntity r
            WHERE r.departmentId IN :departments AND r.status IN :statuses
              AND (:assignee IS NULL OR r.assigneeId = :assignee)
              AND (:unassigned = FALSE OR r.assigneeId IS NULL)
            ORDER BY r.createdAt ASC
            """)
    List<ServiceRequestEntity> findQueue(@Param("departments") Collection<UUID> departments,
                                         @Param("statuses") Collection<RequestStatus> statuses,
                                         @Param("assignee") UUID assignee, @Param("unassigned") boolean unassigned,
                                         Limit limit);

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

interface RequestEventJpaRepository extends JpaRepository<RequestEventEntity, UUID> {

    List<RequestEventEntity> findByRequestIdOrderByOccurredAtAsc(UUID requestId);
}

interface RequestNoteJpaRepository extends JpaRepository<RequestNoteEntity, UUID> {

    List<RequestNoteEntity> findByRequestIdOrderByCreatedAtAsc(UUID requestId);
}

@Repository
class JpaServiceRequestRepository implements ServiceRequestRepository {

    private static final Set<RequestStatus> OPEN = Set.of(RequestStatus.COLLECTING, RequestStatus.PENDING,
            RequestStatus.IN_PROGRESS);

    private final ServiceRequestJpaRepository jpa;
    private final RequestEventJpaRepository events;
    private final RequestNoteJpaRepository notes;
    private final Departments departments;

    JpaServiceRequestRepository(ServiceRequestJpaRepository jpa, RequestEventJpaRepository events,
                                RequestNoteJpaRepository notes, Departments departments) {
        this.jpa = jpa;
        this.events = events;
        this.notes = notes;
        this.departments = departments;
    }

    @Override
    public ServiceRequest save(ServiceRequest request) {
        if (request.departmentId().isEmpty()) {
            request.routeTo(departments.defaultDepartment().id(), request.updatedAt());
        }
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
        return jpa.findOpenOfContactExcept(CurrentClinic.require().value(), contact.channel().name(),
                        contact.externalContactId(), excluded.value(), limit)
                .stream().map(ServiceRequestEntity::toDomain).toList();
    }

    @Override
    public List<ServiceRequest> findQueue(Set<UUID> departmentIds, Set<RequestStatus> statuses, UUID assignee,
                                          boolean unassigned, int limit) {
        if (departmentIds.isEmpty() || statuses.isEmpty()) {
            return List.of();
        }
        return jpa.findQueue(departmentIds, statuses, assignee, unassigned, Limit.of(limit)).stream()
                .map(ServiceRequestEntity::toDomain).toList();
    }

    @Override
    public Counts count(java.time.Instant from, java.time.Instant to) {
        return new Counts(jpa.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(from, to),
                jpa.countByStatusAndUpdatedAtGreaterThanEqualAndUpdatedAtLessThan(RequestStatus.RESOLVED, from, to),
                jpa.countByStatusIn(java.util.EnumSet.of(RequestStatus.COLLECTING, RequestStatus.PENDING,
                        RequestStatus.IN_PROGRESS)));
    }

    @Override
    public List<ServiceRequest> findOpenAssignedTo(UUID assignee) {
        return jpa.findByAssigneeIdAndStatusIn(assignee, OPEN).stream().map(ServiceRequestEntity::toDomain).toList();
    }

    @Override
    public List<ServiceRequest> findByTutor(UUID tutorId) {
        return jpa.findByTutorIdOrderByCreatedAtDesc(tutorId).stream().map(ServiceRequestEntity::toDomain).toList();
    }

    @Override
    public void saveEvent(RequestEvent event) {
        events.save(RequestEventEntity.from(event));
    }

    @Override
    public List<RequestEvent> findEvents(ServiceRequestId id) {
        return events.findByRequestIdOrderByOccurredAtAsc(id.value()).stream().map(RequestEventEntity::toDomain)
                .toList();
    }

    @Override
    public void saveNote(ServiceRequestId id, InternalNote note) {
        notes.save(new RequestNoteEntity(id.value(), note));
    }

    @Override
    public List<InternalNote> findNotes(ServiceRequestId id) {
        return notes.findByRequestIdOrderByCreatedAtAsc(id.value()).stream().map(RequestNoteEntity::toDomain).toList();
    }
}
