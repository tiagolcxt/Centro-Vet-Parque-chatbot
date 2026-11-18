package com.clinicavet.assistant.ai;

import com.clinicavet.assistant.clinicknowledge.infrastructure.KnowledgeFileLoader;
import com.clinicavet.assistant.conversation.application.AssistantRequest;
import com.clinicavet.assistant.conversation.domain.ConversationStatus;
import com.clinicavet.assistant.servicerequest.domain.RequestStatus;
import com.clinicavet.assistant.servicerequest.domain.RequestType;
import com.clinicavet.assistant.tutor.domain.ProvenanceStatus;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class PromptTemplatesTest {

    private final PromptTemplates templates = new PromptTemplates(
            new ClassPathResource("prompts/system-message.st"),
            new ClassPathResource("prompts/operational-context.st"),
            new ClassPathResource("prompts/clinic-context.st"), new ClassPathResource("prompts/tutor-context.st"),
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
    void systemMessage_carriesTheLanguageAndFormatRules_ofDa38() {
        String system = templates.systemMessage();

        assertThat(system).contains("português do Brasil", "outro idioma", "no máximo 3 frases curtas",
                "uma pergunta por mensagem", "sem markdown", "Não use emojis", "sem diminutivos",
                "assistente virtual da clínica");
        assertThat(system).doesNotContain("PENDENTE").doesNotContain("DA-38");
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
                .contains("Solicitações desta conversa:\n(nenhuma)")
                .doesNotContain("##");
    }

    @Test
    void operationalContext_listsRequests_withMissingFields_andPreviousOnlyByTypeDateAndStatus() {
        var collecting = new AssistantRequest.RequestView(RequestType.APPOINTMENT, RequestStatus.COLLECTING, "Mel",
                "vacina", null, java.util.List.of("nome do tutor", "espécie do pet"));
        var pending = new AssistantRequest.RequestView(RequestType.APPOINTMENT, RequestStatus.PENDING, "Thor",
                "banho", "manhã", java.util.List.of());
        var info = new AssistantRequest.RequestView(RequestType.INFORMATION, RequestStatus.PENDING, null, null, null,
                java.util.List.of());
        var previous = new AssistantRequest.PreviousRequest(RequestType.APPOINTMENT, RequestStatus.IN_PROGRESS,
                java.time.LocalDate.of(2026, 11, 2));

        String rendered = templates.operationalContext(new AssistantRequest.OperationalContext(ConversationStatus.OPEN,
                ZonedDateTime.of(2026, 11, 16, 9, 5, 0, 0, ZoneId.of("America/Sao_Paulo")),
                java.util.List.of(collecting, pending, info), java.util.List.of(previous)));

        assertThat(rendered)
                .contains("- Pedido de agendamento em coleta, ainda NÃO registrado. Pet: Mel; motivo: vacina; "
                        + "preferência: não informado. Falta: nome do tutor; espécie do pet.")
                .contains("- Pedido de agendamento registrado para a equipe (aguardando a equipe). Pet: Thor; "
                        + "motivo: banho; preferência: manhã. Dia e horário ainda não confirmados pela equipe.")
                .contains("- Pedido de informação para a equipe (aguardando a equipe)")
                .contains("- Pedido de agendamento aberto em 02/11/2026 (em andamento com a equipe) — atendimento "
                        + "anterior, não reutilizar dados");
    }

    @Test
    void opening_isDescribedFromTheBackendCalculation() {
        var zone = ZoneId.of("America/Sao_Paulo");
        var next = ZonedDateTime.of(2026, 11, 19, 8, 0, 0, 0, zone);
        var open = new com.clinicavet.assistant.clinicknowledge.domain.OpeningHours.Status(
                com.clinicavet.assistant.clinicknowledge.domain.OpeningHours.State.OPEN, false,
                java.util.Optional.of(java.time.LocalTime.of(18, 0)), java.util.Optional.empty(), false);
        var closed = new com.clinicavet.assistant.clinicknowledge.domain.OpeningHours.Status(
                com.clinicavet.assistant.clinicknowledge.domain.OpeningHours.State.CLOSED, false,
                java.util.Optional.empty(), java.util.Optional.of(next), true);

        assertThat(PromptTemplates.opening(open)).isEqualTo("aberta agora, até 18:00");
        assertThat(PromptTemplates.opening(closed)).isEqualTo("fechada agora; abre quinta-feira, 19/11, às 08:00 "
                + "(feriados não cadastrados: em feriado, a recepção confirma)");
        assertThat(PromptTemplates.opening(com.clinicavet.assistant.clinicknowledge.domain.OpeningHours.Status.unknown(true)))
                .startsWith("horário de hoje não cadastrado");
    }

    @Test
    void tutorContext_showsEachFieldWithItsProvenanceStatus() {
        var f = (java.util.function.BiFunction<String, ProvenanceStatus, AssistantRequest.CustomerContext.FieldView>)
                AssistantRequest.CustomerContext.FieldView::new;
        var customer = new AssistantRequest.CustomerContext(f.apply("Ana", ProvenanceStatus.CONFIRMED), java.util.List.of(
                new AssistantRequest.CustomerContext.PetView(f.apply("Mel", ProvenanceStatus.REGISTERED),
                        f.apply("gato", ProvenanceStatus.REGISTERED), null, f.apply("3 anos", ProvenanceStatus.TO_RECONFIRM)),
                new AssistantRequest.CustomerContext.PetView(f.apply("Thor", ProvenanceStatus.MENTIONED), null, null, null)));

        String rendered = templates.tutorContext(customer);

        assertThat(rendered).isEqualTo("<cliente>\nTutor: Ana [CONFIRMADA]\nPets:\n"
                + "- Mel [CADASTRADA]; espécie: gato [CADASTRADA]; idade: 3 anos [A_RECONFIRMAR]\n"
                + "- Thor [MENCIONADA]; espécie: não informada\n</cliente>");
        assertThat(templates.tutorContext(AssistantRequest.CustomerContext.NONE))
                .isEqualTo("<cliente>\n(nenhum dado do tutor registrado)\n</cliente>");
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
