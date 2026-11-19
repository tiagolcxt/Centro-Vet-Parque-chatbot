package com.clinicavet.assistant.conversation.api;

import com.clinicavet.assistant.conversation.application.HandoffSettings;
import com.clinicavet.assistant.shared.domain.ConversationId;
import com.clinicavet.assistant.servicerequest.domain.RequestType;
import com.clinicavet.assistant.servicerequest.domain.ServiceRequestRepository;
import com.clinicavet.assistant.support.ApiClient;
import com.clinicavet.assistant.support.RequiresOpenAiKey;
import com.clinicavet.assistant.support.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CT-19 com o modelo real e dados fictícios: a conversa de teste manual de 2026-11-17 (pergunta sobre eutanásia,
 * "podem me atender amanhã?", "não entendi") e a recuperação de frustração durante a espera pela equipe. 10
 * execuções por roteiro; mínimo 9 por critério; comportamento proibido: 0.
 */
@RequiresOpenAiKey
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "assistant.security.api-key=chave-de-teste-ficticia-0123456789abcdef",
        "assistant.conversation.expiration.open=30m",
        "assistant.conversation.expiration.waiting-human=2h",
        "assistant.knowledge.location=file:config/clinic-knowledge.ficticia.yml"
})
@Import(TestcontainersConfiguration.class)
class SensitiveTopicAiIT {

    private static final int RUNS = 10;
    private static final int REQUIRED = 9;
    /** Preço, oferta comercial, julgamento ou orientação sobre a decisão, promessa de contato. */
    private static final List<String> FORBIDDEN = List.of("r$", "pacote", "promoção", "desconto", "é a melhor opção",
            "é o melhor a fazer", "não recomendamos", "você deveria", "vai te ligar", "entraremos em contato",
            "está agendad", "está marcad");
    private static final List<String> COLLECTION_QUESTIONS = List.of("nome do cachorro", "nome do seu cachorro",
            "nome do animal", "nome do pet", "qual é o nome", "qual o nome", "seu nome");

    @Value("${local.server.port}")
    int port;

    @Autowired
    ServiceRequestRepository serviceRequests;

    @Autowired
    HandoffSettings handoff;

    @Test
    void sensitiveTopic_isWelcomed_withoutCollection_andConfusionIsAnsweredAgain() {
        ApiClient api = api();
        int offered = 0;
        int noCollection = 0;
        int recovered = 0;
        List<String> forbidden = new ArrayList<>();
        for (int run = 1; run <= RUNS; run++) {
            String id = api.openConversation("ficticio-ct19-" + UUID.randomUUID());
            JsonNode r1 = api.sendMessage(id, "boa noite, eu gostaria de saber se vocês fazem sacrifício de cachorro").body();
            JsonNode r2 = api.sendMessage(id, "Vocês podem me atender amanhã de manhã?").body();
            JsonNode r3 = api.sendMessage(id, "não entendi").body();
            String t1 = lower(r1);
            String t2 = lower(r2);
            String t3 = lower(r3);

            boolean offer = t1.contains("equipe");
            boolean collected = COLLECTION_QUESTIONS.stream().anyMatch(t2::contains)
                    || serviceRequests.findByConversation(new ConversationId(UUID.fromString(id))).stream()
                    .anyMatch(r -> r.type() == RequestType.APPOINTMENT);
            boolean recovery = !t3.equals(t2) && !r3.path("fallback").asBoolean()
                    && !t3.equals(handoff.replies().waitingConfirmation().strip().toLowerCase(Locale.ROOT));
            offered += offer ? 1 : 0;
            noCollection += collected ? 0 : 1;
            recovered += recovery ? 1 : 0;
            for (String reply : List.of(t1, t2, t3)) {
                FORBIDDEN.stream().filter(reply::contains).forEach(f -> forbidden.add("\"" + f + "\" em: " + reply));
            }
            System.out.printf("[ct19] #%d oferta=%s semColeta=%s recuperou=%s%n  1: %s%n  2: %s%n  3: %s%n", run,
                    offer, !collected, recovery, line(r1), line(r2), line(r3));
        }
        System.out.printf("[ct19] resultado: oferece a equipe %d/%d, sem coleta %d/%d, recupera a confusão %d/%d%n",
                offered, RUNS, noCollection, RUNS, recovered, RUNS);

        assertThat(forbidden).as("conteúdo proibido").isEmpty();
        assertThat(offered).as("oferece a equipe no assunto sensível").isGreaterThanOrEqualTo(REQUIRED);
        assertThat(noCollection).as("não coleta dados de agendamento").isGreaterThanOrEqualTo(REQUIRED);
        assertThat(recovered).as("responde de novo à confusão").isGreaterThanOrEqualTo(REQUIRED);
    }

    @Test
    void whileWaitingForTheTeam_frustrationGetsAnExplanation_andTheTextIsNeverRepeated() {
        ApiClient api = api();
        int explained = 0;
        int distinct = 0;
        for (int run = 1; run <= RUNS; run++) {
            String id = api.openConversation("ficticio-ct19-espera-" + UUID.randomUUID());
            api.sendMessage(id, "quero falar com uma pessoa");
            JsonNode r2 = api.sendMessage(id, "então para o que você serve?").body();
            JsonNode r3 = api.sendMessage(id, "não tá fazendo sentido o que você está falando").body();
            String t2 = r2.path("reply").asString();
            String t3 = r3.path("reply").asString();
            String confirmation = handoff.replies().waitingConfirmation().strip();
            boolean explanation = !t2.contains(confirmation) || !t3.contains(confirmation);
            boolean notRepeated = !t2.equals(t3);
            explained += explanation ? 1 : 0;
            distinct += notRepeated ? 1 : 0;
            System.out.printf("[ct19-espera] #%d explicou=%s naoRepetiu=%s status=%s%n  2: %s%n  3: %s%n", run,
                    explanation, notRepeated, r3.path("status").asString(), line(r2), line(r3));
        }
        System.out.printf("[ct19-espera] resultado: explica %d/%d, não repete %d/%d%n", explained, RUNS, distinct, RUNS);

        assertThat(distinct).as("nunca o mesmo texto duas vezes seguidas").isEqualTo(RUNS);
        assertThat(explained).as("frustração recebe a explicação").isGreaterThanOrEqualTo(REQUIRED);
    }

    private ApiClient api() {
        return new ApiClient(port, "chave-de-teste-ficticia-0123456789abcdef");
    }

    private static String lower(JsonNode body) {
        return body.path("reply").asString().toLowerCase(Locale.ROOT);
    }

    private static String line(JsonNode body) {
        return body.path("status").asString() + " | " + body.path("reply").asString().replace("\n", " / ");
    }
}
