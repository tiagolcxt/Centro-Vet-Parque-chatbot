package com.clinicavet.assistant.conversation.api;

import com.clinicavet.assistant.conversation.application.AssistantModelException;
import com.clinicavet.assistant.conversation.application.AssistantRequest;
import com.clinicavet.assistant.conversation.application.HandoffSettings;
import com.clinicavet.assistant.conversation.domain.ClinicalPriority;
import com.clinicavet.assistant.conversation.domain.Conversation;
import com.clinicavet.assistant.conversation.domain.ConversationId;
import com.clinicavet.assistant.conversation.domain.ConversationRepository;
import com.clinicavet.assistant.conversation.domain.ConversationStatus;
import com.clinicavet.assistant.conversation.domain.HandoffReason;
import com.clinicavet.assistant.conversation.domain.Message;
import com.clinicavet.assistant.conversation.domain.MessageAuthor;
import com.clinicavet.assistant.conversation.domain.MessageRepository;
import com.clinicavet.assistant.conversation.domain.TurnSignal;
import com.clinicavet.assistant.servicerequest.domain.RequestStatus;
import com.clinicavet.assistant.servicerequest.domain.RequestType;
import com.clinicavet.assistant.servicerequest.domain.ServiceRequestRepository;
import com.clinicavet.assistant.support.ApiClient;
import com.clinicavet.assistant.support.FakeAssistantModel;
import com.clinicavet.assistant.support.IntegrationTest;
import com.clinicavet.assistant.support.TestClock;
import com.clinicavet.assistant.support.TurnJson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Camadas U e I dos cenários das Diretrizes (CT-01 a CT-08, CT-10 a CT-12, CT-14 a CT-16), com o modelo
 * falso <b>sem sinalizar</b> encaminhamento, emergência nem prioridade (sempre P6) ou <b>falhando</b>:
 * o que precisa acontecer acontece pelo backend. Base fictícia com a lista ilustrativa de alertas.
 */
@IntegrationTest
class GuidelineScenariosIT {

    @Value("${local.server.port}")
    int port;

    @Autowired
    FakeAssistantModel model;

    @Autowired
    TestClock clock;

    @Autowired
    ConversationRepository conversations;

    @Autowired
    MessageRepository messages;

    @Autowired
    ServiceRequestRepository serviceRequests;

    @Autowired
    HandoffSettings handoff;

    ApiClient api;

    @BeforeEach
    void setUp() {
        model.reset();
        api = ApiClient.authenticated(port);
    }

    private String open() {
        return api.openConversation("ficticio-" + UUID.randomUUID());
    }

    private Conversation load(String id) {
        return conversations.findById(new ConversationId(UUID.fromString(id))).orElseThrow();
    }

    private List<RequestType> openRequestTypes(String id) {
        return serviceRequests.findByConversation(new ConversationId(UUID.fromString(id))).stream()
                .filter(r -> r.status() == RequestStatus.PENDING).map(r -> r.type()).toList();
    }

    private Message lastAssistantMessage(String id) {
        return messages.findLatest(new ConversationId(UUID.fromString(id)), 1).getFirst();
    }

    private String emergency() {
        return handoff.replies().emergencyPlaceholder().strip();
    }

    private String shortGuidance() {
        return handoff.replies().shortGuidancePlaceholder().strip();
    }

    private void assertNeverClaimsNotification(String reply) {
        assertThat(reply.toLowerCase()).doesNotContain("avisad", "notificad", "vai te ligar", "vão te responder",
                "já vão", "entraremos em contato");
    }

    private void failTwice() {
        model.thenFail(AssistantModelException.Kind.TIMEOUT).thenFail(AssistantModelException.Kind.TIMEOUT);
    }

