package com.clinicavet.assistant.conversation.api;

import com.clinicavet.assistant.conversation.application.ConversationSettings;
import com.clinicavet.assistant.shared.domain.ConversationId;
import com.clinicavet.assistant.conversation.domain.ConversationRepository;
import com.clinicavet.assistant.conversation.domain.Intent;
import com.clinicavet.assistant.servicerequest.domain.RequestStatus;
import com.clinicavet.assistant.servicerequest.domain.RequestType;
import com.clinicavet.assistant.servicerequest.domain.ServiceRequest;
import com.clinicavet.assistant.servicerequest.domain.ServiceRequestRepository;
import com.clinicavet.assistant.support.ApiClient;
import com.clinicavet.assistant.support.FakeAssistantModel;
import com.clinicavet.assistant.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Etapa 3 com o modelo falso: lacuna vira pedido de informação; resposta reprovada vira resposta segura. */
@IntegrationTest
class KnowledgeTurnIT {

    @Value("${local.server.port}")
    int port;

    @Autowired
    FakeAssistantModel model;

    @Autowired
    ServiceRequestRepository serviceRequests;

    @Autowired
    ConversationRepository conversations;

    @Autowired
    ConversationSettings settings;

    ApiClient api;

    @BeforeEach
    void setUp() {
        model.reset();
        api = ApiClient.authenticated(port);
    }

    private String open() {
        return api.openConversation("ficticio-" + UUID.randomUUID());
    }

    private List<ServiceRequest> informationRequests(String id) {
        return serviceRequests.findByConversation(new ConversationId(UUID.fromString(id))).stream()
                .filter(r -> r.type() == RequestType.INFORMATION).toList();
    }

    @Test
    void knowledgeGap_createsAPendingInformationRequest_withTheQuestion() {
        String id = open();
        model.thenTurn("Não tenho a informação sobre feriados; a equipe da clínica pode confirmar.",
                "CLINIC_INFORMATION", List.of(), true);

        ApiClient.Response response = api.sendMessage(id, "Vocês abrem no feriado?");

        assertThat(response.body().path("fallback").asBoolean()).isFalse();
        assertThat(informationRequests(id)).singleElement().satisfies(request -> {
            assertThat(request.status()).isEqualTo(RequestStatus.PENDING);
            assertThat(request.summary()).contains("Vocês abrem no feriado?");
        });
    }

    @Test
    void secondGapInTheSameConversation_isAppendedToTheSameRequest() {
        String id = open();
        model.thenTurn("Não tenho essa informação.", "CLINIC_INFORMATION", List.of(), true)
                .thenTurn("Também não tenho essa informação.", "PRICE_INQUIRY", List.of(), true);

        api.sendMessage(id, "Vocês abrem no feriado?");
        api.sendMessage(id, "Quanto custa o banho e tosa?");

        assertThat(informationRequests(id)).singleElement().satisfies(request ->
                assertThat(request.summary()).contains("Vocês abrem no feriado? | Quanto custa o banho e tosa?"));
    }

    @Test
    void answerWithItsSource_isDelivered_withoutInformationRequest() {
        String id = open();
        model.thenTurn("A consulta custa R$ 150,00.", "PRICE_INQUIRY", List.of("services.consulta.price-cents"), false);

        ApiClient.Response response = api.sendMessage(id, "Quanto custa a consulta?");

        assertThat(response.body().path("fallback").asBoolean()).isFalse();
        assertThat(response.body().path("reply").asString()).endsWith("A consulta custa R$ 150,00.");
        assertThat(informationRequests(id)).isEmpty();
    }

    @Test
    void inventedPrice_isRejectedTwice_answersTheSafeReply_andChangesNothing() {
        String id = open();
        model.thenReply("Olá!", "GREETING");
        api.sendMessage(id, "Oi");
        model.thenTurn("O banho e tosa custa R$ 80.", "PRICE_INQUIRY", List.of("services.banho-e-tosa"), false)
                .thenTurn("Custa R$ 80, sim.", "PRICE_INQUIRY", List.of(), false);
        int callsBefore = model.requests().size();

        ApiClient.Response response = api.sendMessage(id, "Quanto custa o banho e tosa?");

        assertThat(response.body().path("fallback").asBoolean()).isTrue();
        assertThat(response.body().path("reply").asString()).startsWith(settings.fallbackReply().strip())
                    .contains("ligue para a recepção");   // DA-41: a resposta segura indica a recepção
        assertThat(model.requests().size() - callsBefore).isEqualTo(2);
        assertThat(conversations.findById(new ConversationId(UUID.fromString(id))).orElseThrow().currentIntent())
                .contains(Intent.GREETING);
        assertThat(informationRequests(id)).isEmpty();
    }

    @Test
    void confirmationOfAppointment_isRejected_thenTheCorrectedAnswerIsUsed() {
        String id = open();
        model.thenTurn("Sua consulta está marcada para sábado.", "APPOINTMENT_REQUEST", List.of(), false)
                .thenTurn("Anotei seu pedido para sábado; a equipe da clínica confirma a disponibilidade.",
                        "APPOINTMENT_REQUEST", List.of(), false);

        ApiClient.Response response = api.sendMessage(id, "Pode marcar uma consulta no sábado?");

        assertThat(response.body().path("fallback").asBoolean()).isFalse();
        assertThat(response.body().path("reply").asString()).contains("Anotei seu pedido");
        assertThat(model.requests()).hasSize(2);
    }
}
