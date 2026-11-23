package com.clinicavet.assistant.conversation.api;

import com.clinicavet.assistant.clinicknowledge.domain.Departments;
import com.clinicavet.assistant.servicerequest.domain.RequestType;
import com.clinicavet.assistant.servicerequest.domain.ServiceRequestRepository;
import com.clinicavet.assistant.shared.domain.ConversationId;
import com.clinicavet.assistant.support.ApiClient;
import com.clinicavet.assistant.support.FakeAssistantModel;
import com.clinicavet.assistant.support.FictitiousClinicExtension;
import com.clinicavet.assistant.support.IntegrationTest;
import com.clinicavet.assistant.support.TurnJson;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Etapa S5: setor pelas regras (RF-S37, RF-S61), texto de encaminhamento com o setor (RF-S62) e níveis de ativação
 * (ADR-0025, RF-S64). O nível é da clínica fictícia e volta ao 3 depois de cada teste (outros testes contam com ele).
 */
@IntegrationTest
class ActivationAndRoutingIT {

    @Value("${local.server.port}")
    int port;

    @Autowired
    FakeAssistantModel model;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    Departments departments;

    @Autowired
    ServiceRequestRepository requests;

    ApiClient api;

    @BeforeEach
    void setUp() {
        model.reset();
        departments.ensureDefaults();
        api = ApiClient.authenticated(port);
    }

    @AfterEach
    void restoreLevel() {
        level(3);
    }

    private void level(int value) {
        jdbc.update("UPDATE clinic SET activation_level = ? WHERE id = UUID_TO_BIN(?)", value,
                FictitiousClinicExtension.CLINIC.toString());
    }

    private static String contact() {
        return "s5-" + UUID.randomUUID();
    }

    private UUID clinical() {
        return departments.list().stream().filter(d -> d.kind() == Departments.Kind.CLINICAL).findFirst()
                .orElseThrow().id();
    }

    @Test
    void healthConcern_waitsInTheClinicalDepartment_andTheTutorIsToldWhere() {
        String id = api.openConversation(contact());
        model.thenJson(TurnJson.of("Entendo.", "TREATMENT_FOLLOW_UP").priority("P3_CLINICAL_FOLLOW_UP"));

        JsonNode body = api.sendMessage(id, "Quero falar com um atendente sobre o remédio que a veterinária passou")
                .body();

        assertThat(body.path("status").asString()).isEqualTo("WAITING_HUMAN");
        assertThat(body.path("reply").asString()).contains("encaminhada para o setor Clínica");
        assertThat(body.path("reply").asString()).doesNotContainIgnoringCase("avisei").doesNotContainIgnoringCase("notifiquei");
        assertThat(requests.findByConversation(new ConversationId(UUID.fromString(id))))
                .filteredOn(r -> r.type() == RequestType.HUMAN_CONTACT)
                .singleElement().satisfies(r -> {
                    assertThat(r.departmentId()).contains(clinical());
                    // H3: a equipe lê o motivo em português, não o código interno.
                    assertThat(r.summary()).hasValueSatisfying(s -> assertThat(s).startsWith("Pediu uma pessoa: ")
                            .doesNotContain("EXPLICIT_REQUEST"));
                });
        UUID waiting = jdbc.queryForObject("SELECT BIN_TO_UUID(waiting_department_id) FROM conversation WHERE id = "
                + "UUID_TO_BIN(?)", UUID.class, id);
        assertThat(waiting).isEqualTo(clinical());
    }

    @Test
    void operationsIndicators_showWaiting_openByDepartment_andTheLevel() {
        String id = api.openConversation(contact());
        model.thenJson(TurnJson.of("Entendo.", "TREATMENT_FOLLOW_UP").priority("P3_CLINICAL_FOLLOW_UP"));
        api.sendMessage(id, "Quero falar com um atendente sobre o remédio do meu gato");
        level(2);

        com.clinicavet.assistant.support.StaffClient admin = com.clinicavet.assistant.support.TestStaff.admin(port,
                context);
        JsonNode ops = admin.get("/app/api/metrics/operations?days=7").body();
        assertThat(ops.path("waitingNow").asLong()).isPositive();
        assertThat(ops.path("activationLevel").asInt()).isEqualTo(2);
        assertThat(ops.path("activationLabel").asString()).isEqualTo("Informa");
        JsonNode clinic = null;
        for (JsonNode d : ops.path("openByDepartment")) {
            if (d.path("departmentId").asString().equals(clinical().toString())) {
                clinic = d;
            }
        }
        assertThat(clinic).isNotNull();
        assertThat(clinic.path("open").asLong()).isPositive();
        assertThat(admin.get("/app/api/channel/usage?days=7").status()).isEqualTo(200);
    }

