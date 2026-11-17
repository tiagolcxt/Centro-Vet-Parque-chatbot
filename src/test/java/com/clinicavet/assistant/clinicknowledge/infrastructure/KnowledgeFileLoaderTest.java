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
        assertThat(knowledge.resolve("services.consulta")).hasSize(5);
        assertThat(knowledge.resolve("services.acupuntura")).isEmpty();
        assertThat(knowledge.resolve("approved-texts.emergency-policy")).as("texto interno não é fonte").isEmpty();
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
        String withoutPolicies = FICTITIOUS.replaceAll("(?s)policies:.*?\\n\\n", "");
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
                .replace("sunday: []", "sunday: [\"fechado\"]");

        assertThat(problemsOf(broken)).hasSize(3);
    }

    @Test
    void exceptionMessage_listsSourceAndProblems() {
        assertThatThrownBy(() -> KnowledgeFileLoader.parse(FICTITIOUS.replace("  holidays: null\n", ""), "config/x.yml"))
                .hasMessageContaining("config/x.yml").hasMessageContaining("hours.holidays");
    }
}
