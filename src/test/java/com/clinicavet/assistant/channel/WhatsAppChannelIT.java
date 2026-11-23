package com.clinicavet.assistant.channel;

import com.clinicavet.assistant.support.FakeAssistantModel;
import com.clinicavet.assistant.support.FakeWhatsAppApi;
import com.clinicavet.assistant.support.IntegrationTest;
import com.clinicavet.assistant.support.StaffClient;
import com.clinicavet.assistant.support.TestClock;
import com.clinicavet.assistant.support.TestStaff;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Canal WhatsApp de ponta a ponta (Etapa S4): webhook assinado → assistente → outbox → Cloud API falsa → avisos de
 * situação. Corpos no formato dos exemplos da documentação da Meta, só com números fictícios (prefixo 55009).
 */
@IntegrationTest
class WhatsAppChannelIT {

    static final String PHONE_NUMBER_ID = "100000000000001";

    @Value("${local.server.port}")
    int port;

    @Autowired
    ApplicationContext context;

    @Autowired
    FakeAssistantModel model;

    @Autowired
    FakeWhatsAppApi whatsApp;

    @Autowired
    Outbox outbox;

    @Autowired
    com.clinicavet.assistant.support.FakeAudioTranscriber audioTranscriber;

    @Autowired
    ChannelJobs jobs;

    @Autowired
    TestClock clock;

    @Autowired
    JdbcTemplate jdbc;

    StaffClient admin;
    final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void setUp() {
        model.reset();
        // Respostas que outros testes deixaram na fila saem agora, para não contar nas tentativas deste.
        while (outbox.dispatchDue() > 0) {
            // repete até esvaziar
        }
        whatsApp.reset();
        admin = TestStaff.admin(port, context);
        StaffClient.Response saved = admin.put("/app/api/channel", Map.of("phoneNumberId", PHONE_NUMBER_ID,
                "displayPhone", "+55 00 0000-0000", "wabaId", "200000000000001", "accessToken", "token-ficticio-1",
                "active", true));
        assertThat(saved.status()).isEqualTo(200);
    }

    @Test
    void webhookVerification_echoesTheChallenge_onlyWithTheRightToken() throws Exception {
        assertThat(get("/webhooks/whatsapp?hub.mode=subscribe&hub.verify_token=" + IntegrationTest.WHATSAPP_VERIFY_TOKEN
                + "&hub.challenge=12345")).satisfies(r -> {
            assertThat(r.statusCode()).isEqualTo(200);
            assertThat(r.body()).isEqualTo("12345");
        });
        assertThat(get("/webhooks/whatsapp?hub.mode=subscribe&hub.verify_token=errado&hub.challenge=1").statusCode())
                .isEqualTo(403);
    }

    @Test
    void unsignedOrWronglySignedWebhook_isRejected_andNothingIsStored() throws Exception {
        String contact = fictitiousNumber();
        String body = textMessage(contact, wamid(), "Olá");
        assertThat(post(body, null).statusCode()).isEqualTo(401);
        assertThat(post(body, WebhookSignature.sign(body.getBytes(StandardCharsets.UTF_8), "outro-segredo")).statusCode())
                .isEqualTo(401);
        assertThat(conversationOf(contact)).isNull();
    }

    @Test
    void tutorMessage_getsTheAssistantReply_sentThroughTheOutbox_withDeliveryStatus() throws Exception {
        String contact = fictitiousNumber();
        model.thenReply("Olá! Como posso ajudar?", "GREETING");

        assertThat(signedPost(textMessage(contact, wamid(), "Bom dia")).statusCode()).isEqualTo(200);

        String conversation = conversationOf(contact);
        assertThat(conversation).isNotNull();
        assertThat(model.lastRequest().customerMessage()).contains("Bom dia");
        assertThat(whatsApp.sentTo(contact)).as("nada sai antes do despachante").isEmpty();

        outbox.dispatchDue();
        List<FakeWhatsAppApi.Sent> sent = whatsApp.sentTo(contact);
        assertThat(sent).hasSize(1);
        assertThat(sent.getFirst().text()).contains("Como posso ajudar?");
        assertThat(sent.getFirst().phoneNumberId()).isEqualTo(PHONE_NUMBER_ID);

        JsonNode reply = lastAssistantMessage(conversation);
        assertThat(reply.path("delivery").path("status").asString()).isEqualTo("SENT");

        // A Meta avisa fora de ordem: "lida" antes de "entregue". A situação só avança.
        signedPost(status(sent.getFirst().wamid(), contact, "read", null));
        signedPost(status(sent.getFirst().wamid(), contact, "delivered", null));
        assertThat(lastAssistantMessage(conversation).path("delivery").path("status").asString()).isEqualTo("READ");

        JsonNode detail = admin.get("/app/api/conversations/" + conversation).body();
        assertThat(detail.path("channel").asString()).isEqualTo("WHATSAPP");
        assertThat(detail.path("replyWindowEndsAt").asString("")).isNotBlank();
    }

