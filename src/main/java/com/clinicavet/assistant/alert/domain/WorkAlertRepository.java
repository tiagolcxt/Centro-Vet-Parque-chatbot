package com.clinicavet.assistant.alert.domain;

import com.clinicavet.assistant.alert.ForgottenWork;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Alertas da clínica atual (ADR-0021: filtrados pelo tenant). */
public interface WorkAlertRepository {

    WorkAlert save(WorkAlert alert);

    Optional<WorkAlert> findById(UUID id);

    List<WorkAlert> findOpen();

    /** Já houve alerta para este episódio (mesmo que fechado)? */
    boolean exists(ForgottenWork.Kind kind, UUID subjectId, Instant pendingSince);
}
