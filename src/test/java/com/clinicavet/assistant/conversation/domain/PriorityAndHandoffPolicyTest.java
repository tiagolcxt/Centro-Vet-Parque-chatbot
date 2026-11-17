package com.clinicavet.assistant.conversation.domain;

import com.clinicavet.assistant.conversation.domain.HandoffPolicy.Unproductive;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Optional;
import java.util.Set;

import static com.clinicavet.assistant.conversation.domain.ClinicalPriority.P1_POSSIBLE_EMERGENCY;
import static com.clinicavet.assistant.conversation.domain.ClinicalPriority.P2_URGENT;
import static com.clinicavet.assistant.conversation.domain.ClinicalPriority.P3_CLINICAL_FOLLOW_UP;
import static com.clinicavet.assistant.conversation.domain.ClinicalPriority.P4_SERVICE_REQUEST;
import static com.clinicavet.assistant.conversation.domain.ClinicalPriority.P5_ADMINISTRATIVE;
import static com.clinicavet.assistant.conversation.domain.ClinicalPriority.P6_RELATIONSHIP;
import static org.assertj.core.api.Assertions.assertThat;

class PriorityAndHandoffPolicyTest {

    /** ADR-0018, H3–H8. */
    @Nested
    class Priority {

        private PriorityPolicy.Inputs in(ClinicalPriority model, Set<Intent> intents, Set<TurnSignal> signals,
                                         boolean emergency, boolean uncertainty, ClinicalPriority alert) {
            return new PriorityPolicy.Inputs(model, intents, signals, emergency, uncertainty, Optional.ofNullable(alert));
        }

        @Test
        void possibleEmergency_floorsAtP1_evenIfTheModelSaysP5() {
            var result = PriorityPolicy.assess(P6_RELATIONSHIP, in(P5_ADMINISTRATIVE, Set.of(), Set.of(), true, false, null));
            assertThat(result).isEqualTo(new PriorityPolicy.Assessment(P1_POSSIBLE_EMERGENCY, P1_POSSIBLE_EMERGENCY,
                    PrioritySource.BACKEND_FLOOR));
        }

        @Test
        void medicalConcern_worsening_postProcedure_uncertainty_floorAtP2() {
            assertThat(PriorityPolicy.assess(P6_RELATIONSHIP, in(P6_RELATIONSHIP, Set.of(Intent.MEDICAL_CONCERN), Set.of(), false, false, null)).effective()).isEqualTo(P2_URGENT);
            assertThat(PriorityPolicy.assess(P6_RELATIONSHIP, in(null, Set.of(), Set.of(TurnSignal.WORSENING), false, false, null)).effective()).isEqualTo(P2_URGENT);
            assertThat(PriorityPolicy.assess(P6_RELATIONSHIP, in(null, Set.of(), Set.of(TurnSignal.POST_PROCEDURE_CHANGE), false, false, null)).effective()).isEqualTo(P2_URGENT);
            assertThat(PriorityPolicy.assess(P6_RELATIONSHIP, in(null, Set.of(), Set.of(), false, true, null)).effective()).isEqualTo(P2_URGENT);
        }

        @Test
        void secondaryMedicalIntent_alsoFloors() {
            assertThat(PriorityPolicy.assess(P6_RELATIONSHIP,
                    in(P5_ADMINISTRATIVE, Set.of(Intent.PRICE_INQUIRY, Intent.MEDICAL_CONCERN), Set.of(), false, false, null))
                    .effective()).isEqualTo(P2_URGENT);
        }

        @Test
        void treatmentFollowUp_floorsAtP3() {
            assertThat(PriorityPolicy.assess(P6_RELATIONSHIP,
                    in(P6_RELATIONSHIP, Set.of(Intent.TREATMENT_FOLLOW_UP), Set.of(), false, false, null)).effective())
                    .isEqualTo(P3_CLINICAL_FOLLOW_UP);
        }

        @Test
        void alertExpression_floorsAtItsLevel() {
            assertThat(PriorityPolicy.assess(P6_RELATIONSHIP, PriorityPolicy.Inputs.deterministicOnly(Optional.of(P1_POSSIBLE_EMERGENCY))).effective())
                    .isEqualTo(P1_POSSIBLE_EMERGENCY);
        }

