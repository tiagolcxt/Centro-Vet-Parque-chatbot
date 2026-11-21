package com.clinicavet.assistant.clinicknowledge.infrastructure;

import com.clinicavet.assistant.clinicknowledge.domain.ClinicConfig;
import com.clinicavet.assistant.clinicknowledge.domain.ClinicKnowledge;
import com.clinicavet.assistant.clinicknowledge.domain.SimulatedKnowledge;
import com.clinicavet.assistant.platform.ClinicId;
import com.clinicavet.assistant.platform.CurrentClinic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A base que o assistente lê (ADR-0026 item 4): a versão publicada da clínica atual, em cache por clínica. Publicar
 * limpa o cache da clínica depois do commit. No simulador, vale o rascunho ({@link SimulatedKnowledge}).
 * <p>
 * Clínica sem versão publicada: base vazia. O assistente trata tudo como desconhecido e encaminha à equipe.
 */
@Component
class PublishedKnowledge {

    private static final Logger log = LoggerFactory.getLogger(PublishedKnowledge.class);

    private final ConfigVersionJpaRepository versions;
    private final Clock clock;
    private final Map<UUID, ClinicKnowledge> cache = new ConcurrentHashMap<>();

    PublishedKnowledge(ConfigVersionJpaRepository versions, Clock clock) {
        this.versions = versions;
        this.clock = clock;
    }

    ClinicKnowledge current() {
        var simulated = SimulatedKnowledge.current();
        if (simulated.isPresent()) {
            return simulated.get();
        }
        return CurrentClinic.get().map(this::forClinic).orElseGet(ClinicKnowledge::empty);
    }

    private ClinicKnowledge forClinic(ClinicId clinic) {
        return cache.computeIfAbsent(clinic.value(), id -> load());
    }

    private ClinicKnowledge load() {
        return versions.findByStatus(ClinicConfig.Status.PUBLISHED)
                .map(v -> {
                    try {
                        return KnowledgeDocument.read(KnowledgeDocument.fromJson(v.document()), clock);
                    } catch (RuntimeException e) {
                        // Não deveria acontecer (a publicação valida), mas a aplicação não cai por isso (R-15).
                        log.error("Versão publicada ilegível; base vazia até nova publicação [versão={}]", v.number());
                        return ClinicKnowledge.empty();
                    }
                })
                .orElseGet(ClinicKnowledge::empty);
    }

    void evictAfterCommit(UUID clinic) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    cache.remove(clinic);
                }
            });
        } else {
            cache.remove(clinic);
        }
    }

    void evictAll() {
        cache.clear();
    }
}
