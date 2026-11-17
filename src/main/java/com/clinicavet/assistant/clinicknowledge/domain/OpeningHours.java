package com.clinicavet.assistant.clinicknowledge.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * A clínica está aberta agora? Calculado só a partir da base (horário por dia, dias especiais, 24 horas), no fuso da
 * clínica. É a base para saber se há alguém na recepção e para o próximo passo ao tutor ("ligue para a recepção").
 * <p>
 * Regras:
 * <ul>
 *   <li>{@code hours.twenty-four-hours = true}: sempre aberta;</li>
 *   <li>a data em {@code hours.holidays} manda sobre o dia da semana ({@code []} = fechado);</li>
 *   <li>horário do dia DESCONHECIDO: {@link State#UNKNOWN}, nunca um palpite;</li>
 *   <li>{@code hours.holidays} DESCONHECIDO: o cálculo segue o dia da semana, mas o resultado avisa
 *       ({@link Status#holidaysUnknown()}) que pode ser feriado.</li>
 * </ul>
 * A próxima abertura é procurada nos 7 dias seguintes; um dia DESCONHECIDO no caminho interrompe a busca.
 */
public final class OpeningHours {

    public enum State { OPEN, CLOSED, UNKNOWN }

    /**
     * @param closesAt    quando {@code OPEN} (e não 24 horas): fim do intervalo atual
     * @param nextOpening quando {@code CLOSED}: próxima abertura conhecida
     */
    public record Status(State state, boolean twentyFourHours, Optional<LocalTime> closesAt,
                         Optional<ZonedDateTime> nextOpening, boolean holidaysUnknown) {

        public static Status unknown(boolean holidaysUnknown) {
            return new Status(State.UNKNOWN, false, Optional.empty(), Optional.empty(), holidaysUnknown);
        }
    }

    static final List<String> DAY_KEYS = List.of("hours.monday", "hours.tuesday", "hours.wednesday",
            "hours.thursday", "hours.friday", "hours.saturday", "hours.sunday");
    private static final int SEARCH_DAYS = 7;

    private OpeningHours() {
    }

    public static Status at(ClinicKnowledge knowledge, ZonedDateTime now) {
        boolean holidaysUnknown = knowledge.item("hours.holidays").map(i -> !i.isKnown()).orElse(true);
        if (knowledge.item("hours.twenty-four-hours").map(i -> Boolean.TRUE.equals(i.value())).orElse(false)) {
            return new Status(State.OPEN, true, Optional.empty(), Optional.empty(), holidaysUnknown);
        }
        Optional<List<String>> today = intervalsOn(knowledge, now.toLocalDate());
        if (today.isEmpty()) {
            return Status.unknown(holidaysUnknown);
        }
        LocalTime time = now.toLocalTime();
        for (String interval : today.get()) {
            LocalTime start = LocalTime.parse(interval.substring(0, 5));
            LocalTime end = LocalTime.parse(interval.substring(6));
            if (!time.isBefore(start) && time.isBefore(end)) {
                return new Status(State.OPEN, false, Optional.of(end), Optional.empty(), holidaysUnknown);
            }
        }
        // Fechada agora: próxima abertura, hoje mais tarde ou nos próximos dias.
        for (int offset = 0; offset <= SEARCH_DAYS; offset++) {
            LocalDate date = now.toLocalDate().plusDays(offset);
            Optional<List<String>> intervals = offset == 0 ? today : intervalsOn(knowledge, date);
            if (intervals.isEmpty()) {
                return new Status(State.CLOSED, false, Optional.empty(), Optional.empty(), holidaysUnknown);
            }
            for (String interval : intervals.get()) {
                LocalTime start = LocalTime.parse(interval.substring(0, 5));
                if (offset > 0 || start.isAfter(time)) {
                    ZonedDateTime next = LocalDateTime.of(date, start).atZone(now.getZone());
                    return new Status(State.CLOSED, false, Optional.empty(), Optional.of(next), holidaysUnknown);
                }
            }
        }
        return new Status(State.CLOSED, false, Optional.empty(), Optional.empty(), holidaysUnknown);
    }

    /** Intervalos do dia: dia especial, se houver; senão o dia da semana. Vazio = DESCONHECIDO. */
    static Optional<List<String>> intervalsOn(ClinicKnowledge knowledge, LocalDate date) {
        Optional<List<String>> special = knowledge.item("hours.holidays")
                .map(KnowledgeItem::specialDays)
                .map(days -> days.get(date));
        if (special.isPresent()) {
            return special;
        }
        String key = DAY_KEYS.get(date.getDayOfWeek().getValue() - 1);
        return knowledge.item(key).filter(KnowledgeItem::isKnown).map(KnowledgeItem::intervals);
    }

    /** Nome do dia em português ({@code segunda-feira}...). */
    public static String dayName(DayOfWeek day) {
        return day.getDisplayName(java.time.format.TextStyle.FULL, Locale.of("pt", "BR"));
    }
}
