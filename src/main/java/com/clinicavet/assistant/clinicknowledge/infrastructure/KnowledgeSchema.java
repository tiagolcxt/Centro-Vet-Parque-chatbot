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

    /**
     * Campos opcionais de uma entrada (S3-4): ausentes valem {@code null}. Assim um arquivo antigo continua válido.
     * {@code services.<id>.department}: setor que cuida do serviço ({@link #DEPARTMENT_KINDS}).
     */
    static final Map<String, Map<String, ItemType>> OPTIONAL_IN_ENTRY = Map.of(
            "services", Map.of("department", ItemType.TEXT));

    /** Valores aceitos em {@code services.<id>.department}: o tipo do setor (Departments.Kind). */
    static final Set<String> DEPARTMENT_KINDS = Set.of("RECEPTION", "CLINICAL", "PETSHOP", "OTHER");

    static final Map<String, Map<String, ItemType>> SECTIONS = new LinkedHashMap<>();
    static final Map<String, Map<String, ItemType>> COLLECTION_ENTRIES = new LinkedHashMap<>();

    static {
        SECTIONS.put("review", ordered(
                "reviewed-at", ItemType.DATE,
                "reviewed-by", ItemType.TEXT));
        SECTIONS.put("identification", ordered(
                "name", ItemType.TEXT,
                "address", ItemType.TEXT,
                "location-reference", ItemType.TEXT,
                "access", ItemType.TEXT,
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
                "holidays", ItemType.SPECIAL_DAYS,
                "twenty-four-hours", ItemType.BOOLEAN));
        SECTIONS.put("emergency", ordered(
                "attended", ItemType.TEXT,
                "after-hours-referral", ItemType.TEXT));
        SECTIONS.put("petshop-hours", ordered(
                "monday", ItemType.HOURS,
                "tuesday", ItemType.HOURS,
                "wednesday", ItemType.HOURS,
                "thursday", ItemType.HOURS,
                "friday", ItemType.HOURS,
                "saturday", ItemType.HOURS,
                "sunday", ItemType.HOURS));
        SECTIONS.put("petshop", ordered(
                "grooming-requirements", ItemType.TEXT,
                "pickup-delivery", ItemType.TEXT,
                "boarding", ItemType.TEXT,
                "products", ItemType.TEXT,
                "orders", ItemType.TEXT));
        SECTIONS.put("payment", ordered(
                "prices-disclosed", ItemType.BOOLEAN,
                "methods", ItemType.TEXT,
                "installments", ItemType.TEXT,
                "health-plans", ItemType.TEXT,
                "estimates", ItemType.TEXT));
        SECTIONS.put("policies", ordered(
                "scheduling", ItemType.TEXT,
                "cancellation", ItemType.TEXT,
                "no-show", ItemType.TEXT,
                "transport", ItemType.TEXT));
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
                "species", ItemType.TEXT,
                "booking", ItemType.TEXT,
                "duration", ItemType.TEXT,
                "price-cents", ItemType.PRICE_CENTS,
                "price-by-size", ItemType.PRICE_TABLE,
                "price-note", ItemType.TEXT));
        COLLECTION_ENTRIES.put("faq", ordered(
                "question", ItemType.TEXT,
                "answer", ItemType.TEXT));
    }

    /** Ordem de primeiro nível no arquivo e no prompt. */
    static final java.util.List<String> TOP_LEVEL = java.util.List.of("review", "identification", "hours",
            "emergency", "services", "petshop-hours", "petshop", "payment", "policies", "faq", "triage", "approved-texts");

    /** Subchave de {@code review} com o "válido até" de cada seção ({@code ClinicKnowledge.VALIDATED_SECTIONS}). */
    static final String VALID_UNTIL = "valid-until";

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
