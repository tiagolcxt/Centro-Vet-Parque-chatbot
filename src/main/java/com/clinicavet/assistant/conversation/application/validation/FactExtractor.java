package com.clinicavet.assistant.conversation.application.validation;

import com.clinicavet.assistant.conversation.application.validation.Fact.Type;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Encontra os valores factuais de um texto (lista aprovada na DA-29, ADR-0008 seção 3).
 * <p>
 * Os padrões rodam numa ordem fixa e cada trecho do texto só pode virar um fato: o que um padrão já
 * reconheceu fica "coberto" para os seguintes. Assim {@code em 48 horas} é prazo e não horário, e o
 * {@code @} de um e-mail não vira perfil de rede social.
 * <p>
 * Escolhas de detecção dentro da lista aprovada, para evitar alarmes falsos óbvios:
 * <ul>
 *   <li>{@code às 8} exige "às" com acento ("as 3 vacinas" não é horário); {@code das 8} só conta
 *       seguido de "às/até" ({@code das 8 às 18});</li>
 *   <li>abreviações de dia ({@code seg}, {@code ter}...) só contam com ponto ({@code ter.}) ou num
 *       intervalo ({@code seg a sex}), porque "ter" também é verbo;</li>
 *   <li>prazo exige preposição antes ({@code em}, {@code em até}, {@code dentro de}, {@code com},
 *       {@code por}, {@code após}, {@code depois de}, {@code antes de}). "Até 18h" é horário;</li>
 *   <li>números sem unidade nem moeda ("3 anos", "2 vezes ao dia") ficam de fora, de propósito.</li>
 * </ul>
 */
public final class FactExtractor {

    private static final String DAY_ABBR = "(seg|ter|qua|qui|sex|sáb|sab|dom)";
    private static final Map<String, String> WEEKDAYS = Map.ofEntries(
            Map.entry("segunda", "segunda"), Map.entry("seg", "segunda"),
            Map.entry("terça", "terca"), Map.entry("terca", "terca"), Map.entry("ter", "terca"),
            Map.entry("quarta", "quarta"), Map.entry("qua", "quarta"),
            Map.entry("quinta", "quinta"), Map.entry("qui", "quinta"),
            Map.entry("sexta", "sexta"), Map.entry("sex", "sexta"),
            Map.entry("sábado", "sabado"), Map.entry("sabado", "sabado"), Map.entry("sáb", "sabado"),
            Map.entry("sab", "sabado"),
            Map.entry("domingo", "domingo"), Map.entry("dom", "domingo"));
    private static final Map<String, Integer> MONTHS = Map.ofEntries(
            Map.entry("janeiro", 1), Map.entry("fevereiro", 2), Map.entry("março", 3), Map.entry("marco", 3),
            Map.entry("abril", 4), Map.entry("maio", 5), Map.entry("junho", 6), Map.entry("julho", 7),
            Map.entry("agosto", 8), Map.entry("setembro", 9), Map.entry("outubro", 10),
            Map.entry("novembro", 11), Map.entry("dezembro", 12));

    private record Rule(Pattern pattern, BiConsumer<Matcher, List<Fact>> toFacts) {
    }

