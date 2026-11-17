package com.clinicavet.assistant.servicerequest.domain;

import com.clinicavet.assistant.servicerequest.domain.RequestOutcome;
import com.clinicavet.assistant.conversation.domain.ConversationId;
import com.clinicavet.assistant.shared.exception.InvalidStateTransitionException;
import com.clinicavet.assistant.shared.exception.MissingRequiredFieldsException;
import com.clinicavet.assistant.tutor.domain.PetId;
import com.clinicavet.assistant.tutor.domain.TutorId;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServiceRequestTest {

    private static final Instant T0 = Instant.parse("2026-11-11T13:00:00Z");
    /** Conjunto de teste: os obrigatórios reais de cada tipo são pendentes (P-13). */
    private static final Set<RequestField> REQUIRED = EnumSet.of(RequestField.SUMMARY, RequestField.PET);

    private ServiceRequest collecting() {
        return ServiceRequest.open(ConversationId.newId(), RequestType.APPOINTMENT, T0);
    }

    private ServiceRequest pending() {
        ServiceRequest request = collecting();
        request.submit(Set.of(), T0.plusSeconds(1));
        return request;
    }

    @Test
    void open_startsCollecting() {
        ServiceRequest request = collecting();

        assertThat(request.status()).isEqualTo(RequestStatus.COLLECTING);
        assertThat(request.createdAt()).isEqualTo(request.updatedAt());
    }

    @Nested
    class Submit {

        @Test
        void isRejected_listingMissingFields() {
            ServiceRequest request = collecting();
            request.describe("Consulta de rotina (fictícia)", T0);

            assertThat(request.missingFields(REQUIRED)).containsExactly(RequestField.PET);
            assertThatThrownBy(() -> request.submit(REQUIRED, T0))
                    .isInstanceOf(MissingRequiredFieldsException.class)
                    .satisfies(ex -> assertThat(((MissingRequiredFieldsException) ex).missingFields())
                            .containsExactly("PET"));
            assertThat(request.status()).isEqualTo(RequestStatus.COLLECTING);
        }

        @Test
        void succeeds_whenRequiredFieldsPresent() {
            ServiceRequest request = collecting();
            request.describe("Consulta de rotina (fictícia)", T0);
            request.attachPet(PetId.newId(), T0);
            request.attachTutor(TutorId.newId(), T0);

            request.submit(REQUIRED, T0.plusSeconds(5));

            assertThat(request.status()).isEqualTo(RequestStatus.PENDING);
            assertThat(request.updatedAt()).isEqualTo(T0.plusSeconds(5));
        }

        @Test
        void blankSummary_doesNotCountAsPresent() {
            ServiceRequest request = collecting();
            request.describe("   ", T0);

            assertThat(request.missingFields(EnumSet.of(RequestField.SUMMARY))).containsExactly(RequestField.SUMMARY);
        }

        @Test
        void outsideCollecting_isRejected() {
            ServiceRequest request = pending();

            assertThatThrownBy(() -> request.submit(Set.of(), T0)).isInstanceOf(InvalidStateTransitionException.class);
        }
    }

    @Nested
    class Lifecycle {

        @Test
        void happyPath_collectingToResolved() {
            ServiceRequest request = pending();

            request.startProgress(T0.plusSeconds(10));
            request.resolve(RequestOutcome.SCHEDULED_BY_STAFF, null, T0.plusSeconds(20));

            assertThat(request.status()).isEqualTo(RequestStatus.RESOLVED);
        }

        @Test
        void pending_cannotJumpToResolved() {
            ServiceRequest request = pending();

            assertThatThrownBy(() -> request.resolve(RequestOutcome.SCHEDULED_BY_STAFF, null, T0)).isInstanceOf(InvalidStateTransitionException.class);
        }

        @Test
        void collecting_cannotStartProgress() {
            assertThatThrownBy(() -> collecting().startProgress(T0)).isInstanceOf(InvalidStateTransitionException.class);
        }

        @Test
        void cancel_allowedFromEveryNonFinalStatus() {
            ServiceRequest fromCollecting = collecting();
            ServiceRequest fromPending = pending();
            ServiceRequest fromInProgress = pending();
            fromInProgress.startProgress(T0);

            fromCollecting.cancel(RequestOutcome.CUSTOMER_GAVE_UP, null, T0);
            fromPending.cancel(RequestOutcome.CUSTOMER_GAVE_UP, null, T0);
            fromInProgress.cancel(RequestOutcome.CUSTOMER_GAVE_UP, null, T0);

            assertThat(fromCollecting.status()).isEqualTo(RequestStatus.CANCELLED);
            assertThat(fromPending.status()).isEqualTo(RequestStatus.CANCELLED);
            assertThat(fromInProgress.status()).isEqualTo(RequestStatus.CANCELLED);
        }

        @Test
        void finalStatuses_rejectEverything() {
            ServiceRequest resolved = pending();
            resolved.startProgress(T0);
            resolved.resolve(RequestOutcome.SCHEDULED_BY_STAFF, null, T0);
            ServiceRequest cancelled = collecting();
            cancelled.cancel(RequestOutcome.CUSTOMER_GAVE_UP, null, T0);

            for (ServiceRequest request : new ServiceRequest[]{resolved, cancelled}) {
                assertThatThrownBy(() -> request.cancel(RequestOutcome.CUSTOMER_GAVE_UP, null, T0)).isInstanceOf(InvalidStateTransitionException.class);
                assertThatThrownBy(() -> request.describe("x", T0)).isInstanceOf(InvalidStateTransitionException.class);
                assertThatThrownBy(() -> request.attachPet(PetId.newId(), T0)).isInstanceOf(InvalidStateTransitionException.class);
                assertThatThrownBy(() -> request.startProgress(T0)).isInstanceOf(InvalidStateTransitionException.class);
            }
        }
    }

    @Nested
    class AppointmentDetails {

        @Test
        void reasonAndPeriod_areKept_andReasonIsARequirableField() {
            ServiceRequest request = collecting();

            request.describeReason("  vacina anual ", T0.plusSeconds(1));
            request.describePreferredPeriod("de manhã", T0.plusSeconds(1));
            request.describeReason("  ", T0.plusSeconds(2));   // vazio não apaga

            assertThat(request.reason()).contains("vacina anual");
            assertThat(request.preferredPeriod()).contains("de manhã");
            assertThat(request.missingFields(EnumSet.of(RequestField.REASON))).isEmpty();
            assertThat(collecting().missingFields(EnumSet.of(RequestField.REASON))).containsExactly(RequestField.REASON);
        }

        @Test
        void onlyAppointment_hasReasonAndPeriod() {
            ServiceRequest info = ServiceRequest.open(ConversationId.newId(), RequestType.INFORMATION, T0);

            assertThatThrownBy(() -> info.describeReason("vacina", T0))
                    .isInstanceOf(com.clinicavet.assistant.shared.exception.DomainValidationException.class);
            assertThatThrownBy(() -> info.describePreferredPeriod("manhã", T0))
                    .isInstanceOf(com.clinicavet.assistant.shared.exception.DomainValidationException.class);
        }

        @Test
        void reasonOverTheLimit_isRejected() {
            assertThatThrownBy(() -> collecting().describeReason("a".repeat(ServiceRequest.MAX_REASON_LENGTH + 1), T0))
                    .isInstanceOf(com.clinicavet.assistant.shared.exception.DomainValidationException.class);
        }
    }
}
