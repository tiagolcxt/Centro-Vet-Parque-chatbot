package com.clinicavet.assistant.clinicknowledge.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Base institucional carregada e validada (ADR-0003). Imutável depois da carga.
 * <p>
 * Regras de leitura:
 * <ul>
 *   <li>um item {@code null} é DESCONHECIDO;</li>
 *   <li>uma coleção inteira {@code null} (ex.: {@code services: null}) também é desconhecida: não há
 *       itens dela;</li>
 *   <li>se {@code payment.prices-disclosed} não for {@code true}, os preços são tratados como
 *       desconhecidos (P-11, DA-29): não aparecem no prompt nem autorizam valores.</li>
 * </ul>
 * Itens de {@link #INTERNAL_SECTIONS} são textos aprovados que o backend insere (ADR-0003, item 6) e
 * não vão ao prompt.
 */
public final class ClinicKnowledge {

    public static final String PRICES_DISCLOSED_KEY = "payment.prices-disclosed";
    public static final Set<String> INTERNAL_SECTIONS = Set.of("approved-texts");
    /** Seções cujas entradas têm id escolhido pela clínica: {@code services.<id>}, {@code faq.<id>}. */
    public static final Set<String> COLLECTIONS = Set.of("services", "faq");

    private final Map<String, KnowledgeItem> items;
    private final Set<String> unknownCollections;
    private final boolean pricesDisclosed;

    public ClinicKnowledge(List<KnowledgeItem> loaded, Set<String> unknownCollections) {
        Map<String, KnowledgeItem> byKey = new LinkedHashMap<>();
        for (KnowledgeItem item : loaded) {
            byKey.put(item.key(), item);
        }
        KnowledgeItem disclosed = byKey.get(PRICES_DISCLOSED_KEY);
        this.pricesDisclosed = disclosed != null && Boolean.TRUE.equals(disclosed.value());
        Map<String, KnowledgeItem> effective = new LinkedHashMap<>();
        byKey.forEach((key, item) -> effective.put(key,
                item.type() == ItemType.PRICE_CENTS && !pricesDisclosed
                        ? new KnowledgeItem(item.key(), item.type(), null)
                        : item));
        this.items = Collections.unmodifiableMap(effective);
        this.unknownCollections = Set.copyOf(unknownCollections);
    }

    public static ClinicKnowledge empty() {
        return new ClinicKnowledge(List.of(), Set.of());
    }

    public Optional<KnowledgeItem> item(String key) {
        return Optional.ofNullable(items.get(key));
    }

    /** Itens que vão ao prompt (camada 2), na ordem do arquivo. */
    public List<KnowledgeItem> promptItems() {
        return items.values().stream()
                .filter(item -> !INTERNAL_SECTIONS.contains(section(item.key())))
                .toList();
    }

    /**
     * Itens que uma referência do modelo designa: a própria chave, se for um item, ou todos os itens de
     * uma entrada de coleção ({@code services.consulta}). Vazio se a referência não existir.
     */
    public List<KnowledgeItem> resolve(String ref) {
        if (ref == null || ref.isBlank() || INTERNAL_SECTIONS.contains(section(ref))) {
            return List.of();
        }
        KnowledgeItem exact = items.get(ref);
        if (exact != null) {
            return List.of(exact);
        }
        if (ref.chars().filter(c -> c == '.').count() == 1 && COLLECTIONS.contains(section(ref))) {
            List<KnowledgeItem> entry = new ArrayList<>();
            items.forEach((key, item) -> {
                if (key.startsWith(ref + ".")) {
                    entry.add(item);
                }
            });
            return List.copyOf(entry);
        }
        return List.of();
    }

    public boolean pricesDisclosed() {
        return pricesDisclosed;
    }

    /** Coleções declaradas como desconhecidas por inteiro ({@code services: null}). */
    public Set<String> unknownCollections() {
        return unknownCollections;
    }

    public static String section(String key) {
        int dot = key.indexOf('.');
        return dot < 0 ? key : key.substring(0, dot);
    }
}
