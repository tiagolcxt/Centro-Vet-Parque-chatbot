package com.clinicavet.assistant.clinicknowledge.infrastructure;

import com.clinicavet.assistant.clinicknowledge.domain.ClinicKnowledge;
import com.clinicavet.assistant.clinicknowledge.domain.KnowledgeItem;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** ADR-0003, item 4: arquivo inválido impede a inicialização, com a chave e o problema. */
class KnowledgeFileLoaderTest {

    private static final String FICTITIOUS = read("config/clinic-knowledge.ficticia.yml");
    private static final String EXAMPLE = read("config/clinic-knowledge.example.yml");

    private static String read(String path) {
        try {
            return Files.readString(Path.of(path)).replace("\r\n", "\n");   // independe do fim de linha do checkout
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private static List<String> problemsOf(String yaml) {
        try {
            KnowledgeFileLoader.parse(yaml, "teste");
        } catch (KnowledgeValidationException e) {
            return e.problems();
        }
        throw new AssertionError("o arquivo deveria ter sido recusado");
    }

    @Test
    void fictitiousFixture_loads_withTypedValues() {
        ClinicKnowledge knowledge = KnowledgeFileLoader.parse(FICTITIOUS, "ficticia");

        assertThat(knowledge.item("hours.saturday")).map(KnowledgeItem::value).contains(List.of("08:00-12:00"));
        assertThat(knowledge.item("hours.sunday")).map(KnowledgeItem::display).contains("fechado");
        assertThat(knowledge.item("services.consulta.price-cents")).map(KnowledgeItem::value).contains(15000L);
        assertThat(knowledge.item("services.banho-e-tosa.price-cents")).map(KnowledgeItem::isKnown).contains(false);
        assertThat(knowledge.item("hours.holidays")).map(KnowledgeItem::display).contains(KnowledgeItem.UNKNOWN_TEXT);
        assertThat(knowledge.pricesDisclosed()).isTrue();
        assertThat(knowledge.resolve("services.consulta")).hasSize(9);
        assertThat(knowledge.resolve("services.acupuntura")).isEmpty();
        assertThat(knowledge.resolve("approved-texts.emergency-policy")).as("texto interno não é fonte").isEmpty();
        assertThat(knowledge.resolve("hours")).as("seção inteira (DA-41)").extracting(KnowledgeItem::key)
                .contains("hours.monday", "hours.saturday", "hours.holidays");
        assertThat(knowledge.resolve("review")).as("seção interna não é fonte").isEmpty();
        assertThat(knowledge.resolve("inexistente")).isEmpty();
    }

    @Test
    void versionedExample_loads_withEverythingUnknown() {
        ClinicKnowledge knowledge = KnowledgeFileLoader.parse(EXAMPLE, "example");

        assertThat(knowledge.promptItems()).isNotEmpty().noneMatch(KnowledgeItem::isKnown);
        assertThat(knowledge.unknownCollections()).containsExactlyInAnyOrder("services", "faq");
        assertThat(knowledge.pricesDisclosed()).isFalse();
    }

    @Test
    void pricesNotDisclosed_areTreatedAsUnknown() {
        ClinicKnowledge knowledge = KnowledgeFileLoader.parse(
                FICTITIOUS.replace("prices-disclosed: true", "prices-disclosed: false"), "teste");

        assertThat(knowledge.item("services.consulta.price-cents")).map(KnowledgeItem::isKnown).contains(false);
    }

    @Test
    void malformedYaml_isRejected() {
        assertThat(problemsOf("identification: [sem fechar")).singleElement().asString().startsWith("YAML malformado");
    }

    @Test
    void duplicateKey_isRejected() {
        assertThat(problemsOf(FICTITIOUS + "\nhours:\n  monday: []\n")).singleElement().asString().startsWith("YAML malformado");
    }

    @Test
    void missingKey_isRejected_namingTheKey() {
        assertThat(problemsOf(FICTITIOUS.replace("  holidays: null\n", "")))
                .containsExactly("hours.holidays: chave obrigatória ausente (use null para DESCONHECIDO)");
    }

    @Test
    void missingSection_isRejected() {
        String withoutPolicies = FICTITIOUS.replaceAll("(?ms)^policies:.*?\\n\\n", "");
        assertThat(problemsOf(withoutPolicies)).anyMatch(p -> p.startsWith("policies: chave obrigatória ausente"));
    }

    @Test
    void unknownKey_isRejected() {
        assertThat(problemsOf(FICTITIOUS.replace("  holidays: null", "  holidays: null\n  feriados: null")))
                .singleElement().asString().startsWith("hours.feriados: chave desconhecida");
        assertThat(problemsOf(FICTITIOUS + "\nextras: null\n"))
                .singleElement().asString().startsWith("extras: chave desconhecida");
    }

    @Test
    void nonNumericPrice_isRejected() {
        assertThat(problemsOf(FICTITIOUS.replace("price-cents: 15000", "price-cents: \"R$ 150\"")))
                .singleElement().asString().startsWith("services.consulta.price-cents: preço deve ser um número inteiro");
        assertThat(problemsOf(FICTITIOUS.replace("price-cents: 15000", "price-cents: 150.00")))
                .singleElement().asString().startsWith("services.consulta.price-cents:");
    }

    @Test
    void hoursOutOfFormat_areRejected() {
        assertThat(problemsOf(FICTITIOUS.replace("saturday: [\"08:00-12:00\"]", "saturday: [\"8h às 12h\"]")))
                .singleElement().asString().startsWith("hours.saturday: horário fora do formato");
        assertThat(problemsOf(FICTITIOUS.replace("saturday: [\"08:00-12:00\"]", "saturday: [\"12:00-08:00\"]")))
                .singleElement().asString().startsWith("hours.saturday: o fim do intervalo");
        assertThat(problemsOf(FICTITIOUS.replace("saturday: [\"08:00-12:00\"]", "saturday: \"08:00-12:00\"")))
                .singleElement().asString().startsWith("hours.saturday: deve ser uma lista");
    }

    @Test
    void invalidTextFormats_areRejected() {
        assertThat(problemsOf(FICTITIOUS.replace("\"(11) 3000-1000\"", "\"3000\"")))
                .singleElement().asString().startsWith("identification.phone: telefone inválido");
        assertThat(problemsOf(FICTITIOUS.replace("\"(11) 3000-1000\"", "1130001000")))
                .singleElement().asString().startsWith("identification.phone: deve ser texto entre aspas");
        assertThat(problemsOf(FICTITIOUS.replace("\"01000-000\"", "\"0100\"")))
                .singleElement().asString().startsWith("identification.zip-code: CEP inválido");
        assertThat(problemsOf(FICTITIOUS.replace("\"@clinicaficticia\"", "\"clinicaficticia\"")))
                .singleElement().asString().startsWith("identification.instagram: perfil inválido");
        assertThat(problemsOf(FICTITIOUS.replace("twenty-four-hours: false", "twenty-four-hours: \"não\"")))
                .singleElement().asString().startsWith("hours.twenty-four-hours: deve ser true, false ou null");
    }

    @Test
    void collectionEntries_areChecked() {
        assertThat(problemsOf(FICTITIOUS.replace("  vacina-v10:", "  Vacina_V10:")))
                .singleElement().asString().startsWith("services.Vacina_V10: id inválido");
        assertThat(problemsOf(FICTITIOUS.replace("name: \"Banho e tosa\"", "name: null")))
                .containsExactly("services.banho-e-tosa.name: obrigatório dentro da entrada (não pode ser null)");
    }

    @Test
    void everyProblemIsListedAtOnce() {
        String broken = FICTITIOUS.replace("price-cents: 15000", "price-cents: \"x\"")
                .replace("  holidays: null\n", "")
                .replaceFirst("sunday: \\[\\]", "sunday: [\"fechado\"]");   // só o da clínica

        assertThat(problemsOf(broken)).hasSize(3);
    }

    @Test
    void newTypes_areRead_andRendered() {
        ClinicKnowledge knowledge = KnowledgeFileLoader.parse(FICTITIOUS
                .replace("  holidays: null\n", "  holidays:\n    \"2026-12-25\": []\n    \"2026-11-20\": [\"08:00-12:00\"]\n"),
                "teste");

        assertThat(knowledge.item("services.tosa-higienica.price-by-size")).map(KnowledgeItem::display)
                .contains("pequeno: R$ 40,00; médio: R$ 50,00; grande: R$ 65,00");
        assertThat(knowledge.item("hours.holidays")).map(KnowledgeItem::display)
                .contains("20/11/2026: 08:00–12:00; 25/12/2026: fechado");
        assertThat(knowledge.item("review.reviewed-at")).map(KnowledgeItem::display).contains("17/11/2026");
        assertThat(knowledge.promptItems()).noneMatch(i -> i.key().startsWith("review."));
        assertThat(knowledge.validUntil("services")).contains(java.time.LocalDate.of(2027, 12, 31));
    }

    @Test
    void newTypes_areValidated() {
        assertThat(problemsOf(FICTITIOUS.replace("  holidays: null\n", "  holidays:\n    \"25/12\": []\n")))
                .singleElement().asString().startsWith("hours.holidays: data \"25/12\" fora do formato");
        assertThat(problemsOf(FICTITIOUS.replace("      pequeno: 4000", "      pequeno: \"R$ 40\"")))
                .singleElement().asString().startsWith("services.tosa-higienica.price-by-size: cada linha");
        assertThat(problemsOf(FICTITIOUS.replace("    services: \"2027-12-31\"", "    services: \"31/12/2027\"")))
                .singleElement().asString().startsWith("review.valid-until.services: data deve estar no formato");
        assertThat(problemsOf(FICTITIOUS.replace("    faq: \"2027-12-31\"\n", "")))
                .containsExactly("review.valid-until.faq: chave obrigatória ausente (use null para DESCONHECIDO)");
    }

    @Test
    void expiredSection_isTreatedAsUnknown_fromTheDayAfterTheDate() {
        String yaml = FICTITIOUS.replace("    services: \"2027-12-31\"", "    services: \"2026-11-30\"")
                .replace("    hours: \"2027-12-31\"", "    hours: \"2026-11-30\"");
        java.time.ZoneId zone = java.time.ZoneId.of("America/Sao_Paulo");
        ClinicKnowledge onTheDate = KnowledgeFileLoader.parse(yaml, "teste",
                java.time.Clock.fixed(java.time.Instant.parse("2026-12-01T02:59:00Z"), zone));   // 30/11, 23:59 local
        ClinicKnowledge dayAfter = onTheDate.withClock(
                java.time.Clock.fixed(java.time.Instant.parse("2026-12-01T03:00:00Z"), zone));   // 01/12, 00:00 local

        assertThat(onTheDate.item("services.consulta.price-cents")).map(KnowledgeItem::isKnown).contains(true);
        assertThat(onTheDate.expiredSections()).isEmpty();
        assertThat(dayAfter.expiredSections()).containsExactlyInAnyOrder("services", "hours");
        assertThat(dayAfter.unknownCollections()).contains("services");
        assertThat(dayAfter.resolve("services.consulta")).isEmpty();
        assertThat(dayAfter.item("hours.saturday")).map(KnowledgeItem::isKnown).contains(false);
        assertThat(dayAfter.item("identification.phone")).map(KnowledgeItem::isKnown).contains(true);
        assertThat(dayAfter.item("triage.alert-expressions-p1")).map(KnowledgeItem::isKnown).contains(true);
    }

    @Test
    void sectionsWithKnownItems_andNoValidity_areReported() {
        ClinicKnowledge knowledge = KnowledgeFileLoader.parse(
                FICTITIOUS.replace("    payment: \"2027-12-31\"", "    payment: null"), "teste");

        assertThat(knowledge.sectionsWithoutValidity()).containsExactly("payment");
        assertThat(KnowledgeFileLoader.parse(EXAMPLE, "example").sectionsWithoutValidity()).isEmpty();
    }

    @Test
    void exceptionMessage_listsSourceAndProblems() {
        assertThatThrownBy(() -> KnowledgeFileLoader.parse(FICTITIOUS.replace("  holidays: null\n", ""), "config/x.yml"))
                .hasMessageContaining("config/x.yml").hasMessageContaining("hours.holidays");
    }
}