    @Test
    void ct01_priceTogetherWithBreathingDifficulty_isP1_guidanceFirstAndAlone() {
        String id = open();
        model.thenJson(TurnJson.of("A consulta custa R$ 150,00.", "PRICE_INQUIRY").refs("services.consulta.price-cents"));

        JsonNode body = api.sendMessage(id, "Quanto custa a consulta? Ele está com dificuldade para respirar.").body();

        assertThat(body.path("priority").asString()).isEqualTo("P1_POSSIBLE_EMERGENCY");
        assertThat(body.path("status").asString()).isEqualTo("WAITING_HUMAN");
        assertThat(body.path("handoffReason").asString()).isEqualTo("POSSIBLE_EMERGENCY");
        assertThat(body.path("reply").asString()).startsWith(emergency()).doesNotContain("150");
        assertNeverClaimsNotification(body.path("reply").asString());
        assertThat(openRequestTypes(id)).contains(RequestType.HUMAN_CONTACT);
        assertThat(lastAssistantMessage(id).metadata().signals()).contains(TurnSignal.ALERT_EXPRESSION);
    }

    @Test
    void ct01_sameWithTheModelFailing() {
        String id = open();
        failTwice();

        JsonNode body = api.sendMessage(id, "Quanto custa a consulta? Ele está com dificuldade para respirar.").body();

        assertThat(body.path("fallback").asBoolean()).isTrue();
        assertThat(body.path("handoffReason").asString()).isEqualTo("POSSIBLE_EMERGENCY");
        assertThat(body.path("reply").asString()).startsWith(emergency());
    }

    @Test
    void ct02_explicitRequest_handsOffBeforeTheModel_withoutAskingWhy_thenShortConfirmation() {
        String id = open();
        model.thenJson(TurnJson.of("Antes, posso te ajudar com mais alguma coisa?", "OTHER"));

        JsonNode first = api.sendMessage(id, "quero falar com um atendente").body();

        assertThat(first.path("status").asString()).isEqualTo("WAITING_HUMAN");
        assertThat(first.path("handoffReason").asString()).isEqualTo("EXPLICIT_REQUEST");
        assertThat(first.path("reply").asString()).isEqualTo(handoff.replies().handoff().strip());
        assertThat(openRequestTypes(id)).containsExactly(RequestType.HUMAN_CONTACT);

        JsonNode second = api.sendMessage(id, "já falei, quero uma pessoa").body();

        assertThat(model.lastRequest().mode()).isEqualTo(AssistantRequest.Mode.RESTRICTED);
        assertThat(second.path("reply").asString()).isEqualTo(handoff.replies().waitingConfirmation().strip());
        assertThat(openRequestTypes(id)).as("não duplica o pedido").containsExactly(RequestType.HUMAN_CONTACT);
    }

    @Test
    void ct02_explicitRequest_withTheModelFailing() {
        String id = open();
        failTwice();

        JsonNode body = api.sendMessage(id, "Me passa pra alguém aí, por favor").body();

        assertThat(body.path("status").asString()).isEqualTo("WAITING_HUMAN");
        assertThat(body.path("handoffReason").asString()).isEqualTo("EXPLICIT_REQUEST");
        assertThat(body.path("reply").asString()).isEqualTo(handoff.replies().handoff().strip());
    }

    @Test
    void ct03_intenseConcern_isAtLeastP2_andHandsOff() {
        String id = open();
        model.thenJson(TurnJson.of("Entendo sua preocupação.", "MEDICAL_CONCERN").signals("EMOTIONAL_DISTRESS"));

        JsonNode body = api.sendMessage(id, "Estou desesperada, minha gata está muito estranha desde ontem").body();

        assertThat(body.path("priority").asString()).isEqualTo("P2_URGENT");
        assertThat(body.path("handoffReason").asString()).isEqualTo("CLINICAL_PRIORITY");
        assertThat(body.path("reply").asString()).startsWith(shortGuidance());
        assertThat(lastAssistantMessage(id).metadata().signals()).contains(TurnSignal.EMOTIONAL_DISTRESS);
    }

    @Test
    void ct04_changeAfterProcedure_isAtLeastP2() {
        String id = open();
        model.thenJson(TurnJson.of("Entendo.", "TREATMENT_FOLLOW_UP").signals("POST_PROCEDURE_CHANGE"));

        JsonNode body = api.sendMessage(id, "Depois da castração, o local da cirurgia ficou inchado").body();

        assertThat(body.path("priority").asString()).isEqualTo("P2_URGENT");
        assertThat(body.path("status").asString()).isEqualTo("WAITING_HUMAN");
        assertThat(openRequestTypes(id)).contains(RequestType.HUMAN_CONTACT);
    }

