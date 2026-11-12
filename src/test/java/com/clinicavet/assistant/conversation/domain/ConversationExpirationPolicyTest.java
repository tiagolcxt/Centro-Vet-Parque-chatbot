package com.clinicavet.assistant.conversation.domain;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConversationExpirationPolicyTest {

    @Test
    void requiresPositiveTimeouts_thereIsNoDefault() {
        assertThatThrownBy(() -> new ConversationExpirationPolicy(null, Duration.ofHours(1)))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ConversationExpirationPolicy(Duration.ZERO, Duration.ofHours(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ConversationExpirationPolicy(Duration.ofHours(1), Duration.ofMinutes(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void timeoutDependsOnStatus() {
        var policy = new ConversationExpirationPolicy(Duration.ofHours(1), Duration.ofHours(24));

        assertThat(policy.timeoutFor(ConversationStatus.OPEN)).isEqualTo(Duration.ofHours(1));
        assertThat(policy.timeoutFor(ConversationStatus.WAITING_HUMAN)).isEqualTo(Duration.ofHours(24));
        assertThatThrownBy(() -> policy.timeoutFor(ConversationStatus.CLOSED))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
