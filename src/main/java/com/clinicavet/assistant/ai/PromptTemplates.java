package com.clinicavet.assistant.ai;

import com.clinicavet.assistant.conversation.application.AssistantRequest;
import com.clinicavet.assistant.conversation.domain.ConversationStatus;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Templates de {@code src/main/resources/prompts}, lidos em UTF-8 na inicialização (DM-05): se faltar
 * um, a aplicação não sobe.
 * <p>
 * Linhas que começam com {@code ##} são notas para quem mantém o arquivo (pendências, origem) e
 * <b>não</b> vão para o modelo.
 */
@Component
class PromptTemplates {

    private static final DateTimeFormatter LOCAL_DATE_TIME =
            DateTimeFormatter.ofPattern("EEEE, dd/MM/yyyy, HH:mm", Locale.of("pt", "BR"));

    private final String systemMessage;
    private final String operationalContext;

    PromptTemplates(@Value("classpath:prompts/system-message.st") Resource systemMessage,
                    @Value("classpath:prompts/operational-context.st") Resource operationalContext) {
        this.systemMessage = read(systemMessage);
        this.operationalContext = read(operationalContext);
    }

    /** Camada 1: identidade e comportamento. Texto fixo, sem dados da clínica. */
    String systemMessage() {
        return systemMessage;
    }

    /** Camada 5 (parte da Etapa 2): status e data/hora local, no bloco {@code <estado_do_atendimento>}. */
    String operationalContext(AssistantRequest.OperationalContext context) {
        return new PromptTemplate(operationalContext).render(Map.of(
                "status", describe(context.status()),
                "agora", LOCAL_DATE_TIME.format(context.localNow())));
    }

    private static String describe(ConversationStatus status) {
        return switch (status) {
            case OPEN -> "em atendimento pelo assistente";
            case WAITING_HUMAN -> "sinalizada para revisão da equipe";
            case CLOSED -> "encerrada";
        };
    }

    private static String read(Resource resource) {
        try {
            String raw = resource.getContentAsString(StandardCharsets.UTF_8);
            return raw.lines()
                    .filter(line -> !line.startsWith("##"))
                    .collect(Collectors.joining("\n"))
                    .strip();
        } catch (IOException e) {
            throw new UncheckedIOException("template de prompt ausente ou ilegível: " + resource.getDescription(), e);
        }
    }
}
