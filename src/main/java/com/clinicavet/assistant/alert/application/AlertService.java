package com.clinicavet.assistant.alert.application;

import com.clinicavet.assistant.alert.ForgottenWork;
import com.clinicavet.assistant.alert.domain.WorkAlert;
import com.clinicavet.assistant.alert.domain.WorkAlertRepository;
import com.clinicavet.assistant.platform.AuditLog;
import com.clinicavet.assistant.platform.LiveUpdates;
import com.clinicavet.assistant.platform.StaffUser;
import com.clinicavet.assistant.shared.exception.ResourceNotFoundException;
import com.clinicavet.assistant.team.StaffDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Alerta de trabalho esquecido (E3, achado A10). <b>Provisório até D4</b>: usa os prazos por nível já existentes
 * (P-02) e, no lugar da escala de plantão, escala para toda a equipe.
 * <ul>
 *   <li><b>Varredura</b> (a cada minuto, por clínica): pendência além do prazo ganha um alerta; alerta sem "Recebi"
 *       por mais um prazo (no mínimo {@link #MINIMUM_GAP}) escala; pendência que acabou fecha o alerta.</li>
 *   <li><b>Quem vê:</b> antes de escalar, quem trabalha no setor da pendência; escalado, toda a equipe.</li>
 *   <li><b>Ao tutor nada muda</b>: o alerta é interno. O tutor nunca ouve "avisamos a equipe" por causa dele.</li>
 * </ul>
 * Tudo vai para o registro de atividades.
 */
@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    /** Intervalo mínimo entre o alerta e o escalonamento, para prazos curtos (emergência: 15 min). */
    static final Duration MINIMUM_GAP = Duration.ofMinutes(15);

    /** Um alerta aberto com a pendência atual. */
    public record Visible(WorkAlert alert, ForgottenWork.Pending pending, String acknowledgedByName) {

        public Duration waiting(Instant now) {
            return Duration.between(pending.since(), now);
        }
    }

    private final List<ForgottenWork> sources;
    private final WorkAlertRepository alerts;
    private final StaffDirectory staff;
    private final AuditLog audit;
    private final LiveUpdates live;
    private final Clock clock;

    AlertService(List<ForgottenWork> sources, WorkAlertRepository alerts, StaffDirectory staff, AuditLog audit,
                 LiveUpdates live, Clock clock) {
        this.sources = sources;
        this.alerts = alerts;
        this.staff = staff;
        this.audit = audit;
        this.live = live;
        this.clock = clock;
    }

    /** Varre a clínica atual. @return quantos alertas mudaram */
    @Transactional
    public int sweep() {
        Instant now = clock.instant();
        Map<Key, WorkAlert> open = alerts.findOpen().stream()
                .collect(Collectors.toMap(a -> new Key(a.kind(), a.subjectId(), a.pendingSince()), a -> a));
        int changed = 0;
        Set<Key> stillPending = new java.util.HashSet<>();
        for (ForgottenWork source : sources) {
            for (ForgottenWork.Pending pending : source.pending(now)) {
                Key key = new Key(source.kind(), pending.subjectId(), normalize(pending.since()));
                stillPending.add(key);
                WorkAlert alert = open.get(key);
                if (alert == null) {
                    if (overdue(pending, now) && !alerts.exists(key.kind(), key.subjectId(), key.since())) {
                        alert = alerts.save(WorkAlert.raise(key.kind(), key.subjectId(), key.since(), now));
                        record("alert.raised", alert, "Esperando além do prazo: " + describe(pending));
                        changed++;
                    }
                } else if (alert.escalateIfDue(gap(pending.deadline()), now)) {
                    alerts.save(alert);
                    record("alert.escalated", alert, "Sem resposta da equipe; alerta para toda a equipe: "
                            + describe(pending));
                    changed++;
                }
            }
        }
        for (Map.Entry<Key, WorkAlert> entry : open.entrySet()) {
            if (!stillPending.contains(entry.getKey())) {
                WorkAlert alert = entry.getValue();
                alert.close(now);
                alerts.save(alert);
                record("alert.closed", alert, "A pendência acabou");
                changed++;
            }
        }
        if (changed > 0) {
            log.info("Alertas de trabalho esquecido [mudancas={}]", changed);
        }
        return changed;
    }

    /** Alertas abertos que a pessoa vê, com a pendência atual. Pendência que já acabou não aparece. */
    @Transactional(readOnly = true)
    public List<Visible> visibleTo(StaffUser user) {
        Instant now = clock.instant();
        Map<Key, ForgottenWork.Pending> pending = new HashMap<>();
        for (ForgottenWork source : sources) {
            for (ForgottenWork.Pending p : source.pending(now)) {
                pending.put(new Key(source.kind(), p.subjectId(), normalize(p.since())), p);
            }
        }
        Set<UUID> departments = staff.visibleDepartments(user);
        List<Visible> result = new ArrayList<>();
        List<WorkAlert> open = alerts.findOpen();
        Map<UUID, String> names = staff.names(open.stream().map(a -> a.acknowledgedBy().orElse(null))
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet()));
        for (WorkAlert alert : open) {
            ForgottenWork.Pending p = pending.get(new Key(alert.kind(), alert.subjectId(), alert.pendingSince()));
            if (p == null) {
                continue;
            }
            // Só quem pode abrir o que o alerta aponta; antes de escalar, só o setor dele.
            com.clinicavet.assistant.platform.Permission needed = alert.kind() == ForgottenWork.Kind.CONVERSATION_WAITING
                    ? com.clinicavet.assistant.platform.Permission.CONVERSATION_VIEW
                    : com.clinicavet.assistant.platform.Permission.REQUEST_VIEW;
            boolean sees = user.has(needed) && (alert.isEscalated() || p.departmentId() == null
                    || departments.contains(p.departmentId()));
            if (sees) {
                result.add(new Visible(alert, p, alert.acknowledgedBy().map(names::get).orElse(null)));
            }
        }
        // Escalados primeiro; depois quem espera há mais tempo.
        result.sort(java.util.Comparator.comparing((Visible v) -> !v.alert().isEscalated())
                .thenComparing(v -> v.pending().since()));
        return result;
    }

    /** "Recebi". Só quem vê o alerta pode confirmar. */
    @Transactional
    public WorkAlert acknowledge(StaffUser user, UUID id) {
        Optional<Visible> visible = visibleTo(user).stream().filter(v -> v.alert().id().equals(id)).findFirst();
        if (visible.isEmpty()) {
            throw new ResourceNotFoundException("Alerta", id);
        }
        WorkAlert alert = visible.get().alert();
        alert.acknowledge(user.userId(), clock.instant());
        WorkAlert saved = alerts.save(alert);
        record("alert.acknowledged", saved, "Recebi: " + describe(visible.get().pending()));
        return saved;
    }

    private void record(String action, WorkAlert alert, String summary) {
        String target = alert.kind() == ForgottenWork.Kind.CONVERSATION_WAITING ? "conversation" : "request";
        audit.record(action, target, alert.subjectId(), summary);
        live.changed(LiveUpdates.Topic.ALERT, alert.id());
    }

    private static String describe(ForgottenWork.Pending pending) {
        return pending.title() + (pending.what() == null || pending.what().isBlank() ? "" : " (" + pending.what() + ")");
    }

    private static boolean overdue(ForgottenWork.Pending pending, Instant now) {
        return Duration.between(pending.since(), now).compareTo(pending.deadline()) > 0;
    }

    static Duration gap(Duration deadline) {
        return deadline.compareTo(MINIMUM_GAP) < 0 ? MINIMUM_GAP : deadline;
    }

    private static Instant normalize(Instant at) {
        return com.clinicavet.assistant.shared.domain.Timestamps.normalize(at);
    }

    private record Key(ForgottenWork.Kind kind, UUID subjectId, Instant since) {
    }
}
