package com.clinicavet.assistant.clinicknowledge.infrastructure;

import com.clinicavet.assistant.clinicknowledge.domain.ClinicKnowledge;
import com.clinicavet.assistant.clinicknowledge.domain.ItemType;
import com.clinicavet.assistant.clinicknowledge.domain.KnowledgeItem;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Lê e valida o arquivo da base (ADR-0003).
 * <p>
 * <b>Por que não {@code @ConfigurationProperties}</b> (desvio do item 1 do ADR-0003): o binding do Spring
 * não distingue chave ausente de chave {@code null}, e o ADR exige que ausente seja erro e {@code null}
 * seja DESCONHECIDO. O arquivo é lido com SnakeYAML ({@code SafeConstructor}, sem chaves duplicadas) e
 * conferido contra o {@link KnowledgeSchema}. Todos os problemas são listados de uma vez.
 */
public final class KnowledgeFileLoader {

    static final int MAX_TEXT = 1_000;
    private static final Pattern ENTRY_ID = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");
    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+");
    private static final Pattern URL = Pattern.compile("(https?://)?[\\w-]+(\\.[\\w-]+)+(/\\S*)?");
    private static final Pattern HANDLE = Pattern.compile("@[A-Za-z0-9_.]{2,30}");
    private static final Pattern ZIP = Pattern.compile("\\d{5}-?\\d{3}");
    private static final Pattern PHONE_CHARS = Pattern.compile("[\\d\\s()+.-]+");
    private static final Pattern INTERVAL = Pattern.compile("([01]\\d|2[0-3]):([0-5]\\d)-([01]\\d|2[0-3]):([0-5]\\d)");

    private KnowledgeFileLoader() {
    }

    public static ClinicKnowledge parse(String yaml, String source) {
        List<String> problems = new ArrayList<>();
        Object root;
        try {
            LoaderOptions options = new LoaderOptions();
            options.setAllowDuplicateKeys(false);
            root = new Yaml(new SafeConstructor(options)).load(yaml);
        } catch (YAMLException e) {
            throw new KnowledgeValidationException(source, List.of("YAML malformado: " + firstLine(e.getMessage())));
        }
        if (!(root instanceof Map<?, ?> rootMap)) {
            throw new KnowledgeValidationException(source,
                    List.of("o arquivo deve ter as seções " + KnowledgeSchema.TOP_LEVEL));
        }

        List<KnowledgeItem> items = new ArrayList<>();
        Set<String> unknownCollections = new HashSet<>();
        checkKeys("(raiz)", rootMap, new LinkedHashSet<>(KnowledgeSchema.TOP_LEVEL), problems);

        for (String section : KnowledgeSchema.TOP_LEVEL) {
            if (!rootMap.containsKey(section)) {
                continue;
            }
            Object value = rootMap.get(section);
            if (KnowledgeSchema.COLLECTION_ENTRIES.containsKey(section)) {
                readCollection(section, value, items, unknownCollections, problems);
            } else {
                readSection(section, value, KnowledgeSchema.SECTIONS.get(section), items, problems);
            }
        }
        if (!problems.isEmpty()) {
            throw new KnowledgeValidationException(source, problems);
        }
        return new ClinicKnowledge(items, unknownCollections);
    }

    private static void readSection(String prefix, Object value, Map<String, ItemType> fields,
                                    List<KnowledgeItem> items, List<String> problems) {
        if (!(value instanceof Map<?, ?> map)) {
            problems.add(prefix + ": deve conter as chaves " + fields.keySet()
                    + " (use null em cada uma para DESCONHECIDO)");
            return;
        }
        checkKeys(prefix, map, fields.keySet(), problems);
        fields.forEach((field, type) -> {
            if (map.containsKey(field)) {
                String key = prefix + "." + field;
                Object raw = map.get(field);
                Object parsed = raw == null ? null : convert(key, type, raw, problems);
                items.add(new KnowledgeItem(key, type, parsed));
            }
        });
    }