    @Test
    void ct05_complaint_staysP6_butHandsOff() {
        String id = open();
        model.thenJson(TurnJson.of("Sinto muito pela experiência.", "COMPLAINT").signals("DISSATISFACTION"));

        JsonNode body = api.sendMessage(id, "Fui muito mal atendido na semana passada").body();

        assertThat(body.path("priority").asString()).isEqualTo("P6_RELATIONSHIP");
        assertThat(body.path("handoffReason").asString()).isEqualTo("COMPLAINT");
        assertNeverClaimsNotification(body.path("reply").asString());
    }

    @Test
    void ct06_administrativeThatTurnsUrgent_isRaisedOnTheSecondTurn() {
        String id = open();
        model.thenJson(TurnJson.of("Aos sábados abrimos das 8h às 12h.", "CLINIC_INFORMATION").refs("hours.saturday").priority("P5_ADMINISTRATIVE"))
                .thenJson(TurnJson.of("Entendo.", "MEDICAL_CONCERN"));

        JsonNode first = api.sendMessage(id, "Vocês abrem sábado?").body();
        JsonNode second = api.sendMessage(id, "É que ele não come nada há dois dias e está vomitando.").body();

        assertThat(first.path("priority").asString()).isEqualTo("P5_ADMINISTRATIVE");
        assertThat(first.path("status").asString()).isEqualTo("OPEN");
        assertThat(second.path("priority").asString()).isEqualTo("P2_URGENT");
        assertThat(second.path("status").asString()).isEqualTo("WAITING_HUMAN");
        assertThat(second.path("reply").asString()).startsWith(shortGuidance());
    }

    @Test
    void ct07_emergencyWhileWaiting_raisesToP1_staysWaiting_guidanceFirst() {
        String id = open();
        api.sendMessage(id, "quero falar com um atendente");
        model.thenRestricted(true, false, "P6_RELATIONSHIP");

        JsonNode body = api.sendMessage(id, "Ele começou a tremer e caiu").body();

        assertThat(body.path("priority").asString()).isEqualTo("P1_POSSIBLE_EMERGENCY");
        assertThat(body.path("status").asString()).isEqualTo("WAITING_HUMAN");
        assertThat(body.path("reply").asString()).startsWith(emergency());
        assertNeverClaimsNotification(body.path("reply").asString());
    }

    @Test
    void ct07_restrictedModeFailing_failSafeIncludesShortGuidance() {
        String id = open();
        api.sendMessage(id, "quero falar com um atendente");
        model.thenFail(AssistantModelException.Kind.TIMEOUT);

        JsonNode body = api.sendMessage(id, "Ele começou a tremer e caiu").body();

        assertThat(body.path("fallback").asBoolean()).isTrue();
        assertThat(body.path("reply").asString()).contains(handoff.replies().waitingConfirmation().strip(), shortGuidance());
        assertThat(model.requests().stream().filter(r -> r.mode() == AssistantRequest.Mode.RESTRICTED))
                .as("modo restrito não repete").hasSize(1);
    }

    @Test
    void ct07_alertExpressionWhileWaiting_raisesEvenIfTheModelSaysP6() {
        String id = open();
        api.sendMessage(id, "quero falar com um atendente");
        model.thenRestricted(false, false, "P6_RELATIONSHIP");

        JsonNode body = api.sendMessage(id, "Agora ele está convulsionando").body();

        assertThat(body.path("priority").asString()).isEqualTo("P1_POSSIBLE_EMERGENCY");
        assertThat(body.path("reply").asString()).startsWith(emergency());
    }

    @Test
    void ct08_medicationQuestion_isAtLeastP3_withSymptomP2() {
        String a = open();
        model.thenJson(TurnJson.of("Não posso orientar sobre medicamentos.", "TREATMENT_FOLLOW_UP"));
        JsonNode followUp = api.sendMessage(a, "Posso continuar dando o remédio que o veterinário passou?").body();

        String b = open();
        model.thenJson(TurnJson.of("Não posso orientar sobre medicamentos.", "TREATMENT_FOLLOW_UP").secondary("MEDICAL_CONCERN"));
        JsonNode withSymptom = api.sendMessage(b, "Posso dar mais remédio? Ele continua com febre").body();

        assertThat(followUp.path("priority").asString()).isEqualTo("P3_CLINICAL_FOLLOW_UP");
        assertThat(followUp.path("handoffReason").asString()).isEqualTo("CLINICAL_PRIORITY");
        assertThat(withSymptom.path("priority").asString()).isEqualTo("P2_URGENT");
    }

