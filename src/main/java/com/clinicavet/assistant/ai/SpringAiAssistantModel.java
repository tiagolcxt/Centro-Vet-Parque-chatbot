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
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
        EMOTIONAL_DISTRESS, DISSATISFACTION, CONFUSION, FINANCIAL_VULNERABILITY, BEREAVEMENT, NEEDS_STAFF_DECISION,
        WORSENING, POST_PROCEDURE_CHANGE
    }

    /** Formato do {@code AssistantTurn} v3 visto pelo modelo: os enums viram listas fechadas no schema. */
    record AssistantTurnSchema(String reply, Intent intent, List<Intent> secondaryIntents, List<String> knowledgeRefs,
                               boolean knowledgeGap, boolean handoffRequested, boolean possibleEmergency,
                               ClinicalPriority priority, List<ModelSignal> signals, boolean clinicalUncertainty) {
    }

    /** Modo restrito ({@code WAITING_HUMAN}): só sinais, sem texto (ADR-0007, item 4). */
    record RestrictedSchema(boolean possibleEmergency, boolean explicitHumanRequest, ClinicalPriority priority,
                            List<ModelSignal> signals) {
    }

    static final String SCHEMA_NAME = "assistant_turn_v3";
    static final String RESTRICTED_SCHEMA_NAME = "waiting_human_signals_v1";
    /** DA-28: modo restrito com saída de até 500 tokens e prazo de 15 s, sem nova tentativa. */
    static final int RESTRICTED_MAX_TOKENS = 500;
    static final Duration RESTRICTED_TIMEOUT = Duration.ofSeconds(15);
    private static final String OPEN_TAG = "<mensagem_do_cliente>";
    private static final String CLOSE_TAG = "</mensagem_do_cliente>";
    private static final Pattern DELIMITER = Pattern.compile("(?i)<\\s*/?\\s*mensagem_do_cliente\\s*>");
    private static final Pattern HTTP_STATUS = Pattern.compile("^HTTP (\\d{3})");

    private final ChatClient chatClient;
    private final PromptTemplates templates;
    private final OpenAiChatOptions structuredOutput;
    private final OpenAiChatOptions restrictedOutput;
    private final ExecutorService restrictedExecutor = Executors.newVirtualThreadPerTaskExecutor();

    SpringAiAssistantModel(ChatClient.Builder chatClientBuilder, PromptTemplates templates) {
        this.chatClient = chatClientBuilder.build();
        this.templates = templates;
        this.structuredOutput = OpenAiChatOptions.builder()
                .responseFormat(strictSchema(SCHEMA_NAME, AssistantTurnSchema.class))
                .build();
        this.restrictedOutput = OpenAiChatOptions.builder()
                .responseFormat(strictSchema(RESTRICTED_SCHEMA_NAME, RestrictedSchema.class))
                .maxCompletionTokens(RESTRICTED_MAX_TOKENS)
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
        if (request.mode() == AssistantRequest.Mode.RESTRICTED) {
            return respondRestricted(request);
        }
        Prompt prompt = new Prompt(toMessages(request), structuredOutput);
        ChatResponse response;
        try {
            response = chatClient.prompt(prompt).call().chatResponse();
        } catch (RuntimeException e) {
            throw translate(e);
        }
        return toReply(response);
    }

    /**
     * O prazo de 15 s do modo restrito vale para a resposta: depois dele, o fail-safe responde ao tutor. A
     * requisição HTTP em si continua até o read-timeout geral (30 s) e é descartada; um cliente HTTP próprio
     * para o modo restrito evitaria isso, mas não foi necessário no MVP (ADR-0016, complemento).
     */
    private ModelReply respondRestricted(AssistantRequest request) {
        Prompt prompt = new Prompt(toMessages(request), restrictedOutput);
        Future<ChatResponse> call = restrictedExecutor.submit(() -> chatClient.prompt(prompt).call().chatResponse());
        try {
            return toReply(call.get(RESTRICTED_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS));
        } catch (TimeoutException e) {
            call.cancel(true);
            throw new AssistantModelException(AssistantModelException.Kind.TIMEOUT, "timeout do modo restrito", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssistantModelException(AssistantModelException.Kind.TRANSIENT, "interrompido", e);
        } catch (ExecutionException e) {
            throw e.getCause() instanceof RuntimeException runtime ? translate(runtime)
                    : new AssistantModelException(AssistantModelException.Kind.PERMANENT, "falha", e);
        }
    }

    List<Message> toMessages(AssistantRequest request) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(request.mode() == AssistantRequest.Mode.RESTRICTED
                ? templates.waitingHumanMessage() : templates.systemMessage()));
        messages.add(new SystemMessage(templates.clinicContext()));
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
