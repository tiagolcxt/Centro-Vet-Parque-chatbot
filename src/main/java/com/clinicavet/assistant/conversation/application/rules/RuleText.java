package com.clinicavet.assistant.conversation.application.rules;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Normalização usada pelos detectores determinísticos (ADR-0007, ADR-0019, DA-30): minúsculas, sem
 * acentos, pontuação trocada por espaço e espaços colapsados. Assim "Quero falar com um ATENDENTE!!" e
 * "quero   falar com um atendente" são o mesmo texto.
 */
public final class RuleText {

    private static final Set<String> NEGATIONS = Set.of("nao", "nunca");

    private RuleText() {
    }

    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String noAccents = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return noAccents.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .strip();
    }

    public static int wordCount(String normalized) {
        return normalized.isEmpty() ? 0 : normalized.split(" ").length;
    }

    /** Há "não" ou "nunca" nas {@code window} palavras antes da posição {@code start}? */
    public static boolean negatedBefore(String normalized, int start, int window) {
        String before = normalized.substring(0, start).strip();
        if (before.isEmpty()) {
            return false;
        }
        List<String> words = Arrays.asList(before.split(" "));
        return words.subList(Math.max(0, words.size() - window), words.size()).stream().anyMatch(NEGATIONS::contains);
    }

    /** A expressão (normalizada) aparece no texto como palavras inteiras? */
    public static int indexOfWords(String normalizedText, String normalizedExpression) {
        if (normalizedExpression.isEmpty()) {
            return -1;
        }
        String padded = " " + normalizedText + " ";
        int index = padded.indexOf(" " + normalizedExpression + " ");
        return index < 0 ? -1 : index;
    }
}