    private static void readCollection(String section, Object value, List<KnowledgeItem> items,
                                       Set<String> unknownCollections, List<String> problems) {
        if (value == null) {
            unknownCollections.add(section);
            return;
        }
        if (!(value instanceof Map<?, ?> entries)) {
            problems.add(section + ": deve ser um mapa de entradas (id: campos) ou null");
            return;
        }
        Map<String, ItemType> fields = KnowledgeSchema.COLLECTION_ENTRIES.get(section);
        for (Map.Entry<?, ?> entry : entries.entrySet()) {
            String id = String.valueOf(entry.getKey());
            String prefix = section + "." + id;
            if (!ENTRY_ID.matcher(id).matches()) {
                problems.add(prefix + ": id inválido (use minúsculas, dígitos e hífen, ex.: vacina-v10)");
                continue;
            }
            int before = items.size();
            readSection(prefix, entry.getValue(), fields, items, problems);
            for (String required : KnowledgeSchema.REQUIRED_IN_ENTRY.getOrDefault(section, Set.of())) {
                items.subList(before, items.size()).stream()
                        .filter(item -> item.key().equals(prefix + "." + required) && !item.isKnown())
                        .findFirst()
                        .ifPresent(item -> problems.add(item.key() + ": obrigatório dentro da entrada (não pode ser null)"));
            }
        }
    }

    private static void checkKeys(String prefix, Map<?, ?> map, Set<String> expected, List<String> problems) {
        for (Object key : map.keySet()) {
            if (!expected.contains(String.valueOf(key))) {
                problems.add(join(prefix, String.valueOf(key)) + ": chave desconhecida (esperadas: " + expected + ")");
            }
        }
        for (String key : expected) {
            if (!map.containsKey(key)) {
                problems.add(join(prefix, key) + ": chave obrigatória ausente (use null para DESCONHECIDO)");
            }
        }
    }

    private static Object convert(String key, ItemType type, Object raw, List<String> problems) {
        switch (type) {
            case BOOLEAN -> {
                if (raw instanceof Boolean b) {
                    return b;
                }
                problems.add(key + ": deve ser true, false ou null");
            }
            case PRICE_CENTS -> {
                if (raw instanceof Number n && !(raw instanceof Double) && !(raw instanceof Float) && n.longValue() >= 0) {
                    return n.longValue();
                }
                problems.add(key + ": preço deve ser um número inteiro de centavos, sem aspas (ex.: 15000 = R$ 150,00)");
            }
            case HOURS -> {
                if (raw instanceof List<?> list) {
                    List<String> intervals = new ArrayList<>();
                    for (Object interval : list) {
                        String text = String.valueOf(interval).strip();
                        var m = INTERVAL.matcher(text);
                        if (!(interval instanceof String) || !m.matches()) {
                            problems.add(key + ": horário fora do formato \"HH:mm-HH:mm\" (ex.: \"08:00-18:00\")");
                        } else if (text.substring(0, 5).compareTo(text.substring(6)) >= 0) {
                            problems.add(key + ": o fim do intervalo deve ser depois do início (" + text + ")");
                        } else {
                            intervals.add(text);
                        }
                    }
                    return List.copyOf(intervals);
                }
                problems.add(key + ": deve ser uma lista de intervalos \"HH:mm-HH:mm\" ([] = fechado) ou null");
            }
            default -> {
                if (!(raw instanceof String text)) {
                    problems.add(key + ": deve ser texto entre aspas ou null");
                    return null;
                }
                String value = text.strip();
                String error = checkText(type, value);
                if (error == null) {
                    return value;
                }
                problems.add(key + ": " + error);
            }
        }
        return null;
    }

    private static String checkText(ItemType type, String value) {
        if (value.isEmpty()) {
            return "texto vazio (use null para DESCONHECIDO)";
        }
        if (value.length() > MAX_TEXT) {
            return "excede " + MAX_TEXT + " caracteres";
        }
        return switch (type) {
            case PHONE -> {
                long digits = value.chars().filter(Character::isDigit).count();
                yield PHONE_CHARS.matcher(value).matches() && digits >= 8 && digits <= 13
                        ? null : "telefone inválido (8 a 13 dígitos, com ou sem +55, DDD e separadores)";
            }
            case EMAIL -> EMAIL.matcher(value).matches() ? null : "e-mail inválido";
            case URL -> URL.matcher(value).matches() ? null : "site inválido";
            case HANDLE -> HANDLE.matcher(value).matches() ? null : "perfil inválido (formato @perfil)";
            case ZIP -> ZIP.matcher(value).matches() ? null : "CEP inválido (00000-000)";
            default -> null;
        };
    }

    private static String join(String prefix, String key) {
        return "(raiz)".equals(prefix) ? key : prefix + "." + key;
    }

    private static String firstLine(String message) {
        if (message == null) {
            return "erro de sintaxe";
        }
        return message.lines().limit(3).reduce((a, b) -> a + " " + b.strip()).orElse(message);
    }
}
