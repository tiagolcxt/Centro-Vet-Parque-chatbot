package com.clinicavet.assistant.conversation.api;

import com.clinicavet.assistant.conversation.application.AssistantModelException;
import com.clinicavet.assistant.support.ApiClient;
import com.clinicavet.assistant.support.FakeAssistantModel;
import com.clinicavet.assistant.support.IntegrationTest;
import com.clinicavet.assistant.support.StaffClient;
import com.clinicavet.assistant.support.TestStaff;
import com.clinicavet.assistant.support.TurnJson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * D9 (revisão pré-piloto 3.2): risco clínico, tipo da demanda e prioridade da fila, separados. O nível P1–P6 continua
 * como motor interno; estes testes conferem o que a equipe vê e como as filas ordenam.
 */
@IntegrationTest
class PriorityDimensionsIT {

    @Value("${local.server.port}")
    int port;

    @Autowired
    FakeAssistantModel model;

    @Autowired
    ApplicationContext context;

    ApiClient tutor;
    StaffClient admin;

    @BeforeEach
    void setUp() {
        model.reset();
        tutor = ApiClient.authenticated(port);
        admin = TestStaff.admin(port, context);
    }

    private static String contact() {
        return "d9-" + UUID.randomUUID();
    }

    private JsonNode detail(String conversation) {
        return admin.get("/app/api/conversations/" + conversation).body();
    }

    @Test
    void riskIsNotAssessed_whileTheModelFails_andNoneIdentified_afterAValidTurn() {
        String failing = tutor.openConversation(contact());
        model.thenFail(AssistantModelException.Kind.TRANSIENT).thenFail(AssistantModelException.Kind.TRANSIENT);
        tutor.sendMessage(failing, "Oi");
        assertThat(detail(failing).path("clinicalRisk").asString()).isEqualTo("NOT_ASSESSED");
        assertThat(detail(failing).path("operationalPriority").asString()).isEqualTo("NORMAL");

        String fine = tutor.openConversation(contact());
        model.thenReply("Olá! Como posso ajudar?", "GREETING");
        tutor.sendMessage(fine, "Oi");
        assertThat(detail(fine).path("clinicalRisk").asString()).isEqualTo("NONE_IDENTIFIED");
    }

    @Test
    void possibleEmergency_goesToTheTopOfTheQueue_asImmediate() {
        String routine = tutor.openConversation(contact());
        model.thenReply("Entendi.", "OTHER");
        tutor.sendMessage(routine, "Quero falar com um atendente sobre uma dúvida");

        String emergency = tutor.openConversation(contact());
        model.thenJson(TurnJson.of("Entendo.", "MEDICAL_CONCERN").emergency().priority("P1_POSSIBLE_EMERGENCY"));
        tutor.sendMessage(emergency, "Meu cachorro está convulsionando");

        List<String> order = new ArrayList<>();
        JsonNode inbox = admin.get("/app/api/conversations?filter=WAITING").body();
        for (JsonNode item : inbox) {
            order.add(item.path("id").asString());
            if (item.path("id").asString().equals(emergency)) {
                assertThat(item.path("clinicalRisk").asString()).isEqualTo("POSSIBLE_EMERGENCY");
                assertThat(item.path("operationalPriority").asString()).isEqualTo("IMMEDIATE");
            }
        }
        assertThat(order).contains(emergency, routine);
        assertThat(order.indexOf(emergency)).isLessThan(order.indexOf(routine));
    }

    @Test
    void staffCanFixTheQueuePriority_andGoBackToTheRule_withAudit() {
        String id = tutor.openConversation(contact());
        model.thenReply("Entendi.", "OTHER");
        tutor.sendMessage(id, "Quero falar com um atendente");

        JsonNode raised = admin.patch("/app/api/conversations/" + id + "/queue-priority", Map.of("priority", "HIGH"))
                .body();
        assertThat(raised.path("operationalPriority").asString()).isEqualTo("HIGH");
        assertThat(raised.path("operationalOverride").asString()).isEqualTo("HIGH");
        assertThat(raised.path("clinicalRisk").asString()).as("não muda o risco").isEqualTo("NONE_IDENTIFIED");

        java.util.HashMap<String, Object> back = new java.util.HashMap<>();
        back.put("priority", null);
        JsonNode reset = admin.patch("/app/api/conversations/" + id + "/queue-priority", back).body();
        assertThat(reset.path("operationalOverride").isNull()).isTrue();

        JsonNode audit = admin.get("/app/api/audit?action=conversation.queue_priority_changed&targetId=" + id).body();
        assertThat(audit.path("items")).hasSize(2);
    }

    @Test
    void staffChangesTheRisk_byName() {
        String id = tutor.openConversation(contact());
        model.thenReply("Olá!", "GREETING");
        tutor.sendMessage(id, "Oi");
        JsonNode changed = admin.patch("/app/api/conversations/" + id + "/priority",
                Map.of("risk", "URGENT", "note", "tutor ligou relatando piora")).body();
        assertThat(changed.path("clinicalRisk").asString()).isEqualTo("URGENT");
        assertThat(changed.path("operationalPriority").asString()).isEqualTo("HIGH");
        assertThat(admin.patch("/app/api/conversations/" + id + "/priority", Map.of("risk", "NOT_ASSESSED")).status())
                .isEqualTo(422);
    }

    @Test
    void requestsCarryTheDemandType_andStaffCanCorrectIt() {
        String id = tutor.openConversation(contact());
        model.thenJson(TurnJson.of("Sinto muito pelo ocorrido.", "COMPLAINT"));
        tutor.sendMessage(id, "Quero falar com um atendente, quero fazer uma reclamação do atendimento");

        JsonNode request = admin.get("/app/api/conversations/" + id).body().path("requests").path(0);
        String requestId = request.path("id").asString();
        JsonNode listed = null;
        for (JsonNode r : admin.get("/app/api/service-requests").body()) {
            if (r.path("id").asString().equals(requestId)) {
                listed = r;
            }
        }
        assertThat(listed).isNotNull();
        assertThat(listed.path("demandType").asString()).isEqualTo("COMPLAINT");
        assertThat(listed.path("clinicalRisk").asString()).isNotBlank();
        assertThat(listed.path("operationalPriority").asString()).isNotBlank();

        JsonNode fixed = admin.patch("/app/api/service-requests/" + requestId + "/demand-type",
                Map.of("demandType", "ADMINISTRATIVE_INFO")).body();
        assertThat(fixed.path("demandType").asString()).isEqualTo("ADMINISTRATIVE_INFO");
        assertThat(admin.get("/app/api/audit?action=request.reclassified&targetId=" + requestId).body().path("items"))
                .hasSize(1);
    }
}
