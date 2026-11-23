package com.clinicavet.assistant.conversation.api;

import com.clinicavet.assistant.conversation.application.AssistantRequest;
import com.clinicavet.assistant.conversation.application.CustomerDataSettings;
import com.clinicavet.assistant.conversation.domain.Conversation;
import com.clinicavet.assistant.shared.domain.ConversationId;
import com.clinicavet.assistant.conversation.domain.ConversationRepository;
import com.clinicavet.assistant.servicerequest.domain.RequestStatus;
import com.clinicavet.assistant.servicerequest.domain.RequestType;
import com.clinicavet.assistant.servicerequest.domain.ServiceRequest;
import com.clinicavet.assistant.servicerequest.domain.ServiceRequestRepository;
import com.clinicavet.assistant.support.ApiClient;
import com.clinicavet.assistant.support.FakeAssistantModel;
import com.clinicavet.assistant.support.IntegrationTest;
import com.clinicavet.assistant.support.TestClock;
import com.clinicavet.assistant.support.TurnJson;
import com.clinicavet.assistant.tutor.domain.Pet;
import com.clinicavet.assistant.tutor.domain.PetRepository;
import com.clinicavet.assistant.tutor.domain.ProvenanceStatus;
import com.clinicavet.assistant.tutor.domain.Species;
import com.clinicavet.assistant.tutor.domain.Tutor;
import com.clinicavet.assistant.tutor.domain.TutorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Etapa 5, camada I: coleta de tutor e pet com proveniência, pedido de agendamento e contexto de conversas novas
 * (ADR-0010, DA-35 a DA-37; P-06, P-07, P-08 e P-13 com valores provisórios). Modelo falso e dados fictícios.
 */
@IntegrationTest
class CustomerDataIT {

    private static final String CONFIRM_QUESTION = "Só para confirmar: é para a Mel, uma gata, certo?";

    @Value("${local.server.port}")
    int port;

    @Autowired
    org.springframework.context.ApplicationContext context;

    @Autowired
    FakeAssistantModel model;

    @Autowired
    TestClock clock;

    @Autowired
    ConversationRepository conversations;

    @Autowired
    ServiceRequestRepository serviceRequests;

    @Autowired
    TutorRepository tutors;

    @Autowired
    PetRepository pets;

    @Autowired
    CustomerDataSettings customerData;

    ApiClient api;

    @BeforeEach
    void setUp() {
        model.reset();
        api = ApiClient.authenticated(port);
    }

    private static String contact() {
        return "ficticio-dados-" + UUID.randomUUID();
    }

    private Conversation load(String id) {
        return conversations.findById(new ConversationId(UUID.fromString(id))).orElseThrow();
    }

    private Optional<Tutor> tutorOf(String conversationId) {
        return tutors.findByContact(load(conversationId).contact());
    }

    private List<ServiceRequest> appointments(String conversationId) {
        return serviceRequests.findByConversation(new ConversationId(UUID.fromString(conversationId))).stream()
                .filter(r -> r.type() == RequestType.APPOINTMENT).toList();
    }

    /** Turnos 2 e 3 do fluxo: dados e confirmação. Devolve a resposta do turno da confirmação. */
    private JsonNode giveDataAndConfirm(String id) {
        model.thenJson(TurnJson.of(CONFIRM_QUESTION, "APPOINTMENT_REQUEST").priority("P4_SERVICE_REQUEST")
                        .tutorName("Ana").pet("Mel", "CAT").appointment("Mel", "vacina", "de manhã"))
                .thenJson(TurnJson.of("Obrigado, Ana!", "APPOINTMENT_REQUEST").priority("P4_SERVICE_REQUEST")
                        .confirm("PET_NAME", "Mel").confirm("PET_SPECIES", "Mel"));
        api.sendMessage(id, "É para a Mel, minha gata. É para a vacina, de manhã se der. Meu nome é Ana.");
        return api.sendMessage(id, "Sim, isso mesmo").body();
    }