        @Test
        void modelMoreUrgentThanTheFloor_winsAsModel() {
            var result = PriorityPolicy.assess(P6_RELATIONSHIP, in(P1_POSSIBLE_EMERGENCY, Set.of(Intent.MEDICAL_CONCERN), Set.of(), false, false, null));
            assertThat(result.effective()).isEqualTo(P1_POSSIBLE_EMERGENCY);
            assertThat(result.source()).isEqualTo(PrioritySource.MODEL);
        }

        /** H4: nunca rebaixa automaticamente. */
        @ParameterizedTest
        @EnumSource(ClinicalPriority.class)
        void neverLowersTheCurrentLevel(ClinicalPriority modelSuggestion) {
            var result = PriorityPolicy.assess(P1_POSSIBLE_EMERGENCY, in(modelSuggestion, Set.of(), Set.of(), false, false, null));
            assertThat(result.effective()).isEqualTo(P1_POSSIBLE_EMERGENCY);
            assertThat(result.source()).isEqualTo(PrioritySource.CURRENT_LEVEL);
        }

        @Test
        void turnLevel_isReportedSeparatelyFromTheConversationLevel() {
            var result = PriorityPolicy.assess(P2_URGENT, in(P5_ADMINISTRATIVE, Set.of(Intent.PRICE_INQUIRY), Set.of(), false, false, null));
            assertThat(result.turnLevel()).isEqualTo(P5_ADMINISTRATIVE);
            assertThat(result.effective()).isEqualTo(P2_URGENT);
        }

        @Test
        void aiFailure_keepsTheLevel_noSuggestion() {
            var result = PriorityPolicy.assess(P2_URGENT, PriorityPolicy.Inputs.deterministicOnly(Optional.empty()));
            assertThat(result.effective()).isEqualTo(P2_URGENT);
            assertThat(result.turnLevel()).isEqualTo(P6_RELATIONSHIP);
        }

        @Test
        void commercialDataIsNotAnInput() {
            assertThat(PriorityPolicy.Inputs.class.getRecordComponents()).extracting(c -> c.getName())
                    .containsExactly("modelSuggestion", "intents", "signals", "possibleEmergency", "clinicalUncertainty",
                            "alertFloor");
        }
    }

    /** ADR-0007, item 2 (cada condição isolada) e DA-25, DA-31, DA-33. */
    @Nested
    class Handoff {

        private HandoffPolicy.Inputs base() {
            return new HandoffPolicy.Inputs(false, Set.of(Intent.OTHER), false, false, Set.of(), P6_RELATIONSHIP,
                    Unproductive.NONE, false, false);
        }

        private Optional<HandoffReason> reason(HandoffPolicy.Inputs in) {
            return HandoffPolicy.decide(in).handoff();
        }

        @Test
        void nothingSpecial_noHandoffNoOffer() {
            assertThat(HandoffPolicy.decide(base())).isEqualTo(HandoffPolicy.Decision.NONE);
        }

        @Test
        void explicitRequestFromTheDetector() {
            var in = base();
            assertThat(reason(new HandoffPolicy.Inputs(true, in.intents(), false, false, Set.of(), P6_RELATIONSHIP,
                    Unproductive.NONE, false, false))).contains(HandoffReason.EXPLICIT_REQUEST);
        }

        @Test
        void humanRequestOrComplaintIntent_evenAsSecondary() {
            assertThat(reason(new HandoffPolicy.Inputs(false, Set.of(Intent.PRICE_INQUIRY, Intent.HUMAN_REQUEST), false, false,
                    Set.of(), P6_RELATIONSHIP, Unproductive.NONE, false, false))).contains(HandoffReason.EXPLICIT_REQUEST);
            assertThat(reason(new HandoffPolicy.Inputs(false, Set.of(Intent.COMPLAINT), false, false, Set.of(),
                    P6_RELATIONSHIP, Unproductive.NONE, false, false))).contains(HandoffReason.COMPLAINT);
        }

        @Test
        void clinicalIntent_withoutPriorityFloor() {
            assertThat(reason(new HandoffPolicy.Inputs(false, Set.of(Intent.MEDICAL_CONCERN), false, false, Set.of(),
                    P6_RELATIONSHIP, Unproductive.NONE, false, false))).contains(HandoffReason.CLINICAL);
        }

