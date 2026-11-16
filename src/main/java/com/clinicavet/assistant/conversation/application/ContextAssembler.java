package com.clinicavet.assistant.conversation.application;

import com.clinicavet.assistant.conversation.domain.Conversation;
import com.clinicavet.assistant.conversation.domain.Message;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * Monta o {@link AssistantRequest} de um turno a partir do banco (ADR-0004, ADR-0015). Na Etapa 2:
 * camada 4 (histórico) e parte da camada 5 (status e data/hora local). Institucional, tutor/pet,
 * encaminhamento e solicitação entram nas Etapas 3 a 5.
 */
@Component
class ContextAssembler {

    private final ConversationSettings settings;
    private final TokenEstimator tokens;
    private final Clock clock;

    ContextAssembler(ConversationSettings settings, TokenEstimator tokens, Clock clock) {
        this.settings = settings;
        this.tokens = tokens;
        this.clock = clock;
    }

    /**
     * @param recent  as mensagens mais recentes da conversa, em ordem cronológica, incluindo a atual
     * @param current a mensagem que o tutor acabou de enviar
     */
    AssistantRequest assemble(Conversation conversation, List<Message> recent, Message current, Instant now) {
        List<Message> previous = recent.stream()
                .filter(m -> m.conversationId().equals(conversation.id()))   // só a conversa atual (ADR-0009)
                .filter(m -> !m.id().equals(current.id()))
                .toList();
        // O limite de 20 conta a mensagem atual; o orçamento de tokens vale só para as anteriores.
        List<Message> window = HistoryWindow.fit(previous, settings.historyMaxMessages() - 1,
                settings.historyTokenBudget(), tokens);
        List<AssistantRequest.HistoryEntry> history = window.stream()
                .map(m -> new AssistantRequest.HistoryEntry(m.author(), m.content()))
                .toList();
        AssistantRequest.OperationalContext operational = new AssistantRequest.OperationalContext(
                conversation.status(), ZonedDateTime.ofInstant(now, clock.getZone()));
        return new AssistantRequest(operational, history, current.content());
    }
}