    private static AssistantRequest.RequestView onlyRequest(AssistantRequest request) {
        assertThat(request.operational().requests()).hasSize(1);
        return request.operational().requests().getFirst();
    }

    @Test
    void appointmentInThreeTurns_reachesPending_withTheRightData_andNeverAsksTheSameThingTwice() {
        String id = api.openConversation(contact());
        model.thenJson(TurnJson.of("Claro! Para qual pet é a consulta?", "APPOINTMENT_REQUEST").priority("P4_SERVICE_REQUEST"));
        api.sendMessage(id, "Oi, queria marcar uma consulta");

        // Sem nenhum dado aceito: nada de cadastro (DA-36) nem de solicitação.
        assertThat(tutorOf(id)).isEmpty();
        assertThat(appointments(id)).isEmpty();

        JsonNode last = giveDataAndConfirm(id);

        List<AssistantRequest> sent = model.requests();
        // Turno 3: o tutor respondeu à pergunta de confirmação sem corrigir. O modelo já vê o pet CONFIRMADO e nada
        // faltando, e não repete a pergunta (A1). Nome e motivo continuam MENCIONADOS.
        AssistantRequest third = sent.get(2);
        assertThat(onlyRequest(third).status()).isEqualTo(RequestStatus.COLLECTING);
        assertThat(onlyRequest(third).missing()).isEmpty();
        assertThat(third.customer().tutorName().value()).isEqualTo("Ana");
        assertThat(third.customer().tutorName().status()).isEqualTo(ProvenanceStatus.MENTIONED);
        assertThat(third.customer().pets()).singleElement().satisfies(p -> {
            assertThat(p.name().value()).isEqualTo("Mel");
            assertThat(p.species().value()).isEqualTo("gato");
            assertThat(p.species().status()).isEqualTo(ProvenanceStatus.CONFIRMED);
        });

        ServiceRequest request = appointments(id).getFirst();
        Tutor tutor = tutorOf(id).orElseThrow();
        Pet mel = pets.findByTutor(tutor.id()).getFirst();
        assertThat(request.status()).isEqualTo(RequestStatus.PENDING);
        assertThat(request.tutorId()).contains(tutor.id());
        assertThat(request.petId()).contains(mel.id());
        assertThat(request.reason()).contains("vacina");
        assertThat(request.preferredPeriod()).contains("de manhã");
        assertThat(request.summary()).contains("Pedido de agendamento — pet: Mel (gato); motivo: vacina; preferência: de manhã");
        assertThat(mel.species()).contains(Species.CAT);
        assertThat(load(id).tutorId()).contains(tutor.id());
        // O sistema avisa o registro; o texto não confirma dia nem horário.
        assertThat(last.path("reply").asString()).endsWith(customerData.appointmentRegisteredReply().strip())
                .doesNotContainIgnoringCase("está confirmad");
        assertThat(last.path("status").asString()).isEqualTo("OPEN");

        // A equipe vê os detalhes.
        JsonNode staff = com.clinicavet.assistant.support.TestStaff.admin(port, context)
                .get("/app/api/service-requests?status=PENDING").body();
        assertThat(staff.toString()).contains("\"reason\":\"vacina\"", "\"preferredPeriod\":\"de manhã\"");
    }

    @Test
    void a1_answerWithoutCorrection_confirmsThePet_withoutAskingAgain() {
        String id = api.openConversation(contact());
        // Turno 1: o tutor dá tudo; o assistente pergunta a confirmação uma vez.
        model.thenJson(TurnJson.of(CONFIRM_QUESTION, "APPOINTMENT_REQUEST").priority("P4_SERVICE_REQUEST")
                        .tutorName("Ana").pet("Mel", "CAT").appointment("Mel", "vacina", ""))
                // Turno 2: o tutor manda o período em vez de "sim"; o modelo não marca confirmations.
                .thenJson(TurnJson.of("Certo, terça de manhã.", "APPOINTMENT_REQUEST")
                        .priority("P4_SERVICE_REQUEST").appointment("Mel", "vacina", "terça de manhã"));
        api.sendMessage(id, "Quero marcar vacina para a Mel, minha gata. Sou a Ana.");
        assertThat(appointments(id).getFirst().status()).isEqualTo(RequestStatus.COLLECTING);

        JsonNode reply = api.sendMessage(id, "Pode ser na terça de manhã").body();

        assertThat(onlyRequest(model.lastRequest()).missing()).isEmpty();
        ServiceRequest request = appointments(id).getFirst();
        assertThat(request.status()).isEqualTo(RequestStatus.PENDING);
        assertThat(request.preferredPeriod()).contains("terça de manhã");
        assertThat(reply.path("reply").asString()).endsWith(customerData.appointmentRegisteredReply().strip());
    }

