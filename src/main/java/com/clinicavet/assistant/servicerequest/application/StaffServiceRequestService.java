package com.clinicavet.assistant.servicerequest.application;

import com.clinicavet.assistant.servicerequest.domain.RequestOutcome;
import com.clinicavet.assistant.servicerequest.domain.RequestStatus;
import com.clinicavet.assistant.servicerequest.domain.ServiceRequest;
import com.clinicavet.assistant.servicerequest.domain.ServiceRequestId;
import com.clinicavet.assistant.servicerequest.domain.ServiceRequestRepository;
import com.clinicavet.assistant.shared.exception.DomainValidationException;
import com.clinicavet.assistant.shared.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Operações da equipe sobre solicitações (ADR-0011, DA-34). Não alteram a conversa. Transição inválida vira
 * {@code 409}; desfecho ausente, {@code 422}.
 */
@Service
public class StaffServiceRequestService {

    private final ServiceRequestRepository requests;
    private final Clock clock;

    StaffServiceRequestService(ServiceRequestRepository requests, Clock clock) {
        this.requests = requests;
        this.clock = clock;
    }

    public List<ServiceRequest> list(RequestStatus status) {
        return requests.findByStatus(status);
    }

    /**
     * @param target  {@code IN_PROGRESS}, {@code RESOLVED} ou {@code CANCELLED}
     * @param outcome obrigatório para {@code RESOLVED} e {@code CANCELLED}
     */
    @Transactional
    public ServiceRequest update(ServiceRequestId id, RequestStatus target, RequestOutcome outcome, String note) {
        ServiceRequest request = requests.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Solicitação", id.value()));
        Instant now = clock.instant();
        switch (target) {
            case IN_PROGRESS -> request.startProgress(now);
            case RESOLVED -> request.resolve(outcome, note, now);
            case CANCELLED -> request.cancel(outcome, note, now);
            default -> throw new DomainValidationException("status",
                    "a equipe só pode mudar para IN_PROGRESS, RESOLVED ou CANCELLED");
        }
        return requests.save(request);
    }
}
