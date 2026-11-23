package com.clinicavet.assistant.conversation.api;

import com.clinicavet.assistant.conversation.application.MessageChannelInfo;
import com.clinicavet.assistant.conversation.application.StaffConversationService;
import org.springframework.beans.factory.ObjectProvider;
import com.clinicavet.assistant.conversation.domain.ClinicalPriority;
import com.clinicavet.assistant.conversation.domain.Conversation;
import com.clinicavet.assistant.conversation.domain.ConversationStatus;
import com.clinicavet.assistant.conversation.domain.HandoffReason;
import com.clinicavet.assistant.conversation.domain.Intent;
import com.clinicavet.assistant.conversation.domain.Message;
import com.clinicavet.assistant.conversation.domain.MessageAuthor;
import com.clinicavet.assistant.conversation.domain.PriorityChange;
import com.clinicavet.assistant.platform.StaffUser;
import com.clinicavet.assistant.servicerequest.domain.RequestStatus;
import com.clinicavet.assistant.servicerequest.domain.RequestType;
import com.clinicavet.assistant.servicerequest.domain.ServiceRequestRepository;
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
import org.springframework.http.ResponseEntity;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Caixa de entrada da equipe (Etapa S2; RF-S20 a RF-S29). Ver exige {@code conversation:view}; assumir, encerrar e
 * mudar a prioridade, {@code conversation:assume}; responder, {@code conversation:reply}; transferir e devolver ao
 * assistente, {@code conversation:transfer}.
 */
@RestController
@RequestMapping("/app/api/conversations")
@PreAuthorize("hasAuthority('conversation:view')")
class StaffConversationController {

    private final StaffConversationService staff;
    private final StaffDirectory directory;
    private final TutorRepository tutors;
    private final PetRepository pets;
    private final ServiceRequestRepository requests;

    private final ObjectProvider<MessageChannelInfo> channelInfo;

    StaffConversationController(StaffConversationService staff, StaffDirectory directory, TutorRepository tutors,
                                PetRepository pets, ServiceRequestRepository requests,
                                ObjectProvider<MessageChannelInfo> channelInfo) {
        this.channelInfo = channelInfo;
        this.staff = staff;
        this.directory = directory;
        this.tutors = tutors;
        this.pets = pets;
        this.requests = requests;
    }

    @GetMapping
    List<InboxItem> inbox(@RequestParam(defaultValue = "ALL") StaffConversationService.Filter filter) {
        List<StaffConversationService.QueueItem> items = staff.inbox(user(), filter);
        Map<UUID, String> names = directory.names(items.stream().map(i -> i.conversation().assigneeId().orElse(null))
                .filter(Objects::nonNull).collect(Collectors.toSet()));
        return items.stream().map(item -> {
            Conversation c = item.conversation();
            return new InboxItem(c.id().value(), c.status(), c.clinicalPriority(), c.clinicalRisk(),
                    item.operationalPriority(), c.currentIntent().orElse(null),
                    c.handoffReason().orElse(null), c.handoffAt().orElse(null),
                    item.waiting().map(java.time.Duration::toMinutes).orElse(null), item.overdue(),
                    c.lastActivityAt(), c.tutorId().map(t -> t.value()).orElse(null),
                    c.tutorId().flatMap(tutors::findById).flatMap(Tutor::name).orElse(null),
                    c.waitingDepartmentId().orElse(null), c.assigneeId().orElse(null),
                    c.assigneeId().map(names::get).orElse(null));
        }).toList();
    }

    @GetMapping("/{id}")
    DetailResponse detail(@PathVariable UUID id) {
        return detailOf(new ConversationId(id));
    }

    @PostMapping("/{id}/assume")
    @PreAuthorize("hasAuthority('conversation:assume')")
    DetailResponse assume(@PathVariable UUID id) {
        staff.assume(user(), new ConversationId(id));
        return detailOf(new ConversationId(id));
    }

    @PostMapping("/{id}/reply")
    @PreAuthorize("hasAuthority('conversation:reply')")
    DetailResponse reply(@PathVariable UUID id, @Valid @RequestBody TextRequest body) {
        staff.reply(user(), new ConversationId(id), body.text());
        return detailOf(new ConversationId(id));
    }

    @PostMapping("/{id}/return-to-assistant")
    @PreAuthorize("hasAuthority('conversation:transfer')")
    ResponseEntity<DetailResponse> returnToAssistant(@PathVariable UUID id,
                                                    @RequestBody(required = false) ReasonRequest body) {
        staff.returnToAssistant(user(), new ConversationId(id), body == null ? null : body.reason());
        return detailIfVisible(new ConversationId(id));
    }

    @PostMapping("/{id}/transfer")
    @PreAuthorize("hasAuthority('conversation:transfer')")
    ResponseEntity<DetailResponse> transfer(@PathVariable UUID id, @Valid @RequestBody TransferRequest body) {
        staff.transfer(user(), new ConversationId(id), body.departmentId(), body.reason());
        return detailIfVisible(new ConversationId(id));
    }

