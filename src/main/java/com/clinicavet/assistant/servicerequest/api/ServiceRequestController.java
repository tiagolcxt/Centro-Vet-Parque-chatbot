package com.clinicavet.assistant.servicerequest.api;

import com.clinicavet.assistant.platform.StaffUser;
import com.clinicavet.assistant.servicerequest.application.StaffServiceRequestService;
import com.clinicavet.assistant.servicerequest.domain.ConversationFacts;
import com.clinicavet.assistant.servicerequest.domain.RequestEvent;
import com.clinicavet.assistant.servicerequest.domain.RequestOutcome;
import com.clinicavet.assistant.servicerequest.domain.RequestStatus;
import com.clinicavet.assistant.servicerequest.domain.RequestType;
import com.clinicavet.assistant.servicerequest.domain.ServiceRequest;
import com.clinicavet.assistant.servicerequest.domain.ServiceRequestId;
import com.clinicavet.assistant.shared.domain.ConversationId;
import com.clinicavet.assistant.shared.domain.InternalNote;
import com.clinicavet.assistant.team.StaffDirectory;
import com.clinicavet.assistant.tutor.domain.Pet;
import com.clinicavet.assistant.tutor.domain.PetRepository;
import com.clinicavet.assistant.tutor.domain.Tutor;
import com.clinicavet.assistant.tutor.domain.TutorRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Solicitações por setor na aplicação da equipe (Etapa S2; RF-S30 a RF-S36). Ver exige {@code request:view};
 * atribuir, transferir e iniciar, {@code request:assign}; resolver e cancelar, {@code request:close}.
 */
@RestController
@RequestMapping("/app/api/service-requests")
@PreAuthorize("hasAuthority('request:view')")
class ServiceRequestController {

    private final StaffServiceRequestService staff;
    private final StaffDirectory directory;
    private final ConversationFacts conversations;
    private final TutorRepository tutors;
    private final PetRepository pets;
    private final com.clinicavet.assistant.clinicknowledge.domain.Departments departments;
    private final java.time.Clock clock;

    ServiceRequestController(StaffServiceRequestService staff, StaffDirectory directory,
                             ConversationFacts conversations, TutorRepository tutors, PetRepository pets,
                             com.clinicavet.assistant.clinicknowledge.domain.Departments departments,
                             java.time.Clock clock) {
        this.staff = staff;
        this.directory = directory;
        this.conversations = conversations;
        this.tutors = tutors;
        this.pets = pets;
        this.departments = departments;
        this.clock = clock;
    }

    /**
     * Pedidos em planilha (S3-18): CSV com ponto e vírgula, que o Excel abre direto. Leva nome do tutor e do pet, então
     * exige também {@code customer:export}. Mesma visibilidade da fila: só os setores do funcionário.
     */
    @GetMapping(value = "/export.csv", produces = "text/csv;charset=UTF-8")
    @PreAuthorize("hasAuthority('request:view') and hasAuthority('customer:export')")
    org.springframework.http.ResponseEntity<String> export(@RequestParam(required = false) Set<RequestStatus> status) {
        StaffUser user = StaffUser.current().orElseThrow();
        Set<RequestStatus> wanted = status == null || status.isEmpty()
                ? java.util.EnumSet.allOf(RequestStatus.class) : status;
        List<RequestResponse> rows = responses(staff.queue(user, null, wanted, false, false));
        Map<UUID, String> sectors = departments.list().stream().collect(Collectors.toMap(
                com.clinicavet.assistant.clinicknowledge.domain.Departments.Department::id,
                com.clinicavet.assistant.clinicknowledge.domain.Departments.Department::name));
        java.time.format.DateTimeFormatter date = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
                .withZone(clock.getZone());
        StringBuilder csv = new StringBuilder(BOM);
        csv.append("Criado em;Tipo;Estado;Setor;Responsável;Tutor;Pet;Resumo;Motivo;Período preferido;Resultado;Observação")
                .append(CRLF);
        for (RequestResponse r : rows.stream().sorted(Comparator.comparing(RequestResponse::createdAt)).toList()) {
            csv.append(String.join(";", java.util.Arrays.asList(
                    date.format(r.createdAt()), RequestLabels.type(r.type()), RequestLabels.status(r.status()),
                    cell(r.departmentId() == null ? null : sectors.get(r.departmentId())), cell(r.assigneeName()),
                    cell(r.tutorName()), cell(r.petName()), cell(r.summary()), cell(r.reason()),
                    cell(r.preferredPeriod()), r.outcome() == null ? "" : RequestLabels.outcome(r.outcome()),
                    cell(r.outcomeNote())))).append(CRLF);
        }
        String file = "pedidos-" + java.time.LocalDate.now(clock) + ".csv";
        return org.springframework.http.ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"" + file + "\"")
                .contentType(org.springframework.http.MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(csv.toString());
    }

