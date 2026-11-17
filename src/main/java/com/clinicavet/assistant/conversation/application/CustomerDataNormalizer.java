package com.clinicavet.assistant.conversation.application;

import com.clinicavet.assistant.conversation.application.rules.RuleText;
import com.clinicavet.assistant.tutor.domain.Pet;
import com.clinicavet.assistant.tutor.domain.Species;
import com.clinicavet.assistant.tutor.domain.Tutor;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Normalização e validação dos dados que o modelo diz ter lido (ADR-0010, item 4). O que não passa é
 * descartado, sem falhar o turno.
 * <ul>
 *   <li><b>Nomes</b> (tutor e pet): letras, espaço, apóstrofo, ponto e hífen; até 5 palavras; dentro do limite do
 *       cadastro. E precisam <b>aparecer como palavras em alguma mensagem do tutor desta conversa</b> (ou já estar
 *       no cadastro deste tutor): um nome inventado pelo modelo, ou copiado da resposta do assistente, não entra;</li>
 *   <li><b>Textos livres</b> (raça, idade, motivo, preferência): sem quebra de linha nem os caracteres
 *       {@code < > { } [ ]}, que delimitam blocos no prompt; acima do limite, cortados.</li>
 * </ul>
 */
final class CustomerDataNormalizer {

    private static final Pattern NAME = Pattern.compile("^\\p{L}[\\p{L}\\p{M}' .-]*$");
    private static final Pattern BLOCK_CHARS = Pattern.compile("[<>{}\\[\\]\\r\\n\\t]+");
    private static final Pattern SPACES = Pattern.compile("\\s+");
    private static final int MAX_NAME_WORDS = 5;

    static final Set<String> DOG_WORDS = Set.of("cachorro", "cachorra", "cao", "cadela", "cachorrinho", "cachorrinha",
            "canino", "canina");
    static final Set<String> CAT_WORDS = Set.of("gato", "gata", "gatinho", "gatinha", "felino", "felina");
    private static final Set<String> DISAGREEMENT = Set.of("errado", "errada", "incorreto", "incorreta");

    private CustomerDataNormalizer() {
    }

    /** Nome válido ou {@code null}. */
    static String name(String raw, int maxLength) {
        if (raw == null) {
            return null;
        }
        String clean = SPACES.matcher(raw.strip()).replaceAll(" ");
        if (clean.isEmpty() || clean.length() > maxLength || !NAME.matcher(clean).matches()
                || clean.split(" ").length > MAX_NAME_WORDS) {
            return null;
        }
        return clean;
    }

    static String tutorName(String raw) {
        return name(raw, Tutor.MAX_NAME_LENGTH);
    }

    /**
     * Nome de pet, ou {@code null}. Além do formato, recusa espécie ou palavra genérica no lugar do nome ("cachorro",
     * "minha gata", "o pet"): de "sacrifício de cachorro" não sai um pet chamado Cachorro (CT-19).
     */
    static String petName(String raw) {
        String name = name(raw, Pet.MAX_TEXT_LENGTH);
        if (name == null) {
            return null;
        }
        boolean onlyGeneric = words(RuleText.normalize(name)).stream()
                .allMatch(w -> GENERIC_PET_WORDS.contains(w) || ARTICLES.contains(w));
        return onlyGeneric ? null : name;
    }

    /** Espécies e termos genéricos que não são nome de animal. */
    static final Set<String> GENERIC_PET_WORDS = Set.of("cachorro", "cachorra", "cachorrinho", "cachorrinha", "cao",
            "caes", "cadela", "cadelinha", "gato", "gata", "gatinho", "gatinha", "gatos", "cachorros", "pet", "pets",
            "animal", "animais", "bicho", "bichinho", "bichinha", "filhote", "filhotinho", "felino", "felina",
            "canino", "canina", "coelho", "passaro", "peixe", "hamster", "calopsita", "papagaio", "tartaruga",
            "porquinho", "cobaia", "furao", "dog", "cat");
    private static final Set<String> ARTICLES = Set.of("o", "a", "os", "as", "meu", "minha", "meus", "minhas", "um",
            "uma", "seu", "sua", "do", "da", "de", "nosso", "nossa");

    /** Texto livre limpo e cortado no limite, ou {@code null} se vazio. */
    static String freeText(String raw, int maxLength) {
        if (raw == null) {
            return null;
        }
        String clean = SPACES.matcher(BLOCK_CHARS.matcher(raw).replaceAll(" ")).replaceAll(" ").strip();
        if (clean.isEmpty()) {
            return null;
        }
        return clean.length() <= maxLength ? clean : clean.substring(0, maxLength - 1).strip() + "…";
    }

    /** O nome aparece como palavras inteiras em algum dos textos (comparação normalizada)? */
    static boolean appearsIn(String name, List<String> texts) {
        String expression = RuleText.normalize(name);
        return !expression.isEmpty()
                && texts.stream().anyMatch(t -> RuleText.indexOfWords(RuleText.normalize(t), expression) >= 0);
    }

    /** A espécie aparece no texto (palavras de cachorro ou gato, ou a descrição de OTHER)? */
    static boolean speciesAppearsIn(Species species, String description, String text) {
        String normalized = RuleText.normalize(text);
        Set<String> words = words(normalized);
        return switch (species) {
            case DOG -> DOG_WORDS.stream().anyMatch(words::contains);
            case CAT -> CAT_WORDS.stream().anyMatch(words::contains);
            case OTHER -> description != null && appearsIn(description, List.of(text));
        };
    }

    private static Set<String> words(String normalized) {
        return new java.util.HashSet<>(java.util.Arrays.asList(normalized.split(" ")));
    }

    /**
     * A mensagem do tutor discorda? (P-08 provisório: começa com "não" ou diz "errado"/"incorreto".) Nesse caso
     * nenhuma confirmação do turno vale, mesmo que o modelo tenha marcado.
     */
    static boolean disagrees(String customerText) {
        String normalized = RuleText.normalize(customerText);
        if (normalized.equals("nao") || normalized.startsWith("nao ")) {
            return true;
        }
        return words(normalized).stream().anyMatch(DISAGREEMENT::contains);
    }
}
