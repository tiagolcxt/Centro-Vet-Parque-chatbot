package com.clinicavet.assistant.conversation.api;

import com.clinicavet.assistant.conversation.application.AssistantModelException;
import com.clinicavet.assistant.conversation.application.AssistantRequest;
import com.clinicavet.assistant.conversation.application.ConversationSettings;
import com.clinicavet.assistant.conversation.application.HandleIncomingMessageUseCase;
import com.clinicavet.assistant.conversation.application.ModelReply;
import com.clinicavet.assistant.conversation.application.TokenEstimator;
import com.clinicavet.assistant.conversation.domain.CloseReason;
import com.clinicavet.assistant.conversation.domain.Conversation;
import com.clinicavet.assistant.conversation.domain.ConversationId;
import com.clinicavet.assistant.conversation.domain.ConversationRepository;
import com.clinicavet.assistant.conversation.domain.ConversationStatus;
import com.clinicavet.assistant.conversation.domain.Intent;
import com.clinicavet.assistant.conversation.domain.MessageAuthor;
import com.clinicavet.assistant.support.ApiClient;
import com.clinicavet.assistant.support.FakeAssistantModel;
import com.clinicavet.assistant.support.IntegrationTest;
import com.clinicavet.assistant.support.TestClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Critérios de aceite da Etapa 2 contra o servidor HTTP real, MySQL real e o modelo falso.
 * Só dados fictícios.
 */
@IntegrationTest
class ConversationApiIT {

    @Value("${local.server.port}")
    int port;

    @Autowired
    FakeAssistantModel model;

    @Autowired
    TestClock clock;

    @Autowired
    ConversationRepository conversations;

    @Autowired
    HandleIncomingMessageUseCase handleMessage;

    @Autowired
    ConversationSettings settings;

    @Autowired
    TokenEstimator tokens;

    @Autowired
    JdbcTemplate jdbc;

    ApiClient api;

    @BeforeEach
    void setUp() {
        model.reset();
        api = ApiClient.authenticated(port);
    }

    private static String newContact() {
        return "ficticio-" + UUID.randomUUID();
    }

    private Conversation load(String id) {
        return conversations.findById(new ConversationId(UUID.fromString(id))).orElseThrow();
    }

    private List<JsonNode> history(String id) {
        List<JsonNode> list = new ArrayList<>();
        api.get("/api/conversations/" + id + "/messages?size=100").body().path("content").forEach(list::add);
        return list;
    }

    @Nested
    @ExtendWith(OutputCaptureExtension.class)
    class Authentication {

        @Test
        void missingOrWrongKey_is401_onEveryRoute_andNoKeyIsLogged(CapturedOutput output) {
            String id = api.openConversation(newContact());
            String wrongKey = "chave-errada-ficticia-" + UUID.randomUUID();
            ApiClient anonymous = api.withKey(null);
            ApiClient wrong = api.withKey(wrongKey);

            assertThat(anonymous.post("/api/conversations", Map.of("externalContactId", "x")).status()).isEqualTo(401);
            assertThat(wrong.post("/api/conversations", Map.of("externalContactId", "x")).status()).isEqualTo(401);
            assertThat(anonymous.sendMessage(id, "Oi").status()).isEqualTo(401);
            assertThat(wrong.get("/api/conversations/" + id + "/messages").status()).isEqualTo(401);
            ApiClient.Response denied = wrong.sendMessage(id, "Oi");
            assertThat(denied.status()).isEqualTo(401);
            assertThat(denied.body().path("title").asString()).isEqualTo("Não autorizado");

            assertThat(model.requests()).as("requisição barrada não chega ao modelo").isEmpty();
            assertThat(history(id)).as("requisição barrada não grava nada").isEmpty();
            assertThat(output.getAll()).doesNotContain(IntegrationTest.API_KEY).doesNotContain(wrongKey);
        }
    }

    @Nested
    class Lifecycle {

        @Test
        void secondPostForSameContact_returnsTheSameConversation_with200() {
            String contact = newContact();

            ApiClient.Response first = api.post("/api/conversations", Map.of("externalContactId", contact));
            ApiClient.Response second = api.post("/api/conversations", Map.of("externalContactId", contact));

            assertThat(first.status()).isEqualTo(201);
            assertThat(second.status()).isEqualTo(200);
            assertThat(second.body().path("id").asString()).isEqualTo(first.body().path("id").asString());
            assertThat(first.body().path("status").asString()).isEqualTo("OPEN");
        }