    /** Marca de ordem de bytes: o Excel só reconhece UTF-8 (acentos) com ela. */
    private static final String BOM = String.valueOf((char) 0xFEFF);
    private static final String CRLF = String.valueOf((char) 13) + (char) 10;

    /** Célula de CSV: aspas quando precisa; nada começa com = + - @ (o Excel trataria como fórmula). */
    static String cell(String value) {
        if (value == null) {
            return "";
        }
        String clean = value.replace((char) 13, (char) 32).replace((char) 10, (char) 32);
        if (!clean.isEmpty() && "=+-@".indexOf(clean.charAt(0)) >= 0) {
            clean = "'" + clean;
        }
        return clean.contains(";") || clean.contains("\"") ? "\"" + clean.replace("\"", "\"\"") + "\"" : clean;
    }

    /** Fila: mais urgente primeiro e, no mesmo nível, a mais antiga (RF-S31). */
    @GetMapping
    List<RequestResponse> queue(@RequestParam(required = false) UUID departmentId,
                                @RequestParam(required = false) Set<RequestStatus> status,
                                @RequestParam(defaultValue = "false") boolean mine,
                                @RequestParam(defaultValue = "false") boolean unassigned) {
        StaffUser user = StaffUser.current().orElseThrow();
        List<ServiceRequest> found = staff.queue(user, departmentId, status == null ? Set.of() : status, mine,
                unassigned);
        List<RequestResponse> responses = responses(found);
        return responses.stream().sorted(Comparator.comparingInt(RequestResponse::priorityRank)
                .thenComparing(RequestResponse::createdAt)).toList();
    }

    /** D9: corrigir o tipo da demanda (quem atende a solicitação). */
    @PatchMapping("/{id}/demand-type")
    @PreAuthorize("hasAuthority('request:assign') or hasAuthority('request:close')")
    RequestResponse reclassify(@PathVariable UUID id, @Valid @RequestBody DemandTypeRequest body) {
        return one(staff.reclassify(StaffUser.current().orElseThrow(), new ServiceRequestId(id), body.demandType()));
    }

    @GetMapping("/{id}")
    DetailResponse detail(@PathVariable UUID id) {
        StaffServiceRequestService.Detail detail = staff.detail(StaffUser.current().orElseThrow(), new ServiceRequestId(id));
        Set<UUID> people = new HashSet<>();
        detail.events().forEach(e -> {
            if (e.actorId() != null) {
                people.add(e.actorId());
            }
        });
        detail.notes().forEach(n -> {
            if (n.authorId() != null) {
                people.add(n.authorId());
            }
        });
        Map<UUID, String> names = directory.names(people);
        return new DetailResponse(responses(List.of(detail.request())).getFirst(),
                detail.events().stream().map(e -> EventResponse.of(e, names)).toList(),
                detail.notes().stream().map(n -> NoteResponse.of(n, names)).toList());
    }

    @PostMapping("/{id}/assign")
    @PreAuthorize("hasAuthority('request:assign')")
    RequestResponse assign(@PathVariable UUID id, @RequestBody(required = false) AssignRequest body) {
        return one(staff.assign(StaffUser.current().orElseThrow(), new ServiceRequestId(id),
                body == null ? null : body.assigneeId()));
    }

    @PostMapping("/{id}/transfer")
    @PreAuthorize("hasAuthority('request:assign')")
    RequestResponse transfer(@PathVariable UUID id, @Valid @RequestBody TransferRequest body) {
        return one(staff.transfer(StaffUser.current().orElseThrow(), new ServiceRequestId(id), body.departmentId(),
                body.reason()));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('request:assign') or hasAuthority('request:close')")
    RequestResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateRequest body) {
        StaffUser user = StaffUser.current().orElseThrow();
        boolean closing = body.status() == RequestStatus.RESOLVED || body.status() == RequestStatus.CANCELLED;
        if (closing && !user.has(com.clinicavet.assistant.platform.Permission.REQUEST_CLOSE)
                || !closing && !user.has(com.clinicavet.assistant.platform.Permission.REQUEST_ASSIGN)) {
            throw new org.springframework.security.access.AccessDeniedException("permissão");
        }
        return one(staff.update(user, new ServiceRequestId(id), body.status(), body.outcome(), body.outcomeNote()));
    }

    @PostMapping("/{id}/notes")
    @ResponseStatus(HttpStatus.CREATED)
    NoteResponse note(@PathVariable UUID id, @Valid @RequestBody NoteRequest body) {
        StaffUser user = StaffUser.current().orElseThrow();
        InternalNote note = staff.addNote(user, new ServiceRequestId(id), body.text());
        return NoteResponse.of(note, directory.names(Set.of(user.userId())));
    }

    private RequestResponse one(ServiceRequest request) {
        return responses(List.of(request)).getFirst();
    }

