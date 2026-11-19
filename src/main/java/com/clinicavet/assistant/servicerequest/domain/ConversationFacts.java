package com.clinicavet.assistant.servicerequest.domain;

import com.clinicavet.assistant.shared.domain.ConversationId;

import java.util.Map;
import java.util.Set;

/**
 * Porta para mostrar a prioridade clínica na fila de solicitações (RF-S31). Implementada pelo módulo de atendimento,
 * que é o dono da prioridade (ADR-0018). Sem ela, este módulo dependeria do atendimento e formaria um ciclo.
 */
public interface ConversationFacts {

    /** @param rank 1 = mais urgente (P1) */
    record Priority(String level, int rank) {
    }

    Map<ConversationId, Priority> priorities(Set<ConversationId> conversations);
}
