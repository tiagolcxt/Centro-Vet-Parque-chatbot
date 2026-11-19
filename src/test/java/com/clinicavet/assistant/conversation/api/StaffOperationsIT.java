package com.clinicavet.assistant.conversation.api;

import com.clinicavet.assistant.servicerequest.domain.RequestType;
import com.clinicavet.assistant.servicerequest.domain.ServiceRequest;
import com.clinicavet.assistant.servicerequest.domain.ServiceRequestRepository;
import com.clinicavet.assistant.shared.domain.ConversationId;
import com.clinicavet.assistant.support.ApiClient;
import com.clinicavet.assistant.support.FakeAssistantModel;
import com.clinicavet.assistant.support.IntegrationTest;
import com.clinicavet.assistant.support.StaffClient;
import com.clinicavet.assistant.support.TestStaff;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import tools.jackson.databind.JsonNode;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Operações da equipe (Etapa 4, rotas da Etapa S2 em {@code /app/api}): solicitação e conversa são independentes
 * (ADR-0011); transição inválida {@code 409}; desfecho ausente {@code 422} (DA-34).
 */
@IntegrationTest
class StaffOperationsIT {

    @Value("${local.server.port}")
    int port;

    @Autowired
    FakeAssistantModel model;

    @Autowired
    ServiceRequestRepository serviceRequests;

    @Autowired
    ApplicationContext context;

    ApiClient api;
    StaffClient staff;

    @BeforeEach
    void setUp() {
        model.reset();
        api = ApiClient.authenticated(port);
        staff = TestStaff.admin(port, context);
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
        return staff.get("/app/api/conversations/" + id).body().path("status").asString();
    }

    @Test
    void updatingTheRequest_doesNotChangeTheConversation() {
        String id = handedOff();
        String requestId = humanContact(id).id().value().toString();

        StaffClient.Response inProgress = staff.patch("/app/api/service-requests/" + requestId,
                Map.of("status", "IN_PROGRESS"));
        StaffClient.Response resolved = staff.patch("/app/api/service-requests/" + requestId,
                Map.of("status", "RESOLVED", "outcome", "HANDLED_OUTSIDE"));

        assertThat(inProgress.status()).isEqualTo(200);
        assertThat(inProgress.body().path("assigneeId").asString()).as("em andamento = com quem iniciou").isNotEmpty();
        assertThat(resolved.status()).isEqualTo(200);
        assertThat(resolved.body().path("outcome").asString()).isEqualTo("HANDLED_OUTSIDE");
        assertThat(conversationStatus(id)).as("a conversa continua aguardando").isEqualTo("WAITING_HUMAN");
    }

    @Test
    void closingOrReturningTheConversation_doesNotChangeRequests() {
        String a = handedOff();
        String b = handedOff();

        assertThat(staff.post("/app/api/conversations/" + a + "/close", Map.of()).body().path("status").asString())
                .isEqualTo("CLOSED");
        assertThat(staff.post("/app/api/conversations/" + b + "/return-to-assistant", Map.of()).body().path("status")
                .asString()).isEqualTo("OPEN");

        assertThat(humanContact(a).status().name()).isEqualTo("PENDING");
        assertThat(humanContact(b).status().name()).isEqualTo("PENDING");
    }

    @Test
    void invalidTransitions_are409() {
        String id = handedOff();
        String requestId = humanContact(id).id().value().toString();

        assertThat(staff.patch("/app/api/service-requests/" + requestId,
                Map.of("status", "RESOLVED", "outcome", "SCHEDULED_BY_STAFF")).status())
                .as("PENDING → RESOLVED sem IN_PROGRESS").isEqualTo(409);
        staff.post("/app/api/conversations/" + id + "/return-to-assistant", Map.of());
        assertThat(staff.post("/app/api/conversations/" + id + "/return-to-assistant", Map.of()).status())
                .as("já está OPEN").isEqualTo(409);
        staff.post("/app/api/conversations/" + id + "/close", Map.of());
        assertThat(staff.post("/app/api/conversations/" + id + "/close", Map.of()).status()).as("já encerrada")
                .isEqualTo(409);
        assertThat(staff.patch("/app/api/conversations/" + id + "/priority", Map.of("priority", "P4_SERVICE_REQUEST"))
                .status()).as("conversa encerrada").isEqualTo(409);
    }

    @Test
    void resolvingWithoutOutcome_is422_andOtherNeedsANote() {
        String id = handedOff();
        String requestId = humanContact(id).id().value().toString();
        staff.patch("/app/api/service-requests/" + requestId, Map.of("status", "IN_PROGRESS"));

        StaffClient.Response noOutcome = staff.patch("/app/api/service-requests/" + requestId,
                Map.of("status", "RESOLVED"));
        StaffClient.Response otherWithoutNote = staff.patch("/app/api/service-requests/" + requestId,
                Map.of("status", "RESOLVED", "outcome", "OTHER"));

        assertThat(noOutcome.status()).isEqualTo(422);
        assertThat(noOutcome.body().path("field").asString()).isEqualTo("outcome");
        assertThat(otherWithoutNote.status()).isEqualTo(422);
    }

    @Test
    void listingRequestsByStatus_andUnknownIds_are404() {
        String id = handedOff();

        JsonNode pending = staff.get("/app/api/service-requests?status=PENDING").body();
        boolean found = false;
        for (JsonNode item : pending) {
            found |= item.path("conversationId").asString().equals(id);
        }

        assertThat(found).isTrue();
        assertThat(staff.patch("/app/api/service-requests/" + UUID.randomUUID(), Map.of("status", "IN_PROGRESS"))
                .status()).isEqualTo(404);
        assertThat(staff.post("/app/api/conversations/" + UUID.randomUUID() + "/close", Map.of()).status())
                .isEqualTo(404);
    }

    @Test
    void staffRoutes_requireALoggedInEmployee_andTheOldServiceKeyRoutesAreGone() {
        StaffClient anonymous = new StaffClient(port);

        assertThat(anonymous.get("/app/api/conversations").status()).isEqualTo(401);
        assertThat(anonymous.get("/app/api/service-requests").status()).isEqualTo(401);
        assertThat(api.get("/api/service-requests").status()).as("a chave de serviço não lista mais").isEqualTo(404);
        assertThat(api.get("/app/api/conversations").status()).isEqualTo(401);
    }
}
