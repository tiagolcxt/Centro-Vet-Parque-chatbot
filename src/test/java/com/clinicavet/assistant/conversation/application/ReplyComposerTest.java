package com.clinicavet.assistant.conversation.application;

import com.clinicavet.assistant.clinicknowledge.domain.ClinicKnowledge;
import com.clinicavet.assistant.conversation.domain.ClinicalPriority;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;

import static com.clinicavet.assistant.conversation.domain.ClinicalPriority.P1_POSSIBLE_EMERGENCY;
import static com.clinicavet.assistant.conversation.domain.ClinicalPriority.P2_URGENT;
import static com.clinicavet.assistant.conversation.domain.ClinicalPriority.P5_ADMINISTRATIVE;
import static com.clinicavet.assistant.conversation.domain.ClinicalPriority.P6_RELATIONSHIP;
import static org.assertj.core.api.Assertions.assertThat;

/** H6–H8, CT-01, CT-02, CT-07, CT-15. */
class ReplyComposerTest {

    private static final HandoffSettings.Replies TEXTS = new HandoffSettings.Replies("REGISTRADA", "RECEBIDA", "OFERTA",
            "EMERGENCIA", "ORIENTACAO-CURTA", "PRAZO-VENCIDO");

    private static HandoffSettings settings() {
        Map<ClinicalPriority, Duration> deadlines = new EnumMap<>(ClinicalPriority.class);
        for (ClinicalPriority p : ClinicalPriority.values()) {
            deadlines.put(p, Duration.ofHours(1));
        }
        return new HandoffSettings(deadlines, TEXTS);
    }

    private final ReplyComposer composer = new ReplyComposer(settings(), ClinicKnowledge.empty());

    private ReplyComposer.Plan plan(ClinicalPriority level, boolean emergency, boolean explicit, boolean handoff, boolean offer) {
        return new ReplyComposer.Plan(level, emergency, explicit, handoff, offer);
    }

    @Test
    void p1_emergencyGuidanceFirstAndAlone_thenRegistration_modelTextDropped() {
        String reply = composer.composeValid(plan(P1_POSSIBLE_EMERGENCY, false, false, true, false), "A consulta custa R$ 150.");

        assertThat(reply).isEqualTo("EMERGENCIA\n\nREGISTRADA").doesNotContain("150");
    }

    @Test
    void possibleEmergencyFlag_sameAsP1() {
        assertThat(composer.composeValid(plan(P5_ADMINISTRATIVE, true, false, true, false), "texto")).startsWith("EMERGENCIA");
    }

    @Test
    void p2_shortGuidanceComesBeforeEverythingElse() {
        assertThat(composer.composeValid(plan(P2_URGENT, false, false, true, false), "Entendo sua preocupação."))
                .isEqualTo("ORIENTACAO-CURTA\n\nEntendo sua preocupação.\n\nREGISTRADA");
    }

    @Test
    void explicitRequest_onlyTheRegistration_noModelText() {
        assertThat(composer.composeValid(plan(P6_RELATIONSHIP, false, true, true, false), "Antes, posso te ajudar com...?"))
                .isEqualTo("REGISTRADA");
    }

    @Test
    void ordinaryTurn_modelTextOnly_orWithOffer() {
        assertThat(composer.composeValid(plan(P5_ADMINISTRATIVE, false, false, false, false), "Abrimos às 8h.")).isEqualTo("Abrimos às 8h.");
        assertThat(composer.composeValid(plan(P5_ADMINISTRATIVE, false, false, false, true), "Abrimos às 8h."))
                .isEqualTo("Abrimos às 8h.\n\nOFERTA");
    }

    @Test
    void failure_inUrgentConversation_includesShortGuidance() {
        assertThat(composer.composeFailure(plan(P6_RELATIONSHIP, false, false, false, false), "SEGURA", P2_URGENT))
                .isEqualTo("ORIENTACAO-CURTA\n\nSEGURA");
        assertThat(composer.composeFailure(plan(P6_RELATIONSHIP, false, false, false, false), "SEGURA", P6_RELATIONSHIP))
                .isEqualTo("SEGURA");
    }

    @Test
    void failure_withHandoff_isTheHandoffReply() {
        assertThat(composer.composeFailure(plan(P6_RELATIONSHIP, false, true, true, false), "SEGURA", P6_RELATIONSHIP))
                .isEqualTo("REGISTRADA");
        assertThat(composer.composeFailure(plan(P1_POSSIBLE_EMERGENCY, false, false, true, false), "SEGURA", P6_RELATIONSHIP))
                .isEqualTo("EMERGENCIA\n\nREGISTRADA");
    }

    @Test
    void waiting_confirmation_emergencyFirst_failSafe_overdue() {
        assertThat(composer.composeWaiting(P6_RELATIONSHIP, false, false, false)).isEqualTo("RECEBIDA");
        assertThat(composer.composeWaiting(P6_RELATIONSHIP, true, false, false)).isEqualTo("EMERGENCIA\n\nRECEBIDA");
        assertThat(composer.composeWaiting(P2_URGENT, false, false, false)).isEqualTo("ORIENTACAO-CURTA\n\nRECEBIDA");
        assertThat(composer.composeWaiting(P6_RELATIONSHIP, false, true, false)).isEqualTo("RECEBIDA\n\nORIENTACAO-CURTA");
        assertThat(composer.composeWaiting(P6_RELATIONSHIP, false, false, true)).isEqualTo("RECEBIDA\n\nPRAZO-VENCIDO");
    }
}
