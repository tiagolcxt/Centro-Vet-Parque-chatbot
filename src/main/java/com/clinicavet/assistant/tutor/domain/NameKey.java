package com.clinicavet.assistant.tutor.domain;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Forma normalizada de nomes para comparação (DA-35): minúsculas, sem acento, espaços colapsados. "Mel", " mel "
 * e "Mél" são o mesmo nome; "Mel" e "Melzinha" não. Sem similaridade aproximada no MVP.
 */
public final class NameKey {

    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    private NameKey() {
    }

    public static String of(String name) {
        if (name == null) {
            return "";
        }
        String decomposed = Normalizer.normalize(name, Normalizer.Form.NFD);
        return SPACES.matcher(MARKS.matcher(decomposed).replaceAll("")).replaceAll(" ").strip()
                .toLowerCase(Locale.ROOT);
    }

    public static boolean same(String a, String b) {
        String key = of(a);
        return !key.isEmpty() && key.equals(of(b));
    }
}
