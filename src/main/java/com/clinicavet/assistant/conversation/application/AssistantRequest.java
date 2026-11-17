package com.clinicavet.assistant.conversation.application;

import com.clinicavet.assistant.conversation.domain.ConversationStatus;
import com.clinicavet.assistant.conversation.domain.MessageAuthor;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;

/**
 * O que vai ao modelo num turno, já recortado pela aplicação (ADR-0004, ADR-0015). Camadas da
 * Etapa 2: identidade (fixa, no template), conversa ({@link #history}) e operacional
 * ({@link #operational}). A mensagem atual vem à parte porque nunca é cortada.
 *
 * @param history         mensagens anteriores da conversa atual, em ordem cronológica
 * @param customerMessage a mensagem que o tutor acabou de enviar
 */
public record AssistantRequest(Mode mode, OperationalContext operational, List<HistoryEntry> history,
                               String customerMessage) {

    /** Principal: resposta completa. Restrito: só sinais, em {@code WAITING_HUMAN} (ADR-0007, item 4). */
    public enum Mode { MAIN, RESTRICTED }

    public AssistantRequest {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(operational, "operational");
        history = List.copyOf(history);
        Objects.requireNonNull(customerMessage, "customerMessage");
    }

    /** Requisição do modo principal (forma da Etapa 2). */
    public AssistantRequest(OperationalContext operational, List<HistoryEntry> history, String customerMessage) {
        this(Mode.MAIN, operational, history, customerMessage);
    }

    public AssistantRequest restricted() {
        return new AssistantRequest(Mode.RESTRICTED, operational, history, customerMessage);
    }

    public record HistoryEntry(MessageAuthor author, String content) {

        public HistoryEntry {
            Objects.requireNonNull(author, "author");
            Objects.requireNonNull(content, "content");
        }
    }

    /** Camada 5 (operacional), parte da Etapa 2: status e data/hora local da clínica. */
    public record OperationalContext(ConversationStatus status, ZonedDateTime localNow) {

        public OperationalContext {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(localNow, "localNow");
        }
    }
}
