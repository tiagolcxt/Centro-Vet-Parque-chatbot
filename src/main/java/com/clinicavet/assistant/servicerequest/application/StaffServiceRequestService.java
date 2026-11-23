package com.clinicavet.assistant.servicerequest.application;

import com.clinicavet.assistant.clinicknowledge.domain.Departments;
import com.clinicavet.assistant.platform.AccountDisabled;
import com.clinicavet.assistant.platform.LiveUpdates;
import com.clinicavet.assistant.platform.SectorScope;
import com.clinicavet.assistant.platform.StaffUser;
import com.clinicavet.assistant.servicerequest.domain.RequestEvent;
import com.clinicavet.assistant.servicerequest.domain.RequestOutcome;
import com.clinicavet.assistant.servicerequest.domain.RequestStatus;
import com.clinicavet.assistant.servicerequest.domain.ServiceRequest;
import com.clinicavet.assistant.servicerequest.domain.ServiceRequestId;
import com.clinicavet.assistant.servicerequest.domain.ServiceRequestRepository;
import com.clinicavet.assistant.shared.domain.InternalNote;
import com.clinicavet.assistant.shared.exception.DomainValidationException;
import com.clinicavet.assistant.shared.exception.ResourceNotFoundException;
import com.clinicavet.assistant.team.StaffDirectory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Operações da equipe sobre solicitações (ADR-0011, DA-34; Etapa S2: RF-S30 a RF-S36). Não alteram a conversa.
 * <ul>
 *   <li>Escopo de setor (RN-S11): quem tem "meus setores" só vê os setores dele e o que está com ele;</li>
 *   <li>toda mudança de setor, responsável ou estado vira um {@link RequestEvent};</li>
 *   <li>transição inválida vira {@code 409}; desfecho ausente, {@code 422}; fora do escopo, {@code 404}.</li>
 * </ul>
 */
@Service
public class StaffServiceRequestService {

    public static final int MAX_QUEUE = 200;

    public record Detail(ServiceRequest request, List<RequestEvent> events, List<InternalNote> notes) {
    }

    private final ServiceRequestRepository requests;
    private final Departments departments;
    private final StaffDirectory staff;
    private final LiveUpdates live;
    private final Clock clock;
    private final com.clinicavet.assistant.platform.AuditLog audit;

    StaffServiceRequestService(ServiceRequestRepository requests, Departments departments, StaffDirectory staff,
                               LiveUpdates live, Clock clock, com.clinicavet.assistant.platform.AuditLog audit) {
        this.audit = audit;
        this.requests = requests;
        this.departments = departments;
        this.staff = staff;
        this.live = live;
        this.clock = clock;
    }

    /**
     * Fila de trabalho do funcionário.
     *
     * @param department um setor, ou {@code null} para todos os visíveis
     * @param mine       só as do próprio funcionário
     * @param unassigned só as sem responsável
     */
    @Transactional(readOnly = true)
    public List<ServiceRequest> queue(StaffUser user, UUID department, Set<RequestStatus> statuses, boolean mine,
                                      boolean unassigned) {
        Set<UUID> visible = visibleDepartments(user);
        Set<UUID> target = department == null ? visible : visible.contains(department) ? Set.of(department) : Set.of();
        Set<RequestStatus> wanted = statuses.isEmpty()
                ? EnumSet.of(RequestStatus.PENDING, RequestStatus.IN_PROGRESS) : statuses;
        List<ServiceRequest> found = requests.findQueue(target, wanted, mine ? user.userId() : null, unassigned,
                MAX_QUEUE);
        if (mine && department == null) {
            // O que está comigo aparece mesmo se já saiu dos meus setores.
            List<ServiceRequest> assigned = requests.findOpenAssignedTo(user.userId()).stream()
                    .filter(r -> wanted.contains(r.status())).toList();
            return java.util.stream.Stream.concat(found.stream(), assigned.stream())
                    .collect(Collectors.toMap(r -> r.id(), r -> r, (a, b) -> a, java.util.LinkedHashMap::new))
                    .values().stream().toList();
        }
        return found;
    }

    @Transactional(readOnly = true)
    public Detail detail(StaffUser user, ServiceRequestId id) {
        ServiceRequest request = visible(user, id);
        return new Detail(request, requests.findEvents(id), requests.findNotes(id));
    }

    /** Atribui a alguém do setor (ou a si mesmo). {@code assignee} vazio = quem pediu. */
    @Transactional
    public ServiceRequest assign(StaffUser user, ServiceRequestId id, UUID assignee) {
        ServiceRequest request = visible(user, id);
        UUID target = assignee == null ? user.userId() : assignee;
        StaffDirectory.Member member = staff.find(target).filter(StaffDirectory.Member::active)
                .orElseThrow(() -> new DomainValidationException("assigneeId", "funcionário inexistente ou inativo"));
        UUID department = request.departmentId().orElseThrow();
        if (!target.equals(user.userId()) && !member.departmentIds().contains(department)) {
            throw new DomainValidationException("assigneeId", "o funcionário não trabalha neste setor");
        }
        UUID previous = request.assigneeId().orElse(null);
        if (target.equals(previous)) {
            return request;
        }
        Instant now = clock.instant();
        request.assign(target, now);
        ServiceRequest saved = requests.save(request);
        requests.saveEvent(RequestEvent.of(id, RequestEvent.Type.ASSIGNED, user.userId(), previous, target, null, now));
        audit.record("request.assigned", "request", id.value(),
                target.equals(user.userId()) ? "pegou para si" : "atribuiu a " + staff.find(target)
                        .map(StaffDirectory.Member::displayName).orElse("outra pessoa"));
        live.changed(LiveUpdates.Topic.REQUEST, id.value());
        return saved;
    }

