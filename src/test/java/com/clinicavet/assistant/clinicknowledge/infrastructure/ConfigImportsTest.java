package com.clinicavet.assistant.clinicknowledge.infrastructure;

import com.clinicavet.assistant.clinicknowledge.domain.ConfigImports;
import com.clinicavet.assistant.shared.exception.DomainValidationException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Planilha de serviços (S3-14) e conversas exportadas do WhatsApp (S3-15), com dados fictícios. */
class ConfigImportsTest {

    @Test
    void sheet_detectsSeparator_headers_pricesAndDepartments() {
        ConfigImportService.ServiceSheet.Parsed parsed = ConfigImportService.ServiceSheet.parse("""
                Nome;Preço;Setor;Observação
                Consulta clínica;R$ 150,00;Clínica;
                "Banho; pequeno porte";60;Banho e tosa;por banho
                Tosa;sessenta;Pet shop;
                ;10;;
                Vacina V10;1.200,50;Cozinha;
                """);
        assertThat(parsed.rows()).extracting(ConfigImportService.ServiceSheet.Row::name)
                .containsExactly("Consulta clínica", "Banho; pequeno porte");
        assertThat(parsed.rows().get(0).values()).containsEntry("price-cents", 15000L).containsEntry("department", "CLINICAL");
        assertThat(parsed.rows().get(1).values()).containsEntry("price-cents", 6000L).containsEntry("department", "PETSHOP")
                .containsEntry("price-note", "por banho");
        assertThat(parsed.problems()).hasSize(3)
                .anyMatch(p -> p.startsWith("linha 4: preço"))
                .anyMatch(p -> p.startsWith("linha 5: sem nome"))
                .anyMatch(p -> p.startsWith("linha 6: setor"));
    }

    @Test
    void sheet_withoutNameColumn_isRefused() {
        assertThatThrownBy(() -> ConfigImportService.ServiceSheet.parse("preço,setor\n10,clínica"))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    void cents_acceptsBrazilianAndPlainFormats() {
        assertThat(ConfigImportService.ServiceSheet.cents("R$ 1.500,00")).isEqualTo(150000L);
        assertThat(ConfigImportService.ServiceSheet.cents("150")).isEqualTo(15000L);
        assertThat(ConfigImportService.ServiceSheet.cents("150.5")).isEqualTo(15050L);
        assertThat(ConfigImportService.ServiceSheet.cents("1.500")).isEqualTo(150000L);
        assertThat(ConfigImportService.ServiceSheet.cents("abc")).isNull();
    }

    private static final String ANDROID = """
            21/11/2026 09:00 - Clínica Fictícia: Bom dia! Como podemos ajudar?
            21/11/2026 09:01 - Tutor Fictício: Quanto custa o banho de um cão pequeno?
            21/11/2026 09:02 - Clínica Fictícia: O banho do porte pequeno custa R$ 60,00.
            21/11/2026 09:03 - Tutor Fictício: <Mídia oculta>
            21/11/2026 09:04 - Tutor Fictício: vocês abrem sábado?
            meu telefone é (11) 90000-0000
            21/11/2026 09:05 - Clínica Fictícia: Sábado das 8h às 12h.
            """;

    private static final String IOS = """
            [22/11/2026, 10:00:00] Outra Tutora Fictícia: Qual o horário de sábado?
            [22/11/2026, 10:01:10] Clínica Fictícia: Sábado das 8h às 12h.
            [22/11/2026, 10:02:00] Outra Tutora Fictícia: Obrigada!
            """;

    @Test
    void whatsApp_findsTheClinic_groupsQuestionsByTopic_andMasksContacts() {
        ConfigImports.ChatAnalysis analysis = WhatsAppExport.analyze(List.of(ANDROID, IOS), null);
        assertThat(analysis.clinicSender()).as("aparece nas duas conversas").isEqualTo("Clínica Fictícia");
        assertThat(analysis.questions()).isEqualTo(3);
        ConfigImports.FaqSuggestion hours = analysis.suggestions().getFirst();
        assertThat(hours.topic()).isEqualTo("Horário de funcionamento");
        assertThat(hours.count()).isEqualTo(2);
        assertThat(hours.suggestedAnswer()).isEqualTo("Sábado das 8h às 12h.");
        assertThat(hours.examples()).anyMatch(e -> e.contains("•••")).noneMatch(e -> e.contains("90000"));
        assertThat(analysis.suggestions()).anyMatch(s -> s.topic().equals("Banho e tosa")
                && s.suggestedAnswer().contains("R$ 60,00"));
    }

    @Test
    void whatsApp_clinicCanBeChosen_andUnknownFormatIsRefused() {
        ConfigImports.ChatAnalysis analysis = WhatsAppExport.analyze(List.of(ANDROID), "Tutor Fictício");
        assertThat(analysis.clinicSender()).isEqualTo("Tutor Fictício");
        assertThatThrownBy(() -> WhatsAppExport.analyze(List.of("texto qualquer sem data"), null))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    void slug_isAValidEntryId() {
        assertThat(BusinessTemplateService.slug("Qual o horário de funcionamento?"))
                .isEqualTo("qual-o-horario-de-funcionamento");
        assertThat(BusinessTemplateService.slug("Tosa higiênica")).isEqualTo("tosa-higienica");
        assertThat(BusinessTemplateService.slug("???")).isEqualTo("item");
    }
}