    @Test
    void sameMessageDeliveredTwice_isHandledOnce() throws Exception {
        String contact = fictitiousNumber();
        String body = textMessage(contact, wamid(), "Quero marcar banho");
        signedPost(body);
        signedPost(body);
        String conversation = conversationOf(contact);
        long fromTutor = messages(conversation).stream().filter(m -> m.path("author").asString().equals("CUSTOMER"))
                .count();
        assertThat(fromTutor).isEqualTo(1);
        assertThat(model.requests()).hasSize(1);
    }

    @Test
    void temporaryMetaFailure_isRetriedWithBackoff_thenMarkedNotSent_andCanBeResent() throws Exception {
        String contact = fictitiousNumber();
        whatsApp.failNext(contact, 5, "131000", true);
        signedPost(textMessage(contact, wamid(), "Oi"));
        String conversation = conversationOf(contact);

        for (int attempt = 1; attempt <= 4; attempt++) {
            outbox.dispatchDue();
            JsonNode delivery = lastAssistantMessage(conversation).path("delivery");
            assertThat(delivery.path("status").asString()).isEqualTo("PENDING");
            assertThat(delivery.path("attempts").asInt()).isEqualTo(attempt);
            assertThat(outbox.dispatchDue()).as("espera antes da próxima tentativa").isZero();
            clock.advance(Duration.ofMinutes(31));
        }
        outbox.dispatchDue();
        JsonNode failed = lastAssistantMessage(conversation);
        assertThat(failed.path("delivery").path("status").asString()).isEqualTo("FAILED");
        assertThat(failed.path("delivery").path("errorCode").asString()).isEqualTo("131000");
        assertThat(whatsApp.sentTo(contact)).isEmpty();

        String messageId = failed.path("id").asString();
        assertThat(admin.post("/app/api/channel/messages/" + messageId + "/resend", Map.of()).status()).isEqualTo(204);
        outbox.dispatchDue();
        assertThat(whatsApp.sentTo(contact)).hasSize(1);
        assertThat(lastAssistantMessage(conversation).path("delivery").path("status").asString()).isEqualTo("SENT");
    }

    @Test
    void permanentMetaFailure_isNotRetried() throws Exception {
        String contact = fictitiousNumber();
        whatsApp.failNext(contact, 1, "131026", false);
        signedPost(textMessage(contact, wamid(), "Oi"));
        outbox.dispatchDue();
        JsonNode delivery = lastAssistantMessage(conversationOf(contact)).path("delivery");
        assertThat(delivery.path("status").asString()).isEqualTo("FAILED");
        assertThat(delivery.path("attempts").asInt()).isEqualTo(1);
    }

    @Test
    void staffReply_isSent_andBlockedAfterThe24HourWindow() throws Exception {
        String contact = fictitiousNumber();
        signedPost(textMessage(contact, wamid(), "Preciso falar com alguém"));
        String conversation = conversationOf(contact);
        assertThat(admin.post("/app/api/conversations/" + conversation + "/assume", Map.of()).status()).isEqualTo(200);
        assertThat(admin.post("/app/api/conversations/" + conversation + "/reply", Map.of("text", "Oi, aqui é a equipe."))
                .status()).isEqualTo(200);
        outbox.dispatchDue();
        assertThat(whatsApp.sentTo(contact)).extracting(FakeWhatsAppApi.Sent::text).contains("Oi, aqui é a equipe.");

        clock.advance(Duration.ofHours(25));
        StaffClient.Response late = admin.post("/app/api/conversations/" + conversation + "/reply",
                Map.of("text", "Ainda está aí?"));
        assertThat(late.status()).isEqualTo(409);
        assertThat(late.body().path("detail").asString()).contains("24 horas");
    }

    @Test
    void replyFromTheClinicPhone_isRecordedInTheConversation_andNotResent() throws Exception {
        String contact = fictitiousNumber();
        signedPost(textMessage(contact, wamid(), "Oi"));
        String conversation = conversationOf(contact);
        outbox.dispatchDue();
        int before = whatsApp.sentTo(contact).size();

        signedPost(echo(contact, wamid(), "Respondi pelo celular"));

        JsonNode last = messages(conversation).stream()
                .filter(m -> m.path("author").asString().equals("BUSINESS_APP")).findFirst().orElseThrow();
        assertThat(last.path("authorName").asString()).isEqualTo("Celular da clínica");
        assertThat(last.path("content").asString()).isEqualTo("Respondi pelo celular");
        assertThat(model.requests()).as("o assistente não roda para eco").hasSize(1);
        outbox.dispatchDue();
        assertThat(whatsApp.sentTo(contact)).hasSize(before);
    }

