package com.clinicavet.assistant.conversation.api;

import com.clinicavet.assistant.platform.RoleTemplate;
import com.clinicavet.assistant.support.ApiClient;
import com.clinicavet.assistant.support.FakeAssistantModel;
import com.clinicavet.assistant.support.FictitiousClinicExtension;
import com.clinicavet.assistant.support.IntegrationTest;
import com.clinicavet.assistant.support.StaffClient;
import com.clinicavet.assistant.support.TestClock;
import com.clinicavet.assistant.support.TestStaff;
import com.clinicavet.assistant.support.TurnJson;
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
 * Caixa de entrada e filas por setor (Etapa S2; RF-S20 a RF-S37; DA-47; RN-S03, RN-S05 a RN-S07, RN-S10, RN-S11).
 */
@IntegrationTest
class InboxIT {

    @Value("${local.server.port}")
    int port;

    @Autowired
    FakeAssistantModel model;

    @Autowired
    TestClock clock;

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

    private String handedOff() {
        String id = tutor.openConversation("ficticio-" + UUID.randomUUID());
        tutor.sendMessage(id, "quero falar com um atendente");
        return id;
    }

    private UUID department(String name) {
        for (JsonNode d : admin.get("/app/api/departments").body()) {
            if (d.path("name").asString().equals(name)) {
                return UUID.fromString(d.path("id").asString());
            }
        }
        throw new AssertionError(name);
    }

    private StaffClient member(RoleTemplate role, String department) {
        return TestStaff.member(port, context, FictitiousClinicExtension.CLINIC, role, Set.of(department(department)));
    }

    private JsonNode detail(StaffClient staff, String id) {
        return staff.get("/app/api/conversations/" + id).body();
    }

    private static List<String> ids(JsonNode list) {
        List<String> ids = new ArrayList<>();
        list.forEach(item -> ids.add(item.path("id").asString()));
        return ids;
    }

    private static List<String> authors(JsonNode detail) {
        List<String> authors = new ArrayList<>();
        detail.path("messages").forEach(m -> authors.add(m.path("author").asString()));
        return authors;
    }

    @Test
    void assume_stopsTheAssistant_tellsTheTutorWhoTookIt_andOnlyOneCanHoldIt() {
        String id = handedOff();
        StaffClient reception = member(RoleTemplate.RECEPTION, "Recepção");
        StaffClient other = member(RoleTemplate.RECEPTION, "Recepção");

        JsonNode assumed = reception.post("/app/api/conversations/" + id + "/assume", Map.of()).body();
        StaffClient.Response second = other.post("/app/api/conversations/" + id + "/assume", Map.of());

        assertThat(assumed.path("status").asString()).isEqualTo("ASSIGNED");
        String lastToTutor = assumed.path("messages").get(assumed.path("messages").size() - 1).path("content")
                .asString();
        assertThat(lastToTutor).contains("Fictício Secretária", "Recepção", "vai continuar")
                .doesNotContainIgnoringCase("avis").doesNotContainIgnoringCase("notific");
        assertThat(second.status()).isEqualTo(409);
        assertThat(second.body().path("detail").asString()).contains("Fictício Secretária");

        int before = authors(detail(reception, id)).size();
        ApiClient.Response turn = tutor.sendMessage(id, "Ainda estou aqui, alguém pode me ajudar?");
        assertThat(turn.status()).isEqualTo(200);
        assertThat(turn.body().path("reply").isNull()).as("o assistente não responde").isTrue();
        assertThat(authors(detail(reception, id))).hasSize(before + 1).last().isEqualTo("CUSTOMER");

        JsonNode replied = reception.post("/app/api/conversations/" + id + "/reply",
                Map.of("text", "Oi! Já estou vendo seu pedido.")).body();
        JsonNode last = replied.path("messages").get(replied.path("messages").size() - 1);
        assertThat(last.path("author").asString()).isEqualTo("STAFF");
        assertThat(last.path("authorName").asString()).isEqualTo("Fictício Secretária");
        assertThat(other.post("/app/api/conversations/" + id + "/reply", Map.of("text", "Oi")).status())
                .as("só quem assumiu responde").isEqualTo(409);
        JsonNode tutorView = tutor.get("/api/conversations/" + id + "/messages").body();
        assertThat(tutorView.toString()).contains("Já estou vendo seu pedido");
    }

