package com.clinicavet.assistant.conversation.application;

import com.clinicavet.assistant.clinicknowledge.domain.ClinicKnowledge;
import com.clinicavet.assistant.clinicknowledge.domain.OpeningHours;
import com.clinicavet.assistant.conversation.domain.Conversation;
import com.clinicavet.assistant.conversation.domain.ConversationId;
import com.clinicavet.assistant.conversation.domain.Message;
import com.clinicavet.assistant.servicerequest.domain.RequestStatus;
import com.clinicavet.assistant.servicerequest.domain.RequestType;
import com.clinicavet.assistant.servicerequest.domain.ServiceRequest;
import com.clinicavet.assistant.servicerequest.domain.ServiceRequestRepository;
import com.clinicavet.assistant.tutor.domain.DataField;
import com.clinicavet.assistant.tutor.domain.Pet;
import com.clinicavet.assistant.tutor.domain.PetRepository;
import com.clinicavet.assistant.tutor.domain.ProvenanceRepository;
import com.clinicavet.assistant.tutor.domain.ProvenanceStatus;
import com.clinicavet.assistant.tutor.domain.Tutor;
import com.clinicavet.assistant.tutor.domain.TutorRepository;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Monta o {@link AssistantRequest} de um turno a partir do banco (ADR-0004, ADR-0015).
 * <ul>
 *   <li><b>Camada 3</b> (Etapa 5): o cadastro do tutor <b>vinculado à conversa</b> e só dele, com o status de
 *       proveniência de cada campo calculado para esta conversa (ADR-0010). Conversa sem tutor: camada vazia.</li>
 *   <li><b>Camada 4</b>: histórico da conversa atual.</li>
 *   <li><b>Camada 5</b>: status, data/hora local, solicitações desta conversa (agendamento com o que falta) e as
 *       solicitações em aberto de outras conversas do mesmo contato, só com tipo, data e status (DA-37).</li>
 * </ul>
 * Motivo, preferência e resumo de solicitações de outras conversas nunca entram (ADR-0009).
 */
@Component
class ContextAssembler {

    static final int MAX_PREVIOUS_REQUESTS = 3;
    static final int MAX_PETS_IN_CONTEXT = 10;

    private final ConversationSettings settings;
    private final TokenEstimator tokens;
    private final TutorRepository tutors;
    private final PetRepository pets;
    private final ProvenanceRepository provenance;
    private final ServiceRequestRepository serviceRequests;
    private final Duration validity;
    private final ClinicKnowledge knowledge;
    private final Clock clock;

    ContextAssembler(ConversationSettings settings, TokenEstimator tokens, TutorRepository tutors, PetRepository pets,
                     ProvenanceRepository provenance, ServiceRequestRepository serviceRequests,
                     CustomerDataSettings customerData, ClinicKnowledge knowledge, Clock clock) {
        this.settings = settings;
        this.tokens = tokens;
        this.tutors = tutors;
        this.pets = pets;
        this.provenance = provenance;
        this.serviceRequests = serviceRequests;
        this.validity = customerData.registeredValidity();
        this.knowledge = knowledge;
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

        CustomerRecords records = conversation.tutorId()
                .map(id -> CustomerRecords.load(id, tutors, pets, provenance))
                .orElseGet(CustomerRecords::empty);
        AssistantRequest.CustomerContext customer = customer(records, conversation.id(), now);
        List<AssistantRequest.RequestView> requests = serviceRequests.findByConversation(conversation.id()).stream()
                .map(r -> view(r, records, conversation.id(), now))
                .toList();
        List<AssistantRequest.PreviousRequest> previousRequests = serviceRequests
                .findOpenOfContactExcept(conversation.contact(), conversation.id(), MAX_PREVIOUS_REQUESTS).stream()
                .map(r -> new AssistantRequest.PreviousRequest(r.type(), r.status(),
                        r.createdAt().atZone(clock.getZone()).toLocalDate()))
                .toList();
        ZonedDateTime localNow = ZonedDateTime.ofInstant(now, clock.getZone());
        AssistantRequest.OperationalContext operational = new AssistantRequest.OperationalContext(
                conversation.status(), localNow, OpeningHours.at(knowledge, localNow), requests, previousRequests);
        return new AssistantRequest(AssistantRequest.Mode.MAIN, customer, operational, history, current.content());
    }

    private AssistantRequest.CustomerContext customer(CustomerRecords records, ConversationId conversation,
                                                      Instant now) {
        Optional<Tutor> tutor = records.tutor();
        if (tutor.isEmpty()) {
            return AssistantRequest.CustomerContext.NONE;
        }
        UUID tutorId = tutor.get().id().value();
        AssistantRequest.CustomerContext.FieldView name = tutor.get().name()
                .map(n -> field(n, records, tutorId, DataField.TUTOR_NAME, conversation, now)).orElse(null);
        List<AssistantRequest.CustomerContext.PetView> petViews = records.pets().stream()
                .limit(MAX_PETS_IN_CONTEXT)
                .map(p -> petView(p, records, conversation, now))
                .toList();
        return new AssistantRequest.CustomerContext(name, petViews);
    }

    private AssistantRequest.CustomerContext.PetView petView(Pet pet, CustomerRecords records,
                                                             ConversationId conversation, Instant now) {
        UUID id = pet.id().value();
        return new AssistantRequest.CustomerContext.PetView(
                field(pet.name(), records, id, DataField.PET_NAME, conversation, now),
                pet.species().map(s -> field(CustomerDataCollector.speciesLabel(s, pet.speciesDescription().orElse(null)),
                        records, id, DataField.PET_SPECIES, conversation, now)).orElse(null),
                pet.breed().map(b -> field(b, records, id, DataField.PET_BREED, conversation, now)).orElse(null),
                pet.ageDescription().map(a -> field(a, records, id, DataField.PET_AGE, conversation, now)).orElse(null));
    }

    /** Campo sem registro de proveniência (ex.: cadastrado fora do assistente) é tratado como A_RECONFIRMAR. */
    private AssistantRequest.CustomerContext.FieldView field(String value, CustomerRecords records, UUID subject,
                                                             DataField field, ConversationId conversation,
                                                             Instant now) {
        ProvenanceStatus status = records.status(subject, field, conversation, now, validity)
                .orElse(ProvenanceStatus.TO_RECONFIRM);
        return new AssistantRequest.CustomerContext.FieldView(value, status);
    }

    private AssistantRequest.RequestView view(ServiceRequest request, CustomerRecords records,
                                              ConversationId conversation, Instant now) {
        boolean details = request.type() == RequestType.APPOINTMENT && !request.status().isFinal();
        if (!details) {
            return new AssistantRequest.RequestView(request.type(), request.status(), null, null, null, List.of());
        }
        List<String> missing = request.status() == RequestStatus.COLLECTING
                ? AppointmentRequirements.missing(request, records, conversation, now, validity).stream()
                .map(AppointmentRequirements.Gap::label).toList()
                : List.of();
        return new AssistantRequest.RequestView(request.type(), request.status(),
                request.petId().flatMap(records::pet).map(Pet::name).orElse(null),
                request.reason().orElse(null), request.preferredPeriod().orElse(null), missing);
    }
}
