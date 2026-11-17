package com.clinicavet.assistant.conversation.application.rules;

import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Detector determinístico de pedido explícito de atendimento humano (ADR-0007, item 1; DA-30). Roda
 * <b>antes</b> do modelo, em qualquer estado. Quando dispara, o encaminhamento não depende do modelo, da
 * validação nem de falhas técnicas. Lista em {@code rules/human-request-expressions.yml}.
 */
@Component
public class ExplicitHumanRequestDetector {

    static final String RESOURCE = "rules/human-request-expressions.yml";

    private record Rule(String id, Pattern pattern) {
    }

    private final List<Rule> rules;
    private final Set<String> shortMessages;
    private final int negationWindow;
    private final int maxShortWords;

    public ExplicitHumanRequestDetector() {
        this(read());
    }

    @SuppressWarnings("unchecked")
    ExplicitHumanRequestDetector(String yaml) {
        Map<String, Object> root = new Yaml(new SafeConstructor(new LoaderOptions())).load(yaml);
        List<Rule> parsed = new ArrayList<>();
        for (Map<String, Object> raw : (List<Map<String, Object>>) root.get("patterns")) {
            parsed.add(new Rule(String.valueOf(raw.get("id")), Pattern.compile(String.valueOf(raw.get("pattern")))));
        }
        this.rules = List.copyOf(parsed);
        this.shortMessages = ((List<String>) root.get("short-messages")).stream()
                .map(RuleText::normalize).collect(Collectors.toUnmodifiableSet());
        this.negationWindow = ((Number) root.get("negation-window")).intValue();
        this.maxShortWords = ((Number) root.get("max-short-words")).intValue();
    }

    /** O id da regra que disparou, se alguma disparou. */
    public Optional<String> detect(String customerText) {
        String text = RuleText.normalize(customerText);
        if (text.isEmpty()) {
            return Optional.empty();
        }
        if (RuleText.wordCount(text) <= maxShortWords && shortMessages.contains(text)) {
            return Optional.of("mensagem-curta");
        }
        for (Rule rule : rules) {
            Matcher matcher = rule.pattern().matcher(text);
            while (matcher.find()) {
                if (!RuleText.negatedBefore(text, matcher.start(), negationWindow)) {
                    return Optional.of(rule.id());
                }
            }
        }
        return Optional.empty();
    }

    private static String read() {
        try (InputStream in = ExplicitHumanRequestDetector.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("expressões de pedido de humano ausentes: " + RESOURCE);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