    /** Transfere de setor com motivo obrigatório (RN-S06). Limpa o responsável. */
    private String departmentName(UUID id) {
        return departments.find(id).map(Departments.Department::name).orElse("setor removido");
    }

    @Transactional
    public ServiceRequest transfer(StaffUser user, ServiceRequestId id, UUID department, String reason) {
        ServiceRequest request = visible(user, id);
        if (reason == null || reason.isBlank()) {
            throw new DomainValidationException("reason", "obrigatório ao transferir");
        }
        if (departments.activeAmong(Set.of(department)).isEmpty()) {
            throw new DomainValidationException("departmentId", "setor inexistente ou inativo");
        }
        UUID from = request.departmentId().orElseThrow();
        Instant now = clock.instant();
        request.transfer(department, now);
        ServiceRequest saved = requests.save(request);
        requests.saveEvent(RequestEvent.of(id, RequestEvent.Type.TRANSFERRED, user.userId(), from, department,
                truncate(reason.strip()), now));
        audit.record("request.transferred", "request", id.value(), "de " + departmentName(from) + " para "
                + departmentName(department));
        live.changed(LiveUpdates.Topic.REQUEST, id.value());
        return saved;
    }

    /**
     * @param target  {@code IN_PROGRESS}, {@code RESOLVED} ou {@code CANCELLED}. Em andamento sem responsável =
     *                assumida por quem pediu
     * @param outcome obrigatório para {@code RESOLVED} e {@code CANCELLED}
     */
    @Transactional
    public ServiceRequest update(StaffUser user, ServiceRequestId id, RequestStatus target, RequestOutcome outcome,
                                 String note) {
        ServiceRequest request = visible(user, id);
        Instant now = clock.instant();
        RequestStatus from = request.status();
        switch (target) {
            case IN_PROGRESS -> {
                if (request.assigneeId().isEmpty()) {
                    request.assign(user.userId(), now);
                    requests.saveEvent(RequestEvent.of(id, RequestEvent.Type.ASSIGNED, user.userId(), null,
                            user.userId(), null, now));
                }
                request.startProgress(now);
            }
            case RESOLVED -> request.resolve(outcome, note, now);
            case CANCELLED -> request.cancel(outcome, note, now);
            default -> throw new DomainValidationException("status",
                    "a equipe só pode mudar para IN_PROGRESS, RESOLVED ou CANCELLED");
        }
        ServiceRequest saved = requests.save(request);
        requests.saveEvent(RequestEvent.of(id, RequestEvent.Type.STATUS_CHANGED, user.userId(), from, target, null, now));
        audit.record("request.status_changed", "request", id.value(), from + " -> " + target
                + (outcome == null ? "" : " (" + outcome + ")"));
        live.changed(LiveUpdates.Topic.REQUEST, id.value());
        return saved;
    }

    @Transactional
    public InternalNote addNote(StaffUser user, ServiceRequestId id, String text) {
        visible(user, id);
        InternalNote note = InternalNote.by(user.userId(), text, clock.instant());
        requests.saveNote(id, note);
        live.changed(LiveUpdates.Topic.REQUEST, id.value());
        return note;
    }

    /** RN-S07: o que estava com quem foi desativado volta ao setor, sem responsável. */
    @EventListener
    void onAccountDisabled(AccountDisabled event) {
        Instant now = clock.instant();
        for (ServiceRequest request : requests.findOpenAssignedTo(event.userId())) {
            request.unassign(now);
            requests.save(request);
            requests.saveEvent(RequestEvent.of(request.id(), RequestEvent.Type.UNASSIGNED, null, event.userId(), null,
                    null, now));
            live.changed(LiveUpdates.Topic.REQUEST, request.id().value());
        }
    }

    /** Setores que o funcionário enxerga (RN-S11). */
    private Set<UUID> visibleDepartments(StaffUser user) {
        return staff.visibleDepartments(user);
    }

    /** Fora do escopo = {@code 404}: não revela que existe. */
    private ServiceRequest visible(StaffUser user, ServiceRequestId id) {
        ServiceRequest request = requests.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Solicitação", id.value()));
        boolean mine = request.assigneeId().map(user.userId()::equals).orElse(false);
        boolean inScope = request.departmentId().map(visibleDepartments(user)::contains).orElse(false);
        if (!mine && !inScope) {
            throw new ResourceNotFoundException("Solicitação", id.value());
        }
        return request;
    }

    private static String truncate(String text) {
        return text.length() <= RequestEvent.MAX_REASON ? text : text.substring(0, RequestEvent.MAX_REASON);
    }
}
