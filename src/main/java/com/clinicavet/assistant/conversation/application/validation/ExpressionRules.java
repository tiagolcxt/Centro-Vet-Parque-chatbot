package com.clinicavet.assistant.conversation.application.validation;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Expressões reprovadas de V5 e V6 (ADR-0008, DA-29), lidas de {@code validation/expression-rules.yml}.
 * Padrão inválido ou arquivo malformado impede a inicialização.
 */
public final class ExpressionRules {

    public record Rule(String id, String check, Pattern pattern) {
    }

    public record Hit(String id, String check) {
    }

    static final String RESOURCE = "validation/expression-rules.yml";
    private static final Set<String> CHECKS = Set.of("V5", "V6");
    private static final Set<String> NEGATIONS = Set.of("nao", "nunca");
    private static final int NEGATION_WINDOW = 3;

    private final List<Rule> rules;
    private final List<String> allowed;

    private ExpressionRules(List<Rule> rules, List<String> allowed) {
        this.rules = List.copyOf(rules);
        this.allowed = List.copyOf(allowed);
    }

    public static ExpressionRules fromClasspath() {
        try (InputStream in = ExpressionRules.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("regras de validação ausentes: " + RESOURCE);
            }
            return parse(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @SuppressWarnings("unchecked")
    static ExpressionRules parse(String yaml) {
        Map<String, Object> root = new Yaml(new SafeConstructor(new LoaderOptions())).load(yaml);
        List<Rule> rules = new ArrayList<>();
        for (Map<String, Object> raw : (List<Map<String, Object>>) root.get("rules")) {
            String id = String.valueOf(raw.get("id"));
            String check = String.valueOf(raw.get("check"));
            if (!CHECKS.contains(check)) {
                throw new IllegalStateException("regra " + id + ": check deve ser V5 ou V6");
            }
            rules.add(new Rule(id, check, Pattern.compile(String.valueOf(raw.get("pattern")))));
        }
        List<String> allowed = new ArrayList<>();
        Object rawAllowed = root.get("allowed");
        if (rawAllowed instanceof List<?> list) {
            list.forEach(text -> allowed.add(normalize(String.valueOf(text))));
        }
        return new ExpressionRules(rules, allowed);
    }

    /** Expressões reprovadas encontradas no texto (no máximo uma por regra), respeitando a negação. */
    public List<Hit> find(String reply) {
        String text = normalize(reply);
        for (String approved : allowed) {
            text = text.replace(approved, " ");
        }
        List<Hit> hits = new ArrayList<>();
        for (Rule rule : rules) {
            Matcher matcher = rule.pattern().matcher(text);
            while (matcher.find()) {
                if (!negated(text, matcher.start())) {
                    hits.add(new Hit(rule.id(), rule.check()));
                    break;
                }
            }
        }
        return List.copyOf(hits);
    }

    public Optional<Rule> rule(String id) {
        return rules.stream().filter(r -> r.id().equals(id)).findFirst();
    }

    private static boolean negated(String text, int start) {
        String[] before = text.substring(0, start).strip().split("[\\s,;:]+");
        List<String> words = Arrays.asList(before);
        List<String> window = words.subList(Math.max(0, words.size() - NEGATION_WINDOW), words.size());
        return window.stream().anyMatch(NEGATIONS::contains);
    }

    /** Minúsculas, sem acentos, espaços normalizados. */
    public static String normalize(String text) {
        String noAccents = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return noAccents.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").strip();
    }
}
