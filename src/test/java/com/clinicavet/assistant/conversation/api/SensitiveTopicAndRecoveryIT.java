package com.clinicavet.assistant.conversation.api;

import com.clinicavet.assistant.conversation.application.HandoffSettings;
import com.clinicavet.assistant.conversation.domain.Conversation;
import com.clinicavet.assistant.shared.domain.ConversationId;
import com.clinicavet.assistant.conversation.domain.ConversationRepository;
import com.clinicavet.assistant.servicerequest.domain.RequestType;
import com.clinicavet.assistant.servicerequest.domain.ServiceRequestRepository;
import com.clinicavet.assistant.support.ApiClient;
import com.clinicavet.assistant.support.FakeAssistantModel;
import com.clinicavet.assistant.support.IntegrationTest;
import com.clinicavet.assistant.support.TurnJson;
import com.clinicavet.assistant.tutor.domain.Pet;
import com.clinicavet.assistant.tutor.domain.PetRepository;
import com.clinicavet.assistant.tutor.domain.TutorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CT-19, camadas U e I: a conversa real de teste "eutanásia" (2026-11-17), com modelo falso e dados fictícios.
 * Assunto sensível (oferta, sem coleta), intenção preservada (nome de pet genérico recusado, pet citado só no
 * pedido) e recuperação de frustração em {@code WAITING_HUMAN} (texto conforme o sinal, sem repetição).
 */
@IntegrationTest
class SensitiveTopicAndRecoveryIT {

    @Value("${local.server.port}")
    int port;

    @Autowired
    FakeAssistantModel model;

    @Autowired
    ConversationRepository conversations;

    @Autowired
    ServiceRequestRepository serviceRequests;

    @Autowired
    TutorRepository tutors;

    @Autowired
    PetRepository pets;

    @Autowired
    HandoffSettings handoff;

    ApiClient api;

    @BeforeEach
    void setUp() {
        model.reset();
        api = ApiClient.authenticated(port);
    }

    private String open() {
        return api.openConversation("ficticio-ct19-" + UUID.randomUUID());
    }

    private Conversation load(String id) {
        return conversations.findById(new ConversationId(UUID.fromString(id))).orElseThrow();
    }

    private List<RequestType> requestTypes(String id) {
        return serviceRequests.findByConversation(new ConversationId(UUID.fromString(id))).stream()
                .map(r -> r.type()).toList();
    }

    @Test
    void sensitiveTopic_offersTheTeam_andDoesNotStartAnAppointmentCollection() {
        String id = open();
        model.thenJson(TurnJson.of("Entendo que é um momento difícil.", "SERVICE_INFORMATION").gap()
                        .signals("SENSITIVE_TOPIC").priority("P5_ADMINISTRATIVE"))
                .thenJson(TurnJson.of("A equipe veterinária pode conversar com você sobre isso.", "APPOINTMENT_REQUEST")
                        .appointment("", "conversar sobre eutanásia", "amanhã de manhã"));

        JsonNode first = api.sendMessage(id, "boa noite, eu gostaria de saber se vocês fazem sacrifício de cachorro").body();
        JsonNode second = api.sendMessage(id, "Vocês podem me atender amanhã de manhã?").body();

        // DA-40: pedido registrado para a equipe, a conversa continua aberta e termina com o próximo passo.
        assertThat(first.path("status").asString()).isEqualTo("OPEN");
        assertThat(first.path("staffRequestReason").asString()).isEqualTo("SENSITIVE_TOPIC");
        assertThat(first.path("reply").asString()).contains(handoff.replies().register().strip())
                .contains("ligue para a recepção").contains("(11) 3000-1000");
        assertThat(requestTypes(id)).contains(RequestType.HUMAN_CONTACT);
        assertThat(second.path("status").asString()).isEqualTo("OPEN");
        assertThat(requestTypes(id)).doesNotContain(RequestType.APPOINTMENT);
    }

    @Test
    void genericPetName_isRejected_andAPetNamedOnlyInTheRequestIsRecorded() {
        String id = open();
        model.thenJson(TurnJson.of("Claro. Qual é o nome do seu animal?", "APPOINTMENT_REQUEST")
                        .pet("cachorro", "DOG").appointment("", "consulta de rotina", ""))
                .thenJson(TurnJson.of("Só para confirmar: é para o Zico, um cachorro, certo?", "APPOINTMENT_REQUEST")
                        .appointment("Zico", "", ""));

        api.sendMessage(id, "Quero marcar uma consulta de rotina para o meu cachorro");
        api.sendMessage(id, "Zico");

        List<Pet> tutorPets = pets.findByTutor(load(id).tutorId().orElseThrow());
        assertThat(tutorPets).extracting(Pet::name).containsExactly("Zico");
        assertThat(serviceRequests.findByConversation(new ConversationId(UUID.fromString(id))))
                .filteredOn(r -> r.type() == RequestType.APPOINTMENT).singleElement()
                .satisfies(r -> assertThat(r.petId()).contains(tutorPets.getFirst().id()));
    }

    @Test
    void waitingHuman_theModelKeepsAnsweringClinicQuestions() {
        String id = open();
        api.sendMessage(id, "quero falar com um atendente");
        model.thenJson(TurnJson.of("Amanhã abrimos das 08:00 às 18:00.", "CLINIC_INFORMATION").refs("hours.tuesday"));

        JsonNode body = api.sendMessage(id, "que horas a clinica abre amanhã?").body();

        assertThat(body.path("status").asString()).isEqualTo("WAITING_HUMAN");
        assertThat(body.path("fallback").asBoolean()).isFalse();
        assertThat(body.path("reply").asString()).isEqualTo("Amanhã abrimos das 08:00 às 18:00.");
    }

    @Test
    void waitingHuman_whenTheModelFails_failSafeExplains_andNeverRepeatsTheSameText() {
        String id = open();
        api.sendMessage(id, "quero falar com um atendente");   // encaminha pelo detector (DA-30)
        for (int i = 0; i < 8; i++) {
            model.thenFail(com.clinicavet.assistant.conversation.application.AssistantModelException.Kind.TIMEOUT);
        }

        List<String> replies = new ArrayList<>();
        for (String text : List.of("então para o que você serve?", "não tá fazendo sentido o que você está falando",
                "ok", "alguém aí?")) {
            JsonNode body = api.sendMessage(id, text).body();
            assertThat(body.path("status").asString()).isEqualTo("WAITING_HUMAN");
            replies.add(body.path("reply").asString());
        }

        for (int i = 1; i < replies.size(); i++) {
            assertThat(replies.get(i)).as("resposta %d igual à anterior", i).isNotEqualTo(replies.get(i - 1));
        }
        replies.forEach(r -> assertThat(r.toLowerCase()).doesNotContain("vai te ligar", "entraremos em contato",
                "retornaremos"));
    }
}