    @Test
    void imageWithoutCaption_becomesAShortText_andTheFileIsAvailableToStaff() throws Exception {
        String contact = fictitiousNumber();
        signedPost(image(contact, wamid()));
        String conversation = conversationOf(contact);
        assertThat(model.lastRequest().customerMessage()).contains("[Imagem enviada sem texto]");

        JsonNode fromTutor = messages(conversation).stream()
                .filter(m -> m.path("author").asString().equals("CUSTOMER")).findFirst().orElseThrow();
        JsonNode attachment = fromTutor.path("attachment");
        assertThat(attachment.path("kind").asString()).isEqualTo("IMAGE");
        HttpResponse<String> file = admin.raw("/app/api/channel/media/" + attachment.path("ref").asString());
        assertThat(file.statusCode()).isEqualTo(200);
        assertThat(file.headers().firstValue("Content-Type")).hasValueSatisfying(t -> assertThat(t).startsWith("image/jpeg"));
    }

    @Test
    void voiceMessage_isTranscribed_andAnsweredAsText_withTheOriginalAvailable() throws Exception {
        String contact = fictitiousNumber();
        audioTranscriber.thenText("Queria saber o horário de amanhã", 4.2);
        signedPost(audio(contact, wamid()));

        assertThat(model.lastRequest().customerMessage()).contains("[Áudio transcrito] Queria saber o horário de amanhã");
        String conversation = conversationOf(contact);
        JsonNode fromTutor = messages(conversation).stream()
                .filter(m -> m.path("author").asString().equals("CUSTOMER")).findFirst().orElseThrow();
        assertThat(fromTutor.path("content").asString()).startsWith("[Áudio transcrito]");
        assertThat(fromTutor.path("attachment").path("kind").asString()).isEqualTo("AUDIO");
        assertThat(jdbc.queryForObject("SELECT audio_seconds FROM inbound_message WHERE contact = ?", Double.class,
                contact)).isEqualTo(4.2);
    }

    @Test
    void voiceMessage_withoutTranscription_stillReachesTheTeam() throws Exception {
        String contact = fictitiousNumber();
        audioTranscriber.reset();
        signedPost(audio(contact, wamid()));
        assertThat(model.lastRequest().customerMessage()).contains("[Áudio enviado");
    }

    @Test
    void messagesForUnknownNumbers_andUnknownStatuses_areIgnored() throws Exception {
        String contact = fictitiousNumber();
        String body = textMessage(contact, wamid(), "Oi").replace(PHONE_NUMBER_ID, "999999999999999");
        assertThat(signedPost(body).statusCode()).isEqualTo(200);
        assertThat(conversationOf(contact)).isNull();
        assertThat(signedPost(status("wamid.DESCONHECIDO", contact, "delivered", null)).statusCode()).isEqualTo(200);
    }

    @Test
    void channelSettings_neverReturnTheToken_andConnectionCanBeTested() {
        JsonNode view = admin.get("/app/api/channel").body();
        assertThat(view.path("configured").asBoolean()).isTrue();
        assertThat(view.path("tokenSaved").asBoolean()).isTrue();
        assertThat(view.toString()).doesNotContain("token-ficticio");
        String stored = jdbc.queryForObject("SELECT access_token_encrypted FROM channel_account WHERE phone_number_id = ?",
                String.class, PHONE_NUMBER_ID);
        assertThat(stored).startsWith("v1:").doesNotContain("token-ficticio");

        assertThat(admin.post("/app/api/channel/test", Map.of()).body().path("ok").asBoolean()).isTrue();
        // Trocar só o número, sem reenviar o token, mantém o token salvo.
        assertThat(admin.put("/app/api/channel", Map.of("phoneNumberId", PHONE_NUMBER_ID, "active", true)).status())
                .isEqualTo(200);
        assertThat(admin.post("/app/api/channel/test", Map.of()).body().path("ok").asBoolean()).isTrue();
    }

