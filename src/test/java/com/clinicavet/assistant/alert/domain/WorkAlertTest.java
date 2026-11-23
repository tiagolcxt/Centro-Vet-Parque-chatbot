package com.clinicavet.assistant.alert.domain;

import com.clinicavet.assistant.alert.ForgottenWork;
import com.clinicavet.assistant.shared.exception.InvalidStateTransitionException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Estados do alerta de trabalho esquecido (E3). */
class WorkAlertTest {

    private static final Instant T0 = Instant.parse("2026-11-23T12:00:00Z");
    private static final Duration GAP = Duration.ofHours(1);

    private WorkAlert raised() {
        return WorkAlert.raise(ForgottenWork.Kind.CONVERSATION_WAITING, UUID.randomUUID(), T0.minusSeconds(7200), T0);
    }

    @Test
    void escalatesOnlyAfterTheGap_andOnlyOnce() {
        WorkAlert alert = raised();

        assertThat(alert.escalateIfDue(GAP, T0.plus(Duration.ofMinutes(59)))).isFalse();
        assertThat(alert.escalateIfDue(GAP, T0.plus(GAP))).isTrue();
        assertThat(alert.isEscalated()).isTrue();
        assertThat(alert.escalateIfDue(GAP, T0.plus(Duration.ofHours(5)))).as("já escalado").isFalse();
    }

    @Test
    void acknowledgingBuysOneMoreGap_thenTheAlertComesBackWithoutIt() {
        WorkAlert alert = raised();
        UUID carla = UUID.randomUUID();

        alert.acknowledge(carla, T0.plus(Duration.ofMinutes(30)));
        assertThat(alert.escalateIfDue(GAP, T0.plus(GAP))).as("o Recebi vale um prazo").isFalse();

        assertThat(alert.escalateIfDue(GAP, T0.plus(Duration.ofMinutes(90)))).isTrue();
        assertThat(alert.isAcknowledged()).isFalse();
        assertThat(alert.acknowledgedBy()).isEmpty();
    }

    @Test
    void closedAlert_rejectsEverything() {
        WorkAlert alert = raised();
        alert.close(T0.plusSeconds(60));

        assertThat(alert.isOpen()).isFalse();
        assertThat(alert.escalateIfDue(GAP, T0.plus(Duration.ofDays(3)))).isFalse();
        assertThatThrownBy(() -> alert.acknowledge(UUID.randomUUID(), T0)).isInstanceOf(InvalidStateTransitionException.class);
        assertThatThrownBy(() -> alert.close(T0)).isInstanceOf(InvalidStateTransitionException.class);
    }
}