    @PostMapping("/{id}/notes")
    @ResponseStatus(HttpStatus.CREATED)
    NoteResponse note(@PathVariable UUID id, @Valid @RequestBody TextRequest body) {
        InternalNote note = staff.addNote(user(), new ConversationId(id), body.text());
        return NoteResponse.of(note, directory.names(Set.of(user().userId())));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAuthority('conversation:assume')")
    DetailResponse close(@PathVariable UUID id) {
        staff.close(user(), new ConversationId(id));
        return detailOf(new ConversationId(id));
    }

    /** Único caminho de rebaixamento (H4). Também pode elevar. Fica registrado. */
    @PatchMapping("/{id}/priority")
    @PreAuthorize("hasAuthority('conversation:assume')")
    DetailResponse changePriority(@PathVariable UUID id, @Valid @RequestBody ChangePriorityRequest body) {
        ClinicalPriority target = body.priority() != null ? body.priority() : levelFor(body.risk());
        if (target == null) {
            throw new com.clinicavet.assistant.shared.exception.DomainValidationException("risk",
                    "informe o risco clínico");
        }
        staff.changePriority(user(), new ConversationId(id), target, body.note());
        return detailOf(new ConversationId(id));
    }

    /** D9: risco escolhido pela equipe → nível interno. "Sem sinal clínico" vai para o nível inicial. */
    private static ClinicalPriority levelFor(com.clinicavet.assistant.shared.domain.ClinicalRisk risk) {
        if (risk == null) {
            return null;
        }
        return switch (risk) {
            case POSSIBLE_EMERGENCY -> ClinicalPriority.P1_POSSIBLE_EMERGENCY;
            case URGENT -> ClinicalPriority.P2_URGENT;
            case CLINICAL_FOLLOW_UP -> ClinicalPriority.P3_CLINICAL_FOLLOW_UP;
            case NONE_IDENTIFIED -> ClinicalPriority.INITIAL;
            case NOT_ASSESSED -> throw new com.clinicavet.assistant.shared.exception.DomainValidationException("risk",
                    "escolha um risco avaliado");
        };
    }

    /** D9: prioridade da fila escolhida pela equipe; {@code priority} vazio volta para a regra. Auditado. */
    @PatchMapping("/{id}/queue-priority")
    @PreAuthorize("hasAuthority('conversation:assume')")
    DetailResponse changeQueuePriority(@PathVariable UUID id, @RequestBody QueuePriorityRequest body) {
        staff.changeQueuePriority(user(), new ConversationId(id), body.priority());
        return detailOf(new ConversationId(id));
    }

    record QueuePriorityRequest(com.clinicavet.assistant.shared.domain.OperationalPriority priority) {
    }

    /** Depois de transferir ou devolver, a conversa pode sair do escopo de quem agiu (RN-S11): {@code 204}. */
    private ResponseEntity<DetailResponse> detailIfVisible(ConversationId id) {
        try {
            return ResponseEntity.ok(detailOf(id));
        } catch (com.clinicavet.assistant.shared.exception.ResourceNotFoundException e) {
            return ResponseEntity.noContent().build();
        }
    }

    private DetailResponse detailOf(ConversationId id) {
        StaffConversationService.Detail detail = staff.detail(user(), id);
        Conversation c = detail.conversation();
        Set<UUID> people = new HashSet<>();
        detail.messages().forEach(m -> m.staffUserId().ifPresent(people::add));
        detail.notes().forEach(n -> {
            if (n.authorId() != null) {
                people.add(n.authorId());
            }
        });
        c.assigneeId().ifPresent(people::add);
        Map<UUID, String> names = directory.names(people);
        MessageChannelInfo provider = channelInfo.getIfAvailable();
        Map<UUID, MessageChannelInfo.Info> info = provider == null
                ? Map.of() : provider.forMessages(detail.messages().stream().map(m -> m.id().value()).toList());
        TutorResponse tutor = c.tutorId().flatMap(tutors::findById).map(t -> new TutorResponse(t.id().value(),
                t.name().orElse(null), t.phone().orElse(null),
                pets.findByTutor(t.id()).stream().map(PetResponse::of).toList())).orElse(null);
        List<RequestSummary> openRequests = requests.findByConversation(id).stream()
                .map(r -> new RequestSummary(r.id().value(), r.type(), r.status(), r.summary().orElse(null),
                        r.departmentId().orElse(null), r.assigneeId().orElse(null),
                        r.assigneeId().map(who -> directory.names(Set.of(who)).get(who)).orElse(null)))
                .toList();
        return new DetailResponse(c.id().value(), c.status(), c.clinicalPriority(), c.currentIntent().orElse(null),
                c.handoffReason().orElse(null), c.handoffAt().orElse(null), c.lastActivityAt(),
                c.waitingDepartmentId().orElse(null), c.assigneeId().orElse(null),
                c.assigneeId().map(names::get).orElse(null), tutor,
                detail.messages().stream().map(m -> MessageItem.of(m, names, info.get(m.id().value()))).toList(),
                detail.notes().stream().map(n -> NoteResponse.of(n, names)).toList(),
                openRequests,
                detail.priorityChanges().stream().map(PriorityChangeResponse::from).toList(),
                c.contact().channel(), staff.replyWindowEnd(c).orElse(null), c.clinicalRisk(),
                c.operationalPriority(detail.overdue()), c.operationalOverride().orElse(null));
    }

    private static StaffUser user() {
        return StaffUser.current().orElseThrow();
    }

    record TextRequest(@NotBlank @Size(max = 2000) String text) {
    }

    record ReasonRequest(@Size(max = 300) String reason) {
    }

    record TransferRequest(@NotNull UUID departmentId, @NotBlank @Size(max = 300) String reason) {
    }

    /** @param note motivo da mudança; não escrever dados de saúde nem pessoais além do necessário (LGPD) */
    /** {@code priority} (nível interno, compatibilidade) ou {@code risk} (D9). */
    record ChangePriorityRequest(ClinicalPriority priority, com.clinicavet.assistant.shared.domain.ClinicalRisk risk,
                                 @Size(max = PriorityChange.MAX_NOTE) String note) {
    }

    /**
     * @param waitingMinutes minutos aguardando a equipe ({@code null} se não está aguardando)
     * @param overdue        passou do prazo do nível sem ninguém assumir (prazos provisórios, P-02)
     */
    record InboxItem(UUID id, ConversationStatus status, ClinicalPriority priority, com.clinicavet.assistant.shared.domain.ClinicalRisk clinicalRisk,
                     com.clinicavet.assistant.shared.domain.OperationalPriority operationalPriority, Intent intent,
                     HandoffReason handoffReason, Instant handoffAt, Long waitingMinutes, boolean overdue,
                     Instant lastActivityAt, UUID tutorId, String tutorName, UUID departmentId, UUID assigneeId,
                     String assigneeName) {
    }

    record MessageItem(UUID id, MessageAuthor author, String authorName, String content, Intent intent,
                       boolean fallback, Instant at, MessageChannelInfo.Delivery delivery,
                       MessageChannelInfo.Attachment attachment) {
        static MessageItem of(Message m, Map<UUID, String> names, MessageChannelInfo.Info info) {
            String author = switch (m.author()) {
                case CUSTOMER -> "Tutor";
                case ASSISTANT -> "Assistente";
                case STAFF -> m.staffUserId().map(names::get).orElse("Equipe");
                case BUSINESS_APP -> "Celular da clínica";
            };
            return new MessageItem(m.id().value(), m.author(), author, m.content(), m.intent().orElse(null),
                    m.isFallback(), m.createdAt(), info == null ? null : info.delivery(),
                    info == null ? null : info.attachment());
        }
    }

    record NoteResponse(UUID id, UUID authorId, String authorName, String text, Instant at) {
        static NoteResponse of(InternalNote n, Map<UUID, String> names) {
            return new NoteResponse(n.id(), n.authorId(), n.authorId() == null ? null : names.get(n.authorId()),
                    n.text(), n.at());
        }
    }

    record PetResponse(UUID id, String name, String species, String breed) {
        static PetResponse of(Pet p) {
            return new PetResponse(p.id().value(), p.name(), p.species().map(Enum::name).orElse(null),
                    p.breed().orElse(null));
        }
    }

    record TutorResponse(UUID id, String name, String phone, List<PetResponse> pets) {
    }

    record RequestSummary(UUID id, RequestType type, RequestStatus status, String summary, UUID departmentId,
                          UUID assigneeId, String assigneeName) {
    }

    record PriorityChangeResponse(ClinicalPriority from, ClinicalPriority to, String changedBy, String note,
                                  Instant changedAt) {
        static PriorityChangeResponse from(PriorityChange change) {
            return new PriorityChangeResponse(change.from(), change.to(), change.changedBy(), change.note(),
                    change.changedAt());
        }
    }

    record DetailResponse(UUID id, ConversationStatus status, ClinicalPriority priority, Intent intent,
                          HandoffReason handoffReason, Instant handoffAt, Instant lastActivityAt, UUID departmentId,
                          UUID assigneeId, String assigneeName, TutorResponse tutor, List<MessageItem> messages,
                          List<NoteResponse> notes, List<RequestSummary> requests,
                          List<PriorityChangeResponse> priorityChanges,
                          com.clinicavet.assistant.shared.domain.Channel channel, Instant replyWindowEndsAt,
                          com.clinicavet.assistant.shared.domain.ClinicalRisk clinicalRisk, com.clinicavet.assistant.shared.domain.OperationalPriority operationalPriority,
                          com.clinicavet.assistant.shared.domain.OperationalPriority operationalOverride) {
    }
}