    @Test
    void ct10_financialVulnerability_isRecordedWithoutCommercialEffect_andSymptomFloorsP2() {
        String id = open();
        model.thenJson(TurnJson.of("Entendo.", "MEDICAL_CONCERN").signals("FINANCIAL_VULNERABILITY"));

        JsonNode body = api.sendMessage(id, "Estou sem dinheiro e meu cachorro está mancando muito").body();

        assertThat(body.path("priority").asString()).isEqualTo("P2_URGENT");
        assertThat(lastAssistantMessage(id).metadata().signals()).contains(TurnSignal.FINANCIAL_VULNERABILITY);
        assertThat(body.path("reply").asString()).doesNotContain("R$", "%", "parcel", "desconto");
    }

    @Test
    void ct11_noStaffWithinTheDeadline_queueShowsOverdue_andNextReplyGuidesDirectContact() {
        String id = open();
        model.thenJson(TurnJson.of("Entendo.", "MEDICAL_CONCERN"));
        api.sendMessage(id, "Ele está mancando desde ontem");

        clock.advance(handoff.deadlineFor(ClinicalPriority.P2_URGENT).plusMinutes(1));
        JsonNode queue = api.get("/api/conversations?status=WAITING_HUMAN&limit=200").body();
        JsonNode next = api.sendMessage(id, "Ninguém respondeu ainda?").body();

        JsonNode item = findById(queue, id);
        assertThat(item.path("overdue").asBoolean()).isTrue();
        assertThat(item.path("waitingMinutes").asLong()).isGreaterThan(60);
        assertThat(next.path("reply").asString()).contains(handoff.replies().overduePlaceholder().strip());
        assertNeverClaimsNotification(next.path("reply").asString());
    }

    @Test
    void ct12_ambiguousSeverity_floorsAtP2() {
        String id = open();
        model.thenJson(TurnJson.of("Entendo.", "OTHER").uncertainty());

        JsonNode body = api.sendMessage(id, "Ele está meio diferente hoje, não sei explicar").body();

        assertThat(body.path("priority").asString()).isEqualTo("P2_URGENT");
        assertThat(body.path("status").asString()).isEqualTo("WAITING_HUMAN");
    }

    @Test
    void ct14_unproductive_offersAtTheSecondFailure_handsOffAtTheThird() {
        String id = open();
        failTwice();
        api.sendMessage(id, "Primeira tentativa de pergunta");
        failTwice();
        JsonNode second = api.sendMessage(id, "Segunda tentativa de pergunta").body();
        failTwice();
        JsonNode third = api.sendMessage(id, "Terceira tentativa de pergunta").body();

        assertThat(second.path("reply").asString()).contains(handoff.replies().humanOffer().strip());
        assertThat(second.path("status").asString()).isEqualTo("OPEN");
        assertThat(load(id).humanOfferMade()).isTrue();
        assertThat(third.path("status").asString()).isEqualTo("WAITING_HUMAN");
        assertThat(third.path("handoffReason").asString()).isEqualTo("UNPRODUCTIVE_CONVERSATION");
    }

    @Test
    void ct15_aiFailureInAP2Conversation_keepsP2_andIncludesShortGuidance() {
        String id = open();
        model.thenJson(TurnJson.of("Entendo.", "MEDICAL_CONCERN"));
        api.sendMessage(id, "Ele está mancando desde ontem");
        api.post("/api/conversations/" + id + "/return-to-assistant", Map.of());
        failTwice();

        JsonNode body = api.sendMessage(id, "Outra coisa: qual o endereço?").body();

        assertThat(body.path("fallback").asBoolean()).isTrue();
        assertThat(body.path("priority").asString()).isEqualTo("P2_URGENT");
        assertThat(body.path("reply").asString()).startsWith(shortGuidance());
    }

