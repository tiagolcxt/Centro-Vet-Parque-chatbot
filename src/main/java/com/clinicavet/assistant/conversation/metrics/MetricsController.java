package com.clinicavet.assistant.conversation.metrics;

import com.clinicavet.assistant.conversation.domain.MessageAuthor;
import com.clinicavet.assistant.servicerequest.domain.ServiceRequestRepository;
import com.clinicavet.assistant.shared.exception.DomainValidationException;
import jakarta.persistence.EntityManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Indicadores básicos da tela Início (S3-17, DA-53): para a clínica ver o valor desde a primeira semana. Qualquer
 * funcionário logado vê; são contagens, sem conteúdo de mensagem nem dado de tutor.
 * <p>
 * A consulta é JPQL, então o {@code @TenantId} filtra a clínica (ADR-0021).
 */
@RestController
@RequestMapping("/app/api/metrics")
class MetricsController {

    static final int MAX_DAYS = 90;
    /** Teto de mensagens lidas por consulta; acima disso o tempo de resposta usa uma amostra. */
    static final int MAX_ROWS = 50_000;

    /**
     * @param received             mensagens de tutores no período
     * @param conversations        conversas com mensagem de tutor no período
     * @param answered             dessas, quantas tiveram resposta (do assistente ou da equipe)
     * @param answeredByAssistant  respondidas só pelo assistente, sem funcionário
     * @param medianFirstReplySeconds mediana do tempo até a primeira resposta; {@code null} sem dados
     */
    record Basic(LocalDate from, LocalDate to, long received, long conversations, long answered,
                 long answeredByAssistant, Long medianFirstReplySeconds, long requestsCreated, long requestsResolved,
                 long requestsOpen) {
    }

    /**
     * Operação da equipe (RF-S71, Etapa S5).
     *
     * @param waitingNow            conversas aguardando a equipe agora
     * @param medianAssignSeconds   mediana até um funcionário pegar a solicitação, nas abertas no período
     * @param medianResolveSeconds  mediana até resolver, nas abertas no período
     * @param openByDepartment      solicitações abertas agora, por setor
     * @param activationLevel       nível do assistente na clínica (ADR-0025), para a equipe saber o que ele faz
     */
    record Operations(LocalDate from, LocalDate to, long waitingNow, Long medianAssignSeconds,
                      Long medianResolveSeconds, List<DepartmentCount> openByDepartment, int activationLevel,
                      String activationLabel) {
    }

    record DepartmentCount(UUID departmentId, String name, long open) {
    }

    private final EntityManager entities;
    private final ServiceRequestRepository requests;
    private final com.clinicavet.assistant.clinicknowledge.domain.Departments departments;
    private final com.clinicavet.assistant.platform.Clinics clinics;
    private final Clock clock;

    MetricsController(EntityManager entities, ServiceRequestRepository requests,
                      com.clinicavet.assistant.clinicknowledge.domain.Departments departments,
                      com.clinicavet.assistant.platform.Clinics clinics, Clock clock) {
        this.entities = entities;
        this.requests = requests;
        this.departments = departments;
        this.clinics = clinics;
        this.clock = clock;
    }

    @GetMapping("/operations")
    @Transactional(readOnly = true)
    Operations operations(@RequestParam(defaultValue = "7") int days) {
        if (days < 1 || days > MAX_DAYS) {
            throw new DomainValidationException("days", "de 1 a " + MAX_DAYS);
        }
        LocalDate today = LocalDate.now(clock);
        LocalDate first = today.minusDays(days - 1L);
        Instant from = first.atStartOfDay(clock.getZone()).toInstant();
        Instant to = today.plusDays(1).atStartOfDay(clock.getZone()).toInstant();
        long waiting = entities.createQuery("SELECT COUNT(c) FROM ConversationEntity c WHERE c.status = :status",
                        Long.class)
                .setParameter("status", com.clinicavet.assistant.conversation.domain.ConversationStatus.WAITING_HUMAN)
                .getSingleResult();
        ServiceRequestRepository.Timings timings = requests.timings(from, to);
        UUID fallback = departments.defaultDepartment().id();
        Map<UUID, Long> open = new java.util.LinkedHashMap<>();
        timings.openByDepartment().forEach((id, count) -> open.merge(id == null ? fallback : id, count, Long::sum));
        List<DepartmentCount> byDepartment = departments.list().stream()
                .filter(d -> d.active() || open.containsKey(d.id()))
                .map(d -> new DepartmentCount(d.id(), d.name(), open.getOrDefault(d.id(), 0L))).toList();
        com.clinicavet.assistant.platform.ActivationLevel level = clinics.activationLevel(
                com.clinicavet.assistant.platform.CurrentClinic.require());
        return new Operations(first, today, waiting, timings.medianAssignSeconds(), timings.medianResolveSeconds(),
                byDepartment, level.number(), level.label());
    }

    @GetMapping("/basic")
    @Transactional(readOnly = true)
    Basic basic(@RequestParam(defaultValue = "7") int days) {
        if (days < 1 || days > MAX_DAYS) {
            throw new DomainValidationException("days", "de 1 a " + MAX_DAYS);
        }
        LocalDate today = LocalDate.now(clock);
        LocalDate first = today.minusDays(days - 1L);
        Instant from = first.atStartOfDay(clock.getZone()).toInstant();
        Instant to = today.plusDays(1).atStartOfDay(clock.getZone()).toInstant();

        List<Object[]> rows = entities.createQuery("""
                        SELECT m.conversationId, m.author, m.createdAt FROM MessageEntity m
                        WHERE m.createdAt >= :from AND m.createdAt < :to
                        ORDER BY m.createdAt ASC
                        """, Object[].class)
                .setParameter("from", from).setParameter("to", to).setMaxResults(MAX_ROWS)
                .getResultList();

        record Thread(Instant firstCustomer, Instant firstReply, boolean staff) {
        }
        Map<UUID, Thread> threads = new HashMap<>();
        long received = 0;
        for (Object[] row : rows) {
            UUID conversation = (UUID) row[0];
            MessageAuthor author = (MessageAuthor) row[1];
            Instant at = (Instant) row[2];
            Thread current = threads.get(conversation);
            if (author == MessageAuthor.CUSTOMER) {
                received++;
                if (current == null) {
                    threads.put(conversation, new Thread(at, null, false));
                }
            } else if (current != null) {
                threads.put(conversation, new Thread(current.firstCustomer(),
                        current.firstReply() == null ? at : current.firstReply(),
                        current.staff() || author == MessageAuthor.STAFF));
            }
        }
        List<Long> waits = new ArrayList<>();
        long answered = 0;
        long assistantOnly = 0;
        for (Thread thread : threads.values()) {
            if (thread.firstReply() != null) {
                answered++;
                if (!thread.staff()) {
                    assistantOnly++;
                }
                waits.add(Duration.between(thread.firstCustomer(), thread.firstReply()).toSeconds());
            }
        }
        waits.sort(Long::compare);
        Long median = waits.isEmpty() ? null : waits.get(waits.size() / 2);
        ServiceRequestRepository.Counts counts = requests.count(from, to);
        return new Basic(first, today, received, threads.size(), answered, assistantOnly, median, counts.created(),
                counts.resolved(), counts.open());
    }
}
