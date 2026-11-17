package com.clinicavet.assistant.ai;

import com.clinicavet.assistant.conversation.application.AssistantModelException;
import com.clinicavet.assistant.conversation.application.AssistantRequest;
import com.clinicavet.assistant.conversation.application.ModelReply;
import com.clinicavet.assistant.conversation.domain.ConversationStatus;
import com.clinicavet.assistant.conversation.domain.Intent;
import com.clinicavet.assistant.conversation.domain.MessageAuthor;
import com.clinicavet.assistant.support.TestcontainersConfiguration;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * O adaptador real contra um <b>servidor local que imita a API da OpenAI</b> (nenhuma chamada sai da
 * máquina). Confere o que o adaptador envia e como trata cada resposta, inclusive o que o
 * {@code OpenAiCompatibilityProbeIT} não cobre: timeout configurado e ausência de retry do Spring AI.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "assistant.security.api-key=chave-de-teste-ficticia-0123456789abcdef",
        "assistant.conversation.expiration.open=30m",
        "assistant.conversation.expiration.waiting-human=2h",
        "spring.ai.openai.api-key=sk-ficticia-do-servidor-local",
        "spring.http.clients.read-timeout=2s"
})
@Import(TestcontainersConfiguration.class)
class SpringAiAssistantModelIT {

    private static final ObjectMapper JSON = JsonMapper.builder().build();
    private static final StubOpenAi STUB = new StubOpenAi();

    @DynamicPropertySource
    static void pointToStub(DynamicPropertyRegistry registry) {
        registry.add("spring.ai.openai.base-url", STUB::baseUrl);
    }

    @AfterAll
    static void stopStub() {
        STUB.stop();
    }

    @Autowired
    SpringAiAssistantModel model;

    private static final AssistantRequest REQUEST = new AssistantRequest(
            new AssistantRequest.OperationalContext(ConversationStatus.OPEN,
                    ZonedDateTime.of(2026, 11, 16, 9, 0, 0, 0, ZoneId.of("America/Sao_Paulo"))),
            List.of(new AssistantRequest.HistoryEntry(MessageAuthor.CUSTOMER, "Meu gato se chama Mingau."),
                    new AssistantRequest.HistoryEntry(MessageAuthor.ASSISTANT, "Que nome bonito!")),
            "Qual é o nome do meu gato?");

    @BeforeEach
    void reset() {
        STUB.reset();
    }

    @Test
    void sendsStructuredOutputRequest_withConfiguredParameters_andHistoryAsChatMessages() {
        STUB.respond(200, completion("{\\\"reply\\\":\\\"Mingau.\\\",\\\"intent\\\":\\\"PET_INFORMATION\\\"}", "stop", null), 0);

        ModelReply reply = model.respond(REQUEST);

        assertThat(reply.content()).isEqualTo("{\"reply\":\"Mingau.\",\"intent\":\"PET_INFORMATION\"}");
        assertThat(reply.finishReason()).isEqualToIgnoringCase("stop");
        assertThat(reply.model()).isEqualTo("gpt-6-luna");
        assertThat(reply.inputTokens()).isEqualTo(120);
        assertThat(reply.outputTokens()).isEqualTo(30);

        JsonNode sent = STUB.lastBody();
        assertThat(sent.path("model").asString()).isEqualTo("gpt-6-luna");
        assertThat(sent.path("reasoning_effort").asString()).isEqualTo("low");
        assertThat(sent.path("max_completion_tokens").asInt()).isEqualTo(2000);
        assertThat(sent.has("temperature")).as("temperature não é enviada (ADR-0016)").isFalse();
        assertThat(sent.has("tools")).as("nenhuma tool no MVP (DA-02)").isFalse();

        JsonNode format = sent.path("response_format");
        assertThat(format.path("type").asString()).isEqualTo("json_schema");
        assertThat(format.path("json_schema").path("strict").asBoolean()).isTrue();
        assertThat(format.path("json_schema").path("name").asString()).isEqualTo(SpringAiAssistantModel.SCHEMA_NAME);
        JsonNode rawSchema = format.path("json_schema").path("schema");
        JsonNode schema = rawSchema.isString() ? JSON.readTree(rawSchema.asString()) : rawSchema;
        List<String> allowedIntents = new ArrayList<>();
        schema.path("properties").path("intent").path("enum").forEach(n -> allowedIntents.add(n.asString()));
        assertThat(allowedIntents).containsExactlyInAnyOrder(
                java.util.Arrays.stream(Intent.values()).map(Enum::name).toArray(String[]::new));

        JsonNode messages = sent.path("messages");
        List<String> roles = new ArrayList<>();
        messages.forEach(m -> roles.add(m.path("role").asString()));
        assertThat(roles).containsExactly("system", "system", "system", "user", "assistant", "user");
        assertThat(messages.get(1).path("content").asString()).startsWith("<dados_da_clinica>");
        assertThat(messages.get(2).path("content").asString()).contains("<estado_do_atendimento>", "16/11/2026");
        assertThat(messages.get(3).path("content").asString())
                .isEqualTo("<mensagem_do_cliente>\nMeu gato se chama Mingau.\n</mensagem_do_cliente>");
        assertThat(messages.get(4).path("content").asString()).isEqualTo("Que nome bonito!");
        assertThat(messages.get(5).path("content").asString()).contains("Qual é o nome do meu gato?");
    }

