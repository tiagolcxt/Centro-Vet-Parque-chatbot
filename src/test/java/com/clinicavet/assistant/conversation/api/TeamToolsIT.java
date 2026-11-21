package com.clinicavet.assistant.conversation.api;

import com.clinicavet.assistant.platform.RoleTemplate;
import com.clinicavet.assistant.support.ApiClient;
import com.clinicavet.assistant.support.FakeAssistantModel;
import com.clinicavet.assistant.support.FictitiousClinicExtension;
import com.clinicavet.assistant.support.IntegrationTest;
import com.clinicavet.assistant.support.StaffClient;
import com.clinicavet.assistant.support.TestStaff;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import tools.jackson.databind.JsonNode;

import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Respostas prontas (S3-16), indicadores básicos (S3-17) e pedidos em planilha (S3-18). */
@IntegrationTest
class TeamToolsIT {

    @Value("${local.server.port}")
    int port;

    @Autowired
    FakeAssistantModel model;

    @Autowired
    ApplicationContext context;

    StaffClient admin;
    ApiClient tutor;

    @BeforeEach
    void setUp() {
        model.reset();
        admin = TestStaff.admin(port, context);
        tutor = ApiClient.authenticated(port);
    }

    @Test
    void quickReplies_areManagedByConfig_andListedForWhoReplies() {
        String title = "Como chegar " + UUID.randomUUID().toString().substring(0, 8);
        StaffClient.Response created = admin.post("/app/api/quick-replies",
                Map.of("title", title, "body", "Ficamos na Rua Fictícia, 100."));
        assertThat(created.status()).isEqualTo(201);
        String id = created.body().path("id").asString();
        assertThat(admin.post("/app/api/quick-replies", Map.of("title", title, "body", "outro")).status()).isEqualTo(409);

        StaffClient reception = TestStaff.member(port, context, FictitiousClinicExtension.CLINIC, RoleTemplate.RECEPTION,
                null);
        assertThat(reception.get("/app/api/quick-replies").body()).anyMatch(r -> r.path("title").asString().equals(title));
        assertThat(reception.post("/app/api/quick-replies", Map.of("title", "x", "body", "y")).status()).isEqualTo(403);

        assertThat(admin.put("/app/api/quick-replies/" + id, Map.of("title", title, "body", "Novo texto")).body()
                .path("body").asString()).isEqualTo("Novo texto");
        assertThat(admin.delete("/app/api/quick-replies/" + id).status()).isEqualTo(204);
        assertThat(admin.get("/app/api/quick-replies").body()).noneMatch(r -> r.path("id").asString().equals(id));
    }

    @Test
    void basicMetrics_countMessagesRepliesAndRequests() {
        JsonNode before = admin.get("/app/api/metrics/basic?days=7").body();
        model.thenReply("Olá! Como posso ajudar?", "GREETING");
        String id = tutor.openConversation("ficticio-" + UUID.randomUUID());
        tutor.sendMessage(id, "Oi");
        tutor.sendMessage(id, "quero falar com um atendente");

        JsonNode after = admin.get("/app/api/metrics/basic?days=7").body();
        assertThat(after.path("received").asLong() - before.path("received").asLong()).isEqualTo(2);
        assertThat(after.path("conversations").asLong() - before.path("conversations").asLong()).isEqualTo(1);
        assertThat(after.path("answered").asLong() - before.path("answered").asLong()).isEqualTo(1);
        assertThat(after.path("requestsCreated").asLong()).isGreaterThan(before.path("requestsCreated").asLong());
        assertThat(after.path("medianFirstReplySeconds").isNull()).isFalse();
        assertThat(admin.get("/app/api/metrics/basic?days=0").status()).isEqualTo(422);
    }

    @Test
    void requestsSpreadsheet_needsExportPermission_andOpensInExcel() throws Exception {
        String id = tutor.openConversation("ficticio-" + UUID.randomUUID());
        tutor.sendMessage(id, "quero falar com um atendente");

        HttpResponse<String> csv = admin.raw("/app/api/service-requests/export.csv");
        assertThat(csv.statusCode()).isEqualTo(200);
        assertThat(csv.headers().firstValue("Content-Disposition").orElse("")).contains("pedidos-");
        assertThat(csv.body()).startsWith("﻿Criado em;Tipo;Estado;Setor");
        assertThat(csv.body().lines().count()).isGreaterThan(1);

        StaffClient reception = TestStaff.member(port, context, FictitiousClinicExtension.CLINIC, RoleTemplate.RECEPTION,
                null);
        assertThat(reception.raw("/app/api/service-requests/export.csv").statusCode()).isEqualTo(403);
    }

}
