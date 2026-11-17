package com.clinicavet.assistant.ai;

import com.clinicavet.assistant.clinicknowledge.infrastructure.KnowledgeFileLoader;
import com.clinicavet.assistant.conversation.application.AssistantRequest;
import com.clinicavet.assistant.conversation.domain.ConversationStatus;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class PromptTemplatesTest {

    private final PromptTemplates templates = new PromptTemplates(
            new ClassPathResource("prompts/system-message.st"), new ClassPathResource("prompts/waiting-human.st"),
            new ClassPathResource("prompts/operational-context.st"),
            new ClassPathResource("prompts/clinic-context.st"),
            KnowledgeFileLoader.parse(read("config/clinic-knowledge.ficticia.yml"), "ficticia"));

    private static String read(String path) {
        try {
            return java.nio.file.Files.readString(java.nio.file.Path.of(path));
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    @Test
    void systemMessage_dropsMaintainerNotes_andKeepsTheRules() {
        String system = templates.systemMessage();

        assertThat(system).doesNotContain("##").doesNotContain("PROVISÓRIO").doesNotContain("P-10");
        assertThat(system).contains("português do Brasil", "<mensagem_do_cliente>", "intent", "HUMAN_REQUEST");
        // Nenhum dado da clínica no texto fixo (P-11 pendente): só a regra de não inventar.
        assertThat(system).contains("Nunca invente").contains("knowledgeRefs", "knowledgeGap");
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

    @Test
    void clinicContext_listsEveryItemByKey_withUnknownExplicit_andWithoutInternalTexts() {
        String clinic = templates.clinicContext();

        assertThat(clinic).startsWith("<dados_da_clinica>").endsWith("</dados_da_clinica>").doesNotContain("##");
        assertThat(clinic).contains("[hours.saturday] 08:00–12:00", "[hours.sunday] fechado",
                "[services.consulta.price-cents] R$ 150,00", "[services.vacina-v10.price-note] por dose",
                "[services.banho-e-tosa.price-cents] DESCONHECIDO — confirmar com a equipe",
                "[hours.holidays] DESCONHECIDO — confirmar com a equipe", "[hours.twenty-four-hours] não");
        assertThat(clinic).doesNotContain("approved-texts");
    }
}