    @Test
    void a1_answerThatMentionsAnotherSpecies_doesNotConfirm() {
        String id = api.openConversation(contact());
        model.thenJson(TurnJson.of(CONFIRM_QUESTION, "APPOINTMENT_REQUEST").priority("P4_SERVICE_REQUEST")
                        .tutorName("Ana").pet("Mel", "CAT").appointment("Mel", "vacina", ""))
                .thenJson(TurnJson.of("Entendi.", "APPOINTMENT_REQUEST").priority("P4_SERVICE_REQUEST"));
        api.sendMessage(id, "Quero marcar vacina para a Mel, minha gata. Sou a Ana.");
        api.sendMessage(id, "A Mel é cachorra");

        assertThat(onlyRequest(model.lastRequest()).missing()).singleElement().asString()
                .startsWith("confirmação do nome e da espécie");
        assertThat(appointments(id).getFirst().status()).isEqualTo(RequestStatus.COLLECTING);
    }

    @Test
    void a2_staffTakesOverAnIncompleteAppointment_andTheAssistantStopsCollecting() {
        String id = api.openConversation(contact());
        model.thenJson(TurnJson.of("Para qual pet é o banho?", "APPOINTMENT_REQUEST").priority("P4_SERVICE_REQUEST")
                .tutorName("Ana").appointment("", "banho", ""));
        api.sendMessage(id, "Quero marcar um banho. Sou a Ana.");
        ServiceRequest collecting = appointments(id).getFirst();
        assertThat(collecting.status()).isEqualTo(RequestStatus.COLLECTING);
        String path = "/app/api/service-requests/" + collecting.id().value();
        var staff = com.clinicavet.assistant.support.TestStaff.admin(port, context);

        var inProgress = staff.patch(path, java.util.Map.of("status", "IN_PROGRESS"));
        assertThat(inProgress.status()).isEqualTo(200);
        assertThat(inProgress.body().path("assigneeId").asString()).isNotEmpty();

        // O tutor continua; o assistente não pede mais dados nem abre outra solicitação.
        model.thenJson(TurnJson.of("A equipe já está com o seu pedido.", "APPOINTMENT_REQUEST")
                .priority("P4_SERVICE_REQUEST").pet("Thor", "DOG").appointment("Thor", "banho", ""));
        api.sendMessage(id, "É para o Thor, meu cachorro");
        assertThat(onlyRequest(model.lastRequest()).status()).isEqualTo(RequestStatus.IN_PROGRESS);
        assertThat(onlyRequest(model.lastRequest()).missing()).isEmpty();
        assertThat(appointments(id)).singleElement()
                .satisfies(r -> assertThat(r.status()).isEqualTo(RequestStatus.IN_PROGRESS));

        assertThat(staff.patch(path, java.util.Map.of("status", "RESOLVED", "outcome", "SCHEDULED_BY_STAFF"))
                .status()).isEqualTo(200);
    }

