package com.clinicavet.assistant.alert;

import com.clinicavet.assistant.alert.application.AlertService;
import com.clinicavet.assistant.clinicknowledge.domain.Departments;
import com.clinicavet.assistant.platform.CurrentClinic;
import com.clinicavet.assistant.platform.RoleTemplate;
import com.clinicavet.assistant.servicerequest.domain.RequestType;
import com.clinicavet.assistant.servicerequest.domain.ServiceRequest;
import com.clinicavet.assistant.servicerequest.domain.ServiceRequestRepository;
import com.clinicavet.assistant.shared.domain.ConversationId;
import com.clinicavet.assistant.support.ApiClient;
import com.clinicavet.assistant.support.FakeAssistantModel;
import com.clinicavet.assistant.support.FictitiousClinicExtension;
import com.clinicavet.assistant.support.IntegrationTest;
import com.clinicavet.assistant.support.StaffClient;
import com.clinicavet.assistant.support.TestClock;
import com.clinicavet.assistant.support.TestStaff;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Alerta de trabalho esquecido (E3, achado A10). Relógio de teste; a varredura é chamada direto. Dados fictícios.
 * Os prazos são os provisórios de {@code assistant.handoff.deadlines} (no máximo 48 h) e
 * {@code assistant.alerts.request-unassigned-after} (24 h).
 */
@IntegrationTest
class WorkAlertIT {

    /** Acima de qualquer prazo provisório por nível. */
    private static final Duration PAST_ANY_DEADLINE = Duration.ofHours(49);

    @Value("${local.server.port}")
    int port;

    @Autowired
    ApplicationContext context;

    @Autowired
    FakeAssistantModel model;

    @Autowired
    TestClock clock;

    @Autowired
    AlertService alerts;

    @Autowired
    ServiceRequestRepository serviceRequests;

    @Autowired
    Departments departments;

    ApiClient api;
    StaffClient admin;

    @BeforeEach
    void setUp() {
        model.reset();
        api = ApiClient.authenticated(port);
        admin = TestStaff.admin(port, context);
    }

    private void sweep() {
        CurrentClinic.run(FictitiousClinicExtension.CLINIC, alerts::sweep);
    }

    private static JsonNode alertFor(StaffClient who, String subjectId) {
        for (JsonNode alert : who.get("/app/api/alerts").body()) {
            if (alert.path("subjectId").asString().equals(subjectId)) {
                return alert;
            }
        }
        return null;
    }

    private List<String> auditActions(String targetId) {
        List<String> actions = new ArrayList<>();
        for (JsonNode item : admin.get("/app/api/audit?targetId=" + targetId).body().path("items")) {
            actions.add(item.path("action").asString());
        }
        return actions;
    }

    private UUID departmentOf(Departments.Kind kind) {
        return CurrentClinic.call(FictitiousClinicExtension.CLINIC, () -> departments.list().stream()
                .filter(d -> d.kind() == kind).findFirst().orElseThrow().id());
    }