    @Test
    void stalledMessage_isPickedUpByTheSweep() throws Exception {
        String contact = fictitiousNumber();
        // Grava direto, como se o servidor tivesse caído antes de processar.
        InboundStore store = context.getBean(InboundStore.class);
        store.insert(com.clinicavet.assistant.support.FictitiousClinicExtension.CLINIC,
                new WebhookEvents.Inbound(PHONE_NUMBER_ID, wamid(), contact, WebhookEvents.Kind.TEXT, "Oi", null, null,
                        null, clock.instant()), clock.instant());
        assertThat(conversationOf(contact)).isNull();
        clock.advance(Duration.ofMinutes(2));
        jobs.sweepNow();
        assertThat(conversationOf(contact)).isNotNull();
    }

    // ---- apoio ----

    private static String fictitiousNumber() {
        return "55009" + ThreadLocalRandom.current().nextLong(10_000_000L, 99_999_999L);
    }

    private static String wamid() {
        return "wamid.FICTICIO." + UUID.randomUUID();
    }

    private String conversationOf(String contact) {
        return jdbc.queryForList("SELECT BIN_TO_UUID(id) FROM conversation WHERE channel = 'WHATSAPP' "
                + "AND external_contact_id = ? ORDER BY started_at DESC LIMIT 1", String.class, contact)
                .stream().findFirst().orElse(null);
    }

    private List<JsonNode> messages(String conversation) {
        JsonNode detail = admin.get("/app/api/conversations/" + conversation).body();
        List<JsonNode> list = new java.util.ArrayList<>();
        detail.path("messages").forEach(list::add);
        return list;
    }

    private JsonNode lastAssistantMessage(String conversation) {
        return messages(conversation).stream().filter(m -> m.path("author").asString().equals("ASSISTANT"))
                .reduce((a, b) -> b).orElseThrow();
    }

    private String timestamp() {
        return String.valueOf(clock.instant().getEpochSecond());
    }

    private String envelope(String field, String value) {
        return """
                {"object":"whatsapp_business_account","entry":[{"id":"200000000000001","changes":[{"value":%s,"field":"%s"}]}]}
                """.formatted(value, field).strip();
    }

    private String metadata() {
        return """
                "messaging_product":"whatsapp","metadata":{"display_phone_number":"550000000000","phone_number_id":"%s"}"""
                .formatted(PHONE_NUMBER_ID);
    }

    private String textMessage(String contact, String wamid, String text) {
        return envelope("messages", """
                {%s,"contacts":[{"profile":{"name":"Tutor Fictício"},"wa_id":"%s","user_id":"BR.FICTICIO"}],
                "messages":[{"from":"%s","from_user_id":"BR.FICTICIO","id":"%s","timestamp":"%s","type":"text",
                "text":{"body":"%s"}}]}""".formatted(metadata(), contact, contact, wamid, timestamp(), text));
    }

    private String image(String contact, String wamid) {
        return envelope("messages", """
                {%s,"contacts":[{"profile":{"name":"Tutor Fictício"},"wa_id":"%s"}],
                "messages":[{"from":"%s","id":"%s","timestamp":"%s","type":"image",
                "image":{"mime_type":"image/jpeg","sha256":"abc","id":"300000000000001"}}]}"""
                .formatted(metadata(), contact, contact, wamid, timestamp()));
    }

    private String audio(String contact, String wamid) {
        return envelope("messages", """
                {%s,"contacts":[{"profile":{"name":"Tutor Fictício"},"wa_id":"%s"}],
                "messages":[{"from":"%s","id":"%s","timestamp":"%s","type":"audio",
                "audio":{"mime_type":"audio/ogg; codecs=opus","sha256":"abc","id":"300000000000002","voice":true}}]}"""
                .formatted(metadata(), contact, contact, wamid, timestamp()));
    }

    private String status(String wamid, String contact, String status, String errorCode) {
        String errors = errorCode == null ? "" : ",\"errors\":[{\"code\":" + errorCode + ",\"title\":\"x\"}]";
        return envelope("messages", """
                {%s,"statuses":[{"id":"%s","status":"%s","timestamp":"%s","recipient_id":"%s"%s}]}"""
                .formatted(metadata(), wamid, status, timestamp(), contact, errors));
    }

    private String echo(String contact, String wamid, String text) {
        return envelope("smb_message_echoes", """
                {%s,"message_echoes":[{"from":"550000000000","to":"%s","id":"%s","timestamp":"%s","type":"text",
                "text":{"body":"%s"}}]}""".formatted(metadata(), contact, wamid, timestamp(), text));
    }

    private HttpResponse<String> signedPost(String body) throws Exception {
        return post(body, WebhookSignature.sign(body.getBytes(StandardCharsets.UTF_8), IntegrationTest.WHATSAPP_APP_SECRET));
    }

    private HttpResponse<String> post(String body, String signature) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/webhooks/whatsapp"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        if (signature != null) {
            request.header(WebhookSignature.HEADER, signature);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
