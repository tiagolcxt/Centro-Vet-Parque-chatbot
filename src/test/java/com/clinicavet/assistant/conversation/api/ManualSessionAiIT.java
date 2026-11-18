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
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A conversa do teste manual de 2026-11-17 (raiva, castração, "o que você sabe?", "gente atendendo 00h?"), com o
 * modelo real e a base fictícia (DA-41). 10 execuções; mínimo 9 por critério:
 * <ul>
 *   <li>nenhuma resposta segura;</li>
 *   <li>"o que você sabe?" cita pelo menos dois itens reais da base;</li>
 *   <li>a pergunta sobre 00h é respondida com o horário (a clínica não é 24 horas);</li>
 *   <li>o próximo passo ("ligue para a recepção") aparece, mas não em toda resposta;</li>
 *   <li>nenhuma frase igual entre duas respostas seguidas.</li>
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
class ManualSessionAiIT {

    private static final int RUNS = 10;
    private static final int REQUIRED = 9;
    private static final List<String> MESSAGES = List.of("boa noite, voces estão vacinando pra raiva?",
            "estou com uma cadela que precisa castrar. Ela é uma chow chow. Quanto custa?",
            "o que voce sabe?",
            "vocês vão ter gente atendendo 00h?");
    private static final List<String> KNOWN_ITEMS = List.of("consulta", "v10", "banho", "tosa", "08:00", "8h",
            "pix", "rua fictícia", "sábado");

    @Value("${local.server.port}")
    int port;

    @Test
    void theManualSession_getsUsefulAnswers_withoutDeadEndsOrRepetition() {
        ApiClient api = new ApiClient(port, "chave-de-teste-ficticia-0123456789abcdef");
        int noFallback = 0;
        int knowsSomething = 0;
        int answersHours = 0;
        int stepNotEverywhere = 0;
        int noRepetition = 0;
        for (int run = 1; run <= RUNS; run++) {
            String id = api.openConversation("ficticio-sessao-" + UUID.randomUUID());
            List<JsonNode> bodies = new ArrayList<>();
            for (String message : MESSAGES) {
                bodies.add(api.sendMessage(id, message).body());
            }
            List<String> replies = bodies.stream().map(b -> b.path("reply").asString()).toList();
            boolean fallback = bodies.stream().anyMatch(b -> b.path("fallback").asBoolean());
            String knows = replies.get(2).toLowerCase(Locale.ROOT);
            long known = KNOWN_ITEMS.stream().filter(knows::contains).count();
            String hours = replies.get(3).toLowerCase(Locale.ROOT);
            boolean hoursAnswered = hours.matches("(?s).*(18:00|18h|24 horas|24h|fecha).*");
            long steps = replies.stream().filter(r -> r.toLowerCase(Locale.ROOT).contains("ligue para a recepção")).count();
            boolean repeated = false;
            for (int i = 1; i < replies.size(); i++) {
                List<String> before = sentences(replies.get(i - 1));
                repeated |= sentences(replies.get(i)).stream()
                        .anyMatch(s -> s.length() > 25 && before.contains(s) && !s.contains("ligue para a recepção"));
            }
            noFallback += fallback ? 0 : 1;
            knowsSomething += known >= 2 ? 1 : 0;
            answersHours += hoursAnswered ? 1 : 0;
            stepNotEverywhere += steps >= 1 && steps < replies.size() ? 1 : 0;
            noRepetition += repeated ? 0 : 1;
            System.out.printf("[sessão] #%d semFallback=%s sabe=%d horario=%s passos=%d repetiu=%s%n", run, !fallback,
                    known, hoursAnswered, steps, repeated);
            for (int i = 0; i < replies.size(); i++) {
                System.out.printf("  %d: %s | %s%n", i + 1, bodies.get(i).path("status").asString(),
                        replies.get(i).replace("\n", " / "));
            }
        }
        System.out.printf("[sessão] resultado: sem resposta segura %d/%d, \"o que sabe\" útil %d/%d, horário %d/%d, "
                        + "próximo passo sem excesso %d/%d, sem repetição %d/%d%n", noFallback, RUNS, knowsSomething, RUNS,
                answersHours, RUNS, stepNotEverywhere, RUNS, noRepetition, RUNS);

        assertThat(noFallback).as("sem resposta segura").isGreaterThanOrEqualTo(REQUIRED);
        assertThat(knowsSomething).as("'o que você sabe?' útil").isGreaterThanOrEqualTo(REQUIRED);
        assertThat(answersHours).as("pergunta sobre 00h respondida").isGreaterThanOrEqualTo(REQUIRED);
        assertThat(stepNotEverywhere).as("próximo passo presente, sem repetir em tudo").isGreaterThanOrEqualTo(REQUIRED);
        assertThat(noRepetition).as("sem frase repetida").isGreaterThanOrEqualTo(REQUIRED);
    }

    private static List<String> sentences(String reply) {
        return Arrays.stream(reply.split("(?<=[.!?])\\s+")).map(String::strip).filter(s -> !s.isEmpty()).toList();
    }
}