    @Test
    void truncation_reachesTheApplication() {
        STUB.respond(200, completion("{\\\"reply\\\":\\\"Min", "length", null), 0);

        assertThat(model.respond(REQUEST).finishReason()).isEqualToIgnoringCase("length");
    }

    @Test
    void refusal_reachesTheApplication() {
        STUB.respond(200, completion(null, "stop", "Não posso ajudar com isso."), 0);

        assertThat(model.respond(REQUEST).refusal()).isEqualTo("Não posso ajudar com isso.");
    }

    @Test
    void serverError_isTransient_andSpringAiDoesNotRetryOnItsOwn() {
        STUB.respond(500, "{\"error\":{\"message\":\"falha simulada\"}}", 0);

        assertThatThrownBy(() -> model.respond(REQUEST)).isInstanceOfSatisfying(AssistantModelException.class,
                e -> assertThat(e.kind()).isEqualTo(AssistantModelException.Kind.TRANSIENT));
        assertThat(STUB.requestCount()).as("retry do Spring AI desligado (DA-28)").isEqualTo(1);
    }

    @Test
    void rateLimit_isTransient_withoutSpringAiRetry() {
        STUB.respond(429, "{\"error\":{\"message\":\"limite simulado\"}}", 0);

        assertThatThrownBy(() -> model.respond(REQUEST)).isInstanceOfSatisfying(AssistantModelException.class,
                e -> assertThat(e.kind()).isEqualTo(AssistantModelException.Kind.TRANSIENT));
        assertThat(STUB.requestCount()).isEqualTo(1);
    }

    @Test
    void invalidKey_isPermanent_andTheErrorBodyIsNotPropagated() {
        STUB.respond(401, "{\"error\":{\"message\":\"Incorrect API key provided: sk-ficticia****\"}}", 0);

        assertThatThrownBy(() -> model.respond(REQUEST)).isInstanceOfSatisfying(AssistantModelException.class, e -> {
            assertThat(e.kind()).isEqualTo(AssistantModelException.Kind.PERMANENT);
            assertThat(e.getMessage()).isEqualTo("HTTP 401");
        });
        assertThat(STUB.requestCount()).isEqualTo(1);
    }

    @Test
    void slowResponse_hitsTheConfiguredReadTimeout() {
        STUB.respond(200, completion("{}", "stop", null), 5_000);
        long start = System.nanoTime();

        assertThatThrownBy(() -> model.respond(REQUEST)).isInstanceOfSatisfying(AssistantModelException.class,
                e -> assertThat(e.kind()).isEqualTo(AssistantModelException.Kind.TIMEOUT));
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        assertThat(elapsedMs).as("read-timeout de 2 s neste teste").isBetween(1_500L, 4_500L);
        assertThat(STUB.requestCount()).isEqualTo(1);
    }

    /** {@code content} já escapado para dentro de uma string JSON; {@code null} vira JSON null. */
    private static String completion(String escapedContent, String finishReason, String refusal) {
        String content = escapedContent == null ? "null" : "\"" + escapedContent + "\"";
        String refusalJson = refusal == null ? "null" : "\"" + refusal + "\"";
        return """
                {"id":"chatcmpl-ficticio","object":"chat.completion","created":1763290000,"model":"gpt-6-luna",
                 "choices":[{"index":0,"message":{"role":"assistant","content":%s,"refusal":%s},
                             "finish_reason":"%s"}],
                 "usage":{"prompt_tokens":120,"completion_tokens":30,"total_tokens":150}}
                """.formatted(content, refusalJson, finishReason);
    }

    /** Servidor HTTP mínimo no lugar da OpenAI. */
    static final class StubOpenAi {

        private final HttpServer server;
        private final List<String> bodies = new CopyOnWriteArrayList<>();
        private volatile int status = 200;
        private volatile String body = "{}";
        private volatile long delayMs;

        StubOpenAi() {
            try {
                server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            server.createContext("/", exchange -> {
                bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                try {
                    if (delayMs > 0) {
                        Thread.sleep(delayMs);
                    }
                    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    exchange.sendResponseHeaders(status, bytes.length);
                    try (OutputStream out = exchange.getResponseBody()) {
                        out.write(bytes);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (IOException ignored) {
                    // o cliente desistiu (timeout): esperado no teste de timeout
                } finally {
                    exchange.close();
                }
            });
            server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
            server.start();
        }

        String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        void respond(int status, String body, long delayMs) {
            this.status = status;
            this.body = body;
            this.delayMs = delayMs;
        }

        void reset() {
            bodies.clear();
            respond(200, "{}", 0);
        }

        int requestCount() {
            return bodies.size();
        }

        JsonNode lastBody() {
            assertThat(bodies).as("nenhuma requisição chegou ao servidor local").isNotEmpty();
            return JSON.readTree(bodies.getLast());
        }

        void stop() {
            server.stop(0);
        }
    }
}
