package com.clinicavet.assistant.support;

import com.clinicavet.assistant.conversation.application.AssistantModel;
import com.clinicavet.assistant.conversation.application.AssistantModelException;
import com.clinicavet.assistant.conversation.application.AssistantRequest;
import com.clinicavet.assistant.conversation.application.ModelReply;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * <i>Double</i> do modelo para os testes de integração (ADR-0002): nunca chama a OpenAI. Cada chamada
 * consome o próximo comportamento programado; sem programação, responde um turno válido. Guarda as
 * requisições recebidas para conferir o contexto montado.
 */
public final class FakeAssistantModel implements AssistantModel {

    public static final String DEFAULT_REPLY = "Resposta de teste.";

    private final Deque<Function<AssistantRequest, ModelReply>> script = new ArrayDeque<>();
    private final List<AssistantRequest> requests = new CopyOnWriteArrayList<>();

    public synchronized void reset() {
        script.clear();
        requests.clear();
    }

    public static String turnJson(String reply, String intent) {
        return "{\"reply\":\"" + reply.replace("\"", "\\\"") + "\",\"intent\":\"" + intent + "\"}";
    }

    public synchronized FakeAssistantModel thenReply(String reply, String intent) {
        return then(request -> ModelReply.of(turnJson(reply, intent), "STOP"));
    }

    public synchronized FakeAssistantModel thenReturn(ModelReply reply) {
        return then(request -> reply);
    }

    public synchronized FakeAssistantModel thenFail(AssistantModelException.Kind kind) {
        return then(request -> {
            throw new AssistantModelException(kind, "falha simulada", null);
        });
    }

    public synchronized FakeAssistantModel then(Function<AssistantRequest, ModelReply> behavior) {
        script.addLast(behavior);
        return this;
    }

    public List<AssistantRequest> requests() {
        return List.copyOf(requests);
    }

    public AssistantRequest lastRequest() {
        return requests.getLast();
    }

    @Override
    public ModelReply respond(AssistantRequest request) {
        requests.add(request);
        Function<AssistantRequest, ModelReply> behavior;
        synchronized (this) {
            behavior = script.pollFirst();
        }
        if (behavior == null) {
            return ModelReply.of(turnJson(DEFAULT_REPLY, "OTHER"), "STOP");
        }
        return behavior.apply(request);
    }
}
