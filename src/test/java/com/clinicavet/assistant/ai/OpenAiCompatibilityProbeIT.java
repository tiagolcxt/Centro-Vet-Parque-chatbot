package com.clinicavet.assistant.ai;

import com.clinicavet.assistant.support.IntegrationTest;
import com.clinicavet.assistant.support.RequiresOpenAiKey;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.ResponseFormat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verificações obrigatórias do ADR-0016, contra a API real, com dados fictícios. Captura o corpo
 * de cada requisição enviada à OpenAI para verificar o que de fato sai.
 * <ol>
 *   <li>{@code reasoning_effort=low} é enviado e aceito pelo {@code gpt-6-luna};</li>
 *   <li>{@code temperature} não é enviada;</li>
 *   <li>saída estruturada em modo estrito funciona;</li>
 *   <li>{@code finish_reason} (truncamento) e o campo de recusa chegam à aplicação.</li>
 * </ol>
 */
@IntegrationTest
@RequiresOpenAiKey
@Import(OpenAiCompatibilityProbeIT.CaptureRequests.class)
class OpenAiCompatibilityProbeIT {

    /** Formato mínimo, só para a verificação; o AssistantTurn real nasce na Etapa 2. */
    record ProbeTurn(String reply, String intent) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class CaptureRequests {

        static final List<String> BODIES = new CopyOnWriteArrayList<>();

        @Bean
        RestClientCustomizer captureOpenAiRequests() {
            return builder -> builder.requestInterceptor((request, body, execution) -> {
                if (request.getURI().getHost().contains("openai")) {
                    BODIES.add(new String(body, StandardCharsets.UTF_8));
                }
                return execution.execute(request, body);
            });
        }
    }

    @Autowired
    OpenAiChatModel chatModel;

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void verificacoes_1_2_3_reasoningSemTemperatureComSaidaEstruturadaEstrita() {
        CaptureRequests.BODIES.clear();
        BeanOutputConverter<ProbeTurn> converter = new BeanOutputConverter<>(ProbeTurn.class);
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .responseFormat(ResponseFormat.builder()
                        .type(ResponseFormat.Type.JSON_SCHEMA)
                        .jsonSchema(ResponseFormat.JsonSchema.builder()
                                .name("probe_turn")
                                .schema(converter.getJsonSchema())
                                .strict(true)
                                .build())
                        .build())
                .build();
        Prompt prompt = new Prompt(List.of(
                new SystemMessage("Você é a recepcionista virtual de uma clínica veterinária FICTÍCIA. "
                        + "Responda em português, em uma frase. intent deve ser um destes valores: "
                        + "GREETING, CLINIC_INFORMATION, OTHER."),
                new UserMessage("Oi! Vocês abrem no sábado?")), options);

        ChatResponse response = chatModel.call(prompt);

        String body = lastBody();
        JsonNode sent = json.readTree(body);
        JsonNode result = json.readTree(response.getResult().getOutput().getText());
        String finishReason = response.getResult().getMetadata().getFinishReason();
        System.out.println("[probe] modelo pedido=" + sent.path("model").asString()
                + " | modelo respondido=" + response.getMetadata().getModel()
                + " | reasoning_effort=" + sent.path("reasoning_effort").asString()
                + " | temperature presente=" + sent.has("temperature")
                + " | response_format=" + sent.path("response_format").path("type").asString()
                + " strict=" + sent.path("response_format").path("json_schema").path("strict").asBoolean()
                + " | finish_reason=" + finishReason
                + " | intent=" + result.path("intent").asString()
                + " | uso=" + response.getMetadata().getUsage());

        // 1. reasoning_effort enviado e aceito (a chamada não falhou)
        assertThat(sent.path("model").asString()).isEqualTo("gpt-6-luna");
        assertThat(sent.path("reasoning_effort").asString()).isEqualTo("low");
        // 2. sem temperature na requisição
        assertThat(sent.has("temperature")).as("temperature não deve ser enviada").isFalse();
        // 3. saída estruturada estrita, conforme o schema
        assertThat(sent.path("response_format").path("type").asString()).isEqualTo("json_schema");
        assertThat(sent.path("response_format").path("json_schema").path("strict").asBoolean()).isTrue();
        assertThat(result.path("reply").asString()).isNotBlank();
        assertThat(result.path("intent").asString()).isIn("GREETING", "CLINIC_INFORMATION", "OTHER");
        assertThat(finishReason).isNotBlank();
    }

    @Test
    void verificacao_4_truncamentoERecusaChegamAAplicacao() {
        CaptureRequests.BODIES.clear();
        OpenAiChatOptions tiny = OpenAiChatOptions.builder().maxCompletionTokens(16).build();
        Prompt prompt = new Prompt(List.of(new UserMessage(
                "Escreva um texto longo, com pelo menos 300 palavras, sobre cuidados gerais com filhotes.")), tiny);

        ChatResponse response = chatModel.call(prompt);

        String finishReason = response.getResult().getMetadata().getFinishReason();
        AssistantMessage output = response.getResult().getOutput();
        System.out.println("[probe] finish_reason com 16 tokens=" + finishReason
                + " | chaves de metadados da mensagem=" + output.getMetadata().keySet()
                + " | max_completion_tokens enviado=" + json.readTree(lastBody()).path("max_completion_tokens").asInt());

        assertThat(finishReason).as("truncamento precisa ser visível").isNotNull();
        assertThat(finishReason.toLowerCase()).isEqualTo("length");
        assertThat(output.getMetadata()).as("campo de recusa exposto nos metadados").containsKey("refusal");
    }

    private static String lastBody() {
        assertThat(CaptureRequests.BODIES).as("nenhuma requisição capturada").isNotEmpty();
        return CaptureRequests.BODIES.getLast();
    }
}
