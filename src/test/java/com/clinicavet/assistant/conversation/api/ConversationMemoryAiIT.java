package com.clinicavet.assistant.conversation.api;

import com.clinicavet.assistant.support.ApiClient;
import com.clinicavet.assistant.support.RequiresOpenAiKey;
import com.clinicavet.assistant.support.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Critério de aceite da Etapa 2 com o modelo real (DM-13, {@code ./gradlew aiTest}): o assistente usa
 * uma informação dita duas mensagens antes, sem pedir de novo. Dados fictícios. Pelo ADR-0016, o
 * cenário roda 10 vezes e passa com pelo menos 9 acertos.
 */
@RequiresOpenAiKey
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "assistant.security.api-key=chave-de-teste-ficticia-0123456789abcdef",
        "assistant.conversation.expiration.open=30m",
        "assistant.conversation.expiration.waiting-human=2h"
})
@Import(TestcontainersConfiguration.class)
class ConversationMemoryAiIT {

    private static final int RUNS = 10;
    private static final int REQUIRED = 9;

    @Value("${local.server.port}")
    int port;

    @Test
    void usesInformationGivenTwoMessagesEarlier_withoutAskingAgain() {
        ApiClient api = new ApiClient(port, "chave-de-teste-ficticia-0123456789abcdef");
        List<String> failures = new ArrayList<>();
        int passed = 0;

        for (int run = 1; run <= RUNS; run++) {
            String id = api.openConversation("ficticio-ai-" + UUID.randomUUID());
            ApiClient.Response first = api.sendMessage(id, "Oi! Meu cachorro se chama Paçoca e tem 4 anos.");
            ApiClient.Response second = api.sendMessage(id, "Vocês costumam atender aos sábados?");
            ApiClient.Response third = api.sendMessage(id, "Como é mesmo o nome do meu cachorro e qual a idade dele?");

            String reply = third.body().path("reply").asString();
            boolean ok = first.status() == 200 && second.status() == 200 && third.status() == 200
                    && !third.body().path("fallback").asBoolean()
                    && normalize(reply).contains("pacoca")
                    && (reply.contains("4") || normalize(reply).contains("quatro"));
            System.out.printf("[memória] execução %d: %s | resposta: %s%n", run, ok ? "OK" : "FALHOU", reply);
            if (ok) {
                passed++;
            } else {
                failures.add("execução " + run + ": " + reply);
            }
        }

        System.out.printf("[memória] resultado: %d/%d (mínimo %d)%n", passed, RUNS, REQUIRED);
        assertThat(passed).as("acertos em %d execuções; falhas: %s", RUNS, failures).isGreaterThanOrEqualTo(REQUIRED);
    }

    private static String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
}
