package com.clinicavet.assistant.servicerequest.domain;

import com.clinicavet.assistant.conversation.domain.ConversationId;
import com.clinicavet.assistant.shared.domain.Texts;
import com.clinicavet.assistant.shared.domain.Timestamps;
import com.clinicavet.assistant.shared.exception.DomainValidationException;
import com.clinicavet.assistant.shared.exception.InvalidStateTransitionException;
import com.clinicavet.assistant.shared.exception.MissingRequiredFieldsException;
import com.clinicavet.assistant.tutor.domain.PetId;
import com.clinicavet.assistant.tutor.domain.TutorId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Pedido que a equipe precisa resolver (ADR-0011). Independente do ciclo de vida da conversa.
 *
 * <pre>
 * COLLECTING ──submit──► PENDING ──startProgress──► IN_PROGRESS ──resolve──► RESOLVED
 *     └────────────────────┴──────────────cancel────────┴──────────────────► CANCELLED
 * </pre>
 *
 * Não existe data/hora confirmada: uma solicitação nunca representa um agendamento efetivado.
 */
public final class ServiceRequest {

    private static final String AGGREGATE = "ServiceRequest";
    public static final int MAX_SUMMARY_LENGTH = 500;

    private final ServiceRequestId id;
    private final ConversationId conversationId;
    private TutorId tutorId;
    private PetId petId;
    private final RequestType type;
    private RequestStatus status;
    private String summary;
    private RequestOutcome outcome;
    private String outcomeNote;
    private final Instant createdAt;
    private Instant updatedAt;
    private final Long version;

    private ServiceRequest(ServiceRequestId id, ConversationId conversationId, TutorId tutorId,
                           PetId petId, RequestType type, RequestStatus status, String summary,
                           RequestOutcome outcome, String outcomeNote,
                           Instant createdAt, Instant updatedAt, Long version) {
        this.id = Objects.requireNonNull(id, "id");
        this.conversationId = Objects.requireNonNull(conversationId, "conversationId");
        this.tutorId = tutorId;
        this.petId = petId;
        this.type = Objects.requireNonNull(type, "type");
        this.status = Objects.requireNonNull(status, "status");
        this.summary = Texts.optional("summary", summary, MAX_SUMMARY_LENGTH);
        this.outcome = outcome;
        this.outcomeNote = Texts.optional("outcomeNote", outcomeNote, MAX_SUMMARY_LENGTH);
        if (status.isFinal() != (outcome != null)) {
            throw new IllegalStateException("solicitação inconsistente: status=" + status + ", outcome=" + outcome);
        }
        this.createdAt = Timestamps.normalize(createdAt);
        this.updatedAt = Timestamps.normalize(updatedAt);
        this.version = version;
    }

    public static ServiceRequest open(ConversationId conversationId, RequestType type, Instant now) {
        return new ServiceRequest(ServiceRequestId.newId(), conversationId, null, null, type,
                RequestStatus.COLLECTING, null, null, null, now, now, null);
    }

    public static ServiceRequest restore(ServiceRequestId id, ConversationId conversationId,
                                         TutorId tutorId, PetId petId, RequestType type,
                                         RequestStatus status, String summary, RequestOutcome outcome,
                                         String outcomeNote, Instant createdAt, Instant updatedAt, Long version) {
        return new ServiceRequest(id, conversationId, tutorId, petId, type, status, summary, outcome, outcomeNote,
                createdAt, updatedAt, version);
    }

    public void describe(String summary, Instant at) {
        requireNotFinal("describe");
        this.summary = Texts.optional("summary", summary, MAX_SUMMARY_LENGTH);
        touch(at);
    }

    public void attachTutor(TutorId tutorId, Instant at) {
        requireNotFinal("attachTutor");
        this.tutorId = Objects.requireNonNull(tutorId, "tutorId");
        touch(at);
    }

