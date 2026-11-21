package com.clinicavet.assistant.clinicknowledge.domain;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Base institucional carregada e validada (ADR-0003).
 * <p>
 * Regras de leitura:
 * <ul>
 *   <li>um item {@code null} é DESCONHECIDO;</li>
 *   <li>uma coleção inteira {@code null} (ex.: {@code services: null}) também é desconhecida: não há
 *       itens dela;</li>
 *   <li>se {@code payment.prices-disclosed} não for {@code true}, os preços são tratados como
 *       desconhecidos (P-11, DA-29): não aparecem no prompt nem autorizam valores;</li>
 *   <li><b>validade</b> (modelo de informações, 2026-11-17): cada seção de {@link #VALIDATED_SECTIONS} pode ter
 *       "válido até" em {@code review.valid-until}. Passada a data (no fuso do {@link Clock}), a seção inteira é
 *       tratada como DESCONHECIDA: melhor não informar do que informar errado. A validade é avaliada a cada
 *       consulta, então uma aplicação no ar não continua usando um preço vencido.</li>
 * </ul>
 * Itens de {@link #INTERNAL_SECTIONS} não vão ao prompt: textos aprovados que o backend insere (ADR-0003, item 6)
 * e os dados de revisão.
 */
public final class ClinicKnowledge {

    public static final String PRICES_DISCLOSED_KEY = "payment.prices-disclosed";
    public static final Set<String> INTERNAL_SECTIONS = Set.of("approved-texts", "review");
    /** Seções cujas entradas têm id escolhido pela clínica: {@code services.<id>}, {@code faq.<id>}. */
    public static final Set<String> COLLECTIONS = Set.of("services", "faq");
    /** Campo de serviço usado só no encaminhamento (S3-4): não vai ao prompt nem autoriza valores. */
    public static final String SERVICE_DEPARTMENT_FIELD = "department";
    /**
     * Seções com validade. Ficam de fora: alertas de triagem (só elevam a prioridade; vencê-los tiraria uma
     * proteção), textos aprovados (sem eles entram os textos provisórios) e a própria revisão.
     */
    public static final List<String> VALIDATED_SECTIONS = List.of("identification", "hours", "emergency",
            "services", "petshop-hours", "petshop", "payment", "policies", "faq");

    private record View(LocalDate date, Map<String, KnowledgeItem> items, Set<String> unknownCollections,
                        boolean pricesDisclosed, Set<String> expired) {
    }

    private final Map<String, KnowledgeItem> loaded;
    private final Set<String> loadedUnknownCollections;
    private final Map<String, LocalDate> validUntil;
    private final Clock clock;
    private volatile View view;
    /** Base dinâmica: cada leitura vai à base da execução atual (S3, ADR-0026). {@code null} = base fixa. */
    private final Supplier<ClinicKnowledge> source;

    public ClinicKnowledge(List<KnowledgeItem> loaded, Set<String> unknownCollections) {
        this(loaded, unknownCollections, Map.of(), Clock.systemUTC());
    }

    /** @param validUntil "válido até" por seção; seção ausente = sem prazo */
    public ClinicKnowledge(List<KnowledgeItem> loaded, Set<String> unknownCollections,
                           Map<String, LocalDate> validUntil, Clock clock) {
        Map<String, KnowledgeItem> byKey = new LinkedHashMap<>();
        for (KnowledgeItem item : loaded) {
            byKey.put(item.key(), item);
        }
        this.loaded = Collections.unmodifiableMap(byKey);
        this.loadedUnknownCollections = Set.copyOf(unknownCollections);
        this.validUntil = Map.copyOf(validUntil);
        this.clock = clock;
        this.source = null;
    }

    private ClinicKnowledge(Supplier<ClinicKnowledge> source) {
        this.loaded = Map.of();
        this.loadedUnknownCollections = Set.of();
        this.validUntil = Map.of();
        this.clock = Clock.systemUTC();
        this.source = source;
    }

    /**
     * Base que, a cada leitura, consulta {@code source}: a versão publicada da clínica atual ou o rascunho no
     * simulador (ADR-0026, itens 4 e 7). Quem recebe a base por injeção não precisa saber de clínicas nem de
     * versões; só não pode guardar valores lidos dela entre turnos.
     */
    public static ClinicKnowledge dynamic(Supplier<ClinicKnowledge> source) {
        return new ClinicKnowledge(java.util.Objects.requireNonNull(source, "source"));
    }

    public static ClinicKnowledge empty() {
        return new ClinicKnowledge(List.of(), Set.of());
    }

    /** Mesma base, lida com outro relógio (testes e carga). */
    public ClinicKnowledge withClock(Clock other) {
        if (source != null) {
            return source.get().withClock(other);
        }
        return new ClinicKnowledge(List.copyOf(loaded.values()), loadedUnknownCollections, validUntil, other);
    }

    public Optional<KnowledgeItem> item(String key) {
        if (source != null) {
            return source.get().item(key);
        }
        return Optional.ofNullable(current().items().get(key));
    }

    /** Itens que vão ao prompt (camada 2), na ordem do arquivo. */
    public List<KnowledgeItem> promptItems() {
        if (source != null) {
            return source.get().promptItems();
        }
        return current().items().values().stream()
                .filter(item -> !INTERNAL_SECTIONS.contains(section(item.key())))
                .filter(item -> !isRoutingField(item.key()))
                .toList();
    }

    /**
     * Itens que uma referência do modelo designa: a própria chave, se for um item, ou todos os itens de
     * uma entrada de coleção ({@code services.consulta}). Vazio se a referência não existir.
     */
    public List<KnowledgeItem> resolve(String ref) {
        if (source != null) {
            return source.get().resolve(ref);
        }
        if (ref == null || ref.isBlank() || INTERNAL_SECTIONS.contains(section(ref))) {
            return List.of();
        }
        Map<String, KnowledgeItem> items = current().items();
        KnowledgeItem exact = items.get(ref);
        if (exact != null) {
            return List.of(exact);
        }
        if (!ref.contains(".")) {
            // Seção inteira (ex.: "hours"), para quando a resposta resume várias linhas dela (DA-41).
            List<KnowledgeItem> section = items.values().stream().filter(i -> section(i.key()).equals(ref)).toList();
            if (!section.isEmpty()) {
                return section;
            }
        }
        if (ref.chars().filter(c -> c == '.').count() == 1 && COLLECTIONS.contains(section(ref))) {
            List<KnowledgeItem> entry = new ArrayList<>();
            items.forEach((key, item) -> {
                if (key.startsWith(ref + ".") && !isRoutingField(key)) {
                    entry.add(item);
                }
            });
            return List.copyOf(entry);
        }
        return List.of();
    }

    public boolean pricesDisclosed() {
        if (source != null) {
            return source.get().pricesDisclosed();
        }
        return current().pricesDisclosed();
    }

    /** Coleções desconhecidas por inteiro ({@code services: null}) ou vencidas. */
    public Set<String> unknownCollections() {
        if (source != null) {
            return source.get().unknownCollections();
        }
        return current().unknownCollections();
    }

    /** Seções vencidas hoje (tratadas como DESCONHECIDAS). */
    public Set<String> expiredSections() {
        if (source != null) {
            return source.get().expiredSections();
        }
        return current().expired();
    }

    /** Seções com itens conhecidos e sem "válido até" (aviso na inicialização). */
    public Set<String> sectionsWithoutValidity() {
        if (source != null) {
            return source.get().sectionsWithoutValidity();
        }
        Set<String> without = new HashSet<>();
        for (String section : VALIDATED_SECTIONS) {
            boolean hasKnown = loaded.values().stream()
                    .anyMatch(i -> section(i.key()).equals(section) && i.isKnown());
            if (hasKnown && !validUntil.containsKey(section)) {
                without.add(section);
            }
        }
        return Set.copyOf(without);
    }

    public Optional<LocalDate> validUntil(String section) {
        if (source != null) {
            return source.get().validUntil(section);
        }
        return Optional.ofNullable(validUntil.get(section));
    }

    /** Itens como foram escritos, sem aplicar validade nem "preços divulgados" (comparação de versões). */
    public List<KnowledgeItem> allItems() {
        if (source != null) {
            return source.get().allItems();
        }
        return List.copyOf(loaded.values());
    }

    /** "Válido até" de cada seção que tem prazo. */
    public Map<String, LocalDate> validity() {
        if (source != null) {
            return source.get().validity();
        }
        return validUntil;
    }

    public Clock clock() {
        if (source != null) {
            return source.get().clock();
        }
        return clock;
    }

    /** Setor de cada serviço com setor definido: id do serviço → tipo do setor ({@code PETSHOP}...). */
    public Map<String, String> serviceDepartments() {
        if (source != null) {
            return source.get().serviceDepartments();
        }
        Map<String, String> result = new LinkedHashMap<>();
        loaded.forEach((key, item) -> {
            if (isRoutingField(key) && item.isKnown()) {
                result.put(key.substring("services.".length(), key.lastIndexOf('.')), item.value().toString());
            }
        });
        return Collections.unmodifiableMap(result);
    }

    private static boolean isRoutingField(String key) {
        return key.startsWith("services.") && key.endsWith("." + SERVICE_DEPARTMENT_FIELD)
                && key.chars().filter(c -> c == '.').count() == 2;
    }

    public static String section(String key) {
        int dot = key.indexOf('.');
        return dot < 0 ? key : key.substring(0, dot);
    }

    private View current() {
        LocalDate today = LocalDate.now(clock);
        View cached = view;
        if (cached != null && cached.date().equals(today)) {
            return cached;
        }
        View fresh = compute(today);
        view = fresh;
        return fresh;
    }

    private View compute(LocalDate today) {
        Set<String> expired = new HashSet<>();
        validUntil.forEach((section, until) -> {
            if (today.isAfter(until)) {
                expired.add(section);
            }
        });
        Map<String, KnowledgeItem> withValidity = new LinkedHashMap<>();
        loaded.forEach((key, item) -> withValidity.put(key,
                expired.contains(section(key)) ? new KnowledgeItem(key, item.type(), null) : item));
        KnowledgeItem disclosed = withValidity.get(PRICES_DISCLOSED_KEY);
        boolean pricesDisclosed = disclosed != null && Boolean.TRUE.equals(disclosed.value());
        Map<String, KnowledgeItem> effective = new LinkedHashMap<>();
        withValidity.forEach((key, item) -> effective.put(key,
                isPrice(item.type()) && !pricesDisclosed ? new KnowledgeItem(key, item.type(), null) : item));
        Set<String> unknown = new HashSet<>(loadedUnknownCollections);
        for (String collection : COLLECTIONS) {
            if (expired.contains(collection)) {
                unknown.add(collection);
                effective.keySet().removeIf(k -> section(k).equals(collection));
            }
        }
        return new View(today, Collections.unmodifiableMap(effective), Set.copyOf(unknown), pricesDisclosed,
                Set.copyOf(expired));
    }

    private static boolean isPrice(ItemType type) {
        return type == ItemType.PRICE_CENTS || type == ItemType.PRICE_TABLE;
    }
}
