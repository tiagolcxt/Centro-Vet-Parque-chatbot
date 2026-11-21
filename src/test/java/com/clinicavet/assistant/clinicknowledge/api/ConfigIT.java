package com.clinicavet.assistant.clinicknowledge.api;

import com.clinicavet.assistant.platform.RoleTemplate;
import com.clinicavet.assistant.support.ApiClient;
import com.clinicavet.assistant.support.FakeAssistantModel;
import com.clinicavet.assistant.support.IntegrationTest;
import com.clinicavet.assistant.support.OtherClinic;
import com.clinicavet.assistant.support.StaffClient;
import com.clinicavet.assistant.support.TestStaff;
import com.clinicavet.assistant.support.TurnJson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Configuração pela web (Etapa S3; ADR-0026; RF-S11 a RF-S19). Os testes que mudam a configuração usam a segunda
 * clínica fictícia, para não mexer na base que os outros testes de integração usam.
 */
@IntegrationTest
class ConfigIT {

    private static final ObjectMapper JSON = JsonMapper.builder().build();
    private static final String PRICE_REPLY = "A consulta clínica custa R$ 170,00.";

    @Value("${local.server.port}")
    int port;

    @Autowired
    FakeAssistantModel model;

    @Autowired
    ApplicationContext context;

    @Autowired
    JdbcTemplate jdbc;

    StaffClient admin;
    ApiClient tutor;

    @BeforeEach
    void setUp() throws IOException {
        model.reset();
        OtherClinic.ensure(jdbc);
        admin = TestStaff.admin(port, context, OtherClinic.CLINIC);
        tutor = ApiClient.authenticated(port).withKey(IntegrationTest.OTHER_CLINIC_API_KEY);
        // Ponto de partida igual em todo teste: a base fictícia publicada, sem rascunho.
        admin.delete("/app/api/config/draft");
        assertThat(admin.post("/app/api/config/import/yaml",
                Map.of("content", Files.readString(Path.of("config/clinic-knowledge.ficticia.yml")))).status())
                .isEqualTo(200);
        assertThat(admin.post("/app/api/config/publish", Map.of("note", "base do teste")).status()).isEqualTo(200);
    }

