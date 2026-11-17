package com.clinicavet.assistant.conversation.application;

import com.clinicavet.assistant.conversation.domain.ConversationStatus;
import com.clinicavet.assistant.conversation.domain.MessageAuthor;
import com.clinicavet.assistant.servicerequest.domain.RequestStatus;
import com.clinicavet.assistant.servicerequest.domain.RequestType;
import com.clinicavet.assistant.tutor.domain.ProvenanceStatus;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;

/**
 * O que vai ao modelo num turno, já recortado pela aplicação (ADR-0004, ADR-0015). Camadas: identidade (fixa, no
 * template), institucional (fixa, no adaptador), tutor e pet ({@link #customer}, Etapa 5), conversa
 * ({@link #history}) e operacional ({@link #operational}). A mensagem atual vem à parte porque nunca é cortada.
 *
 * @param customer        camada 3: só o tutor vinculado à conversa, com proveniência por campo (ADR-0010)
 * @param history         mensagens anteriores da conversa atual, em ordem cronológica
 * @param customerMessage a mensagem que o tutor acabou de enviar
 */
public record AssistantRequest(Mode mode, CustomerContext customer, OperationalContext operational,
                               List<HistoryEntry> history, String customerMessage) {

    /** Principal: resposta completa. Restrito: só sinais, em {@code WAITING_HUMAN} (ADR-0007, item 4). */
    public enum Mode { MAIN, RESTRICTED }

    public AssistantRequest {
        Objects.requireNonNull(mode, "mode");
        customer = customer == null ? CustomerContext.NONE : customer;
        Objects.requireNonNull(operational, "operational");
        history = List.copyOf(history);
        Objects.requireNonNull(customerMessage, "customerMessage");
    }

    public AssistantRequest(Mode mode, OperationalContext operational, List<HistoryEntry> history,
                            String customerMessage) {
        this(mode, CustomerContext.NONE, operational, history, customerMessage);
    }

    /** Requisição do modo principal sem dados do tutor (forma da Etapa 2). */
    public AssistantRequest(OperationalContext operational, List<HistoryEntry> history, String customerMessage) {
        this(Mode.MAIN, CustomerContext.NONE, operational, history, customerMessage);
    }

    public AssistantRequest restricted() {
        return new AssistantRequest(Mode.RESTRICTED, customer, operational, history, customerMessage);
    }

    public record HistoryEntry(MessageAuthor author, String content) {

        public HistoryEntry {
            Objects.requireNonNull(author, "author");
            Objects.requireNonNull(content, "content");
        }
    }

    /**
     * Camada 3 (tutor e pet). Vazia quando a conversa ainda não tem tutor (DA-36).
     *
     * @param tutorName {@code null} se não há nome registrado
     */
    public record CustomerContext(FieldView tutorName, List<PetView> pets) {

        public static final CustomerContext NONE = new CustomerContext(null, List.of());

        public CustomerContext {
            pets = List.copyOf(pets);
        }

        public boolean isEmpty() {
            return tutorName == null && pets.isEmpty();
        }

        /** Valor como deve aparecer ao modelo (ex.: espécie já em português) e seu status nesta conversa. */
        public record FieldView(String value, ProvenanceStatus status) {

            public FieldView {
                Objects.requireNonNull(value, "value");
                Objects.requireNonNull(status, "status");
            }
        }

        /** Campos ausentes ficam {@code null}. */
        public record PetView(FieldView name, FieldView species, FieldView breed, FieldView age) {

            public PetView {
                Objects.requireNonNull(name, "name");
            }
        }
    }

    /**
     * Camada 5 (operacional): status, data/hora local da clínica, solicitações desta conversa e referência mínima
     * às solicitações em aberto de conversas anteriores do mesmo contato (DA-37).
     */
    public record OperationalContext(ConversationStatus status, ZonedDateTime localNow, List<RequestView> requests,
                                     List<PreviousRequest> previousRequests) {

        public OperationalContext {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(localNow, "localNow");
            requests = List.copyOf(requests);
            previousRequests = List.copyOf(previousRequests);
        }

        public OperationalContext(ConversationStatus status, ZonedDateTime localNow) {
            this(status, localNow, List.of(), List.of());
        }
    }

    /**
     * Solicitação desta conversa. Os detalhes só aparecem para agendamento em coleta ou pendente.
     *
     * @param missing o que ainda falta para registrar (agendamento em {@code COLLECTING}), em português
     */
    public record RequestView(RequestType type, RequestStatus status, String petName, String reason,
                              String preferredPeriod, List<String> missing) {

        public RequestView {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(status, "status");
            missing = List.copyOf(missing);
        }
    }

    /** DA-37: só tipo, data de abertura e status. Nada de resumo, motivo, sintomas ou preferências. */
    public record PreviousRequest(RequestType type, RequestStatus status, LocalDate openedOn) {

        public PreviousRequest {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(openedOn, "openedOn");
        }
    }
}
