package com.clinicavet.assistant.clinicknowledge.infrastructure;

import com.clinicavet.assistant.clinicknowledge.domain.ClinicConfig;
import com.clinicavet.assistant.clinicknowledge.domain.ClinicKnowledge;
import com.clinicavet.assistant.clinicknowledge.domain.ItemType;
import com.clinicavet.assistant.clinicknowledge.domain.KnowledgeItem;
import com.clinicavet.assistant.shared.exception.InvalidDocumentException;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

/**
 * O documento da configuração (ADR-0026): um mapa com a estrutura do modelo de informações, gravado em JSON. A
 * validação é a mesma do arquivo YAML ({@link KnowledgeFileLoader}), porque JSON é YAML válido.
 */
final class KnowledgeDocument {

    private static final ObjectMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<LinkedHashMap<String, Object>> MAP = new TypeReference<>() {
    };

    private KnowledgeDocument() {
    }

    /** Todas as chaves, tudo desconhecido; coleções desconhecidas ({@code null}). */
    static Map<String, Object> blank() {
        Map<String, Object> doc = new LinkedHashMap<>();
        for (String section : KnowledgeSchema.TOP_LEVEL) {
            if (KnowledgeSchema.COLLECTION_ENTRIES.containsKey(section)) {
                doc.put(section, null);
                continue;
            }
            Map<String, Object> fields = new LinkedHashMap<>();
            KnowledgeSchema.SECTIONS.get(section).keySet().forEach(f -> fields.put(f, null));
            if (section.equals("review")) {
                Map<String, Object> validity = new LinkedHashMap<>();
                ClinicKnowledge.VALIDATED_SECTIONS.forEach(s -> validity.put(s, null));
                fields.put(KnowledgeSchema.VALID_UNTIL, validity);
            }
            doc.put(section, fields);
        }
        return doc;
    }

    /** Valida e lê o documento como base. Inválido: {@link InvalidDocumentException} com todos os problemas. */
    static ClinicKnowledge read(Map<String, Object> document, Clock clock) {
        try {
            return KnowledgeFileLoader.parse(toJson(document), "configuração", clock);
        } catch (KnowledgeValidationException e) {
            throw new InvalidDocumentException("A configuração tem " + e.problems().size()
                    + (e.problems().size() == 1 ? " problema." : " problemas."), e.problems());
        }
    }

    static String toJson(Map<String, Object> document) {
        Objects.requireNonNull(document, "document");
        return JSON.writeValueAsString(document);
    }

    static Map<String, Object> fromJson(String json) {
        try {
            return JSON.readValue(json, MAP);
        } catch (JacksonException e) {
            throw new IllegalStateException("documento de configuração ilegível no banco", e);
        }
    }

    /** YAML do modelo de informações → documento (RF-S19). Datas viram texto "aaaa-mm-dd". */
    static Map<String, Object> fromYaml(String yaml) {
        Object root;
        try {
            LoaderOptions options = new LoaderOptions();
            options.setAllowDuplicateKeys(false);
            root = new Yaml(new SafeConstructor(options)).load(yaml);
        } catch (YAMLException e) {
            throw new InvalidDocumentException("O arquivo não é um YAML válido.",
                    List.of("(arquivo): YAML malformado"));
        }
        if (!(root instanceof Map<?, ?>)) {
            throw new InvalidDocumentException("O arquivo não tem as seções do modelo de informações.",
                    List.of("(arquivo): deve ter as seções " + KnowledgeSchema.TOP_LEVEL));
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> normalized = (Map<String, Object>) normalize(root);
        return normalized;
    }

    private static Object normalize(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((k, v) -> result.put(k instanceof Date d ? date(d) : String.valueOf(k), normalize(v)));
            return result;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(KnowledgeDocument::normalize).toList();
        }
        if (value instanceof Date d) {
            return date(d);
        }
        return value;
    }

    private static String date(Date d) {
        return DateTimeFormatter.ISO_LOCAL_DATE.format(d.toInstant().atZone(ZoneOffset.UTC).toLocalDate());
    }