    @Test
    void ct16_afterP1_tutorSaysItPassed_priorityStays_onlyStaffLowers_withRecord() {
        String id = open();
        model.thenJson(TurnJson.of("Entendo.", "OTHER"));
        api.sendMessage(id, "Ele desmaiou agora há pouco");
        model.thenRestricted(false, false, "P6_RELATIONSHIP");

        JsonNode body = api.sendMessage(id, "Já passou, pode esquecer").body();
        assertThat(body.path("priority").asString()).isEqualTo("P1_POSSIBLE_EMERGENCY");

        JsonNode lowered = patch("/api/conversations/" + id + "/priority",
                Map.of("priority", "P4_SERVICE_REQUEST", "note", "Equipe avaliou por telefone (fictício)."));
        JsonNode history = api.get("/api/conversations/" + id + "/priority-changes").body();

        assertThat(lowered.path("priority").asString()).isEqualTo("P4_SERVICE_REQUEST");
        assertThat(history).hasSize(1);
        assertThat(history.get(0).path("from").asString()).isEqualTo("P1_POSSIBLE_EMERGENCY");
        assertThat(history.get(0).path("to").asString()).isEqualTo("P4_SERVICE_REQUEST");
        assertThat(history.get(0).path("changedBy").asString()).contains("API Key");
    }

    @Test
    void transferAnnouncedWithoutHandoff_isAFailureTowardsTheHuman() {
        String id = open();
        model.thenJson(TurnJson.of("Vou te transferir para a equipe agora.", "OTHER"))
                .thenJson(TurnJson.of("Vou te transferir para a equipe.", "OTHER"));

        JsonNode body = api.sendMessage(id, "Tenho uma dúvida sobre a clínica").body();

        assertThat(body.path("fallback").asBoolean()).isTrue();
        assertThat(body.path("handoffReason").asString()).isEqualTo("TURN_FAILURE");
        assertThat(body.path("reply").asString()).isEqualTo(handoff.replies().handoff().strip());
    }

    @Test
    void replyClaimingNotification_isRejected() {
        String id = open();
        model.thenJson(TurnJson.of("Já avisei a equipe.", "OTHER")).thenJson(TurnJson.of("A equipe já foi avisada.", "OTHER"));

        JsonNode body = api.sendMessage(id, "Alguma novidade?").body();

        assertThat(body.path("fallback").asBoolean()).isTrue();
        assertNeverClaimsNotification(body.path("reply").asString());
    }

    @Test
    void queue_isOrderedByPriority_thenWaitingTime() {
        String p2 = open();
        model.thenJson(TurnJson.of("Entendo.", "MEDICAL_CONCERN"));
        api.sendMessage(p2, "Ele está mancando desde ontem");
        clock.advance(Duration.ofMinutes(5));
        String p1 = open();
        api.sendMessage(p1, "Ele está convulsionando");

        List<String> order = ids(api.get("/api/conversations?status=WAITING_HUMAN&limit=200").body());

        assertThat(order.indexOf(p1)).isLessThan(order.indexOf(p2));
        assertThat(load(p1).clinicalPriority()).isEqualTo(ClinicalPriority.P1_POSSIBLE_EMERGENCY);
        assertThat(load(p2).status()).isEqualTo(ConversationStatus.WAITING_HUMAN);
        assertThat(load(p1).handoffReason()).contains(HandoffReason.POSSIBLE_EMERGENCY);
        assertThat(messages.findLatest(new ConversationId(UUID.fromString(p1)), 2))
                .extracting(Message::author).containsExactly(MessageAuthor.CUSTOMER, MessageAuthor.ASSISTANT);
    }

    private JsonNode patch(String path, Map<String, ?> body) {
        return api.patch(path, body).body();
    }

    private static JsonNode findById(JsonNode array, String id) {
        for (JsonNode item : array) {
            if (item.path("id").asString().equals(id)) {
                return item;
            }
        }
        throw new AssertionError("conversa fora da fila: " + id);
    }

    private static List<String> ids(JsonNode array) {
        List<String> ids = new java.util.ArrayList<>();
        array.forEach(item -> ids.add(item.path("id").asString()));
        return ids;
    }
}
