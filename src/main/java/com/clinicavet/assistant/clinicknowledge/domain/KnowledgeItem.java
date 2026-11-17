package com.clinicavet.assistant.clinicknowledge.domain;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Um item da base, com chave estável (ex.: {@code hours.saturday}, {@code services.consulta.price-cents})
 * que o modelo cita como fonte (ADR-0008). {@code value == null} significa <b>DESCONHECIDO</b>.
 *
 * @param value {@code String} (texto, telefone, e-mail, site, perfil, CEP), {@code Boolean}, {@code Long}
 *              (centavos), {@code List<String>} (intervalos, textos), {@code LocalDate}, {@code Map<LocalDate,
 *              List<String>>} (dias especiais) ou {@code Map<String, Long>} (tabela de preços), conforme o tipo
 */
public record KnowledgeItem(String key, ItemType type, Object value) {

    public static final String UNKNOWN_TEXT = "DESCONHECIDO — confirmar com a equipe";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public KnowledgeItem {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(type, "type");
    }

    public boolean isKnown() {
        return value != null;
    }

    /** Valor como aparece no prompt e de onde o validador extrai os valores autorizados. */
    public String display() {
        if (value == null) {
            return UNKNOWN_TEXT;
        }
        return switch (type) {
            case BOOLEAN -> ((Boolean) value) ? "sim" : "não";
            case PRICE_CENTS -> formatMoney((Long) value);
            case HOURS -> formatHours(castList(value));
            case TEXT_LIST -> String.join("; ", castList(value));
            case DATE -> DATE.format((LocalDate) value);
            case SPECIAL_DAYS -> specialDays().isEmpty() ? "nenhum"
                    : specialDays().entrySet().stream()
                    .map(e -> DATE.format(e.getKey()) + ": " + formatHours(e.getValue()))
                    .collect(Collectors.joining("; "));
            case PRICE_TABLE -> priceTable().entrySet().stream()
                    .map(e -> e.getKey() + ": " + formatMoney(e.getValue()))
                    .collect(Collectors.joining("; "));
            default -> value.toString();
        };
    }

    public static String formatMoney(long cents) {
        String reais = String.format(Locale.ROOT, "%,d", cents / 100).replace(',', '.');
        return "R$ " + reais + "," + String.format(Locale.ROOT, "%02d", cents % 100);
    }

    public static String formatHours(List<String> intervals) {
        if (intervals.isEmpty()) {
            return "fechado";
        }
        return String.join(" e ", intervals.stream().map(i -> i.replace("-", "–")).toList());
    }

    @SuppressWarnings("unchecked")
    public List<String> intervals() {
        return type == ItemType.HOURS && value != null ? (List<String>) value : List.of();
    }

    @SuppressWarnings("unchecked")
    public Map<LocalDate, List<String>> specialDays() {
        return type == ItemType.SPECIAL_DAYS && value != null ? (Map<LocalDate, List<String>>) value : Map.of();
    }

    @SuppressWarnings("unchecked")
    public Map<String, Long> priceTable() {
        return type == ItemType.PRICE_TABLE && value != null ? (Map<String, Long>) value : Map.of();
    }

    @SuppressWarnings("unchecked")
    private static List<String> castList(Object value) {
        return (List<String>) value;
    }
}