    /**
     * Diferenças legíveis (S3-6), item a item, pela leitura validada das duas versões: preço em reais, horário com
     * travessão, desconhecido explícito. A validade é ignorada aqui: compara o que está escrito.
     */
    static List<ClinicConfig.Change> diff(Map<String, Object> before, Map<String, Object> after) {
        Map<String, String> a = flatten(before);
        Map<String, String> b = flatten(after);
        TreeSet<String> keys = new TreeSet<>(java.util.Comparator.comparingInt(KnowledgeDocument::order)
                .thenComparing(k -> k));
        keys.addAll(a.keySet());
        keys.addAll(b.keySet());
        List<ClinicConfig.Change> changes = new ArrayList<>();
        // Serviço ou pergunta que entrou ou saiu inteiro: uma linha só, com o nome, em vez de uma por campo.
        java.util.Set<String> wholeEntries = new java.util.HashSet<>();
        for (String key : keys) {
            String entry = entryOf(key);
            if (entry == null || wholeEntries.contains(entry)) {
                continue;
            }
            boolean inBefore = a.keySet().stream().anyMatch(k -> k.startsWith(entry + "."));
            boolean inAfter = b.keySet().stream().anyMatch(k -> k.startsWith(entry + "."));
            if (inBefore != inAfter) {
                wholeEntries.add(entry);
                String label = (inAfter ? b : a).getOrDefault(entry + ".name", (inAfter ? b : a).get(entry + ".question"));
                changes.add(new ClinicConfig.Change(entry, inBefore ? label : null, inAfter ? label : null));
            }
        }
        for (String key : keys) {
            String entry = entryOf(key);
            if (entry != null && wholeEntries.contains(entry)) {
                continue;
            }
            String x = a.get(key);
            String y = b.get(key);
            if (!Objects.equals(x, y)) {
                changes.add(new ClinicConfig.Change(key, x, y));
            }
        }
        return changes;
    }

    /** {@code services.banho.price-cents} → {@code services.banho}; fora de coleção → {@code null}. */
    private static String entryOf(String key) {
        String[] parts = key.split("\\.");
        return parts.length == 3 && ClinicKnowledge.COLLECTIONS.contains(parts[0]) ? parts[0] + "." + parts[1] : null;
    }

    private static int order(String key) {
        int index = KnowledgeSchema.TOP_LEVEL.indexOf(ClinicKnowledge.section(key));
        return index < 0 ? Integer.MAX_VALUE : index;
    }

    /** Chave → texto, para comparar. Documento vazio ou {@code null}: nenhum item. */
    static Map<String, String> flatten(Map<String, Object> document) {
        Map<String, String> result = new LinkedHashMap<>();
        if (document == null) {
            return result;
        }
        ClinicKnowledge knowledge;
        try {
            knowledge = KnowledgeFileLoader.parse(toJson(document), "comparação",
                    Clock.fixed(java.time.Instant.EPOCH, ZoneOffset.UTC));
        } catch (KnowledgeValidationException e) {
            return result;
        }
        for (String section : KnowledgeSchema.TOP_LEVEL) {
            if (ClinicKnowledge.COLLECTIONS.contains(section) && knowledge.unknownCollections().contains(section)) {
                result.put(section, KnowledgeItem.UNKNOWN_TEXT);
            }
        }
        for (KnowledgeItem item : knowledge.allItems()) {
            result.put(item.key(), item.isKnown() ? display(item) : KnowledgeItem.UNKNOWN_TEXT);
        }
        knowledge.validity().forEach((section, until) ->
                result.put("review.valid-until." + section, until.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))));
        return result;
    }

    private static String display(KnowledgeItem item) {
        // Preço é mostrado mesmo com "preços divulgados" desligado: a diferença é do que está escrito.
        return item.type() == ItemType.PRICE_CENTS ? KnowledgeItem.formatMoney((Long) item.value()) : item.display();
    }
}
