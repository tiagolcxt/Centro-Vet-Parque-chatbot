package com.clinicavet.assistant.conversation.domain;

import com.clinicavet.assistant.servicerequest.domain.RequestOutcome;
import com.clinicavet.assistant.servicerequest.domain.RequestField;
import com.clinicavet.assistant.servicerequest.domain.RequestStatus;
import com.clinicavet.assistant.servicerequest.domain.RequestType;
import com.clinicavet.assistant.servicerequest.domain.ServiceRequest;
import com.clinicavet.assistant.shared.exception.DomainValidationException;
import com.clinicavet.assistant.shared.exception.InvalidStateTransitionException;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static com.clinicavet.assistant.support.Fixtures.T0;
import static com.clinicavet.assistant.support.Fixtures.newContact;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Etapa 4 no domínio: encaminhamento, prioridade (H4, H9) e desfecho da solicitação (DA-34). */
class ConversationHandoffAndPriorityTest {

    @Test
    void newConversation_startsAtP6_withoutHandoff() {
        Conversation c = Conversation.start(newContact(), T0);
        assertThat(c.clinicalPriority()).isEqualTo(ClinicalPriority.P6_RELATIONSHIP);
        assertThat(c.handoffReason()).isEmpty();
        assertThat(c.humanOfferMade()).isFalse();
    }

    @Test
    void handOff_recordsReasonAndMoment_andReturnClearsThem_withoutTouchingPriority() {
        Conversation c = Conversation.start(newContact(), T0);
        c.raisePriority(ClinicalPriority.P2_URGENT);
        c.handOff(HandoffReason.CLINICAL_PRIORITY, T0.plusSeconds(5));

        assertThat(c.status()).isEqualTo(ConversationStatus.WAITING_HUMAN);
        assertThat(c.handoffReason()).contains(HandoffReason.CLINICAL_PRIORITY);
        assertThat(c.handoffAt()).contains(T0.plusSeconds(5).truncatedTo(java.time.temporal.ChronoUnit.MICROS));

        c.returnToAssistant(T0.plusSeconds(10));
        assertThat(c.status()).isEqualTo(ConversationStatus.OPEN);
        assertThat(c.handoffReason()).isEmpty();
        assertThat(c.clinicalPriority()).as("devolver não rebaixa (H4)").isEqualTo(ClinicalPriority.P2_URGENT);
    }

    @Test
    void raisePriority_neverLowers() {
        Conversation c = Conversation.start(newContact(), T0);
        assertThat(c.raisePriority(ClinicalPriority.P1_POSSIBLE_EMERGENCY)).isTrue();
        for (ClinicalPriority lower : ClinicalPriority.values()) {
            c.raisePriority(lower);
        }
        assertThat(c.clinicalPriority()).isEqualTo(ClinicalPriority.P1_POSSIBLE_EMERGENCY);
    }

    @Test
    void staffChange_isTheOnlyWayDown() {
        Conversation c = Conversation.start(newContact(), T0);
        c.raisePriority(ClinicalPriority.P1_POSSIBLE_EMERGENCY);

        assertThat(c.changePriorityByStaff(ClinicalPriority.P4_SERVICE_REQUEST)).isEqualTo(ClinicalPriority.P1_POSSIBLE_EMERGENCY);
        assertThat(c.clinicalPriority()).isEqualTo(ClinicalPriority.P4_SERVICE_REQUEST);
    }

    @Test
    void waitingWithoutReason_isRejectedOnRestore() {
        assertThatThrownBy(() -> Conversation.restore(ConversationId.newId(), newContact(), null,
                ConversationStatus.WAITING_HUMAN, null, null, T0, T0, null, ClinicalPriority.P6_RELATIONSHIP, null, null,
                false, 0L)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void handOff_onlyFromOpen() {
        Conversation c = Conversation.start(newContact(), T0);
        c.handOff(HandoffReason.EXPLICIT_REQUEST, T0);
        assertThatThrownBy(() -> c.handOff(HandoffReason.COMPLAINT, T0)).isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void serviceRequest_outcomeRequiredToResolveOrCancel_otherNeedsNote() {
        ServiceRequest r = ServiceRequest.open(ConversationId.newId(), RequestType.HUMAN_CONTACT, T0);
        r.describe("pedido", T0);
        r.submit(Set.of(RequestField.SUMMARY), T0);
        r.startProgress(T0);

        assertThatThrownBy(() -> r.resolve(null, null, T0)).isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> r.resolve(RequestOutcome.OTHER, "  ", T0)).isInstanceOf(DomainValidationException.class);

        r.resolve(RequestOutcome.OTHER, "Resolvido por e-mail.", T0);
        assertThat(r.status()).isEqualTo(RequestStatus.RESOLVED);
        assertThat(r.outcome()).contains(RequestOutcome.OTHER);
        assertThat(r.outcomeNote()).contains("Resolvido por e-mail.");
    }
}