    @Test
    void possibleEmergency_inAnAssignedConversation_raisesThePriority_withoutTheAssistantReplying() {
        String id = handedOff();
        admin.post("/app/api/conversations/" + id + "/assume", Map.of());

        JsonNode turn = tutor.sendMessage(id, "Ele está tendo uma convulsao agora").body();

        assertThat(turn.path("reply").isNull()).isTrue();
        assertThat(turn.path("priority").asString()).isEqualTo("P1_POSSIBLE_EMERGENCY");
        JsonNode inbox = admin.get("/app/api/conversations?filter=MINE").body();
        assertThat(inbox.get(0).path("id").asString()).as("no topo").isEqualTo(id);
        assertThat(inbox.get(0).path("status").asString()).isEqualTo("ASSIGNED");
    }

    @Test
    void transfer_needsAReason_movesToTheOtherSector_andTheScopeFollows() {
        String id = handedOff();
        StaffClient reception = member(RoleTemplate.RECEPTION, "Recepção");
        StaffClient vet = member(RoleTemplate.VETERINARIAN, "Clínica");
        assertThat(ids(reception.get("/app/api/conversations?filter=WAITING").body())).contains(id);
        assertThat(vet.get("/app/api/conversations/" + id).status()).as("não é do setor dele").isEqualTo(404);

        reception.post("/app/api/conversations/" + id + "/assume", Map.of());
        assertThat(reception.post("/app/api/conversations/" + id + "/transfer",
                Map.of("departmentId", department("Clínica"), "reason", " ")).status()).isEqualTo(422);
        StaffClient.Response moved = reception.post("/app/api/conversations/" + id + "/transfer",
                Map.of("departmentId", department("Clínica"), "reason", "Pergunta sobre sintoma (fictício)"));

        assertThat(moved.status()).as("transferida; a recepção não vê mais, então não há detalhe").isEqualTo(204);
        assertThat(reception.get("/app/api/conversations/" + id).status()).isEqualTo(404);
        JsonNode atVet = detail(vet, id);
        assertThat(atVet.path("status").asString()).isEqualTo("WAITING_HUMAN");
        assertThat(atVet.path("departmentId").asString()).isEqualTo(department("Clínica").toString());
        assertThat(atVet.path("notes").toString()).contains("Transferida para Clínica", "Pergunta sobre sintoma");
    }

    @Test
    void internalNotes_neverReachTheTutor() {
        String id = handedOff();

        admin.post("/app/api/conversations/" + id + "/notes", Map.of("text", "Nota interna fictícia: ligar depois"));

        assertThat(detail(admin, id).path("notes").toString()).contains("Nota interna fictícia");
        assertThat(tutor.get("/api/conversations/" + id + "/messages").body().toString())
                .doesNotContain("Nota interna fictícia");
    }

    @Test
    void returnToAssistant_fromAssigned_makesTheAssistantAnswerAgain() {
        String id = handedOff();
        admin.post("/app/api/conversations/" + id + "/assume", Map.of());

        JsonNode back = admin.post("/app/api/conversations/" + id + "/return-to-assistant",
                Map.of("reason", "Dúvida resolvida (fictício)")).body();
        model.thenJson(TurnJson.of("Claro, posso ajudar com isso.", "OTHER"));
        JsonNode turn = tutor.sendMessage(id, "Mais uma dúvida").body();

        assertThat(back.path("status").asString()).isEqualTo("OPEN");
        assertThat(turn.path("reply").asString()).contains("posso ajudar");
    }

    @Test
    void assignedConversation_doesNotExpire() {
        String id = handedOff();
        admin.post("/app/api/conversations/" + id + "/assume", Map.of());

        clock.advance(Duration.ofDays(3));
        JsonNode turn = tutor.sendMessage(id, "Voltei depois de uns dias").body();

        assertThat(turn.path("conversationId").asString()).isEqualTo(id);
        assertThat(turn.path("newConversation").asBoolean()).isFalse();
    }

