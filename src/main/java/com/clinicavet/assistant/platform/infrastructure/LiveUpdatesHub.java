package com.clinicavet.assistant.platform.infrastructure;

import com.clinicavet.assistant.platform.ClinicId;
import com.clinicavet.assistant.platform.CurrentClinic;
import com.clinicavet.assistant.platform.LiveUpdates;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Canais SSE abertos, por clínica (RF-S29). Uma instância só no MVP (sem broker). Os avisos levam só tipo e id; nada
 * de conteúdo nem dado pessoal.
 */
@Component
public class LiveUpdatesHub implements LiveUpdates {

    static final Duration TIMEOUT = Duration.ofMinutes(30);   // o navegador reconecta sozinho

    private final Map<UUID, List<SseEmitter>> byClinic = new ConcurrentHashMap<>();

    /** Abre um canal para a clínica (o controller já checou a sessão). */
    public SseEmitter open(ClinicId clinic) {
        SseEmitter emitter = new SseEmitter(TIMEOUT.toMillis());
        List<SseEmitter> emitters = byClinic.computeIfAbsent(clinic.value(), k -> new CopyOnWriteArrayList<>());
        emitters.add(emitter);
        Runnable remove = () -> emitters.remove(emitter);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(e -> remove.run());
        try {
            emitter.send(SseEmitter.event().name("ready").data("{}", MediaType.APPLICATION_JSON));
        } catch (IOException e) {
            remove.run();
        }
        return emitter;
    }

    @Override
    public void changed(Topic topic, UUID id) {
        ClinicId clinic = CurrentClinic.require();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send(clinic, topic, id);
                }
            });
        } else {
            send(clinic, topic, id);
        }
    }

    private void send(ClinicId clinic, Topic topic, UUID id) {
        List<SseEmitter> emitters = byClinic.get(clinic.value());
        if (emitters == null) {
            return;
        }
        String json = "{\"topic\":\"" + topic.name() + "\",\"id\":\"" + id + "\"}";
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("change").data(json, MediaType.APPLICATION_JSON));
            } catch (IOException | IllegalStateException e) {
                emitters.remove(emitter);
            }
        }
    }
}