        @Test
        void afterTheTimeout_aNewConversationOpens_andThePreviousIsClosedAsExpired() {
            String contact = newContact();
            String old = api.openConversation(contact);
            api.sendMessage(old, "Oi");

            clock.advance(settings.expiration().open().plusMinutes(1));
            ApiClient.Response reopened = api.post("/api/conversations", Map.of("externalContactId", contact));

            assertThat(reopened.status()).isEqualTo(201);
            assertThat(reopened.body().path("id").asString()).isNotEqualTo(old);
            Conversation previous = load(old);
            assertThat(previous.status()).isEqualTo(ConversationStatus.CLOSED);
            assertThat(previous.closeReason()).contains(CloseReason.EXPIRED);
        }

        @Test
        void beforeTheTimeout_theSameConversationContinues() {
            String contact = newContact();
            String id = api.openConversation(contact);

            clock.advance(settings.expiration().open().minusMinutes(1));

            assertThat(api.openConversation(contact)).isEqualTo(id);
        }

        @Test
        void messageToAnExpiredConversation_goesToANewOne_withoutTheOldHistory() {
            String contact = newContact();
            String old = api.openConversation(contact);
            api.sendMessage(old, "Assunto da conversa antiga, fictício.");

            clock.advance(settings.expiration().open().plusMinutes(1));
            ApiClient.Response response = api.sendMessage(old, "Oi de novo");

            assertThat(response.status()).isEqualTo(200);
            assertThat(response.body().path("newConversation").asBoolean()).isTrue();
            String current = response.body().path("conversationId").asString();
            assertThat(current).isNotEqualTo(old);
            assertThat(model.lastRequest().history()).as("nada da conversa anterior no contexto (ADR-0009)").isEmpty();
            assertThat(model.lastRequest().customerMessage()).isEqualTo("Oi de novo");
            assertThat(history(old)).hasSize(2);
            assertThat(history(current)).hasSize(2);
        }

        @Test
        void concurrentOpens_neverCreateTwoActiveConversations() throws Exception {
            String contact = newContact();
            int threads = 8;
            CountDownLatch start = new CountDownLatch(1);
            List<Callable<ApiClient.Response>> calls = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                calls.add(() -> {
                    start.await();
                    return api.post("/api/conversations", Map.of("externalContactId", contact));
                });
            }
            List<ApiClient.Response> responses = new ArrayList<>();
            try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
                List<Future<ApiClient.Response>> futures = new ArrayList<>();
                for (Callable<ApiClient.Response> call : calls) {
                    futures.add(pool.submit(call));
                }
                start.countDown();
                for (Future<ApiClient.Response> future : futures) {
                    responses.add(future.get());
                }
            }