    private static final List<Rule> RULES = List.of(
            rule("[\\w.+-]+@[\\w-]+(?:\\.[\\w-]+)+", (m, out) -> out.add(fact(Type.EMAIL, m.group()))),
            rule("(?:https?://)?(?:www\\.)?[a-z0-9-]+(?:\\.[a-z0-9-]+)*\\.(?:com|net|org|vet|example|br)(?:\\.br)?(?:/[^\\s]*)?",
                    (m, out) -> out.add(fact(Type.URL, url(m.group())))),
            rule("(?<![\\w@.])@[a-z0-9_.]{2,30}", (m, out) -> out.add(fact(Type.HANDLE, trimDots(m.group())))),
            // V7: doses
            rule("\\b\\d+(?:[.,]\\d+)?\\s?(?:mg/kg|mg|ml|mcg|gotas?)\\b",
                    (m, out) -> out.add(fact(Type.DOSE, m.group().replaceAll("\\s", "").replace(',', '.')))),
            rule("\\b(?:\\d+(?:[.,]\\d+)?|meio|meia|um|uma|dois|duas|três|tres)\\s+comprimidos?\\b",
                    (m, out) -> out.add(fact(Type.DOSE, m.group().replaceAll("\\s+", " ").replace("três", "tres")))),
            rule("\\bde\\s+(\\d{1,2})\\s+em\\s+(\\d{1,2})\\s+horas\\b",
                    (m, out) -> out.add(fact(Type.DOSE, "de " + m.group(1) + " em " + m.group(2) + " horas"))),
            // Dinheiro
            rule("r\\$\\s?(\\d{1,3}(?:\\.\\d{3})+|\\d+)(?:,(\\d{1,2}))?", (m, out) -> out.add(money(m))),
            rule("\\b(\\d{1,3}(?:\\.\\d{3})+|\\d+)(?:,(\\d{2}))?\\s+reais\\b", (m, out) -> out.add(money(m))),
            rule("\\b(\\d{1,3})(?:,\\d+)?\\s?%", (m, out) -> out.add(fact(Type.PERCENT, m.group(1)))),
            // CEP
            rule("\\bcep:?\\s*(\\d{5}-?\\d{3})\\b", (m, out) -> out.add(fact(Type.ZIP, digits(m.group(1))))),
            rule("\\b\\d{5}-\\d{3}\\b", (m, out) -> out.add(fact(Type.ZIP, digits(m.group())))),
            // Prazos
            rule("\\b(?:em até|em|dentro de|com|por|após|apos|depois de|antes de)\\s+(\\d{1,3})\\s*"
                            + "(minutos?|min|horas?|h|dias? úteis|dias? uteis|dias?|semanas?)\\b",
                    (m, out) -> out.add(fact(Type.DURATION, m.group(1) + " " + unit(m.group(2))))),
            // 24 horas
            rule("\\b24\\s?h(?:oras)?\\b", (m, out) -> out.add(fact(Type.ALL_DAY, "24h"))),
            // Horários
            rule("\\bdas\\s+([01]?\\d|2[0-3])(?:h|:([0-5]\\d))?\\s*(?:às|até)\\s+([01]?\\d|2[0-3])(?:h|:([0-5]\\d))?",
                    (m, out) -> {
                        out.add(time(m.group(1), m.group(2)));
                        out.add(time(m.group(3), m.group(4)));
                    }),
            rule("\\b([01]?\\d|2[0-3]):([0-5]\\d)\\b", (m, out) -> out.add(time(m.group(1), m.group(2)))),
            rule("\\b([01]?\\d|2[0-3])h([0-5]\\d)?\\b", (m, out) -> out.add(time(m.group(1), m.group(2)))),
            rule("\\bàs\\s+([01]?\\d|2[0-3])(?:\\s?horas?)?\\b(?![:h\\d])", (m, out) -> out.add(time(m.group(1), null))),
            rule("\\b([01]?\\d|2[0-3])\\s+horas\\b", (m, out) -> out.add(time(m.group(1), null))),
            rule("\\bmeio[- ]dia\\b", (m, out) -> out.add(fact(Type.TIME, "12:00"))),
            rule("\\bmeia[- ]noite\\b", (m, out) -> out.add(fact(Type.TIME, "00:00"))),
            // Datas
            rule("\\b(\\d{1,2})/(\\d{1,2})(?:/(\\d{2,4}))?\\b", (m, out) -> date(m.group(1), m.group(2), out)),
            rule("\\b(\\d{1,2})\\s+de\\s+(janeiro|fevereiro|março|marco|abril|maio|junho|julho|agosto|setembro|outubro|novembro|dezembro)\\b",
                    (m, out) -> date(m.group(1), String.valueOf(MONTHS.get(m.group(2))), out)),
            // Telefone
            rule("(?<![\\d])(?:\\+?55\\s?)?(?:\\(?\\d{2}\\)?\\s?)?9?\\d{4}[-\\s.]?\\d{4}(?![\\d])", (m, out) -> {
                String digits = digits(m.group());
                if (digits.length() >= 8 && digits.length() <= 13) {
                    out.add(fact(Type.PHONE, digits));
                }
            }),
            // Parcelamento
            rule("\\b(\\d{1,2})\\s?x\\b", (m, out) -> out.add(fact(Type.INSTALLMENTS, m.group(1)))),
            rule("\\b(?:em até|em|até)\\s+(\\d{1,2})\\s+vezes\\b", (m, out) -> out.add(fact(Type.INSTALLMENTS, m.group(1)))),
            // Dias
            rule("\\b" + DAY_ABBR + "\\.?\\s*(?:a|à|-|–)\\s*" + DAY_ABBR + "\\b\\.?", (m, out) -> {
                out.add(fact(Type.WEEKDAY, WEEKDAYS.get(m.group(1))));
                out.add(fact(Type.WEEKDAY, WEEKDAYS.get(m.group(2))));
            }),
            rule("\\b(segunda|terça|terca|quarta|quinta|sexta)(?:-feira|\\s+feira)?s?\\b",
                    (m, out) -> out.add(fact(Type.WEEKDAY, WEEKDAYS.get(m.group(1))))),
            rule("\\b(sábado|sabado|domingo)s?\\b", (m, out) -> out.add(fact(Type.WEEKDAY, WEEKDAYS.get(m.group(1))))),
            rule("\\b" + DAY_ABBR + "\\.(?=\\s|$|,)", (m, out) -> out.add(fact(Type.WEEKDAY, WEEKDAYS.get(m.group(1))))),
            rule("\\b(?:fim|fins)\\s+de\\s+semana\\b", (m, out) -> out.add(fact(Type.WEEKEND, "fim-de-semana"))),
            rule("\\bferiados?\\b", (m, out) -> out.add(fact(Type.HOLIDAY, "feriado"))));

