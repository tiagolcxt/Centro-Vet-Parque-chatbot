package com.clinicavet.assistant.clinicknowledge.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

/** "Aberta agora" calculado só da base, no fuso da clínica. Dados fictícios. */
class OpeningHoursTest {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    // 16/11/2026 é segunda-feira.
    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 16);

    private static ClinicKnowledge base(Map<LocalDate, List<String>> holidays, Boolean twentyFour,
                                        List<String> saturday, List<String> sunday) {
        List<KnowledgeItem> items = new ArrayList<>();
        for (String day : List.of("monday", "tuesday", "wednesday", "thursday", "friday")) {
            items.add(new KnowledgeItem("hours." + day, ItemType.HOURS, List.of("08:00-12:00", "14:00-18:00")));
        }
        items.add(new KnowledgeItem("hours.saturday", ItemType.HOURS, saturday));
        items.add(new KnowledgeItem("hours.sunday", ItemType.HOURS, sunday));
        items.add(new KnowledgeItem("hours.holidays", ItemType.SPECIAL_DAYS,
                holidays == null ? null : new TreeMap<>(holidays)));
        items.add(new KnowledgeItem("hours.twenty-four-hours", ItemType.BOOLEAN, twentyFour));
        return new ClinicKnowledge(items, Set.of());
    }

    private static ClinicKnowledge regular() {
        return base(Map.of(), false, List.of("08:00-12:00"), List.of());
    }

    private static ZonedDateTime at(LocalDate date, int hour, int minute) {
        return date.atTime(hour, minute).atZone(ZONE);
    }

    @Test
    void insideAnInterval_isOpen_untilItsEnd() {
        OpeningHours.Status status = OpeningHours.at(regular(), at(MONDAY, 10, 30));

        assertThat(status.state()).isEqualTo(OpeningHours.State.OPEN);
        assertThat(status.closesAt()).contains(LocalTime.of(12, 0));
        assertThat(status.holidaysUnknown()).isFalse();
    }

    @Test
    void lunchBreak_isClosed_reopeningTheSameDay() {
        OpeningHours.Status status = OpeningHours.at(regular(), at(MONDAY, 12, 0));

        assertThat(status.state()).isEqualTo(OpeningHours.State.CLOSED);
        assertThat(status.nextOpening()).contains(at(MONDAY, 14, 0));
    }

    @Test
    void saturdayAfternoon_reopensOnMonday() {
        LocalDate saturday = MONDAY.plusDays(5);

        OpeningHours.Status status = OpeningHours.at(regular(), at(saturday, 15, 0));

        assertThat(status.state()).isEqualTo(OpeningHours.State.CLOSED);
        assertThat(status.nextOpening()).contains(at(MONDAY.plusDays(7), 8, 0));
    }

    @Test
    void holidayOverridesTheWeekday_andIsSkippedWhenSearchingTheNextOpening() {
        LocalDate tuesday = MONDAY.plusDays(1);
        ClinicKnowledge knowledge = base(Map.of(tuesday, List.of(), MONDAY, List.of("08:00-10:00")), false,
                List.of("08:00-12:00"), List.of());

        assertThat(OpeningHours.at(knowledge, at(MONDAY, 11, 0)).nextOpening())
                .contains(at(MONDAY.plusDays(2), 8, 0));
        assertThat(OpeningHours.at(knowledge, at(tuesday, 10, 0)).state()).isEqualTo(OpeningHours.State.CLOSED);
    }

    @Test
    void unknownDay_isUnknown_neverAGuess() {
        ClinicKnowledge knowledge = base(Map.of(), false, null, List.of());

        assertThat(OpeningHours.at(knowledge, at(MONDAY.plusDays(5), 9, 0)).state())
                .isEqualTo(OpeningHours.State.UNKNOWN);
        // Sexta à noite: a próxima abertura passaria pelo sábado desconhecido.
        OpeningHours.Status friday = OpeningHours.at(knowledge, at(MONDAY.plusDays(4), 20, 0));
        assertThat(friday.state()).isEqualTo(OpeningHours.State.CLOSED);
        assertThat(friday.nextOpening()).isEmpty();
    }

    @Test
    void unknownHolidays_areFlagged_andTwentyFourHoursIsAlwaysOpen() {
        assertThat(OpeningHours.at(base(null, false, List.of(), List.of()), at(MONDAY, 9, 0)).holidaysUnknown())
                .isTrue();
        OpeningHours.Status always = OpeningHours.at(base(Map.of(), true, List.of(), List.of()), at(MONDAY, 3, 0));
        assertThat(always.state()).isEqualTo(OpeningHours.State.OPEN);
        assertThat(always.twentyFourHours()).isTrue();
    }

    @Test
    void emptyBase_isUnknown() {
        assertThat(OpeningHours.at(ClinicKnowledge.empty(), at(MONDAY, 9, 0)).state())
                .isEqualTo(OpeningHours.State.UNKNOWN);
    }
}