        @Test
        void modelSignalAndPossibleEmergency() {
            assertThat(reason(new HandoffPolicy.Inputs(false, Set.of(Intent.OTHER), true, false, Set.of(),
                    P6_RELATIONSHIP, Unproductive.NONE, false, false))).contains(HandoffReason.MODEL_SIGNAL);
            assertThat(reason(new HandoffPolicy.Inputs(false, Set.of(Intent.OTHER), false, true, Set.of(),
                    P6_RELATIONSHIP, Unproductive.NONE, false, false))).contains(HandoffReason.POSSIBLE_EMERGENCY);
        }

        @Test
        void clinicalPriorityP1toP3_handsOff_P4toP6_doNot() {
            for (ClinicalPriority level : ClinicalPriority.values()) {
                Optional<HandoffReason> result = reason(new HandoffPolicy.Inputs(false, Set.of(Intent.OTHER), false, false,
                        Set.of(), level, Unproductive.NONE, false, false));
                boolean expected = level.ordinal() <= P3_CLINICAL_FOLLOW_UP.ordinal();
                assertThat(result.isPresent()).as(level.name()).isEqualTo(expected);
            }
            assertThat(reason(new HandoffPolicy.Inputs(false, Set.of(), false, false, Set.of(), P1_POSSIBLE_EMERGENCY,
                    Unproductive.NONE, false, false))).contains(HandoffReason.POSSIBLE_EMERGENCY);
            assertThat(reason(new HandoffPolicy.Inputs(false, Set.of(), false, false, Set.of(), P4_SERVICE_REQUEST,
                    Unproductive.NONE, false, false))).isEmpty();
        }

        @Test
        void dissatisfactionAndStaffDecision() {
            assertThat(reason(new HandoffPolicy.Inputs(false, Set.of(Intent.OTHER), false, false,
                    Set.of(TurnSignal.DISSATISFACTION), P6_RELATIONSHIP, Unproductive.NONE, false, false)))
                    .contains(HandoffReason.DISSATISFACTION);
            assertThat(reason(new HandoffPolicy.Inputs(false, Set.of(Intent.OTHER), false, false,
                    Set.of(TurnSignal.NEEDS_STAFF_DECISION), P6_RELATIONSHIP, Unproductive.NONE, false, false)))
                    .contains(HandoffReason.STAFF_DECISION);
        }

        @Test
        void unproductive_offerThenHandoff() {
            var offer = HandoffPolicy.decide(new HandoffPolicy.Inputs(false, Set.of(), false, false, Set.of(),
                    P6_RELATIONSHIP, Unproductive.OFFER, false, false));
            assertThat(offer).isEqualTo(new HandoffPolicy.Decision(Optional.empty(), true));
            assertThat(reason(new HandoffPolicy.Inputs(false, Set.of(), false, false, Set.of(), P6_RELATIONSHIP,
                    Unproductive.HANDOFF, false, false))).contains(HandoffReason.UNPRODUCTIVE_CONVERSATION);
        }

        @Test
        void emotionalSignals_onlyOffer_andOnlyOncePerConversation() {
            var first = HandoffPolicy.decide(new HandoffPolicy.Inputs(false, Set.of(Intent.OTHER), false, false,
                    Set.of(TurnSignal.EMOTIONAL_DISTRESS), P6_RELATIONSHIP, Unproductive.NONE, false, false));
            var again = HandoffPolicy.decide(new HandoffPolicy.Inputs(false, Set.of(Intent.OTHER), false, false,
                    Set.of(TurnSignal.CONFUSION), P6_RELATIONSHIP, Unproductive.NONE, false, true));
            assertThat(first).isEqualTo(new HandoffPolicy.Decision(Optional.empty(), true));
            assertThat(again).isEqualTo(HandoffPolicy.Decision.NONE);
        }

        @Test
        void failureInvolvingHandoff() {
            assertThat(reason(new HandoffPolicy.Inputs(false, Set.of(), false, false, Set.of(), P6_RELATIONSHIP,
                    Unproductive.NONE, true, false))).contains(HandoffReason.TURN_FAILURE);
        }

        @Test
        void mostSevereReasonWins() {
            assertThat(reason(new HandoffPolicy.Inputs(true, Set.of(Intent.COMPLAINT), true, true,
                    Set.of(TurnSignal.DISSATISFACTION), P2_URGENT, Unproductive.HANDOFF, true, false)))
                    .contains(HandoffReason.POSSIBLE_EMERGENCY);
        }
    }
}
