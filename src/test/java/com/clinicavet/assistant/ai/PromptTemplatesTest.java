package com.clinicavet.assistant.ai;

import com.clinicavet.assistant.conversation.application.AssistantRequest;
import com.clinicavet.assistant.conversation.domain.ConversationStatus;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class PromptTemplatesTest {

    private final PromptTemplates templates = new PromptTemplates(
            new ClassPathResource("prompts/system-message.st"), new ClassPathResource("prompts/operational-context.st"));

    @Test
    void systemMessage_dropsMaintainerNotes_andKeepsTheRules() {
        String system = templates.systemMessage();

        assertThat(system).doesNotContain("##").doesNotContain("PROVISÓRIO").doesNotContain("P-10");
        assertThat(system).contains("português do Brasil", "<mensagem_do_cliente>", "intent", "HUMAN_REQUEST");
        // Nenhum dado da clínica no texto fixo (P-11 pendente): só a regra de não inventar.
        assertThat(system).contains("Nunca invente");
    }

    @Test
    void systemMessage_listsEveryIntent() {
        for (var intent : com.clinicavet.assistant.conversation.domain.Intent.values()) {
            assertThat(templates.systemMessage()).contains(intent.name());
        }
    }

    @Test
    void operationalContext_rendersStatusAndLocalDateTimeInPortuguese() {
        String rendered = templates.operationalContext(new AssistantRequest.OperationalContext(ConversationStatus.OPEN,
                ZonedDateTime.of(2026, 11, 16, 9, 5, 0, 0, ZoneId.of("America/Sao_Paulo"))));

        assertThat(rendered)
                .startsWith("<estado_do_atendimento>")
                .endsWith("</estado_do_atendimento>")
                .contains("em atendimento pelo assistente")
                .contains("segunda-feira, 16/11/2026, 09:05")
                .doesNotContain("##");
    }
}
