package com.clinicavet.assistant.clinicknowledge.infrastructure;

import com.clinicavet.assistant.clinicknowledge.domain.ItemType;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Estrutura esperada do arquivo da base (ADR-0003). <b>Todas</b> as chaves listadas precisam estar no
 * arquivo; {@code null} = DESCONHECIDO. Chave fora desta lista é erro.
 * <p>
 * {@code services} e {@code faq} são coleções: os ids das entradas são da clínica (minúsculas, dígitos
 * e hífen); os campos de cada entrada são fixos. Uma coleção inteira pode ser {@code null}.
 */
final class KnowledgeSchema {

    /** Campos obrigatoriamente preenchidos (não podem ser {@code null}) dentro de uma entrada. */
    static final Map<String, Set<String>> REQUIRED_IN_ENTRY = Map.of(
            "services", Set.of("name"),
            "faq", Set.of("question"));

    static final Map<String, Map<String, ItemType>> SECTIONS = new LinkedHashMap<>();
    static final Map<String, Map<String, ItemType>> COLLECTION_ENTRIES = new LinkedHashMap<>();

    static {
        SECTIONS.put("identification", ordered(
                "name", ItemType.TEXT,
                "address", ItemType.TEXT,
                "zip-code", ItemType.ZIP,
                "phone", ItemType.PHONE,
                "whatsapp", ItemType.PHONE,
                "email", ItemType.EMAIL,
                "website", ItemType.URL,
                "instagram", ItemType.HANDLE));
        SECTIONS.put("hours", ordered(
                "monday", ItemType.HOURS,
                "tuesday", ItemType.HOURS,
                "wednesday", ItemType.HOURS,
                "thursday", ItemType.HOURS,
                "friday", ItemType.HOURS,
                "saturday", ItemType.HOURS,
                "sunday", ItemType.HOURS,
                "holidays", ItemType.TEXT,
                "after-hours", ItemType.TEXT,
                "twenty-four-hours", ItemType.BOOLEAN));
        SECTIONS.put("payment", ordered(
                "prices-disclosed", ItemType.BOOLEAN,
                "methods", ItemType.TEXT,
                "installments", ItemType.TEXT));
        SECTIONS.put("policies", ordered(
                "scheduling", ItemType.TEXT,
                "cancellation", ItemType.TEXT,
                "no-show", ItemType.TEXT));
        SECTIONS.put("triage", ordered(
                "alert-expressions-p1", ItemType.TEXT_LIST,
                "alert-expressions-p2", ItemType.TEXT_LIST));
        SECTIONS.put("approved-texts", ordered(
                "emergency-policy", ItemType.TEXT,
                "direct-contact", ItemType.TEXT));

        COLLECTION_ENTRIES.put("services", ordered(
                "name", ItemType.TEXT,
                "offered", ItemType.BOOLEAN,
                "description", ItemType.TEXT,
                "price-cents", ItemType.PRICE_CENTS,
                "price-note", ItemType.TEXT));
        COLLECTION_ENTRIES.put("faq", ordered(
                "question", ItemType.TEXT,
                "answer", ItemType.TEXT));
    }

    /** Ordem de primeiro nível no arquivo e no prompt. */
    static final java.util.List<String> TOP_LEVEL = java.util.List.of(
            "identification", "hours", "services", "payment", "policies", "faq", "triage", "approved-texts");

    private KnowledgeSchema() {
    }

    private static Map<String, ItemType> ordered(Object... pairs) {
        Map<String, ItemType> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], (ItemType) pairs[i + 1]);
        }
        return map;
    }
}
