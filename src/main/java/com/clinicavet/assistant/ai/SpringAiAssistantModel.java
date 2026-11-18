package com.clinicavet.assistant.ai;

import com.clinicavet.assistant.conversation.application.AssistantModel;
import com.clinicavet.assistant.conversation.application.AssistantModelException;
import com.clinicavet.assistant.conversation.application.AssistantRequest;
import com.clinicavet.assistant.conversation.application.ModelReply;
import com.clinicavet.assistant.conversation.domain.ClinicalPriority;
import com.clinicavet.assistant.conversation.domain.Intent;
import com.clinicavet.assistant.conversation.domain.MessageAuthor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.ResponseFormat;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;

import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Adaptador da porta {@link AssistantModel} com o Spring AI 2.0.0-M4 e a OpenAI (ADR-0002, ADR-0016).
 * <ul>
 *   <li>Uma chamada por tentativa, com saída estruturada em JSON Schema <b>estrito</b>;</li>
 *   <li>histórico montado pela aplicação e convertido aqui em mensagens {@code user}/{@code assistant},
 *       sem {@code ChatMemory} (ADR-0015);</li>
 *   <li>nenhuma tool registrada (DA-02);</li>
 *   <li>a mensagem do tutor vai delimitada como dado ({@code <mensagem_do_cliente>});</li>
 *   <li>modelo, {@code reasoning_effort} e limite de tokens vêm de {@code spring.ai.openai.chat.options};
 *       o timeout, de {@code spring.http.clients.read-timeout}; o retry do Spring AI está desligado.</li>
 * </ul>
 * Não julga a resposta: devolve o conteúdo bruto ao {@code TurnResultClassifier}. Os erros viram
 * {@link AssistantModelException} sem o corpo da resposta da API, que pode trazer parte da chave.
 */
@Component
class SpringAiAssistantModel implements AssistantModel {

    /** Sinais que o modelo pode enviar (os do backend, como alerta e conversa improdutiva, ficam fora). */
    enum ModelSignal {
        EMOTIONAL_DISTRESS, DISSATISFACTION, CONFUSION, FINANCIAL_VULNERABILITY, BEREAVEMENT, SENSITIVE_TOPIC,
        NEEDS_STAFF_DECISION,
        WORSENING, POST_PROCEDURE_CHANGE
    }

    /** Espécie vista pelo modelo; {@code UNKNOWN} quando o tutor não disse (vira ausência no backend). */
    enum ModelSpecies { DOG, CAT, OTHER, UNKNOWN }

    /** Campos que o modelo pode marcar como confirmados (P-08 provisório). */
    enum ModelField { TUTOR_NAME, PET_NAME, PET_SPECIES, PET_BREED, PET_AGE }

    /** Pet citado. Textos vazios significam "não informado" (o modo estrito não aceita campo opcional). */
    record PetSchema(String name, ModelSpecies species, String speciesDescription, String breed, String age,
                     boolean newPet) {
    }

    record ExtractedSchema(String tutorName, List<PetSchema> pets) {
    }

    record ConfirmationSchema(ModelField field, String petName) {
    }

    record AppointmentSchema(String petName, String reason, String preferredPeriod) {
    }

    /** Formato do {@code AssistantTurn} v4 visto pelo modelo: os enums viram listas fechadas no schema. */
    record AssistantTurnSchema(String reply, Intent intent, List<Intent> secondaryIntents, List<String> knowledgeRefs,
                               boolean knowledgeGap, boolean handoffRequested, boolean possibleEmergency,
                               ClinicalPriority priority, List<ModelSignal> signals, boolean clinicalUncertainty,
                               ExtractedSchema extracted, List<ConfirmationSchema> confirmations,
                               AppointmentSchema appointmentRequest) {
    }

    static final String SCHEMA_NAME = "assistant_turn_v4";
    private static final String OPEN_TAG = "<mensagem_do_cliente>";
    private static final String CLOSE_TAG = "</mensagem_do_cliente>";
    private static final Pattern DELIMITER = Pattern.compile("(?i)<\\s*/?\\s*mensagem_do_cliente\\s*>");
    private static final Pattern HTTP_STATUS = Pattern.compile("^HTTP (\\d{3})");

    private final ChatClient chatClient;
    private final PromptTemplates templates;
    private final OpenAiChatOptions structuredOutput;

    SpringAiAssistantModel(ChatClient.Builder chatClientBuilder, PromptTemplates templates) {
        this.chatClient = chatClientBuilder.build();
        this.templates = templates;
        this.structuredOutput = OpenAiChatOptions.builder()
                .responseFormat(strictSchema(SCHEMA_NAME, AssistantTurnSchema.class))
                .build();
    }

