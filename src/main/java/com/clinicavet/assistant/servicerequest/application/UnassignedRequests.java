package com.clinicavet.assistant.servicerequest.application;

import com.clinicavet.assistant.alert.ForgottenWork;
import com.clinicavet.assistant.clinicknowledge.domain.Departments;
import com.clinicavet.assistant.servicerequest.domain.RequestLabels;
import com.clinicavet.assistant.servicerequest.domain.RequestStatus;
import com.clinicavet.assistant.servicerequest.domain.RequestType;
import com.clinicavet.assistant.servicerequest.domain.ServiceRequest;
import com.clinicavet.assistant.servicerequest.domain.ServiceRequestRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Solicitações pendentes que ninguém pegou (E3, achado A10). Prazo único e provisório
 * ({@code assistant.alerts.request-unassigned-after}, padrão 24 h) até a clínica decidir (D4).
 * <p>
 * "Atendimento humano" de uma conversa que está aguardando a equipe fica de fora: é o mesmo trabalho, e a conversa já
 * tem alerta próprio. Se a conversa voltou ao assistente ou foi encerrada e ninguém pegou a solicitação, ela alerta.
 */
@Component
class UnassignedRequests implements ForgottenWork {

    static final int MAX = 300;
    static final int MAX_WHAT = 80;

    private final ServiceRequestRepository requests;
    private final Departments departments;
    private final com.clinicavet.assistant.servicerequest.domain.ConversationFacts conversations;
    private final Duration deadline;

    UnassignedRequests(ServiceRequestRepository requests, Departments departments,
                       com.clinicavet.assistant.servicerequest.domain.ConversationFacts conversations,
                       @Value("${assistant.alerts.request-unassigned-after:24h}") Duration deadline) {
        this.requests = requests;
        this.conversations = conversations;
        this.departments = departments;
        this.deadline = deadline;
    }

    @Override
    public Kind kind() {
        return Kind.REQUEST_UNASSIGNED;
    }

    @Override
    public List<Pending> pending(Instant now) {
        java.util.Set<UUID> all = departments.list().stream().map(Departments.Department::id).collect(Collectors.toSet());
        List<ServiceRequest> found = requests.findQueue(all, EnumSet.of(RequestStatus.PENDING), null, true, MAX);
        java.util.Set<com.clinicavet.assistant.shared.domain.ConversationId> waiting = conversations.waitingForStaff(
                found.stream().filter(r -> r.type() == RequestType.HUMAN_CONTACT).map(ServiceRequest::conversationId)
                        .collect(Collectors.toSet()));
        return found.stream()
                .filter(r -> !(r.type() == RequestType.HUMAN_CONTACT && waiting.contains(r.conversationId())))
                .map(r -> new Pending(r.id().value(), r.createdAt(), deadline, r.departmentId().orElse(null),
                        RequestLabels.type(r.type()), what(r)))
                .toList();
    }

    private static String what(ServiceRequest r) {
        String text = r.summary().orElse("").replaceFirst("^Pedido de agendamento — ", "").strip();
        return text.length() <= MAX_WHAT ? text : text.substring(0, MAX_WHAT - 1) + "…";
    }
}
