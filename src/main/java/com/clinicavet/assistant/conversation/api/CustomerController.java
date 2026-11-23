package com.clinicavet.assistant.conversation.api;

import com.clinicavet.assistant.conversation.domain.ClinicalPriority;
import com.clinicavet.assistant.conversation.domain.ConversationRepository;
import com.clinicavet.assistant.conversation.domain.ConversationStatus;
import com.clinicavet.assistant.platform.AuditLog;
import com.clinicavet.assistant.platform.StaffUser;
import com.clinicavet.assistant.servicerequest.domain.RequestStatus;
import com.clinicavet.assistant.servicerequest.domain.RequestType;
import com.clinicavet.assistant.servicerequest.domain.ServiceRequestRepository;
import com.clinicavet.assistant.shared.exception.ResourceNotFoundException;
import com.clinicavet.assistant.tutor.domain.Pet;
import com.clinicavet.assistant.tutor.domain.PetRepository;
import com.clinicavet.assistant.tutor.domain.Provenance;
import com.clinicavet.assistant.tutor.domain.ProvenanceRepository;
import com.clinicavet.assistant.tutor.domain.Tutor;
import com.clinicavet.assistant.tutor.domain.TutorId;
import com.clinicavet.assistant.tutor.domain.TutorRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Clientes e pets (RF-S40, RF-S42, RF-S44). Exige {@code customer:view}. As solicitações da ficha respeitam o escopo de
 * setor; notas internas não aparecem aqui. Abrir a ficha fica na auditoria (RF-S07).
 * <p>
 * Edição pela equipe (RF-S41) ainda não: a proveniência hoje exige a conversa de origem (ADR-0010), e a edição sem
 * conversa pede uma migração. Fica para a Etapa S3.
 */
@RestController
@RequestMapping("/app/api/customers")
@PreAuthorize("hasAuthority('customer:view')")
class CustomerController {

    static final int MAX_RESULTS = 30;

    private final TutorRepository tutors;
    private final PetRepository pets;
    private final ProvenanceRepository provenance;
    private final ConversationRepository conversations;
    private final ServiceRequestRepository requests;
    private final com.clinicavet.assistant.team.StaffDirectory requestScope;
    private final AuditLog audit;

    CustomerController(TutorRepository tutors, PetRepository pets, ProvenanceRepository provenance,
                       ConversationRepository conversations, ServiceRequestRepository requests,
                       com.clinicavet.assistant.team.StaffDirectory requestScope, AuditLog audit) {
        this.tutors = tutors;
        this.pets = pets;
        this.provenance = provenance;
        this.conversations = conversations;
        this.requests = requests;
        this.requestScope = requestScope;
        this.audit = audit;
    }

    @GetMapping
    List<CustomerItem> search(@RequestParam(required = false) String q) {
        // Sem busca: os que falaram por último (revisão pré-piloto, H3).
        List<com.clinicavet.assistant.tutor.domain.Tutor> found = q == null || q.isBlank()
                ? tutors.recent(MAX_RESULTS) : tutors.search(q, MAX_RESULTS);
        return found.stream().map(t -> new CustomerItem(t.id().value(), t.name().orElse(null),
                t.phone().orElse(null), pets.findByTutor(t.id()).stream().map(Pet::name).toList(), t.lastSeenAt()))
                .toList();
    }

    @GetMapping("/{id}")
    @Transactional
    CustomerDetail detail(@PathVariable UUID id) {
        TutorId tutorId = new TutorId(id);
        Tutor tutor = tutors.findById(tutorId).orElseThrow(() -> new ResourceNotFoundException("Cliente", id));
        List<Pet> tutorPets = pets.findByTutor(tutorId);
        List<UUID> subjects = new ArrayList<>(List.of(id));
        tutorPets.forEach(p -> subjects.add(p.id().value()));
        List<ProvenanceItem> origin = provenance.findBySubjects(subjects).stream().map(ProvenanceItem::of).toList();
        Set<UUID> visible = requestScope.visibleDepartments(StaffUser.current().orElseThrow());
        audit.record("customer.viewed", "tutor", id, null);
        return new CustomerDetail(id, tutor.name().orElse(null), tutor.phone().orElse(null),
                tutor.contact().channel().name(), tutor.firstSeenAt(), tutor.lastSeenAt(),
                tutorPets.stream().map(p -> new PetItem(p.id().value(), p.name(), p.species().map(Enum::name).orElse(null),
                        p.breed().orElse(null), p.ageDescription().orElse(null))).toList(),
                origin,
                conversations.findByTutor(tutorId).stream().map(c -> new ConversationItem(c.id().value(), c.status(),
                        c.clinicalPriority(), c.startedAt(), c.lastActivityAt())).toList(),
                requests.findByTutor(id).stream()
                        .filter(r -> r.departmentId().map(visible::contains).orElse(false))
                        .map(r -> new RequestItem(r.id().value(), r.type(), r.status(), r.departmentId().orElse(null),
                                r.createdAt())).toList());
    }

    record CustomerItem(UUID id, String name, String phone, List<String> pets, Instant lastSeenAt) {
    }

    record PetItem(UUID id, String name, String species, String breed, String age) {
    }

    /** Proveniência por campo (ADR-0010): mencionado ou confirmado na conversa. */
    record ProvenanceItem(UUID subjectId, String field, String status, Instant confirmedAt) {
        static ProvenanceItem of(Provenance p) {
            return new ProvenanceItem(p.subjectId(), p.field().name(), p.status().name(),
                    p.confirmedAt().orElse(null));
        }
    }

    record ConversationItem(UUID id, ConversationStatus status, ClinicalPriority priority, Instant startedAt,
                            Instant lastActivityAt) {
    }

    record RequestItem(UUID id, RequestType type, RequestStatus status, UUID departmentId, Instant createdAt) {
    }

    record CustomerDetail(UUID id, String name, String phone, String channel, Instant firstSeenAt, Instant lastSeenAt,
                          List<PetItem> pets, List<ProvenanceItem> provenance, List<ConversationItem> conversations,
                          List<RequestItem> requests) {
    }
}