    @Autowired
    org.springframework.context.ApplicationContext context;

    @Test
    void customersWithoutSearch_listWhoTalkedLast() {
        String id = api.openConversation(contact());
        model.thenJson(TurnJson.of("Prazer, Joana.", "GREETING").tutorName("Joana Fictícia Recente"));
        api.sendMessage(id, "Oi, sou a Joana Fictícia Recente");

        JsonNode recent = com.clinicavet.assistant.support.TestStaff.admin(port, context).get("/app/api/customers").body();
        assertThat(recent.isArray()).isTrue();
        assertThat(recent.get(0).path("name").asString()).isEqualTo("Joana Fictícia Recente");
    }

    @Test
    void urgentCase_staysInTheDefaultDepartment_whateverTheSubject() {
        String id = api.openConversation(contact());
        model.thenJson(TurnJson.of("Entendo.", "MEDICAL_CONCERN").priority("P2_URGENT"));

        JsonNode body = api.sendMessage(id, "Meu cachorro está mancando desde ontem").body();

        assertThat(body.path("status").asString()).isEqualTo("WAITING_HUMAN");
        assertThat(body.path("reply").asString()).contains("encaminhada para o setor Recepção");
    }

    @Test
    void level1_neverSendsTheModelText_andForwardsEverything() {
        level(1);
        String id = api.openConversation(contact());
        model.thenReply("Olá! Como posso ajudar?", "GREETING");

        JsonNode body = api.sendMessage(id, "Oi, bom dia").body();

        assertThat(body.path("reply").asString()).doesNotContain("Como posso ajudar?")
                .contains("encaminhada para o setor Recepção");
        assertThat(body.path("status").asString()).isEqualTo("WAITING_HUMAN");
        assertThat(body.path("handoffReason").asString()).isEqualTo("CLINIC_RULE");

        // Depois, só confirma o recebimento: o texto do modelo continua fora.
        model.thenReply("Abrimos amanhã.", "CLINIC_INFORMATION");
        JsonNode next = api.sendMessage(id, "Vocês abrem amanhã?").body();
        assertThat(next.path("reply").asString()).doesNotContain("Abrimos amanhã.");
    }

    @Test
    void level1_emergencyGuidanceStillComesFirst() {
        level(1);
        String id = api.openConversation(contact());
        model.thenJson(TurnJson.of("Entendo.", "MEDICAL_CONCERN").emergency().priority("P1_POSSIBLE_EMERGENCY"));

        JsonNode body = api.sendMessage(id, "Meu cachorro comeu chocolate e está convulsionando").body();

        assertThat(body.path("priority").asString()).isEqualTo("P1_POSSIBLE_EMERGENCY");
        assertThat(body.path("reply").asString()).contains("procure atendimento veterinário imediatamente");
        assertThat(body.path("status").asString()).isEqualTo("WAITING_HUMAN");
    }

    @Test
    void level2_answersQuestionsAboutTheClinic_butForwardsRequests() {
        level(2);
        String id = api.openConversation(contact());
        model.thenReply("Olá! Como posso ajudar?", "GREETING");
        JsonNode info = api.sendMessage(id, "Oi").body();
        assertThat(info.path("reply").asString()).contains("Como posso ajudar?");
        assertThat(info.path("status").asString()).isEqualTo("OPEN");

        model.thenJson(TurnJson.of("Claro, qual o nome do pet?", "APPOINTMENT_REQUEST"));
        JsonNode request = api.sendMessage(id, "Quero marcar uma consulta").body();
        assertThat(request.path("reply").asString()).doesNotContain("qual o nome do pet")
                .contains("encaminhada para o setor");
        assertThat(request.path("status").asString()).isEqualTo("WAITING_HUMAN");
        assertThat(requests.findByConversation(new ConversationId(UUID.fromString(id))))
                .extracting(r -> r.type()).contains(RequestType.HUMAN_CONTACT).doesNotContain(RequestType.APPOINTMENT);
    }

    @Test
    void level3_keepsTheFullBehaviour() {
        String id = api.openConversation(contact());
        model.thenJson(TurnJson.of("Claro, qual o nome do pet?", "APPOINTMENT_REQUEST"));
        JsonNode body = api.sendMessage(id, "Quero marcar uma consulta").body();
        assertThat(body.path("reply").asString()).contains("qual o nome do pet");
        assertThat(body.path("status").asString()).isEqualTo("OPEN");
    }
}