    private JsonNode draft() {
        return admin.get("/app/api/config/draft").body();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> document(JsonNode version) {
        return JSON.convertValue(version.path("document"), Map.class);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> at(Map<String, Object> document, String... path) {
        Map<String, Object> node = document;
        for (String key : path) {
            node = (Map<String, Object>) node.get(key);
        }
        return node;
    }

    private StaffClient.Response save(JsonNode draft, Map<String, Object> document) {
        return admin.put("/app/api/config/draft",
                Map.of("document", document, "revision", draft.path("summary").path("revision").asLong()));
    }

    /** Duas vezes: uma resposta recusada pelo validador é refeita uma vez antes da resposta segura. */
    private void priceTwice() {
        model.thenJson(TurnJson.of(PRICE_REPLY, "PRICE_INQUIRY").refs("services.consulta"))
                .thenJson(TurnJson.of(PRICE_REPLY, "PRICE_INQUIRY").refs("services.consulta"));
    }

    private ApiClient.Response askPrice() {
        model.reset();
        priceTwice();
        return tutor.sendMessage(tutor.openConversation("ficticio-" + UUID.randomUUID()), "quanto custa a consulta?");
    }

    @Test
    void fictitiousClinic_wasLoadedFromTheFile_atStartup() {
        StaffClient fictitious = TestStaff.admin(port, context);
        JsonNode history = fictitious.get("/app/api/config/versions").body();
        assertThat(history).anyMatch(v -> v.path("status").asString().equals("PUBLISHED")
                && v.path("note").asString().startsWith("Carga inicial do arquivo"));
        assertThat(fictitious.get("/app/api/config/overview").body().path("validity")).isNotEmpty();
    }

    @Test
    void priceChange_isTestedInTheSimulator_publishedAndUsedByTheNextMessage_andAudited() {
        assertThat(askPrice().body().path("fallback").asBoolean())
                .as("R$ 170,00 ainda não está na base: o validador recusa").isTrue();

        JsonNode draft = draft();
        Map<String, Object> document = document(draft);
        at(document, "services", "consulta").put("price-cents", 17000);
        assertThat(save(draft, document).status()).isEqualTo(200);

        Long conversationsBefore = conversations();
        priceTwice();
        StaffClient.Response simulated = admin.post("/app/api/config/simulate",
                Map.of("text", "quanto custa a consulta?"));
        assertThat(simulated.status()).isEqualTo(200);
        assertThat(simulated.body().path("fallback").asBoolean()).isFalse();
        assertThat(simulated.body().path("reply").asString()).contains("R$ 170,00");
        assertThat(conversations()).as("o simulador não grava nada").isEqualTo(conversationsBefore);

        assertThat(askPrice().body().path("fallback").asBoolean())
                .as("rascunho não publicado não vale para o tutor").isTrue();

        assertThat(admin.get("/app/api/config/draft/changes").body())
                .anyMatch(c -> c.path("key").asString().equals("services.consulta.price-cents")
                        && c.path("before").asString().equals("R$ 150,00")
                        && c.path("after").asString().equals("R$ 170,00"));
        assertThat(admin.post("/app/api/config/publish", Map.of("note", "novo preço")).status()).isEqualTo(200);

        ApiClient.Response after = askPrice();
        assertThat(after.body().path("fallback").asBoolean()).isFalse();
        assertThat(after.body().path("reply").asString()).contains("R$ 170,00");
        assertThat(jdbc.queryForObject("SELECT summary FROM audit_event WHERE clinic_id = UUID_TO_BIN(?) "
                        + "AND action = 'config.published' ORDER BY occurred_at DESC LIMIT 1", String.class,
                IntegrationTest.OTHER_CLINIC)).contains("services.consulta.price-cents");
    }

    @Test
    void simulatedTime_changesTheOpeningHoursTheAssistantSees() {
        model.thenJson(TurnJson.of("Estamos fechados agora.", "CLINIC_INFORMATION").refs("hours"));
        admin.post("/app/api/config/simulate", Map.of("text", "vocês estão abertos?", "at", "2026-11-22T10:00"));
        String sunday = model.lastRequest().operational().opening().toString();
        model.thenJson(TurnJson.of("Estamos abertos.", "CLINIC_INFORMATION").refs("hours"));
        admin.post("/app/api/config/simulate", Map.of("text", "vocês estão abertos?", "at", "2026-11-23T10:00"));
        String monday = model.lastRequest().operational().opening().toString();
        assertThat(sunday).isNotEqualTo(monday);
        assertThat(model.lastRequest().operational().localNow().toLocalDate()).hasToString("2026-11-23");
    }

    @Test
    void invalidDraft_isRefused_withEveryProblem_andNothingChanges() {
        JsonNode draft = draft();
        Map<String, Object> document = document(draft);
        at(document, "identification").put("email", "sem-arroba");
        at(document, "hours").put("monday", List.of("19:00-08:00"));
        StaffClient.Response refused = save(draft, document);
        assertThat(refused.status()).isEqualTo(422);
        assertThat(refused.body().path("problems")).anyMatch(p -> p.asString().startsWith("identification.email:"))
                .anyMatch(p -> p.asString().startsWith("hours.monday:"));
        assertThat(admin.get("/app/api/config/draft/changes").body()).isEmpty();
        assertThat(admin.get("/app/api/config/overview").status()).as("a aplicação segue de pé").isEqualTo(200);
    }

    @Test
    void staleRevision_isAConflict() {
        JsonNode draft = draft();
        Map<String, Object> document = document(draft);
        at(document, "identification").put("access", "Estacionamento fictício na rua");
        assertThat(save(draft, document).status()).isEqualTo(200);
        assertThat(save(draft, document).status()).isEqualTo(409);
    }

    @Test
    void history_diff_andRestore() {
        JsonNode draft = draft();
        Map<String, Object> document = document(draft);
        at(document, "hours").put("saturday", List.of());
        save(draft, document);
        admin.post("/app/api/config/publish", Map.of());

        JsonNode history = admin.get("/app/api/config/versions").body();
        String current = history.get(0).path("id").asString();
        String previous = history.get(1).path("id").asString();
        assertThat(history.get(0).path("status").asString()).isEqualTo("PUBLISHED");
        assertThat(history.get(1).path("status").asString()).isEqualTo("ARCHIVED");
        assertThat(admin.get("/app/api/config/versions/" + previous + "/diff/" + current).body())
                .extracting(c -> c.path("key").asString() + " " + c.path("after").asString())
                .containsExactly("hours.saturday fechado");

        JsonNode restored = admin.post("/app/api/config/versions/" + previous + "/restore", Map.of()).body();
        assertThat(restored.path("summary").path("status").asString()).isEqualTo("DRAFT");
        assertThat(at(document(restored), "hours").get("saturday")).isEqualTo(List.of("08:00-12:00"));
    }

    @Test
    void permissions_overviewForEveryone_editAndPublishByProfile() {
        StaffClient reception = TestStaff.member(port, context, OtherClinic.CLINIC, RoleTemplate.RECEPTION, null);
        assertThat(reception.get("/app/api/config/overview").status()).isEqualTo(200);
        assertThat(reception.get("/app/api/config/draft").status()).isEqualTo(403);
        assertThat(reception.post("/app/api/config/publish", Map.of()).status()).isEqualTo(403);
        assertThat(reception.post("/app/api/config/simulate", Map.of("text", "oi")).status()).isEqualTo(403);
    }

    @Test
    void otherClinicsConfig_doesNotLeakIntoTheFictitiousClinic() {
        JsonNode draft = draft();
        Map<String, Object> document = document(draft);
        at(document, "services", "consulta").put("price-cents", 17000);
        save(draft, document);
        admin.post("/app/api/config/publish", Map.of());

        ApiClient fictitious = ApiClient.authenticated(port);
        priceTwice();
        ApiClient.Response response = fictitious.sendMessage(
                fictitious.openConversation("ficticio-" + UUID.randomUUID()), "quanto custa a consulta?");
        assertThat(response.body().path("fallback").asBoolean()).as("a outra clínica continua com R$ 150,00").isTrue();
    }

    @Test
    void everyBusinessTemplate_producesAValidDraft_thatTheSimulatorAccepts_withoutInventingData() {
        JsonNode list = admin.get("/app/api/config/templates").body();
        assertThat(list).hasSize(4);
        for (JsonNode template : list) {
            String type = template.path("type").asString();
            StaffClient.Response applied = admin.post("/app/api/config/templates/" + type + "/apply", Map.of());
            assertThat(applied.status()).as(type).isEqualTo(200);
            Map<String, Object> services = at(document(applied.body()), "services");
            template.path("services").forEach(service -> assertThat(services.values())
                    .anyMatch(e -> service.path("name").asString().equals(((Map<?, ?>) e).get("name"))));
            model.thenReply("Olá!", "GREETING");
            assertThat(admin.post("/app/api/config/simulate", Map.of("text", "oi")).status()).as(type).isEqualTo(200);
        }
        assertThat(at(document(draft()), "services")).as("\"Consulta clínica\" já existe com o id consulta")
                .doesNotContainKey("consulta-clinica");
        Map<String, Object> faq = at(document(draft()), "faq");
        assertThat(faq.values()).as("respostas novas ficam desconhecidas")
                .anyMatch(e -> ((Map<?, ?>) e).get("question").equals("Vocês atendem de madrugada?")
                        && ((Map<?, ?>) e).get("answer") == null);
        JsonNode departments = admin.get("/app/api/departments").body();
        assertThat(departments).anyMatch(d -> d.path("kind").asString().equals("CLINICAL") && d.path("active").asBoolean());
        assertThat(departments).anyMatch(d -> d.path("kind").asString().equals("PETSHOP") && !d.path("active").asBoolean());
        admin.post("/app/api/config/templates/CLINIC_WITH_PETSHOP/apply", Map.of());
    }

    @Test
    void servicesSheet_andApprovedFaq_goToTheDraft() {
        JsonNode result = admin.post("/app/api/config/import/services", Map.of("content",
                "nome;preço;setor\nConsulta clínica;R$ 180,00;clínica\nHidratação;45;banho e tosa\nX;abc;\n")).body();
        assertThat(result.path("added").asInt()).isEqualTo(1);
        assertThat(result.path("updated").asInt()).isEqualTo(1);
        assertThat(result.path("problems")).hasSize(1);
        Map<String, Object> services = at(document(result.path("draft")), "services");
        assertThat(services).doesNotContainKey("consulta-clinica");
        assertThat(((Map<?, ?>) services.get("consulta")).get("price-cents")).isEqualTo(18000);
        assertThat(((Map<?, ?>) services.get("hidratacao")).get("department")).isEqualTo("PETSHOP");

        JsonNode analysis = admin.post("/app/api/config/import/whatsapp", Map.of("files", List.of(
                "21/11/2026 09:00 - Tutor Fictício: vocês abrem domingo?\n21/11/2026 09:01 - Clínica: Não abrimos.")))
                .body();
        assertThat(analysis.path("suggestions")).isNotEmpty();
        assertThat(jdbcCount("SELECT COUNT(*) FROM message WHERE content LIKE '%abrem domingo%'"))
                .as("nada da conversa é gravado").isZero();

        JsonNode faq = admin.post("/app/api/config/import/faq", Map.of("items",
                List.of(Map.of("question", "Vocês abrem domingo?", "answer", "Não abrimos aos domingos.")))).body();
        assertThat(((Map<?, ?>) at(document(faq), "faq").get("voces-abrem-domingo")).get("answer"))
                .isEqualTo("Não abrimos aos domingos.");
    }

    private long jdbcCount(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }

    private Long conversations() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM conversation WHERE clinic_id = UUID_TO_BIN(?)", Long.class,
                IntegrationTest.OTHER_CLINIC);
    }
}
