package com.clinicavet.assistant.servicerequest.api;

import com.clinicavet.assistant.servicerequest.application.StaffServiceRequestService;
import com.clinicavet.assistant.servicerequest.domain.RequestOutcome;
import com.clinicavet.assistant.servicerequest.domain.RequestStatus;
import com.clinicavet.assistant.servicerequest.domain.RequestType;
import com.clinicavet.assistant.servicerequest.domain.ServiceRequest;
import com.clinicavet.assistant.servicerequest.domain.ServiceRequestId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Solicitações para a equipe (ADR-0011). Uma solicitação nunca é um agendamento confirmado. Atualizar uma
 * solicitação não altera a conversa.
 */
@RestController
@RequestMapping("/api/service-requests")
class ServiceRequestController {

    private final StaffServiceRequestService staff;

    ServiceRequestController(StaffServiceRequestService staff) {
        this.staff = staff;
    }

    @GetMapping
    List<ServiceRequestResponse> list(@RequestParam(defaultValue = "PENDING") RequestStatus status) {
        return staff.list(status).stream().map(ServiceRequestResponse::from).toList();
    }

    @PatchMapping("/{id}")
    ServiceRequestResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateRequest body) {
        return ServiceRequestResponse.from(
                staff.update(new ServiceRequestId(id), body.status(), body.outcome(), body.outcomeNote()));
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

    record ServiceRequestResponse(UUID id, UUID conversationId, RequestType type, RequestStatus status, String summary,
                                  RequestOutcome outcome, String outcomeNote, Instant createdAt, Instant updatedAt) {

        static ServiceRequestResponse from(ServiceRequest r) {
            return new ServiceRequestResponse(r.id().value(), r.conversationId().value(), r.type(), r.status(),
                    r.summary().orElse(null), r.outcome().orElse(null), r.outcomeNote().orElse(null),
                    r.createdAt(), r.updatedAt());
        }
    }
}
