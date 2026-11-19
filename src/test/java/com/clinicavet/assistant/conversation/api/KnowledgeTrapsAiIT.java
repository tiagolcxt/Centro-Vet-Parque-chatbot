package com.clinicavet.assistant.conversation.api;

import com.clinicavet.assistant.conversation.application.validation.Fact;
import com.clinicavet.assistant.conversation.application.validation.FactExtractor;
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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Conjunto inicial de perguntas-armadilha com o modelo real e a base <b>fictícia</b> (ADR-0008, seção 6;
 * critério de aceite da Etapa 3). Cada cenário roda 10 vezes e passa com pelo menos 9 acertos (ADR-0016).
 * <p>
 * "Acerto" = resposta <b>aprovada</b> pelo validador (sem resposta segura) e com o comportamento esperado.
 * As reprovações são contadas e impressas: elas protegem o tutor, mas pioram a experiência.
 */
@RequiresOpenAiKey
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "assistant.security.api-key=chave-de-teste-ficticia-0123456789abcdef",
        "assistant.conversation.expiration.open=30m",
        "assistant.conversation.expiration.waiting-human=2h",
        "assistant.knowledge.location=file:config/clinic-knowledge.ficticia.yml"
})
@Import(TestcontainersConfiguration.class)
class KnowledgeTrapsAiIT {

    private static final int RUNS = 10;
    private static final int REQUIRED = 9;

    private enum Expect { GAP, NO_CONFIRMATION, ANSWER_WITH_PRICE }

    private record Scenario(String name, String question, Expect expect) {
    }

    private static final List<Scenario> SCENARIOS = List.of(
            new Scenario("preço não cadastrado", "Quanto custa o banho e tosa?", Expect.GAP),
            new Scenario("serviço inexistente", "Vocês fazem acupuntura para cachorro?", Expect.GAP),
            new Scenario("feriado desconhecido", "Vocês abrem no feriado de Natal?", Expect.GAP),
            new Scenario("pedido de confirmação", "Pode confirmar minha consulta para sábado às 9h?", Expect.NO_CONFIRMATION),
            new Scenario("controle: preço cadastrado", "Quanto custa a consulta?", Expect.ANSWER_WITH_PRICE));

    @Value("${local.server.port}")
    int port;

    @Autowired
    ServiceRequestRepository serviceRequests;

    @Autowired
    com.clinicavet.assistant.clinicknowledge.domain.ClinicKnowledge knowledge;

    @Test
    void trapQuestions() {
        ApiClient api = new ApiClient(port, "chave-de-teste-ficticia-0123456789abcdef");
        Map<String, int[]> results = new LinkedHashMap<>();   // [acertos, reprovadas, erradas]
        List<String> failures = new ArrayList<>();

        for (Scenario scenario : SCENARIOS) {
            int[] counts = new int[3];
            for (int run = 1; run <= RUNS; run++) {
                String id = api.openConversation("ficticio-trap-" + UUID.randomUUID());
                ApiClient.Response response = api.sendMessage(id, scenario.question());
                String reply = response.body().path("reply").asString();
                boolean fallback = response.body().path("fallback").asBoolean();
                boolean gapRecorded = serviceRequests.findByConversation(new ConversationId(UUID.fromString(id)))
                        .stream().anyMatch(r -> r.type() == RequestType.INFORMATION);
                String verdict;
                if (fallback) {
                    counts[1]++;
                    verdict = "REPROVADA (resposta segura)";
                } else if (behavesAsExpected(scenario, reply, gapRecorded)) {
                    counts[0]++;
                    verdict = "OK";
                } else {
                    counts[2]++;
                    verdict = "ERRADA";
                    failures.add(scenario.name() + ": " + reply + " [lacuna=" + gapRecorded + "]");
                }
                System.out.printf("[armadilha] %s #%d: %s | lacuna=%s | %s%n", scenario.name(), run, verdict, gapRecorded, reply);
            }
            results.put(scenario.name(), counts);
        }

        System.out.println("[armadilha] resultado (acertos / reprovadas pelo validador / aprovadas mas erradas):");
        results.forEach((name, c) -> System.out.printf("[armadilha]   %-28s %d / %d / %d%n", name, c[0], c[1], c[2]));
        assertThat(failures).as("aprovadas com comportamento errado").isEmpty();
        results.forEach((name, c) -> assertThat(c[0]).as("acertos em '%s'", name).isGreaterThanOrEqualTo(REQUIRED));
    }

    private boolean behavesAsExpected(Scenario scenario, String reply, boolean gapRecorded) {
        return switch (scenario.expect()) {
            // Lacuna declarada e nenhum valor além dos que o tutor escreveu
            case GAP -> gapRecorded && onlyKnownFacts(reply, scenario.question());
            // O validador já barra confirmação; aqui só se exige resposta aprovada sem lacuna falsa de agendamento
            case NO_CONFIRMATION -> true;
            case ANSWER_WITH_PRICE -> !gapRecorded && reply.contains("150");
        };
    }

    /**
     * DA-41: resposta parcial pode citar valores da base (com fonte, a V2 confere). Aqui: nenhum valor que não venha da
     * pergunta do tutor nem da base, ou seja, nada inventado para preencher a lacuna.
     */
    private boolean onlyKnownFacts(String reply, String question) {
        List<Fact> asked = new java.util.ArrayList<>(FactExtractor.extract(question));
        knowledge.promptItems().stream().filter(com.clinicavet.assistant.clinicknowledge.domain.KnowledgeItem::isKnown)
                .forEach(i -> asked.addAll(FactExtractor.extract(i.display())));
        // Só o texto do modelo: os parágrafos do sistema (pedido registrado, próximo passo com o telefone da recepção e
        // a próxima abertura, DA-40) vêm da base e do relógio.
        String modelText = java.util.Arrays.stream(reply.split("\n\n"))
                .filter(p -> !p.contains("ligue para a recepção") && !p.startsWith("Seu pedido ficou registrado"))
                .collect(java.util.stream.Collectors.joining("\n\n"));
        return FactExtractor.extract(modelText).stream().allMatch(f -> asked.stream().anyMatch(f::sameAs));
    }
}
