package com.clinicavet.assistant.clinicknowledge.infrastructure;

import com.clinicavet.assistant.clinicknowledge.domain.ClinicKnowledge;
import com.clinicavet.assistant.clinicknowledge.domain.ItemType;
import com.clinicavet.assistant.clinicknowledge.domain.KnowledgeItem;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.TreeMap;
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
    private static final Pattern DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final Pattern INTERVAL = Pattern.compile("([01]\\d|2[0-3]):([0-5]\\d)-([01]\\d|2[0-3]):([0-5]\\d)");

    private KnowledgeFileLoader() {
    }

    public static ClinicKnowledge parse(String yaml, String source) {
        return parse(yaml, source, Clock.systemUTC());
    }

    /** @param clock relógio usado na validade das seções (o da aplicação, no fuso da clínica) */
    public static ClinicKnowledge parse(String yaml, String source, Clock clock) {
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
        Map<String, LocalDate> validUntil = new LinkedHashMap<>();
        checkKeys("(raiz)", rootMap, new LinkedHashSet<>(KnowledgeSchema.TOP_LEVEL), problems);

        for (String section : KnowledgeSchema.TOP_LEVEL) {
            if (!rootMap.containsKey(section)) {
                continue;
            }
            Object value = rootMap.get(section);
            if (KnowledgeSchema.COLLECTION_ENTRIES.containsKey(section)) {
                readCollection(section, value, items, unknownCollections, problems);
            } else if (section.equals("review")) {
                readReview(value, items, validUntil, problems);
            } else {
                readSection(section, value, KnowledgeSchema.SECTIONS.get(section), items, problems);
            }
        }
        if (!problems.isEmpty()) {
            throw new KnowledgeValidationException(source, problems);
        }
        return new ClinicKnowledge(items, unknownCollections, validUntil, clock);
    }

    /**
     * {@code review}: data e responsável da revisão e, em {@code valid-until}, o "válido até" de cada seção de
     * {@link ClinicKnowledge#VALIDATED_SECTIONS} ({@code null} = sem prazo; a inicialização avisa).
     */
    private static void readReview(Object value, List<KnowledgeItem> items, Map<String, LocalDate> validUntil,
                                   List<String> problems) {
        Map<String, ItemType> fields = KnowledgeSchema.SECTIONS.get("review");
        if (!(value instanceof Map<?, ?> map)) {
            problems.add("review: deve conter as chaves " + fields.keySet() + " e " + KnowledgeSchema.VALID_UNTIL);
            return;
        }
        Set<String> expected = new LinkedHashSet<>(fields.keySet());
        expected.add(KnowledgeSchema.VALID_UNTIL);
        checkKeys("review", map, expected, problems);
        fields.forEach((field, type) -> {
            if (map.containsKey(field)) {
                Object raw = map.get(field);
                items.add(new KnowledgeItem("review." + field, type,
                        raw == null ? null : convert("review." + field, type, raw, problems)));
            }
        });
        if (!map.containsKey(KnowledgeSchema.VALID_UNTIL)) {
            return;
        }
        String prefix = "review." + KnowledgeSchema.VALID_UNTIL;
        Object raw = map.get(KnowledgeSchema.VALID_UNTIL);
        if (!(raw instanceof Map<?, ?> dates)) {
            problems.add(prefix + ": deve ter uma data (ou null) para cada seção " + ClinicKnowledge.VALIDATED_SECTIONS);
            return;
        }
        checkKeys(prefix, dates, new LinkedHashSet<>(ClinicKnowledge.VALIDATED_SECTIONS), problems);
        for (String section : ClinicKnowledge.VALIDATED_SECTIONS) {
            Object date = dates.get(section);
            if (date != null) {
                Object parsed = convert(prefix + "." + section, ItemType.DATE, date, problems);
                if (parsed instanceof LocalDate d) {
                    validUntil.put(section, d);
                }
            }
        }
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
            Map<String, ItemType> optional = KnowledgeSchema.OPTIONAL_IN_ENTRY.getOrDefault(section, Map.of());
            Map<String, ItemType> all = new LinkedHashMap<>(fields);
            all.putAll(optional);
            Object raw = entry.getValue();
            if (raw instanceof Map<?, ?> given && !optional.isEmpty()) {
                Map<Object, Object> filled = new LinkedHashMap<>(given);
                optional.keySet().forEach(k -> filled.putIfAbsent(k, null));
                raw = filled;
            }
            readSection(prefix, raw, all, items, problems);
            items.subList(before, items.size()).stream()
                    .filter(item -> item.key().equals(prefix + ".department") && item.isKnown()
                            && !KnowledgeSchema.DEPARTMENT_KINDS.contains(item.value()))
                    .findFirst()
                    .ifPresent(item -> problems.add(item.key() + ": setor deve ser um de "
                            + new java.util.TreeSet<>(KnowledgeSchema.DEPARTMENT_KINDS)));
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
            case TEXT_LIST -> {
                if (raw instanceof List<?> list && !list.isEmpty()
                        && list.stream().allMatch(v -> v instanceof String s && !s.isBlank() && s.length() <= 200)) {
                    return list.stream().map(v -> ((String) v).strip()).toList();
                }
                problems.add(key + ": deve ser uma lista não vazia de textos entre aspas (até 200 caracteres cada) ou null");
            }
            case HOURS -> {
                return intervals(key, raw, problems);
            }
            case DATE -> {
                LocalDate date = date(raw);
                if (date != null) {
                    return date;
                }
                problems.add(key + ": data deve estar no formato \"aaaa-mm-dd\", entre aspas (ex.: \"2026-12-25\")");
            }
            case SPECIAL_DAYS -> {
                if (raw instanceof Map<?, ?> map) {
                    TreeMap<LocalDate, List<String>> days = new TreeMap<>();
                    for (Map.Entry<?, ?> entry : map.entrySet()) {
                        LocalDate date = date(entry.getKey());
                        if (date == null) {
                            problems.add(key + ": data \"" + entry.getKey() + "\" fora do formato \"aaaa-mm-dd\"");
                            continue;
                        }
                        List<String> intervals = intervals(key + "." + date, entry.getValue(), problems);
                        if (intervals != null) {
                            days.put(date, intervals);
                        }
                    }
                    return Collections.unmodifiableSortedMap(days);
                }
                problems.add(key + ": deve ser um mapa \"aaaa-mm-dd\": [intervalos] ([] = fechado) ou null");
            }
            case PRICE_TABLE -> {
                if (raw instanceof Map<?, ?> map && !map.isEmpty()) {
                    Map<String, Long> table = new LinkedHashMap<>();
                    for (Map.Entry<?, ?> entry : map.entrySet()) {
                        String label = String.valueOf(entry.getKey()).strip();
                        Object cents = entry.getValue();
                        if (label.isEmpty() || label.length() > 40 || !(cents instanceof Number n)
                                || cents instanceof Double || cents instanceof Float || n.longValue() < 0) {
                            problems.add(key + ": cada linha deve ser rótulo (até 40 caracteres): centavos, sem aspas"
                                    + " (ex.: pequeno: 6000)");
                        } else {
                            table.put(label, n.longValue());
                        }
                    }
                    return Collections.unmodifiableMap(table);
                }
                problems.add(key + ": deve ser um mapa rótulo: centavos (ex.: pequeno: 6000) ou null");
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

    /** Lista de intervalos {@code HH:mm-HH:mm}, ou {@code null} com o problema registrado. */
    private static List<String> intervals(String key, Object raw, List<String> problems) {
        if (!(raw instanceof List<?> list)) {
            problems.add(key + ": deve ser uma lista de intervalos \"HH:mm-HH:mm\" ([] = fechado) ou null");
            return null;
        }
        List<String> intervals = new ArrayList<>();
        for (Object interval : list) {
            String text = String.valueOf(interval).strip();
            if (!(interval instanceof String) || !INTERVAL.matcher(text).matches()) {
                problems.add(key + ": horário fora do formato \"HH:mm-HH:mm\" (ex.: \"08:00-18:00\")");
            } else if (text.substring(0, 5).compareTo(text.substring(6)) >= 0) {
                problems.add(key + ": o fim do intervalo deve ser depois do início (" + text + ")");
            } else {
                intervals.add(text);
            }
        }
        return List.copyOf(intervals);
    }

    /** Data "aaaa-mm-dd" (texto, ou data que o YAML já converteu), ou {@code null}. */
    private static LocalDate date(Object raw) {
        if (raw instanceof Date d) {
            return d.toInstant().atZone(ZoneOffset.UTC).toLocalDate();
        }
        if (raw instanceof String s && DATE.matcher(s.strip()).matches()) {
            try {
                return LocalDate.parse(s.strip());
            } catch (DateTimeParseException e) {
                return null;
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
