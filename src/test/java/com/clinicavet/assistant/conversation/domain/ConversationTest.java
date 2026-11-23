package com.clinicavet.assistant.conversation.domain;

import com.clinicavet.assistant.shared.domain.ConversationId;
import com.clinicavet.assistant.conversation.domain.HandoffReason;
import com.clinicavet.assistant.shared.domain.Channel;
import com.clinicavet.assistant.shared.domain.ContactIdentity;
import com.clinicavet.assistant.shared.exception.DomainValidationException;
import com.clinicavet.assistant.shared.exception.InvalidStateTransitionException;
import com.clinicavet.assistant.tutor.domain.TutorId;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConversationTest {

    private static final Instant T0 = Instant.parse("2026-11-11T13:00:00.123456789Z");
    private static final ContactIdentity CONTACT = new ContactIdentity(Channel.API, "contato-ficticio-1");
    /** Valores de teste explícitos: os prazos reais são pendentes (P-05). */
    private static final ConversationExpirationPolicy POLICY =
            new ConversationExpirationPolicy(Duration.ofHours(2), Duration.ofHours(48));

    @Test
    void start_opensConversationWithNormalizedTimestamps() {
        Conversation conversation = Conversation.start(CONTACT, T0);

        assertThat(conversation.status()).isEqualTo(ConversationStatus.OPEN);
        assertThat(conversation.startedAt()).isEqualTo(Instant.parse("2026-11-11T13:00:00.123456Z"));
        assertThat(conversation.lastActivityAt()).isEqualTo(conversation.startedAt());
        assertThat(conversation.closeReason()).isEmpty();
        assertThat(conversation.closedAt()).isEmpty();
        assertThat(conversation.version()).isNull();
    }

    @Nested
    class ValidTransitions {

        @Test
        void open_toWaitingHuman_andBack() {
            Conversation conversation = Conversation.start(CONTACT, T0);

            conversation.handOff(HandoffReason.EXPLICIT_REQUEST, T0.plusSeconds(60));
            assertThat(conversation.status()).isEqualTo(ConversationStatus.WAITING_HUMAN);

            conversation.returnToAssistant(T0.plusSeconds(120));
            assertThat(conversation.status()).isEqualTo(ConversationStatus.OPEN);
            assertThat(conversation.lastActivityAt()).isEqualTo(Instant.parse("2026-11-11T13:02:00.123456Z"));
        }

        @Test
        void closeByStaff_fromOpen() {
            Conversation conversation = Conversation.start(CONTACT, T0);

            conversation.closeByStaff(T0.plusSeconds(10));

            assertThat(conversation.status()).isEqualTo(ConversationStatus.CLOSED);
            assertThat(conversation.closeReason()).contains(CloseReason.CLOSED_BY_STAFF);
            assertThat(conversation.closedAt()).isPresent();
        }

        @Test
        void closeByStaff_fromWaitingHuman() {
            Conversation conversation = Conversation.start(CONTACT, T0);
            conversation.handOff(HandoffReason.EXPLICIT_REQUEST, T0.plusSeconds(10));

            conversation.closeByStaff(T0.plusSeconds(20));

            assertThat(conversation.status()).isEqualTo(ConversationStatus.CLOSED);
        }
    }

    @Nested
    class InvalidTransitions {

        @Test
        void waitForHuman_whenAlreadyWaiting_isRejected() {
            Conversation conversation = Conversation.start(CONTACT, T0);
            conversation.handOff(HandoffReason.EXPLICIT_REQUEST, T0);

            assertThatThrownBy(() -> conversation.handOff(HandoffReason.EXPLICIT_REQUEST, T0))
                    .isInstanceOf(InvalidStateTransitionException.class);
        }

        @Test
        void returnToAssistant_whenOpen_isRejected() {
            Conversation conversation = Conversation.start(CONTACT, T0);

            assertThatThrownBy(() -> conversation.returnToAssistant(T0))
                    .isInstanceOf(InvalidStateTransitionException.class);
        }

        @Test
        void closedConversation_isFinal() {
            Conversation conversation = Conversation.start(CONTACT, T0);
            conversation.closeByStaff(T0);

            assertThatThrownBy(() -> conversation.handOff(HandoffReason.EXPLICIT_REQUEST, T0)).isInstanceOf(InvalidStateTransitionException.class);
            assertThatThrownBy(() -> conversation.returnToAssistant(T0)).isInstanceOf(InvalidStateTransitionException.class);
            assertThatThrownBy(() -> conversation.closeByStaff(T0)).isInstanceOf(InvalidStateTransitionException.class);
            assertThatThrownBy(() -> conversation.registerActivity(T0)).isInstanceOf(InvalidStateTransitionException.class);
            assertThatThrownBy(() -> conversation.recordIntent(Intent.GREETING)).isInstanceOf(InvalidStateTransitionException.class);
        }
    }

    @Nested
    class Expiration {

        @Test
        void openConversation_expiresOnlyAfterOpenTimeout() {
            Conversation conversation = Conversation.start(CONTACT, T0);

            assertThat(conversation.isExpired(T0.plus(Duration.ofHours(2)), POLICY)).isFalse();
            assertThat(conversation.isExpired(T0.plus(Duration.ofHours(2)).plusSeconds(1), POLICY)).isTrue();
        }

        @Test
        void waitingHumanConversation_usesItsOwnTimeout() {
            Conversation conversation = Conversation.start(CONTACT, T0);
            conversation.handOff(HandoffReason.EXPLICIT_REQUEST, T0);

            assertThat(conversation.isExpired(T0.plus(Duration.ofHours(3)), POLICY)).isFalse();
            assertThat(conversation.isExpired(T0.plus(Duration.ofHours(49)), POLICY)).isTrue();
        }

        @Test
        void activityResetsTheTimer() {
            Conversation conversation = Conversation.start(CONTACT, T0);
            conversation.registerActivity(T0.plus(Duration.ofHours(1)));

            assertThat(conversation.isExpired(T0.plus(Duration.ofHours(2)).plusSeconds(1), POLICY)).isFalse();
        }

        @Test
        void expire_closesWithExpiredReason() {
            Conversation conversation = Conversation.start(CONTACT, T0);
            Instant later = T0.plus(Duration.ofHours(3));

            conversation.expire(later, POLICY);

            assertThat(conversation.status()).isEqualTo(ConversationStatus.CLOSED);
            assertThat(conversation.closeReason()).contains(CloseReason.EXPIRED);
        }

        @Test
        void expire_beforeTimeout_isRejected() {
            Conversation conversation = Conversation.start(CONTACT, T0);

            assertThatThrownBy(() -> conversation.expire(T0.plusSeconds(60), POLICY))
                    .isInstanceOf(InvalidStateTransitionException.class);
        }

        @Test
        void closedConversation_neverReportsExpired() {
            Conversation conversation = Conversation.start(CONTACT, T0);
            conversation.closeByStaff(T0);

            assertThat(conversation.isExpired(T0.plus(Duration.ofDays(30)), POLICY)).isFalse();
        }

        @Test
        void activityInThePast_doesNotMoveTimerBackwards() {
            Conversation conversation = Conversation.start(CONTACT, T0);
            conversation.registerActivity(T0.minusSeconds(600));

            assertThat(conversation.lastActivityAt()).isEqualTo(conversation.startedAt());
        }
    }

    @Test
    void linkTutor_neverSwitchesToAnotherTutor() {
        Conversation conversation = Conversation.start(CONTACT, T0);
        TutorId first = TutorId.newId();
        conversation.linkTutor(first);
        conversation.linkTutor(first);

        assertThat(conversation.tutorId()).contains(first);
        assertThatThrownBy(() -> conversation.linkTutor(TutorId.newId()))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    void restore_rejectsInconsistentClosedState() {
        assertThatThrownBy(() -> Conversation.restore(ConversationId.newId(), CONTACT, null,
                ConversationStatus.CLOSED, null, null, T0, T0, null, 1L))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> Conversation.restore(ConversationId.newId(), CONTACT, null,
                ConversationStatus.OPEN, null, CloseReason.EXPIRED, T0, T0, T0, 1L))
                .isInstanceOf(IllegalStateException.class);
    }

    /** Revisão pré-piloto (R1, E3.1): caso urgente esperando a equipe não some da fila por inatividade. */
    @Test
    void urgentConversationWaitingForTheTeam_neverExpires_butANonUrgentOneDoes() {
        Conversation urgent = Conversation.start(CONTACT, T0);
        urgent.raisePriority(ClinicalPriority.P1_POSSIBLE_EMERGENCY);
        urgent.handOff(HandoffReason.POSSIBLE_EMERGENCY, T0);
        assertThat(urgent.isExpired(T0.plus(Duration.ofDays(30)), POLICY)).isFalse();
        assertThatThrownBy(() -> urgent.expire(T0.plus(Duration.ofDays(30)), POLICY))
                .isInstanceOf(InvalidStateTransitionException.class);

        Conversation routine = Conversation.start(CONTACT, T0);
        routine.handOff(HandoffReason.EXPLICIT_REQUEST, T0);
        assertThat(routine.isExpired(T0.plus(Duration.ofHours(49)), POLICY)).isTrue();
    }
}
