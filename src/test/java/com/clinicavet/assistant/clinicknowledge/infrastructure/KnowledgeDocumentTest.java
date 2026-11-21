package com.clinicavet.assistant.clinicknowledge.infrastructure;

import com.clinicavet.assistant.clinicknowledge.domain.ClinicConfig;
import com.clinicavet.assistant.clinicknowledge.domain.ClinicKnowledge;
import com.clinicavet.assistant.shared.exception.InvalidDocumentException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** O documento da configuração no banco (ADR-0026): mesma validação do arquivo, importação e diferenças. */
class KnowledgeDocumentTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-21T12:00:00Z"), ZoneId.of("America/Sao_Paulo"));

    private static String read(String path) {
        try {
            return Files.readString(Path.of(path));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void blankDocument_isValid_andKnowsNothing() {
        ClinicKnowledge knowledge = KnowledgeDocument.read(KnowledgeDocument.blank(), CLOCK);
        assertThat(knowledge.promptItems()).allMatch(i -> !i.isKnown());
        assertThat(knowledge.unknownCollections()).containsExactlyInAnyOrder("services", "faq");
    }

    @Test
    void versionedYamlFiles_import_andSurviveJsonRoundTrip() {
        for (String file : List.of("config/clinic-knowledge.ficticia.yml", "config/clinic-knowledge.example.yml")) {
            Map<String, Object> document = KnowledgeDocument.fromYaml(read(file));
            Map<String, Object> roundTrip = KnowledgeDocument.fromJson(KnowledgeDocument.toJson(document));
            assertThat(KnowledgeDocument.diff(document, roundTrip)).as(file).isEmpty();
            KnowledgeDocument.read(roundTrip, CLOCK);
        }
        ClinicKnowledge fictitious = KnowledgeDocument.read(
                KnowledgeDocument.fromYaml(read("config/clinic-knowledge.ficticia.yml")), CLOCK);
        assertThat(fictitious.pricesDisclosed()).isTrue();
        assertThat(fictitious.item("services.consulta.price-cents")).get()
                .extracting(i -> i.display()).isEqualTo("R$ 150,00");
    }

    @Test
    void invalidDocument_listsEveryProblem_withTheFieldKey() {
        Map<String, Object> document = KnowledgeDocument.blank();
        section(document, "identification").put("email", "sem-arroba");
        section(document, "hours").put("monday", List.of("18:00-08:00"));
        document.put("extra", null);
        assertThatThrownBy(() -> KnowledgeDocument.read(document, CLOCK))
                .isInstanceOfSatisfying(InvalidDocumentException.class, e -> assertThat(e.problems())
                        .anyMatch(p -> p.startsWith("identification.email:"))
                        .anyMatch(p -> p.startsWith("hours.monday:"))
                        .anyMatch(p -> p.startsWith("extra:")));
    }

    @Test
    void serviceDepartment_isOptional_validated_andKeptOutOfThePrompt() {
        Map<String, Object> document = KnowledgeDocument.blank();
        Map<String, Object> banho = new LinkedHashMap<>(service("Banho"));
        banho.put("department", "PETSHOP");
        Map<String, Object> services = new LinkedHashMap<>();
        services.put("banho", banho);
        services.put("consulta", service("Consulta"));
        document.put("services", services);

        ClinicKnowledge knowledge = KnowledgeDocument.read(document, CLOCK);
        assertThat(knowledge.serviceDepartments()).containsExactly(Map.entry("banho", "PETSHOP"));
        assertThat(knowledge.promptItems()).noneMatch(i -> i.key().endsWith(".department"));
        assertThat(knowledge.resolve("services.banho")).noneMatch(i -> i.key().endsWith(".department"));

        banho.put("department", "COZINHA");
        assertThatThrownBy(() -> KnowledgeDocument.read(document, CLOCK))
                .isInstanceOfSatisfying(InvalidDocumentException.class, e -> assertThat(e.problems())
                        .anyMatch(p -> p.startsWith("services.banho.department:")));
    }

    @Test
    void diff_isReadable_itemByItem() {
        Map<String, Object> before = KnowledgeDocument.fromYaml(read("config/clinic-knowledge.ficticia.yml"));
        Map<String, Object> after = KnowledgeDocument.fromJson(KnowledgeDocument.toJson(before));
        @SuppressWarnings("unchecked")
        Map<String, Object> consulta = (Map<String, Object>) ((Map<String, Object>) after.get("services")).get("consulta");
        consulta.put("price-cents", 17000);
        section(after, "hours").put("saturday", List.of());

        List<ClinicConfig.Change> changes = KnowledgeDocument.diff(before, after);
        assertThat(changes).containsExactly(
                new ClinicConfig.Change("hours.saturday", "08:00–12:00", "fechado"),
                new ClinicConfig.Change("services.consulta.price-cents", "R$ 150,00", "R$ 170,00"));
        assertThat(KnowledgeDocument.diff(null, before)).isNotEmpty();

        @SuppressWarnings("unchecked")
        Map<String, Object> services = (Map<String, Object>) after.get("services");
        services.put("banho", service("Banho"));
        services.remove("vacina-v10");
        assertThat(KnowledgeDocument.diff(before, after)).as("serviço inteiro: uma linha com o nome")
                .contains(new ClinicConfig.Change("services.banho", null, "Banho"),
                        new ClinicConfig.Change("services.vacina-v10", "Vacina V10 para cães", null))
                .noneMatch(c -> c.key().startsWith("services.banho."));
    }

    @Test
    void malformedYaml_isRejected_withoutTheContent() {
        assertThatThrownBy(() -> KnowledgeDocument.fromYaml("identification: [sem fim"))
                .isInstanceOf(InvalidDocumentException.class);
        assertThatThrownBy(() -> KnowledgeDocument.fromYaml("- lista"))
                .isInstanceOf(InvalidDocumentException.class);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> section(Map<String, Object> document, String name) {
        return (Map<String, Object>) document.get(name);
    }

    private static Map<String, Object> service(String name) {
        Map<String, Object> entry = new LinkedHashMap<>();
        for (String field : KnowledgeSchema.COLLECTION_ENTRIES.get("services").keySet()) {
            entry.put(field, null);
        }
        entry.put("name", name);
        return entry;
    }
}