    @Test
    void confirmation_isIgnored_whenTheAssistantDidNotRepeatTheData() {
        String id = api.openConversation(contact());
        model.thenJson(TurnJson.of("Certo, anotado. Qual o motivo?", "APPOINTMENT_REQUEST")
                        .tutorName("Ana").pet("Mel", "CAT").appointment("Mel", "vacina", ""))
                .thenJson(TurnJson.of("Obrigado!", "APPOINTMENT_REQUEST")
                        .confirm("PET_NAME", "Mel").confirm("PET_SPECIES", "Mel"));
        api.sendMessage(id, "Quero marcar vacina para a Mel, minha gata. Sou a Ana.");
        api.sendMessage(id, "sim");

        assertThat(appointments(id)).singleElement().satisfies(r -> assertThat(r.status()).isEqualTo(RequestStatus.COLLECTING));
        model.thenReply("Ok.", "OTHER");
        api.sendMessage(id, "ok");
        assertThat(model.lastRequest().customer().pets().getFirst().species().status()).isEqualTo(ProvenanceStatus.MENTIONED);
    }

    @Test
    void disagreement_confirmsNothing_andACorrectionInTheSameConversationUpdatesTheSpecies() {
        String id = api.openConversation(contact());
        model.thenJson(TurnJson.of(CONFIRM_QUESTION, "APPOINTMENT_REQUEST")
                        .tutorName("Ana").pet("Mel", "CAT").appointment("Mel", "vacina", ""))
                .thenJson(TurnJson.of("Entendi, é cachorro.", "APPOINTMENT_REQUEST")
                        .pet("Mel", "DOG").confirm("PET_NAME", "Mel"));
        api.sendMessage(id, "Quero marcar vacina para a Mel, minha gata. Sou a Ana.");
        api.sendMessage(id, "Não, na verdade a Mel é cachorro");

        Pet mel = pets.findByTutor(tutorOf(id).orElseThrow().id()).getFirst();
        assertThat(mel.species()).contains(Species.DOG);
        assertThat(appointments(id).getFirst().status()).isEqualTo(RequestStatus.COLLECTING);
        model.thenReply("Ok.", "OTHER");
        api.sendMessage(id, "ok");
        AssistantRequest.CustomerContext.PetView view = model.lastRequest().customer().pets().getFirst();
        assertThat(view.name().status()).isEqualTo(ProvenanceStatus.MENTIONED);
        assertThat(view.species().value()).isEqualTo("cachorro");
    }

    @Test
    void newConversation_showsTheRegistryAsRegisteredOrToReconfirm_andNothingFromThePreviousService() {
        String contact = contact();
        String first = api.openConversation(contact);
        model.thenReply("Claro!", "APPOINTMENT_REQUEST");
        api.sendMessage(first, "Oi, queria marcar uma consulta");
        giveDataAndConfirm(first);
        model.thenJson(TurnJson.of("Anotado.", "PET_INFORMATION").pet("Mel", "CAT", "", "", "3 anos", false));
        api.sendMessage(first, "Ela tem 3 anos");

        clock.advance(Duration.ofMinutes(31));   // expira a conversa (30 min nos testes)
        JsonNode body = api.sendMessage(first, "Oi, bom dia").body();

        String second = body.path("conversationId").asString();
        assertThat(second).isNotEqualTo(first);
        assertThat(load(second).tutorId()).isEqualTo(load(first).tutorId());   // vinculada na abertura (DA-36)
        AssistantRequest request = model.lastRequest();
        assertThat(request.history()).isEmpty();
        assertThat(request.customer().tutorName().status()).isEqualTo(ProvenanceStatus.TO_RECONFIRM);   // nunca confirmado
        AssistantRequest.CustomerContext.PetView mel = request.customer().pets().getFirst();
        assertThat(mel.name().status()).isEqualTo(ProvenanceStatus.REGISTERED);
        assertThat(mel.species().status()).isEqualTo(ProvenanceStatus.REGISTERED);
        assertThat(mel.age().status()).isEqualTo(ProvenanceStatus.TO_RECONFIRM);
        // Nenhum motivo, preferência ou resumo do atendimento anterior: só tipo, data e status (DA-37).
        assertThat(request.operational().requests()).isEmpty();
        assertThat(request.operational().previousRequests()).singleElement().satisfies(p -> {
            assertThat(p.type()).isEqualTo(RequestType.APPOINTMENT);
            assertThat(p.status()).isEqualTo(RequestStatus.PENDING);
        });
        assertThat(request.toString()).doesNotContain("vacina").doesNotContain("de manhã");

        // Passada a validade, a confirmação antiga deixa de valer como CADASTRADA.
        clock.advance(customerData.registeredValidity().plusDays(1));
        api.sendMessage(second, "Oi de novo");
        assertThat(model.lastRequest().customer().pets().getFirst().name().status())
                .isEqualTo(ProvenanceStatus.TO_RECONFIRM);
    }

