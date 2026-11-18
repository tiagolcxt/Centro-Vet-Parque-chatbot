package com.clinicavet.assistant.conversation.api;

import com.clinicavet.assistant.support.ApiClient;
import com.clinicavet.assistant.support.RequiresOpenAiKey;
import com.clinicavet.assistant.support.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DA-40 (atendimento resolutivo) com o modelo real e dados fictícios. 10 execuções por cenário, mínimo 9; promessa
 * de contato: 0.
 * <ul>
 *   <li>pergunta só de informação: responde, sem transferir e sem pedido para a equipe;</li>
 *   <li>pedido de pessoa e reclamação: transfere e termina com "ligue para a recepção";</li>
 *   <li>aguardando a equipe, "que horas abre amanhã?": o assistente responde (não é a resposta segura).</li>
 * </ul>
 */
@RequiresOpenAiKey
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "assistant.security.api-key=chave-de-teste-ficticia-0123456789abcdef",
        "assistant.conversation.expiration.open=30m",
        "assistant.conversation.expiration.waiting-human=2h",
        "assistant.knowledge.location=file:config/clinic-knowledge.ficticia.yml"
})
@Import(TestcontainersConfiguration.class)
class ResolutionAiIT {

    private static final int RUNS = 10;
    private static final int REQUIRED = 9;
    private static final String RECEPTION = "ligue para a recepção";
    private static final List<String> PROMISES = List.of("vai te ligar", "vão te ligar", "entraremos em contato",
            "retornaremos", "entrará em contato", "vão entrar em contato", "aguarde o contato", "vamos te ligar");

    private record Scenario(String name, List<String> messages, Predicate<JsonNode> expected) {
    }

    @Value("${local.server.port}")
    int port;

    @Test
    void informationIsResolvedDirectly_andEveryHandoffEndsWithTheReceptionPhone() {
        ApiClient api = new ApiClient(port, "chave-de-teste-ficticia-0123456789abcdef");
        List<Scenario> scenarios = List.of(
                new Scenario("horário", List.of("Que horas vocês abrem no sábado?"), ResolutionAiIT::resolved),
                new Scenario("preço", List.of("Quanto custa a consulta?"), ResolutionAiIT::resolved),
                new Scenario("vacina", List.of("Vocês têm vacina V10?"), ResolutionAiIT::resolved),
                new Scenario("pedido de pessoa", List.of("Quero falar com uma pessoa, por favor"),
                        b -> waiting(b) && lower(b).contains(RECEPTION)),
                new Scenario("reclamação", List.of("Fui muito mal atendido ontem, quero reclamar"),
                        b -> waiting(b) && lower(b).contains(RECEPTION)),
                new Scenario("informação na espera", List.of("Quero falar com uma pessoa", "Que horas a clínica abre amanhã?"),
                        b -> waiting(b) && !b.path("fallback").asBoolean() && lower(b).matches("(?s).*(\\d{1,2}(:\\d{2}|h)).*")));

        Map<String, Integer> results = new LinkedHashMap<>();
        List<String> promises = new ArrayList<>();
        for (Scenario scenario : scenarios) {
            int ok = 0;
            for (int run = 1; run <= RUNS; run++) {
                String id = api.openConversation("ficticio-resolutivo-" + UUID.randomUUID());
                JsonNode body = null;
                for (String message : scenario.messages()) {
                    body = api.sendMessage(id, message).body();
                    String reply = lower(body);
                    PROMISES.stream().filter(reply::contains).forEach(p -> promises.add(scenario.name() + ": " + reply));
                }
                boolean passed = scenario.expected().test(body);
                ok += passed ? 1 : 0;
                System.out.printf("[resolutivo] %s #%d: %s | status=%s encaminhamento=%s pedido=%s | %s%n",
                        scenario.name(), run, passed ? "OK" : "FALHOU", body.path("status").asString(),
                        body.path("handoffReason").asString(), body.path("staffRequestReason").asString(),
                        body.path("reply").asString().replace("\n", " / "));
            }
            results.put(scenario.name(), ok);
        }
        System.out.println("[resolutivo] resultado (de " + RUNS + "):");
        results.forEach((name, ok) -> System.out.printf("[resolutivo]   %-22s %d%n", name, ok));

        assertThat(promises).as("promessas de contato").isEmpty();
        results.forEach((name, ok) -> assertThat(ok).as(name).isGreaterThanOrEqualTo(REQUIRED));
    }

    /** Respondida pelo assistente: conversa aberta, sem transferência, sem pedido para a equipe, sem resposta segura. */
    private static boolean resolved(JsonNode body) {
        return body.path("status").asString().equals("OPEN") && body.path("handoffReason").asString().isEmpty()
                && body.path("staffRequestReason").asString().isEmpty() && !body.path("fallback").asBoolean();
    }

    private static boolean waiting(JsonNode body) {
        return body.path("status").asString().equals("WAITING_HUMAN");
    }

    private static String lower(JsonNode body) {
        return body.path("reply").asString().toLowerCase(Locale.ROOT);
    }
}