    private FactExtractor() {
    }

    public static List<Fact> extract(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String lower = text.toLowerCase(Locale.ROOT);
        boolean[] covered = new boolean[lower.length()];
        List<Fact> facts = new ArrayList<>();
        for (Rule rule : RULES) {
            Matcher matcher = rule.pattern().matcher(lower);
            while (matcher.find()) {
                if (overlaps(covered, matcher.start(), matcher.end())) {
                    continue;
                }
                List<Fact> found = new ArrayList<>();
                rule.toFacts().accept(matcher, found);
                if (!found.isEmpty()) {
                    facts.addAll(found);
                    for (int i = matcher.start(); i < matcher.end(); i++) {
                        covered[i] = true;
                    }
                }
            }
        }
        return List.copyOf(facts);
    }

    private static Rule rule(String regex, BiConsumer<Matcher, List<Fact>> toFacts) {
        return new Rule(Pattern.compile(regex, Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS), toFacts);
    }

    private static boolean overlaps(boolean[] covered, int start, int end) {
        for (int i = start; i < end; i++) {
            if (covered[i]) {
                return true;
            }
        }
        return false;
    }

    private static Fact fact(Type type, String value) {
        return new Fact(type, value);
    }

    private static Fact money(Matcher m) {
        long reais = Long.parseLong(m.group(1).replace(".", ""));
        String centsText = m.group(2) == null ? "0" : (m.group(2).length() == 1 ? m.group(2) + "0" : m.group(2));
        return fact(Type.MONEY, String.valueOf(reais * 100 + Long.parseLong(centsText)));
    }

    private static Fact time(String hour, String minute) {
        return fact(Type.TIME, String.format(Locale.ROOT, "%02d:%02d",
                Integer.parseInt(hour), minute == null ? 0 : Integer.parseInt(minute)));
    }

    private static void date(String day, String month, List<Fact> out) {
        int d = Integer.parseInt(day);
        int mo = Integer.parseInt(month);
        if (d >= 1 && d <= 31 && mo >= 1 && mo <= 12) {
            out.add(fact(Type.DATE, String.format(Locale.ROOT, "%02d/%02d", d, mo)));
        }
    }

    private static String unit(String raw) {
        String u = raw.replace("ú", "u");
        if (u.startsWith("min")) {
            return "minuto";
        }
        if (u.startsWith("h")) {
            return "hora";
        }
        if (u.contains("uteis")) {
            return "dia util";
        }
        if (u.startsWith("dia")) {
            return "dia";
        }
        return "semana";
    }

    private static String url(String raw) {
        String u = raw.replaceFirst("^https?://", "").replaceFirst("^www\\.", "");
        u = u.replaceAll("[.,;:!?)]+$", "");
        return u.endsWith("/") ? u.substring(0, u.length() - 1) : u;
    }

    private static String trimDots(String raw) {
        return raw.replaceAll("[.]+$", "");
    }

    private static String digits(String raw) {
        return raw.replaceAll("\\D", "");
    }
}