    private static ResponseFormat strictSchema(String name, Class<?> type) {
        return ResponseFormat.builder()
                .type(ResponseFormat.Type.JSON_SCHEMA)
                .jsonSchema(ResponseFormat.JsonSchema.builder()
                        .name(name)
                        .schema(new BeanOutputConverter<>(type).getJsonSchema())
                        .strict(true)
                        .build())
                .build();
    }

    @Override
    public ModelReply respond(AssistantRequest request) {
        Prompt prompt = new Prompt(toMessages(request), structuredOutput);
        ChatResponse response;
        try {
            response = chatClient.prompt(prompt).call().chatResponse();
        } catch (RuntimeException e) {
            throw translate(e);
        }
        return toReply(response);
    }

    List<Message> toMessages(AssistantRequest request) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(templates.systemMessage()));
        messages.add(new SystemMessage(templates.clinicContext()));
        messages.add(new SystemMessage(templates.tutorContext(request.customer())));
        messages.add(new SystemMessage(templates.operationalContext(request.operational())));
        for (AssistantRequest.HistoryEntry entry : request.history()) {
            messages.add(entry.author() == MessageAuthor.CUSTOMER
                    ? new UserMessage(delimit(entry.content()))
                    : new AssistantMessage(entry.content()));
        }
        messages.add(new UserMessage(delimit(request.customerMessage())));
        return messages;
    }

    /** Delimita a mensagem do tutor e remove delimitadores falsos que ele tenha escrito. */
    static String delimit(String customerText) {
        String clean = DELIMITER.matcher(customerText).replaceAll("");
        return OPEN_TAG + "\n" + clean + "\n" + CLOSE_TAG;
    }

    private static ModelReply toReply(ChatResponse response) {
        if (response == null) {
            return new ModelReply(null, null, null, null, null, null);
        }
        ChatResponseMetadata metadata = response.getMetadata();
        Usage usage = metadata == null ? null : metadata.getUsage();
        String model = metadata == null ? null : metadata.getModel();
        Integer input = usage == null ? null : usage.getPromptTokens();
        Integer output = usage == null ? null : usage.getCompletionTokens();
        Generation generation = response.getResult();
        if (generation == null) {
            return new ModelReply(null, null, null, model, input, output);
        }
        AssistantMessage message = generation.getOutput();
        Object refusal = message == null ? null : message.getMetadata().get("refusal");
        return new ModelReply(
                message == null ? null : message.getText(),
                generation.getMetadata() == null ? null : generation.getMetadata().getFinishReason(),
                refusal == null ? null : refusal.toString(),
                model, input, output);
    }

    static AssistantModelException translate(RuntimeException e) {
        if (isTimeout(e)) {
            return new AssistantModelException(AssistantModelException.Kind.TIMEOUT, "timeout", e);
        }
        if (e instanceof NonTransientAiException) {
            String status = httpStatus(e.getMessage());
            AssistantModelException.Kind kind = "429".equals(status)
                    ? AssistantModelException.Kind.TRANSIENT
                    : AssistantModelException.Kind.PERMANENT;
            return new AssistantModelException(kind, "HTTP " + (status == null ? "4xx" : status), e);
        }
        if (e instanceof TransientAiException) {
            String status = httpStatus(e.getMessage());
            return new AssistantModelException(AssistantModelException.Kind.TRANSIENT,
                    "HTTP " + (status == null ? "5xx" : status), e);
        }
        if (e instanceof ResourceAccessException) {
            return new AssistantModelException(AssistantModelException.Kind.TRANSIENT, "falha de rede", e);
        }
        // Inclui exceções que o Spring AI eventualmente embrulhe: classifica pela causa.
        Throwable cause = e.getCause();
        if (cause instanceof RuntimeException runtime && cause != e) {
            return translate(runtime);
        }
        return new AssistantModelException(AssistantModelException.Kind.PERMANENT, e.getClass().getSimpleName(), e);
    }

    /**
     * O cliente HTTP efetivo depende do classpath: hoje é o Reactor Netty (lança
     * {@code io.netty.handler.timeout.ReadTimeoutException}, verificado no {@code SpringAiAssistantModelIT}).
     * Para não depender dele, além dos tipos do JDK vale qualquer causa cujo nome termine em
     * {@code TimeoutException}.
     */
    private static boolean isTimeout(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof SocketTimeoutException || t instanceof HttpTimeoutException
                    || t instanceof TimeoutException || t instanceof InterruptedIOException
                    || t.getClass().getSimpleName().endsWith("TimeoutException")) {
                return true;
            }
        }
        return false;
    }

    /** Só o código HTTP. O resto da mensagem (corpo da API) não sai daqui. */
    private static String httpStatus(String message) {
        if (message == null) {
            return null;
        }
        Matcher matcher = HTTP_STATUS.matcher(message);
        return matcher.find() ? matcher.group(1) : null;
    }
}