    public void attachPet(PetId petId, Instant at) {
        requireNotFinal("attachPet");
        this.petId = Objects.requireNonNull(petId, "petId");
        touch(at);
    }

    /** Campos exigidos que ainda faltam, na ordem do enum. */
    public List<RequestField> missingFields(Set<RequestField> required) {
        Set<RequestField> requiredFields = required.isEmpty()
                ? EnumSet.noneOf(RequestField.class) : EnumSet.copyOf(required);
        List<RequestField> missing = new ArrayList<>();
        for (RequestField field : requiredFields) {
            boolean present = switch (field) {
                case SUMMARY -> summary != null;
                case TUTOR -> tutorId != null;
                case PET -> petId != null;
            };
            if (!present) {
                missing.add(field);
            }
        }
        return missing;
    }

    /** Conclui a coleta. Recusado se faltar algum campo exigido. */
    public void submit(Set<RequestField> required, Instant at) {
        requireStatus(RequestStatus.COLLECTING, "submit");
        List<RequestField> missing = missingFields(required);
        if (!missing.isEmpty()) {
            throw new MissingRequiredFieldsException(missing.stream().map(Enum::name).toList());
        }
        transition(RequestStatus.PENDING, at);
    }

    public void startProgress(Instant at) {
        requireStatus(RequestStatus.PENDING, "startProgress");
        transition(RequestStatus.IN_PROGRESS, at);
    }

    /** Resolve com o desfecho informado pela equipe (DA-34). {@code OTHER} exige observação. */
    public void resolve(RequestOutcome outcome, String note, Instant at) {
        requireStatus(RequestStatus.IN_PROGRESS, "resolve");
        recordOutcome(outcome, note);
        transition(RequestStatus.RESOLVED, at);
    }

    /** Cancela com o desfecho informado pela equipe (DA-34). */
    public void cancel(RequestOutcome outcome, String note, Instant at) {
        requireNotFinal("cancel");
        recordOutcome(outcome, note);
        transition(RequestStatus.CANCELLED, at);
    }

    private void recordOutcome(RequestOutcome outcome, String note) {
        if (outcome == null) {
            throw new DomainValidationException("outcome", "obrigatório ao resolver ou cancelar");
        }
        String cleanNote = Texts.optional("outcomeNote", note, MAX_SUMMARY_LENGTH);
        if (outcome == RequestOutcome.OTHER && cleanNote == null) {
            throw new DomainValidationException("outcomeNote", "obrigatória com o desfecho OTHER");
        }
        this.outcome = outcome;
        this.outcomeNote = cleanNote;
    }

    private void transition(RequestStatus next, Instant at) {
        status = next;
        touch(at);
    }

    private void touch(Instant at) {
        Instant normalized = Timestamps.normalize(at);
        if (normalized.isAfter(updatedAt)) {
            updatedAt = normalized;
        }
    }

    private void requireStatus(RequestStatus expected, String action) {
        if (status != expected) {
            throw new InvalidStateTransitionException(AGGREGATE, status, action);
        }
    }

    private void requireNotFinal(String action) {
        if (status.isFinal()) {
            throw new InvalidStateTransitionException(AGGREGATE, status, action);
        }
    }

    public ServiceRequestId id() {
        return id;
    }

    public ConversationId conversationId() {
        return conversationId;
    }

    public Optional<TutorId> tutorId() {
        return Optional.ofNullable(tutorId);
    }

    public Optional<PetId> petId() {
        return Optional.ofNullable(petId);
    }

    public RequestType type() {
        return type;
    }

    public RequestStatus status() {
        return status;
    }

    public Optional<String> summary() {
        return Optional.ofNullable(summary);
    }

    public Optional<RequestOutcome> outcome() {
        return Optional.ofNullable(outcome);
    }

    public Optional<String> outcomeNote() {
        return Optional.ofNullable(outcomeNote);
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public Long version() {
        return version;
    }
}
