package com.clinicavet.assistant.conversation.application;

import com.clinicavet.assistant.clinicknowledge.domain.ClinicKnowledge;
import com.clinicavet.assistant.clinicknowledge.domain.ItemType;
import com.clinicavet.assistant.clinicknowledge.domain.KnowledgeItem;
import com.clinicavet.assistant.conversation.domain.ClinicalPriority;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** DA-40: o próximo passo é ligar para a recepção, conforme a clínica esteja aberta. Dados fictícios. */
class NextStepTest {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final HandoffSettings.Replies TEXTS = new HandoffSettings.Replies("REGISTRADA", "RECEBIDA", "OFERTA",
            "EMERGENCIA", "ORIENTACAO", "PRAZO", "EXPLICACAO", "EXPLICACAO-2", "PEDIDO",
            "Ligue agora: {phone}.", "Fechada; abre {when}. Ligue: {phone}.", "Ligue no horário: {phone}.",
            "Fechada agora.", "Procure: {referral}", null);

    private static HandoffSettings settings() {
        Map<ClinicalPriority, Duration> deadlines = new EnumMap<>(ClinicalPriority.class);
        for (ClinicalPriority p : ClinicalPriority.values()) {
            deadlines.put(p, Duration.ofHours(1));
        }
        return new HandoffSettings(deadlines, TEXTS);
    }

    private static NextStep at(LocalDateTime local, String phone, String referral, String approved) {
        List<KnowledgeItem> items = new ArrayList<>();
        for (String day : List.of("monday", "tuesday", "wednesday", "thursday", "friday")) {
            items.add(new KnowledgeItem("hours." + day, ItemType.HOURS, List.of("08:00-18:00")));
        }
        items.add(new KnowledgeItem("hours.saturday", ItemType.HOURS, null));
        items.add(new KnowledgeItem("hours.sunday", ItemType.HOURS, List.of()));
        items.add(new KnowledgeItem("identification.phone", ItemType.PHONE, phone));
        items.add(new KnowledgeItem("emergency.after-hours-referral", ItemType.TEXT, referral));
        items.add(new KnowledgeItem("approved-texts.direct-contact", ItemType.TEXT, approved));
        Clock clock = Clock.fixed(local.atZone(ZONE).toInstant(), ZONE);
        return new NextStep(new ClinicKnowledge(items, Set.of()), settings(), clock);
    }

    // 16/11/2026 é segunda-feira.
    private static final LocalDateTime MONDAY_10H = LocalDateTime.of(2026, 11, 16, 10, 0);

    @Test
    void open_callNow() {
        assertThat(at(MONDAY_10H, "(11) 3000-1000", null, null).text()).contains("Ligue agora: (11) 3000-1000.");
    }

    @Test
    void closed_saysWhenItOpens() {
        assertThat(at(MONDAY_10H.withHour(20), "(11) 3000-1000", null, null).text())
                .contains("Fechada; abre terça-feira, 17/11, às 08:00. Ligue: (11) 3000-1000.");
    }

    @Test
    void unknownHoursOnTheWay_callDuringOpeningHours() {
        // Sexta à noite: o sábado não está cadastrado.
        assertThat(at(MONDAY_10H.plusDays(4).withHour(20), "(11) 3000-1000", null, null).text())
                .contains("Ligue no horário: (11) 3000-1000.");
    }

    @Test
    void noPhone_noNextStep_neverAnInventedNumber() {
        assertThat(at(MONDAY_10H, null, null, null).text()).isEmpty();
    }

    @Test
    void approvedText_winsOverTheBuiltOne() {
        assertThat(at(MONDAY_10H, "(11) 3000-1000", null, "Texto aprovado.").text()).contains("Texto aprovado.");
    }

    @Test
    void emergencyWhenClosed_withAndWithoutTheReferral() {
        assertThat(at(MONDAY_10H, "x", "Hospital Fictício", null).emergencyWhenClosed()).isEmpty();
        assertThat(at(MONDAY_10H.withHour(22), "x", "Hospital Fictício, (11) 3999-0000", null).emergencyWhenClosed())
                .contains("Fechada agora. Procure: Hospital Fictício, (11) 3999-0000");
        assertThat(at(MONDAY_10H.withHour(22), "x", null, null).emergencyWhenClosed()).contains("Fechada agora.");
    }
}
