package com.clinicavet.assistant.conversation.domain;

import com.clinicavet.assistant.shared.exception.DomainValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MessageAndIntentTest {

    private static final ConversationId CONVERSATION = ConversationId.newId();
    private static final Instant T0 = Instant.parse("2026-11-11T13:00:00Z");

    @Test
    void customerMessage_hasNoIntent() {
        Message message = Message.fromCustomer(CONVERSATION, "  Oi, tudo bem?  ", T0);

        assertThat(message.author()).isEqualTo(MessageAuthor.CUSTOMER);
        assertThat(message.content()).isEqualTo("Oi, tudo bem?");
        assertThat(message.intent()).isEmpty();
    }

    @Test
    void assistantMessage_recordsIntent() {
        Message message = Message.fromAssistant(CONVERSATION, "Olá! Como posso ajudar?", Intent.GREETING, T0);

        assertThat(message.intent()).contains(Intent.GREETING);
    }

    @Test
    void customerMessage_withIntent_isRejected() {
        assertThatThrownBy(() -> Message.restore(MessageId.newId(), CONVERSATION, MessageAuthor.CUSTOMER,
                "oi", Intent.GREETING, T0)).isInstanceOf(DomainValidationException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\n\t"})
    void blankContent_isRejected(String content) {
        assertThatThrownBy(() -> Message.fromCustomer(CONVERSATION, content, T0))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    void contentAboveStorageLimit_isRejected() {
        String tooLong = "a".repeat(Message.MAX_CONTENT_LENGTH + 1);

        assertThatThrownBy(() -> Message.fromCustomer(CONVERSATION, tooLong, T0))
                .isInstanceOf(DomainValidationException.class);
    }

    @ParameterizedTest
    @CsvSource({
            "APPOINTMENT_REQUEST, APPOINTMENT_REQUEST",
            "appointment_request, APPOINTMENT_REQUEST",
            "' medical-concern ', MEDICAL_CONCERN",
            "human request, HUMAN_REQUEST",
            "SCHEDULE, OTHER",
            "'', OTHER"
    })
    void intentParse_fallsBackToOther(String raw, Intent expected) {
        assertThat(Intent.parse(raw)).isEqualTo(expected);
    }

    @Test
    void intentParse_null_isOther() {
        assertThat(Intent.parse(null)).isEqualTo(Intent.OTHER);
    }
}