            assertThat(responses).extracting(ApiClient.Response::status).allMatch(s -> s == 200 || s == 201);
            assertThat(responses).filteredOn(r -> r.status() == 201).hasSize(1);
            assertThat(responses).extracting(r -> r.body().path("id").asString()).containsOnly(
                    responses.getFirst().body().path("id").asString());
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM conversation WHERE external_contact_id = ? AND status <> 'CLOSED'",
                    Integer.class, contact)).isEqualTo(1);
        }
    }

    @Nested
    class Turn {

        @Test
        void historyIsStoredInOrder_withIntentOnAssistantMessages() {
            String id = api.openConversation(newContact());
            model.thenReply("Olá! Como posso ajudar?", "GREETING")
                    .thenTurn("Não tenho essa informação disponível.", "CLINIC_INFORMATION", List.of(), true);

            ApiClient.Response first = api.sendMessage(id, "Oi");
            ApiClient.Response second = api.sendMessage(id, "Que horas vocês abrem?");

            assertThat(first.status()).isEqualTo(200);
            assertThat(first.body().path("reply").asString()).isEqualTo("Olá! Como posso ajudar?");
            assertThat(first.body().path("intent").asString()).isEqualTo("GREETING");
            assertThat(first.body().path("fallback").asBoolean()).isFalse();
            assertThat(first.body().path("newConversation").asBoolean()).isFalse();
            assertThat(second.body().path("intent").asString()).isEqualTo("CLINIC_INFORMATION");

            List<JsonNode> messages = history(id);
            assertThat(messages).extracting(m -> m.path("author").asString())
                    .containsExactly("CUSTOMER", "ASSISTANT", "CUSTOMER", "ASSISTANT");
            assertThat(messages).extracting(m -> m.path("content").asString())
                    .containsExactly("Oi", "Olá! Como posso ajudar?", "Que horas vocês abrem?",
                            "Não tenho essa informação disponível.");
            assertThat(messages.get(1).path("intent").asString()).isEqualTo("GREETING");
            assertThat(messages.get(0).path("intent").isNull()).isTrue();
            assertThat(load(id).currentIntent()).contains(Intent.CLINIC_INFORMATION);

            AssistantRequest secondRequest = model.lastRequest();
            assertThat(secondRequest.history()).extracting(AssistantRequest.HistoryEntry::author)
                    .containsExactly(MessageAuthor.CUSTOMER, MessageAuthor.ASSISTANT);
            assertThat(secondRequest.customerMessage()).isEqualTo("Que horas vocês abrem?");
            assertThat(secondRequest.operational().status()).isEqualTo(ConversationStatus.OPEN);
            assertThat(secondRequest.operational().localNow().getZone()).isEqualTo(TestClock.ZONE);
        }

        @Test
        void window_respectsTheMessageLimit() {
            String id = api.openConversation(newContact());
            for (int i = 1; i <= 12; i++) {
                model.thenReply("Resposta " + i, "OTHER");   // respostas distintas: repetidas viram conversa improdutiva
                api.sendMessage(id, "Mensagem curta " + i);
            }

            AssistantRequest last = model.lastRequest();
            assertThat(last.history()).hasSize(settings.historyMaxMessages() - 1);   // + a atual = 20
            assertThat(last.history().getLast().content()).isEqualTo("Resposta 11");
            assertThat(last.history().get(last.history().size() - 2).content()).isEqualTo("Mensagem curta 11");
            assertThat(last.customerMessage()).isEqualTo("Mensagem curta 12");
        }

        @Test
        void window_respectsTheTokenBudget_withLongMessages_andNeverCutsTheCurrentOne() {
            String id = api.openConversation(newContact());
            List<String> sent = new ArrayList<>();
            for (int i = 1; i <= 12; i++) {
                String text = longText(i);
                sent.add(text);
                api.sendMessage(id, text);
            }

            AssistantRequest last = model.lastRequest();
            int estimated = last.history().stream().mapToInt(e -> tokens.estimate(e.content()) + 4).sum();
            assertThat(estimated).isLessThanOrEqualTo(settings.historyTokenBudget());
            assertThat(last.history().size()).as("o orçamento cortou antes do limite de 20 mensagens")
                    .isLessThan(settings.historyMaxMessages() - 1);
            assertThat(last.customerMessage()).isEqualTo(sent.getLast()).hasSizeGreaterThan(1_800);
            // O que ficou é o trecho mais recente, em ordem.
            assertThat(last.history().get(last.history().size() - 2).content()).isEqualTo(sent.get(sent.size() - 2));
        }

        @Test
        void tooLongOrBlankMessage_is422_andNothingIsStored() {
            String id = api.openConversation(newContact());

            ApiClient.Response tooLong = api.sendMessage(id, "a".repeat(settings.maxCustomerMessageLength() + 1));
            ApiClient.Response blank = api.sendMessage(id, "   ");

            assertThat(tooLong.status()).isEqualTo(422);
            assertThat(tooLong.body().path("field").asString()).isEqualTo("text");
            assertThat(blank.status()).isEqualTo(422);
            assertThat(history(id)).isEmpty();
            assertThat(model.requests()).isEmpty();
        }

        @Test
        void unknownConversation_is404() {
            String unknown = UUID.randomUUID().toString();

            assertThat(api.sendMessage(unknown, "Oi").status()).isEqualTo(404);
            assertThat(api.get("/api/conversations/" + unknown + "/messages").status()).isEqualTo(404);
        }

        @Test
        void messagesArePaginated_inChronologicalOrder() {
            String id = api.openConversation(newContact());
            api.sendMessage(id, "Primeira");
            api.sendMessage(id, "Segunda");

            JsonNode page = api.get("/api/conversations/" + id + "/messages?page=1&size=2").body();

            assertThat(page.path("totalElements").asLong()).isEqualTo(4);
            assertThat(page.path("content")).hasSize(2);
            assertThat(page.path("content").get(0).path("content").asString()).isEqualTo("Segunda");
        }

        private String longText(int seed) {
            StringBuilder text = new StringBuilder();
            int n = seed * 1_000;
            while (text.length() < 1_900) {
                text.append("item ").append(n++).append(" do texto longo fictício; ");
            }
            return text.substring(0, 1_900);
        }
    }

    @Nested
    class Failures {

        static Stream<Arguments> failures() {
            Consumer<FakeAssistantModel> refusal = m -> m.thenReturn(
                    new ModelReply(null, "STOP", "Não posso ajudar com isso.", null, null, null));
            Consumer<FakeAssistantModel> truncated = m -> m.thenReturn(ModelReply.of("{\"reply\":\"Texto cor", "LENGTH"))
                    .thenReturn(ModelReply.of("{\"reply\":\"Texto cor", "LENGTH"));
            Consumer<FakeAssistantModel> invalid = m -> m.thenReturn(ModelReply.of("isto não é JSON", "STOP"))
                    .thenReturn(ModelReply.of("{\"intent\":\"OTHER\"}", "STOP"));
            Consumer<FakeAssistantModel> timeout = m -> m.thenFail(AssistantModelException.Kind.TIMEOUT)
                    .thenFail(AssistantModelException.Kind.TIMEOUT);
            return Stream.of(
                    Arguments.of("recusa", refusal, 1),
                    Arguments.of("truncada", truncated, 2),
                    Arguments.of("JSON inválido", invalid, 2),
                    Arguments.of("timeout", timeout, 2));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("failures")
        void failedTurn_keepsTheCustomerMessage_doesNotChangeState_andAnswersTheSafeReply(
                String name, Consumer<FakeAssistantModel> scenario, int expectedCalls) {
            String id = api.openConversation(newContact());
            model.thenReply("Olá!", "GREETING");
            api.sendMessage(id, "Oi");
            Conversation before = load(id);
            int callsBefore = model.requests().size();
            scenario.accept(model);

            ApiClient.Response response = api.sendMessage(id, "Quero marcar uma consulta");

            assertThat(response.status()).isEqualTo(200);
            assertThat(response.body().path("fallback").asBoolean()).isTrue();
            assertThat(response.body().path("reply").asString()).isEqualTo(settings.fallbackReply().strip());
            assertThat(response.body().path("intent").isNull()).isTrue();
            assertThat(model.requests().size() - callsBefore).as("chamadas ao modelo").isEqualTo(expectedCalls);

            List<JsonNode> messages = history(id);
            assertThat(messages).hasSize(4);
            assertThat(messages.get(2).path("content").asString()).isEqualTo("Quero marcar uma consulta");
            assertThat(messages.get(3).path("intent").isNull()).isTrue();

            Conversation after = load(id);
            assertThat(after.status()).isEqualTo(before.status());
            assertThat(after.currentIntent()).isEqualTo(before.currentIntent()).contains(Intent.GREETING);
        }

        @Test
        void transientFailureThenSuccess_answersNormally() {
            String id = api.openConversation(newContact());
            model.thenFail(AssistantModelException.Kind.TRANSIENT).thenReply("Tudo certo.", "OTHER");

            ApiClient.Response response = api.sendMessage(id, "Oi");

            assertThat(response.body().path("fallback").asBoolean()).isFalse();
            assertThat(response.body().path("reply").asString()).isEqualTo("Tudo certo.");
            assertThat(model.requests()).hasSize(2);
        }
    }

    @Nested
    class Concurrency {

        /**
         * DA-21: a mensagem B chega enquanto o modelo responde a A. B é processada normalmente e já vê A
         * no histórico; A recebe 409, fica registrada e não recebe resposta.
         */
        @Test
        void messageArrivingDuringTheModelCall_makesTheFirstTurn409_andEntersTheNextHistory() {
            String id = api.openConversation(newContact());
            ConversationId conversationId = new ConversationId(UUID.fromString(id));
            model.then(request -> {
                handleMessage.handle(conversationId, "Mensagem B");
                return ModelReply.of(FakeAssistantModel.turnJson("Resposta para A", "OTHER"), "STOP");
            });

            ApiClient.Response a = api.sendMessage(id, "Mensagem A");

            assertThat(a.status()).isEqualTo(409);
            assertThat(a.body().path("messageRecorded").asBoolean()).isTrue();
            assertThat(history(id)).extracting(m -> m.path("content").asString())
                    .containsExactly("Mensagem A", "Mensagem B", FakeAssistantModel.DEFAULT_REPLY);
            AssistantRequest requestB = model.lastRequest();
            assertThat(requestB.customerMessage()).isEqualTo("Mensagem B");
            assertThat(requestB.history()).extracting(AssistantRequest.HistoryEntry::content).containsExactly("Mensagem A");
        }

        @Test
        void conversationChangedByAnotherOperation_alsoMakesTheTurn409() {
            String id = api.openConversation(newContact());
            ConversationId conversationId = new ConversationId(UUID.fromString(id));
            model.then(request -> {
                Conversation current = conversations.findById(conversationId).orElseThrow();
                current.recordIntent(Intent.COMPLAINT);   // simula uma ação da equipe durante a chamada
                conversations.save(current);
                return ModelReply.of(FakeAssistantModel.turnJson("Resposta", "OTHER"), "STOP");
            });

            assertThat(api.sendMessage(id, "Oi").status()).isEqualTo(409);
            assertThat(history(id)).extracting(m -> m.path("author").asString()).containsExactly("CUSTOMER");
            assertThat(load(id).currentIntent()).contains(Intent.COMPLAINT);
        }
    }
}
