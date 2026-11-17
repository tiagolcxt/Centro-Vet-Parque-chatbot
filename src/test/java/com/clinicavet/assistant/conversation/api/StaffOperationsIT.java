package com.clinicavet.assistant.conversation.api;

import com.clinicavet.assistant.conversation.domain.ConversationId;
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
import tools.jackson.databind.JsonNode;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Operações da equipe (Etapa 4): solicitação e conversa são independentes (ADR-0011); transição inválida
 * {@code 409}; desfecho ausente {@code 422} (DA-34).
 */
@IntegrationTest
class StaffOperationsIT {

    @Value("${local.server.port}")
    int port;

    @Autowired
    FakeAssistantModel model;

    @Autowired
    ServiceRequestRepository serviceRequests;

    ApiClient api;

    @BeforeEach
    void setUp() {
        model.reset();
        api = ApiClient.authenticated(port);
    }

    /** Conversa encaminhada por pedido explícito, com a solicitação HUMAN_CONTACT. */
    private String handedOff() {
        String id = api.openConversation("ficticio-" + UUID.randomUUID());
        api.sendMessage(id, "quero falar com um atendente");
        return id;
    }

    private ServiceRequest humanContact(String conversationId) {
        return serviceRequests.findByConversation(new ConversationId(UUID.fromString(conversationId))).stream()
                .filter(r -> r.type() == RequestType.HUMAN_CONTACT).findFirst().orElseThrow();
    }

    private String conversationStatus(String id) {
        JsonNode queue = api.get("/api/conversations?status=WAITING_HUMAN&limit=200").body();
        for (JsonNode item : queue) {
            if (item.path("id").asString().equals(id)) {
                return "WAITING_HUMAN";
            }
        }
        return "OUTRO";
    }

    @Test
    void updatingTheRequest_doesNotChangeTheConversation() {
        String id = handedOff();
        String requestId = humanContact(id).id().value().toString();

        ApiClient.Response inProgress = api.patch("/api/service-requests/" + requestId, Map.of("status", "IN_PROGRESS"));
        ApiClient.Response resolved = api.patch("/api/service-requests/" + requestId,
                Map.of("status", "RESOLVED", "outcome", "HANDLED_OUTSIDE"));

        assertThat(inProgress.status()).isEqualTo(200);
        assertThat(resolved.status()).isEqualTo(200);
        assertThat(resolved.body().path("outcome").asString()).isEqualTo("HANDLED_OUTSIDE");
        assertThat(conversationStatus(id)).as("a conversa continua aguardando").isEqualTo("WAITING_HUMAN");
    }

    @Test
    void closingOrReturningTheConversation_doesNotChangeRequests() {
        String a = handedOff();
        String b = handedOff();

        assertThat(api.post("/api/conversations/" + a + "/close", Map.of()).body().path("status").asString()).isEqualTo("CLOSED");
        assertThat(api.post("/api/conversations/" + b + "/return-to-assistant", Map.of()).body().path("status").asString())
                .isEqualTo("OPEN");

        assertThat(humanContact(a).status().name()).isEqualTo("PENDING");
        assertThat(humanContact(b).status().name()).isEqualTo("PENDING");
    }

    @Test
    void invalidTransitions_are409() {
        String id = handedOff();
        String requestId = humanContact(id).id().value().toString();

        assertThat(api.patch("/api/service-requests/" + requestId, Map.of("status", "RESOLVED", "outcome", "SCHEDULED_BY_STAFF"))
                .status()).as("PENDING → RESOLVED sem IN_PROGRESS").isEqualTo(409);
        api.post("/api/conversations/" + id + "/return-to-assistant", Map.of());
        assertThat(api.post("/api/conversations/" + id + "/return-to-assistant", Map.of()).status())
                .as("já está OPEN").isEqualTo(409);
        api.post("/api/conversations/" + id + "/close", Map.of());
        assertThat(api.post("/api/conversations/" + id + "/close", Map.of()).status()).as("já encerrada").isEqualTo(409);
        assertThat(api.patch("/api/conversations/" + id + "/priority", Map.of("priority", "P4_SERVICE_REQUEST")).status())
                .as("conversa encerrada").isEqualTo(409);
    }

    @Test
    void resolvingWithoutOutcome_is422_andOtherNeedsANote() {
        String id = handedOff();
        String requestId = humanContact(id).id().value().toString();
        api.patch("/api/service-requests/" + requestId, Map.of("status", "IN_PROGRESS"));

        ApiClient.Response noOutcome = api.patch("/api/service-requests/" + requestId, Map.of("status", "RESOLVED"));
        ApiClient.Response otherWithoutNote = api.patch("/api/service-requests/" + requestId,
                Map.of("status", "RESOLVED", "outcome", "OTHER"));

        assertThat(noOutcome.status()).isEqualTo(422);
        assertThat(noOutcome.body().path("field").asString()).isEqualTo("outcome");
        assertThat(otherWithoutNote.status()).isEqualTo(422);
    }

    @Test
    void listingRequestsByStatus_andUnknownIds_are404() {
        String id = handedOff();

        JsonNode pending = api.get("/api/service-requests?status=PENDING").body();
        boolean found = false;
        for (JsonNode item : pending) {
            found |= item.path("conversationId").asString().equals(id);
        }

        assertThat(found).isTrue();
        assertThat(api.patch("/api/service-requests/" + UUID.randomUUID(), Map.of("status", "IN_PROGRESS")).status()).isEqualTo(404);
        assertThat(api.post("/api/conversations/" + UUID.randomUUID() + "/close", Map.of()).status()).isEqualTo(404);
    }

    @Test
    void staffRoutes_requireTheApiKey() {
        ApiClient anonymous = api.withKey(null);

        assertThat(anonymous.get("/api/conversations?status=WAITING_HUMAN").status()).isEqualTo(401);
        assertThat(anonymous.get("/api/service-requests").status()).isEqualTo(401);
        assertThat(anonymous.patch("/api/service-requests/" + UUID.randomUUID(), Map.of("status", "IN_PROGRESS")).status())
                .isEqualTo(401);
    }
}
