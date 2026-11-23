package com.clinicavet.assistant.conversation.application;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Textos que o sistema envia ao tutor (revisão de linguagem, seção 6, item 3): nunca dizem que alguém foi avisado
 * (RN-S01), não chamam solicitação de "pedido" (reservado à compra) e não afirmam confirmação. Lê o
 * {@code application.yml} de verdade, então vale para os textos provisórios e para os que a clínica aprovar ali.
 */
class TutorTextsTest {

    private static final Pattern FORBIDDEN = Pattern.compile(
            "\\b(avisei|avisamos|notifiquei|notificamos|pedido|pedidos)\\b|foi confirmad|está confirmad",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    @SuppressWarnings("unchecked")
    private static Map<String, Object> section(Map<String, Object> root, String... path) {
        Map<String, Object> current = root;
        for (String key : path) {
            current = (Map<String, Object>) current.get(key);
        }
        return current;
    }

    @Test
    @SuppressWarnings("unchecked")
    void textsSentToTheTutor_followTheGlossary() throws Exception {
        Map<String, Object> root;
        try (InputStream in = getClass().getResourceAsStream("/application.yml")) {
            root = new Yaml().load(in);
        }
        Map<String, Object> assistant = section(root, "assistant");
        List<String> texts = new ArrayList<>();
        section(assistant, "handoff", "replies").values().forEach(v -> texts.add(String.valueOf(v)));
        texts.add(String.valueOf(section(assistant, "customer-data").get("appointment-registered-reply")));
        texts.add(String.valueOf(section(assistant, "staff").get("assumed-reply")));
        texts.add(String.valueOf(section(assistant, "conversation").get("fallback-reply")));
        texts.add(String.valueOf(section(assistant, "conversation").get("first-reply-intro")));

        assertThat(texts).hasSizeGreaterThan(10);
        assertThat(texts).allSatisfy(text -> assertThat(FORBIDDEN.matcher(text).find())
                .as("texto ao tutor fora do glossário: %s", text).isFalse());
    }
}
