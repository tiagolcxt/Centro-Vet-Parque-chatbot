package com.clinicavet.assistant.conversation.application;

import com.clinicavet.assistant.conversation.domain.Intent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class TurnResultClassifierTest {

    private final TurnResultClassifier classifier = new TurnResultClassifier();

    private TurnOutcome classify(String content, String finishReason) {
        return classifier.classify(ModelReply.of(content, finishReason), 1);
    }

    @Test
    void validTurn_isAccepted_withReplyStripped() {
        TurnOutcome outcome = classify("{\"reply\":\"  Olá! Como posso ajudar?  \",\"intent\":\"GREETING\"}", "STOP");

        assertThat(outcome).isEqualTo(new TurnOutcome.Valid(new AssistantTurn("Olá! Como posso ajudar?", Intent.GREETING), 1));
    }

    @Test
    void unknownIntent_becomesOther_andIsNotAFailure() {
        TurnOutcome outcome = classify("{\"reply\":\"Ok.\",\"intent\":\"BOOK_NOW\"}", "stop");

        assertThat(outcome).isInstanceOfSatisfying(TurnOutcome.Valid.class,
                valid -> assertThat(valid.turn().intent()).isEqualTo(Intent.OTHER));
    }

    @Test
    void refusalField_isRefusal_evenWithContent() {
        TurnOutcome outcome = classifier.classify(
                new ModelReply("{\"reply\":\"x\",\"intent\":\"OTHER\"}", "STOP", "Não posso ajudar com isso.", null, null, null), 1);

        assertThat(outcome).isEqualTo(new TurnOutcome.Failed(FailureKind.REFUSAL, 1));
    }

    @Test
    void contentFilter_isRefusal() {
        assertThat(classify(null, "CONTENT_FILTER")).isEqualTo(new TurnOutcome.Failed(FailureKind.REFUSAL, 1));
    }

    /** O Spring AI entrega o enum em maiúsculas (ADR-0016); a API, em minúsculas. As duas valem. */
    @ParameterizedTest
    @ValueSource(strings = {"LENGTH", "length", " Length "})
    void lengthFinishReason_isTruncated_inAnyCase(String finishReason) {
        assertThat(classify("{\"reply\":\"Texto comp", finishReason)).isEqualTo(new TurnOutcome.Failed(FailureKind.TRUNCATED, 1));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "isto não é JSON",
            "{\"reply\":\"incompleto\"",
            "[{\"reply\":\"x\",\"intent\":\"OTHER\"}]",
            "{\"intent\":\"OTHER\"}",
            "{\"reply\":\"   \",\"intent\":\"OTHER\"}",
            "{\"reply\":42,\"intent\":\"OTHER\"}",
            "{\"reply\":\"Sem intenção.\"}",
            "{\"reply\":\"x\",\"intent\":null}",
            "null"
    })
    void contractViolations_areInvalid(String content) {
        assertThat(classify(content, "STOP")).isEqualTo(new TurnOutcome.Failed(FailureKind.INVALID, 1));
    }

    @Test
    void emptyOrMissingContent_isInvalid() {
        assertThat(classify(null, "STOP")).isEqualTo(new TurnOutcome.Failed(FailureKind.INVALID, 1));
        assertThat(classify("  ", null)).isEqualTo(new TurnOutcome.Failed(FailureKind.INVALID, 1));
    }

    @Test
    void replyLongerThanStorageLimit_isInvalid() {
        String huge = "a".repeat(10_001);
        assertThat(classify("{\"reply\":\"" + huge + "\",\"intent\":\"OTHER\"}", "STOP"))
                .isEqualTo(new TurnOutcome.Failed(FailureKind.INVALID, 1));
    }

    @Test
    void attemptNumberIsKept() {
        assertThat(classifier.classify(ModelReply.of("x", "STOP"), 2).attempts()).isEqualTo(2);
    }
}
