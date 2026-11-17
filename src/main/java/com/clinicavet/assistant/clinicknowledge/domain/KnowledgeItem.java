package com.clinicavet.assistant.clinicknowledge.domain;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Um item da base, com chave estável (ex.: {@code hours.saturday}, {@code services.consulta.price-cents})
 * que o modelo cita como fonte (ADR-0008). {@code value == null} significa <b>DESCONHECIDO</b>.
 *
 * @param value {@code String} (texto, telefone, e-mail, site, perfil, CEP), {@code Boolean},
 *              {@code Long} (centavos) ou {@code List<String>} (intervalos), conforme o tipo
 */
public record KnowledgeItem(String key, ItemType type, Object value) {

    public static final String UNKNOWN_TEXT = "DESCONHECIDO — confirmar com a equipe";

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
            default -> value.toString();
        };
    }

    public static String formatMoney(long cents) {
        String reais = String.format(Locale.ROOT, "%,d", cents / 100).replace(',', '.');
        return "R$ " + reais + "," + String.format(Locale.ROOT, "%02d", cents % 100);
    }

    private static String formatHours(List<String> intervals) {
        if (intervals.isEmpty()) {
            return "fechado";
        }
        return String.join(" e ", intervals.stream().map(i -> i.replace("-", "–")).toList());
    }

    @SuppressWarnings("unchecked")
    private static List<String> castList(Object value) {
        return (List<String>) value;
    }
}
