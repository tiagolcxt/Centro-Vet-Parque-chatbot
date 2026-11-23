package com.clinicavet.assistant.alert.api;

import com.clinicavet.assistant.alert.ForgottenWork;
import com.clinicavet.assistant.alert.application.AlertService;
import com.clinicavet.assistant.platform.StaffUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Alertas de trabalho esquecido para a equipe (E3). Qualquer pessoa logada vê os do seu setor e os escalados. */
@RestController
@RequestMapping("/app/api/alerts")
class AlertController {

    /**
     * @param subjectId      conversa ou solicitação
     * @param waitingMinutes há quanto tempo está pendente
     * @param escalated      ninguém confirmou no prazo: aparece para toda a equipe
     */
    record AlertItem(UUID id, ForgottenWork.Kind kind, UUID subjectId, String title, String what, Instant since,
                     long waitingMinutes, Instant raisedAt, boolean escalated, String acknowledgedByName,
                     Instant acknowledgedAt) {
    }

    private final AlertService alerts;
    private final Clock clock;

    AlertController(AlertService alerts, Clock clock) {
        this.alerts = alerts;
        this.clock = clock;
    }

    @GetMapping
    List<AlertItem> list() {
        Instant now = clock.instant();
        return alerts.visibleTo(user()).stream().map(v -> new AlertItem(v.alert().id(), v.alert().kind(),
                v.alert().subjectId(), v.pending().title(), v.pending().what(), v.pending().since(),
                v.waiting(now).toMinutes(), v.alert().raisedAt(), v.alert().isEscalated(), v.acknowledgedByName(),
                v.alert().acknowledgedAt().orElse(null))).toList();
    }

    @PostMapping("/{id}/acknowledge")
    void acknowledge(@PathVariable UUID id) {
        alerts.acknowledge(user(), id);
    }

    private static StaffUser user() {
        return StaffUser.current().orElseThrow();
    }
}