    @Test
    void requests_assignTransferAndHistory_withSectorScope() {
        String id = handedOff();
        JsonNode request = detail(admin, id).path("requests").get(0);
        String requestId = request.path("id").asString();
        StaffClient groomer = member(RoleTemplate.GROOMER, "Pet shop");
        StaffClient reception = member(RoleTemplate.RECEPTION, "Recepção");

        assertThat(groomer.get("/app/api/service-requests/" + requestId).status()).isEqualTo(404);
        assertThat(ids(reception.get("/app/api/service-requests?unassigned=true").body())).contains(requestId);

        JsonNode assigned = reception.post("/app/api/service-requests/" + requestId + "/assign", Map.of()).body();
        assertThat(assigned.path("assigneeName").asString()).isEqualTo("Fictício Secretária");
        assertThat(ids(reception.get("/app/api/service-requests?mine=true").body())).contains(requestId);

        assertThat(reception.post("/app/api/service-requests/" + requestId + "/transfer",
                Map.of("departmentId", department("Pet shop"))).status()).as("sem motivo").isEqualTo(422);
        JsonNode moved = reception.post("/app/api/service-requests/" + requestId + "/transfer",
                Map.of("departmentId", department("Pet shop"), "reason", "É banho, não consulta (fictício)")).body();
        assertThat(moved.path("assigneeId").isNull()).isTrue();

        JsonNode atPetShop = groomer.get("/app/api/service-requests/" + requestId).body();
        assertThat(atPetShop.path("request").path("departmentId").asString())
                .isEqualTo(department("Pet shop").toString());
        assertThat(atPetShop.path("events").toString()).contains("ASSIGNED", "TRANSFERRED", "É banho");
        groomer.post("/app/api/service-requests/" + requestId + "/notes", Map.of("text", "Banho às 10h (fictício)"));
        JsonNode inProgress = groomer.patch("/app/api/service-requests/" + requestId,
                Map.of("status", "IN_PROGRESS")).body();
        assertThat(inProgress.path("assigneeName").asString()).isEqualTo("Fictício Tosador");
        assertThat(reception.get("/app/api/service-requests/" + requestId).status())
                .as("saiu do setor da recepção").isEqualTo(404);
    }

    @Test
    void disablingAnEmployee_returnsTheirWorkToTheSector() {
        String id = handedOff();
        String requestId = detail(admin, id).path("requests").get(0).path("id").asString();
        StaffClient reception = member(RoleTemplate.RECEPTION, "Recepção");
        String receptionId = reception.get("/app/api/me").body().path("id").asString();
        reception.post("/app/api/conversations/" + id + "/assume", Map.of());
        reception.patch("/app/api/service-requests/" + requestId, Map.of("status", "IN_PROGRESS"));

        assertThat(admin.post("/app/api/employees/" + receptionId + "/disable", Map.of()).status()).isEqualTo(204);

        JsonNode conversation = detail(admin, id);
        JsonNode request = admin.get("/app/api/service-requests/" + requestId).body().path("request");
        assertThat(conversation.path("status").asString()).isEqualTo("WAITING_HUMAN");
        assertThat(conversation.path("assigneeId").isNull()).isTrue();
        assertThat(request.path("status").asString()).isEqualTo("PENDING");
        assertThat(request.path("assigneeId").isNull()).isTrue();
    }

    @Test
    void customers_searchByPetName_andShowTheRecordWithProvenance() {
        String id = tutor.openConversation("ficticio-" + UUID.randomUUID());
        String pet = "Mingau" + UUID.randomUUID().toString().replaceAll("[^a-f]", "").substring(0, 4);
        model.thenJson(TurnJson.of("Prazer!", "OTHER").tutorName("Ana Fictícia").pet(pet, "CAT"));
        tutor.sendMessage(id, "Oi, sou a Ana Fictícia e meu gato é o " + pet);

        JsonNode found = admin.get("/app/api/customers?q=" + pet.toLowerCase()).body();
        assertThat(found).hasSize(1);
        JsonNode record = admin.get("/app/api/customers/" + found.get(0).path("id").asString()).body();

        assertThat(record.path("name").asString()).isEqualTo("Ana Fictícia");
        assertThat(record.path("pets").get(0).path("name").asString()).isEqualTo(pet);
        assertThat(record.path("provenance").toString()).contains("PET_NAME", "MENTIONED");
        assertThat(record.path("conversations").get(0).path("id").asString()).isEqualTo(id);
        assertThat(admin.get("/app/api/customers?q=a").body()).as("mínimo de 2 caracteres").isEmpty();
    }

    @Test
    void liveUpdates_requireASession() {
        assertThat(new StaffClient(port).get("/app/api/events").status()).isEqualTo(401);
    }
}