    @Test
    void anotherTutorsData_neverAppears_evenWithAPetOfTheSameName() {
        String ana = api.openConversation(contact());
        model.thenJson(TurnJson.of("Anotado.", "PET_INFORMATION").tutorName("Ana").pet("Mel", "CAT"));
        api.sendMessage(ana, "Sou a Ana e a Mel é minha gata");

        String bruno = api.openConversation(contact());
        model.thenJson(TurnJson.of("Anotado.", "PET_INFORMATION").tutorName("Bruno").pet("Mel", "DOG"));
        api.sendMessage(bruno, "Sou o Bruno e o Mel é meu cachorro");
        model.thenReply("Ok.", "OTHER");
        api.sendMessage(bruno, "Obrigado");

        AssistantRequest request = model.lastRequest();
        assertThat(request.customer().tutorName().value()).isEqualTo("Bruno");
        assertThat(request.customer().pets()).singleElement()
                .satisfies(p -> assertThat(p.species().value()).isEqualTo("cachorro"));
        assertThat(request.toString()).doesNotContain("Ana").doesNotContain("gato");
        assertThat(pets.findByTutor(tutorOf(ana).orElseThrow().id())).singleElement()
                .satisfies(p -> assertThat(p.species()).contains(Species.CAT));
    }

    @Test
    void dataTheTutorNeverSaid_isDiscarded_andNoRegistryIsCreated() {
        String id = api.openConversation(contact());
        model.thenJson(TurnJson.of("Claro! Qual é o pet?", "APPOINTMENT_REQUEST")
                .tutorName("Carlos").pet("Rex", "DOG").appointment("Rex", "", ""));
        api.sendMessage(id, "Quero marcar uma consulta");

        assertThat(tutorOf(id)).isEmpty();
        assertThat(appointments(id)).isEmpty();
        assertThat(load(id).tutorId()).isEmpty();
    }

    @Test
    void unknownPetName_doesNotCreateAPet_unlessTheTutorSaysItIsAnotherAnimal() {
        String id = api.openConversation(contact());
        model.thenJson(TurnJson.of("Anotado.", "PET_INFORMATION").pet("Mel", "CAT"))
                .thenJson(TurnJson.of("A Melzinha é outro animal ou é a Mel?", "PET_INFORMATION").pet("Melzinha", "UNKNOWN"))
                .thenJson(TurnJson.of("Entendi.", "PET_INFORMATION")
                        .pet("Melzinha", "DOG", "", "", "", true));
        api.sendMessage(id, "A Mel é minha gata");
        api.sendMessage(id, "E a Melzinha também precisa de banho");
        TutorIdHolder holder = new TutorIdHolder(tutorOf(id).orElseThrow());
        assertThat(pets.findByTutor(holder.tutor().id())).hasSize(1);

        api.sendMessage(id, "A Melzinha é outra, é minha cachorra");

        assertThat(pets.findByTutor(holder.tutor().id())).extracting(Pet::name).containsExactly("Mel", "Melzinha");
    }

    private record TutorIdHolder(Tutor tutor) {
    }

    @Test
    void handoffInTheSameTurn_keepsTheData_butDoesNotOpenAnAppointmentRequest() {
        String id = api.openConversation(contact());
        model.thenJson(TurnJson.of("Entendo.", "MEDICAL_CONCERN").secondary("APPOINTMENT_REQUEST")
                .pet("Mel", "CAT").appointment("Mel", "vômito", ""));

        JsonNode body = api.sendMessage(id, "Quero marcar consulta pra Mel, minha gata, ela está vomitando").body();

        assertThat(body.path("status").asString()).isEqualTo("WAITING_HUMAN");
        assertThat(appointments(id)).isEmpty();
        Tutor tutor = tutorOf(id).orElseThrow();
        assertThat(pets.findByTutor(tutor.id())).hasSize(1);
        assertThat(serviceRequests.findByConversation(new ConversationId(UUID.fromString(id))))
                .filteredOn(r -> r.type() == RequestType.HUMAN_CONTACT)
                .singleElement().satisfies(r -> assertThat(r.tutorId()).contains(tutor.id()));
    }