    @Test
    void waitingConversation_raisesAfterTheDeadline_escalates_isAcknowledged_andClosesWhenAssumed() {
        String id = api.openConversation("ficticio-alerta-" + UUID.randomUUID());
        api.sendMessage(id, "quero falar com um atendente");

        sweep();
        assertThat(alertFor(admin, id)).as("ainda no prazo").isNull();

        clock.advance(PAST_ANY_DEADLINE);
        sweep();
        JsonNode alert = alertFor(admin, id);
        assertThat(alert).isNotNull();
        assertThat(alert.path("kind").asString()).isEqualTo("CONVERSATION_WAITING");
        assertThat(alert.path("escalated").asBoolean()).isFalse();
        assertThat(alert.path("waitingMinutes").asLong()).isGreaterThanOrEqualTo(PAST_ANY_DEADLINE.toMinutes());

        // "Atendimento humano" é o mesmo trabalho: não gera um segundo alerta.
        String humanContact = serviceRequests.findByConversation(new ConversationId(UUID.fromString(id))).stream()
                .filter(r -> r.type() == RequestType.HUMAN_CONTACT).findFirst().orElseThrow().id().value().toString();
        assertThat(alertFor(admin, humanContact)).isNull();

        // Antes de escalar, só o setor da conversa (Recepção) vê.
        StaffClient clinical = TestStaff.member(port, context, FictitiousClinicExtension.CLINIC,
                RoleTemplate.VETERINARIAN, Set.of(departmentOf(Departments.Kind.CLINICAL)));
        assertThat(alertFor(clinical, id)).isNull();
        // Quem não vê conversas nunca recebe alerta de conversa.
        StaffClient groomer = TestStaff.member(port, context, FictitiousClinicExtension.CLINIC, RoleTemplate.GROOMER,
                null);

        clock.advance(PAST_ANY_DEADLINE);
        sweep();
        assertThat(alertFor(admin, id).path("escalated").asBoolean()).isTrue();
        assertThat(alertFor(clinical, id)).as("escalado: toda a equipe vê").isNotNull();
        assertThat(alertFor(groomer, id)).isNull();

        String alertId = alert.path("id").asString();
        assertThat(clinical.post("/app/api/alerts/" + alertId + "/acknowledge", Map.of()).status()).isEqualTo(200);
        assertThat(alertFor(admin, id).path("acknowledgedByName").asString()).isNotBlank();
        assertThat(alertFor(admin, id).path("acknowledgedAt").isNull()).isFalse();

        // "Recebi" não resolve: vencido mais um prazo, o alerta volta sem o "Recebi".
        clock.advance(PAST_ANY_DEADLINE);
        sweep();
        assertThat(alertFor(admin, id).path("acknowledgedAt").isNull()).isTrue();

        // Quem resolve é assumir a conversa: o alerta some na hora e fecha na varredura.
        assertThat(admin.post("/app/api/conversations/" + id + "/assume", Map.of()).status()).isEqualTo(200);
        assertThat(alertFor(admin, id)).isNull();
        sweep();
        assertThat(auditActions(id)).contains("alert.raised", "alert.escalated", "alert.acknowledged", "alert.closed");
    }

    @Test
    void unassignedRequest_raisesAfter24h_andDisappearsWhenSomeoneTakesIt() {
        String id = api.openConversation("ficticio-alerta-" + UUID.randomUUID());
        model.thenTurn("Não tenho essa informação; a equipe pode confirmar.", "CLINIC_INFORMATION", List.of(), true);
        api.sendMessage(id, "Vocês têm estacionamento?");
        ServiceRequest request = serviceRequests.findByConversation(new ConversationId(UUID.fromString(id))).stream()
                .filter(r -> r.type() == RequestType.INFORMATION).findFirst().orElseThrow();
        String requestId = request.id().value().toString();

        clock.advance(Duration.ofHours(23));
        sweep();
        assertThat(alertFor(admin, requestId)).isNull();

        clock.advance(Duration.ofHours(2));
        sweep();
        JsonNode alert = alertFor(admin, requestId);
        assertThat(alert).isNotNull();
        assertThat(alert.path("kind").asString()).isEqualTo("REQUEST_UNASSIGNED");
        assertThat(alert.path("title").asString()).isEqualTo("Informação");

        assertThat(admin.post("/app/api/service-requests/" + requestId + "/assign", Map.of()).status()).isEqualTo(200);
        assertThat(alertFor(admin, requestId)).isNull();
    }

    @Test
    void humanContactRequest_alertsOnItsOwn_onlyWhenTheConversationIsNoLongerWaiting() {
        String id = api.openConversation("ficticio-alerta-" + UUID.randomUUID());
        api.sendMessage(id, "quero falar com um atendente");
        String humanContact = serviceRequests.findByConversation(new ConversationId(UUID.fromString(id))).stream()
                .filter(r -> r.type() == RequestType.HUMAN_CONTACT).findFirst().orElseThrow().id().value().toString();
        // A conversa volta ao assistente, mas ninguém pegou a solicitação: é trabalho esquecido.
        assertThat(admin.post("/app/api/conversations/" + id + "/return-to-assistant", Map.of()).status()).isEqualTo(200);

        clock.advance(Duration.ofHours(25));
        sweep();

        assertThat(alertFor(admin, id)).as("a conversa não está mais aguardando").isNull();
        assertThat(alertFor(admin, humanContact)).isNotNull();
        assertThat(alertFor(admin, humanContact).path("title").asString()).isEqualTo("Atendimento humano");
    }

    @Test
    void acknowledgingAnAlertYouCannotSee_is404() {
        assertThat(admin.post("/app/api/alerts/" + UUID.randomUUID() + "/acknowledge", Map.of()).status())
                .isEqualTo(404);
    }
}