    private List<RequestResponse> responses(List<ServiceRequest> found) {
        Map<ConversationId, ConversationFacts.Priority> priorities = conversations.priorities(
                found.stream().map(ServiceRequest::conversationId).collect(Collectors.toSet()));
        Map<UUID, String> names = directory.names(found.stream().map(r -> r.assigneeId().orElse(null))
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet()));
        return found.stream().map(r -> {
            ConversationFacts.Priority priority = priorities.get(r.conversationId());
            String tutorName = r.tutorId().flatMap(tutors::findById).flatMap(Tutor::name).orElse(null);
            String petName = r.petId().flatMap(pets::findById).map(Pet::name).orElse(null);
            return new RequestResponse(r.id().value(), r.conversationId().value(), r.type(), r.status(),
                    r.summary().orElse(null), r.tutorId().map(t -> t.value()).orElse(null), tutorName,
                    r.petId().map(p -> p.value()).orElse(null), petName, r.reason().orElse(null),
                    r.preferredPeriod().orElse(null), r.outcome().orElse(null), r.outcomeNote().orElse(null),
                    r.departmentId().orElse(null), r.assigneeId().orElse(null),
                    r.assigneeId().map(names::get).orElse(null),
                    priority == null ? null : priority.level(), operational(priority, r).rank(),
                    r.createdAt(), r.updatedAt(), nextAction(r),
                    priority == null ? com.clinicavet.assistant.shared.domain.ClinicalRisk.NOT_ASSESSED : priority.risk(), operational(priority, r),
                    r.demandType());
        }).toList();
    }

    /** Próxima ação sugerida pelo estado (RF-S31). */
    private static String nextAction(ServiceRequest r) {
        return switch (r.status()) {
            case COLLECTING -> "aguardar dados do tutor";
            case PENDING -> r.assigneeId().isEmpty() ? "atribuir" : "iniciar";
            case IN_PROGRESS -> "resolver e registrar o resultado";
            case RESOLVED, CANCELLED -> null;
        };
    }

    record AssignRequest(UUID assigneeId) {
    }

    record TransferRequest(@NotNull UUID departmentId, @NotBlank @Size(max = 300) String reason) {
    }

    /**
     * @param status      {@code IN_PROGRESS}, {@code RESOLVED} ou {@code CANCELLED}
     * @param outcome     obrigatório com {@code RESOLVED} e {@code CANCELLED} (DA-34)
     * @param outcomeNote obrigatória com {@code OTHER}. Não escrever dados de saúde nem pessoais além do
     *                    necessário (LGPD)
     */
    record UpdateRequest(@NotNull RequestStatus status, RequestOutcome outcome,
                         @Size(max = ServiceRequest.MAX_SUMMARY_LENGTH) String outcomeNote) {
    }

    record NoteRequest(@NotBlank @Size(max = InternalNote.MAX_TEXT) String text) {
    }

    record RequestResponse(UUID id, UUID conversationId, RequestType type, RequestStatus status, String summary,
                           UUID tutorId, String tutorName, UUID petId, String petName, String reason,
                           String preferredPeriod, RequestOutcome outcome, String outcomeNote, UUID departmentId,
                           UUID assigneeId, String assigneeName, String priority, int priorityRank,
                           Instant createdAt, Instant updatedAt, String nextAction,
                           com.clinicavet.assistant.shared.domain.ClinicalRisk clinicalRisk, com.clinicavet.assistant.shared.domain.OperationalPriority operationalPriority,
                           com.clinicavet.assistant.servicerequest.domain.DemandType demandType) {
    }

    /** D9: prioridade da fila da solicitação = risco da conversa + tipo da demanda. */
    private static com.clinicavet.assistant.shared.domain.OperationalPriority operational(ConversationFacts.Priority priority, ServiceRequest request) {
        com.clinicavet.assistant.shared.domain.ClinicalRisk risk = priority == null ? com.clinicavet.assistant.shared.domain.ClinicalRisk.NOT_ASSESSED : priority.risk();
        return com.clinicavet.assistant.shared.domain.OperationalPriority.derive(risk, request.demandType().lowImpact(), false);
    }

    record DemandTypeRequest(@NotNull com.clinicavet.assistant.servicerequest.domain.DemandType demandType) {
    }

    record EventResponse(RequestEvent.Type type, UUID actorId, String actorName, String from, String to,
                         String reason, Instant at) {
        static EventResponse of(RequestEvent e, Map<UUID, String> names) {
            return new EventResponse(e.type(), e.actorId(), e.actorId() == null ? "Assistente" : names.get(e.actorId()),
                    e.from(), e.to(), e.reason(), e.at());
        }
    }

    record NoteResponse(UUID id, UUID authorId, String authorName, String text, Instant at) {
        static NoteResponse of(InternalNote n, Map<UUID, String> names) {
            return new NoteResponse(n.id(), n.authorId(), names.get(n.authorId()), n.text(), n.at());
        }
    }

    record DetailResponse(RequestResponse request, List<EventResponse> events, List<NoteResponse> notes) {
    }
}
