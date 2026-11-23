package com.clinicavet.assistant.team;

import com.clinicavet.assistant.platform.ClinicId;
import com.clinicavet.assistant.platform.RoleTemplate;
import com.clinicavet.assistant.support.ApiClient;
import com.clinicavet.assistant.support.FakeAssistantModel;
import com.clinicavet.assistant.support.FictitiousClinicExtension;
import com.clinicavet.assistant.support.IntegrationTest;
import com.clinicavet.assistant.support.OtherClinic;
import com.clinicavet.assistant.support.StaffClient;
import com.clinicavet.assistant.support.TestStaff;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Consulta da auditoria (revisão pré-piloto, E2): só leitura, filtros, autoria e isolamento entre clínicas. */
@IntegrationTest
class AuditIT {

    @Value("${local.server.port}")
    int port;

    @Autowired
    ApplicationContext context;

    @Autowired
    FakeAssistantModel model;

    @Autowired
    JdbcTemplate jdbc;

    StaffClient admin;

    @BeforeEach
    void setUp() {
        model.reset();
        admin = TestStaff.admin(port, context);
    }

    private List<JsonNode> items(StaffClient client, String query) {
        StaffClient.Response response = client.get("/app/api/audit" + query);
        assertThat(response.status()).isEqualTo(200);
        List<JsonNode> list = new ArrayList<>();
        response.body().path("items").forEach(list::add);
        return list;
    }

    @Test
    void assumingAConversation_andClosingARequest_showWhoDidIt() {
        ApiClient tutor = ApiClient.authenticated(port);
        String conversation = tutor.openConversation("audit-" + UUID.randomUUID());
        tutor.sendMessage(conversation, "Quero falar com um atendente");
        assertThat(admin.post("/app/api/conversations/" + conversation + "/assume", Map.of()).status()).isEqualTo(200);
        String request = admin.get("/app/api/conversations/" + conversation).body().path("requests").path(0).path("id")
                .asString();
        assertThat(admin.patch("/app/api/service-requests/" + request, Map.of("status", "CANCELLED",
                "outcome", "DUPLICATE", "outcomeNote", "teste fictício")).status()).isEqualTo(200);

        List<JsonNode> conversationEvents = items(admin, "?action=conversation.&targetId=" + conversation);
        assertThat(conversationEvents).anySatisfy(e -> {
            assertThat(e.path("action").asString()).isEqualTo("conversation.assumed");
            assertThat(e.path("actorName").asString()).isNotBlank();
            assertThat(e.path("targetType").asString()).isEqualTo("conversation");
        });
        List<JsonNode> requestEvents = items(admin, "?action=request.&targetId=" + request);
        assertThat(requestEvents).anySatisfy(e -> {
            assertThat(e.path("action").asString()).isEqualTo("request.status_changed");
            assertThat(e.path("summary").asString()).contains("CANCELLED");
        });
        // Filtro por prefixo não mistura categorias.
        assertThat(items(admin, "?action=request.&targetId=" + conversation)).isEmpty();
    }

    @Test
    void readOnly_onlyWithPermission_andIsolatedPerClinic() {
        StaffClient reception = TestStaff.member(port, context, FictitiousClinicExtension.CLINIC, RoleTemplate.RECEPTION,
                null);
        assertThat(reception.get("/app/api/audit").status()).isEqualTo(403);

        // Não há rota de escrita, troca nem exclusão.
        assertThat(admin.post("/app/api/audit", Map.of("action", "forjado")).status()).isIn(403, 404, 405);
        assertThat(admin.delete("/app/api/audit").status()).isIn(403, 404, 405);

        OtherClinic.ensure(jdbc);
        StaffClient otherAdmin = TestStaff.admin(port, context, OtherClinic.CLINIC);
        String mine = items(admin, "?size=100").stream().map(e -> e.path("id").asString()).findFirst().orElseThrow();
        assertThat(items(otherAdmin, "?size=100")).noneMatch(e -> e.path("id").asString().equals(mine));
    }

    @Test
    void logins_showThePersonWhoLoggedIn_andCanBeLeftOut() {
        StaffClient person = TestStaff.member(port, context, FictitiousClinicExtension.CLINIC, RoleTemplate.RECEPTION,
                null);
        String email = person.get("/app/api/me").body().path("email").asString();
        String name = person.get("/app/api/me").body().path("displayName").asString();
        List<JsonNode> logins = items(admin, "?action=auth.login&size=100");
        assertThat(logins).anySatisfy(e -> assertThat(e.path("actorName").asString()).isEqualTo(name));
        assertThat(email).isNotBlank();
        assertThat(items(admin, "?withoutLogins=true&size=100"))
                .noneMatch(e -> e.path("action").asString().startsWith("auth."));
    }

    @Test
    void invalidPage_isRejected() {
        assertThat(admin.get("/app/api/audit?size=500").status()).isEqualTo(422);
        assertThat(admin.get("/app/api/audit?from=2026-11-20&to=2026-11-10").status()).isEqualTo(422);
    }
}