    @Test
    void ct09_isItConfirmed_neverClaimsConfirmation_andChangesNothing() {
        String id = api.openConversation(contact());
        model.thenReply("Claro!", "APPOINTMENT_REQUEST");
        api.sendMessage(id, "Oi, queria marcar uma consulta");
        giveDataAndConfirm(id);

        // Modelo errado nas duas tentativas: V5 reprova, resposta segura.
        model.thenJson(TurnJson.of("Sim, está tudo confirmado para amanhã.", "APPOINTMENT_REQUEST").priority("P4_SERVICE_REQUEST"))
                .thenJson(TurnJson.of("Sua consulta está marcada.", "APPOINTMENT_REQUEST").priority("P4_SERVICE_REQUEST"));
        JsonNode wrong = api.sendMessage(id, "Meu agendamento já está confirmado?").body();
        assertThat(wrong.path("fallback").asBoolean()).isTrue();
        assertThat(wrong.path("reply").asString()).doesNotContainIgnoringCase("confirmad").doesNotContainIgnoringCase("marcad");

        // Modelo certo: o pedido está com a equipe; nada muda.
        model.thenJson(TurnJson.of("Seu pedido está com a equipe da clínica. Dia e horário só valem depois que a equipe "
                + "confirmar.", "APPOINTMENT_REQUEST").priority("P4_SERVICE_REQUEST").appointment("Mel", "", ""));
        JsonNode right = api.sendMessage(id, "Meu agendamento já está confirmado?").body();

        assertThat(right.path("fallback").asBoolean()).isFalse();
        assertThat(right.path("priority").asString()).isEqualTo("P4_SERVICE_REQUEST");
        assertThat(right.path("status").asString()).isEqualTo("OPEN");
        assertThat(onlyRequest(model.lastRequest()).status()).isEqualTo(RequestStatus.PENDING);
        assertThat(appointments(id)).singleElement().satisfies(r -> assertThat(r.status()).isEqualTo(RequestStatus.PENDING));
    }

    @Test
    void ct18_followUpWithANewCondition_isAtLeastP2_andThePreviousRequestIsOnlyReferenced() {
        String contact = contact();
        String first = api.openConversation(contact);
        model.thenJson(TurnJson.of(CONFIRM_QUESTION, "APPOINTMENT_REQUEST")
                        .tutorName("Ana").pet("Mel", "CAT").appointment("Mel", "pata machucada", ""))
                .thenJson(TurnJson.of("Obrigado!", "APPOINTMENT_REQUEST")
                        .confirm("PET_NAME", "Mel").confirm("PET_SPECIES", "Mel"));
        api.sendMessage(first, "Quero marcar para a Mel, minha gata, a pata está machucada. Sou a Ana.");
        api.sendMessage(first, "Sim");
        assertThat(appointments(first).getFirst().status()).isEqualTo(RequestStatus.PENDING);

        clock.advance(Duration.ofMinutes(31));
        model.thenJson(TurnJson.of("Entendo.", "TREATMENT_FOLLOW_UP").secondary("MEDICAL_CONCERN"));
        JsonNode body = api.sendMessage(first, "Depois da consulta, agora ela está tossindo").body();

        assertThat(body.path("priority").asString()).isIn("P1_POSSIBLE_EMERGENCY", "P2_URGENT");
        assertThat(body.path("status").asString()).isEqualTo("WAITING_HUMAN");
        AssistantRequest request = model.lastRequest();
        assertThat(request.operational().previousRequests()).hasSize(1);
        assertThat(request.operational().toString()).doesNotContain("machucada");
    }
}
