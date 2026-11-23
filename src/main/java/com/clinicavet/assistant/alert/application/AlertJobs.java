package com.clinicavet.assistant.alert.application;

import com.clinicavet.assistant.platform.Clinics;
import com.clinicavet.assistant.platform.CurrentClinic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Varredura de trabalho esquecido em cada clínica ativa. Desligada com {@code assistant.alerts.scheduling=false} (os
 * testes chamam {@link AlertService#sweep()} direto, com o relógio de teste).
 */
@Component
class AlertJobs {

    private static final Logger log = LoggerFactory.getLogger(AlertJobs.class);

    private final AlertService alerts;
    private final Clinics clinics;
    private final boolean scheduling;

    AlertJobs(AlertService alerts, Clinics clinics, @Value("${assistant.alerts.scheduling:true}") boolean scheduling) {
        this.alerts = alerts;
        this.clinics = clinics;
        this.scheduling = scheduling;
    }

    @Scheduled(fixedDelayString = "${assistant.alerts.sweep-interval:1m}", initialDelayString = "20s")
    void sweep() {
        if (!scheduling) {
            return;
        }
        for (var clinic : clinics.active()) {
            try {
                CurrentClinic.run(clinic, alerts::sweep);
            } catch (RuntimeException e) {
                log.warn("Varredura de alertas falhou numa clínica; tenta de novo no próximo ciclo", e);
            }
        }
    }
}
