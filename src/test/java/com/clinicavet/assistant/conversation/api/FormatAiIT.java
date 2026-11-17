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
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Formato das respostas (DA-38, parte técnica da P-10) com o modelo real e dados fictícios. Primeira mensagem de
 * cada conversa, em cenários sem encaminhamento (o texto do sistema acrescentado não conta). 10 execuções por
 * cenário.
 * <ul>
 *   <li>proibido (0 ocorrências): emoji e markdown (negrito, título, tabela);</li>
 *   <li>mínimo 9/10: até 500 caracteres, no máximo uma pergunta, apresentação como assistente virtual (garantida pelo
 *       backend quando o modelo não se apresenta), resposta sem inglês (inclusive para mensagem em inglês).</li>
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
class FormatAiIT {

    private static final int RUNS = 10;
    private static final int REQUIRED = 9;
    private static final int MAX_LENGTH = 500;
    /** Emojis e símbolos pictográficos (inclui os pares substitutos dos emojis comuns). */
    private static final Pattern EMOJI = Pattern.compile("[\\p{So}\\x{1F000}-\\x{1FAFF}\\x{2600}-\\x{27BF}]");
    private static final Pattern MARKDOWN = Pattern.compile("\\*\\*|__|(?m)^#{1,6} |(?m)^\\|.*\\|$|`");
    /** Palavras em inglês que não aparecem numa resposta em português. */
    private static final Pattern ENGLISH = Pattern.compile("(?i)\\b(the|you|we|are|our|hello|hi|open|saturdays?|"
            + "sorry|please|team)\\b");

    private record Scenario(String name, String message) {
    }

    @Value("${local.server.port}")
    int port;

    @Test
    void firstReplies_followTheLanguageAndFormatRules() {
        ApiClient api = new ApiClient(port, "chave-de-teste-ficticia-0123456789abcdef");
        List<Scenario> scenarios = List.of(
                new Scenario("saudação", "Oi, boa tarde!"),
                new Scenario("horário", "Vocês abrem no sábado? Até que horas?"),
                new Scenario("serviços", "Quais serviços vocês oferecem?"),
                new Scenario("agendamento", "Quero marcar uma consulta para o meu cachorro"),
                new Scenario("outro idioma", "Hi, do you open on Saturdays?"));

        Map<String, int[]> results = new LinkedHashMap<>();   // [tamanho, uma pergunta, apresentação, português]
        List<String> forbidden = new ArrayList<>();
        for (Scenario scenario : scenarios) {
            int[] counts = new int[4];
            for (int run = 1; run <= RUNS; run++) {
                String id = api.openConversation("ficticio-formato-" + UUID.randomUUID());
                JsonNode body = api.sendMessage(id, scenario.message()).body();
                String reply = body.path("reply").asString();
                String lower = reply.toLowerCase(Locale.ROOT);
                boolean shortEnough = reply.length() <= MAX_LENGTH;
                boolean oneQuestion = reply.chars().filter(c -> c == '?').count() <= 1;
                boolean presented = lower.contains("assistente virtual");
                boolean portuguese = !ENGLISH.matcher(reply).find();
                counts[0] += shortEnough ? 1 : 0;
                counts[1] += oneQuestion ? 1 : 0;
                counts[2] += presented ? 1 : 0;
                counts[3] += portuguese ? 1 : 0;
                if (EMOJI.matcher(reply).find()) {
                    forbidden.add(scenario.name() + ": emoji em: " + reply);
                }
                if (MARKDOWN.matcher(reply).find()) {
                    forbidden.add(scenario.name() + ": markdown em: " + reply);
                }
                System.out.printf("[formato] %s #%d: %d caracteres, perguntas=%s, apresentou=%s, pt=%s, status=%s | %s%n",
                        scenario.name(), run, reply.length(), oneQuestion ? "<=1" : ">1", presented, portuguese,
                        body.path("status").asString(), reply.replace("\n", " / "));
            }
            results.put(scenario.name(), counts);
        }
        System.out.println("[formato] resultado (até 500 / uma pergunta / apresentação / português), de " + RUNS + ":");
        results.forEach((name, c) -> System.out.printf("[formato]   %-14s %d / %d / %d / %d%n", name, c[0], c[1], c[2], c[3]));

        assertThat(forbidden).as("emoji ou markdown").isEmpty();
        results.forEach((name, c) -> {
            assertThat(c[0]).as("tamanho em '%s'", name).isGreaterThanOrEqualTo(REQUIRED);
            assertThat(c[1]).as("uma pergunta em '%s'", name).isGreaterThanOrEqualTo(REQUIRED);
            assertThat(c[2]).as("apresentação em '%s'", name).isGreaterThanOrEqualTo(REQUIRED);
            assertThat(c[3]).as("português em '%s'", name).isGreaterThanOrEqualTo(REQUIRED);
        });
    }
}
