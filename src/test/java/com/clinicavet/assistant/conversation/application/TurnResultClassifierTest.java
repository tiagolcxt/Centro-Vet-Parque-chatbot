package com.clinicavet.assistant.conversation.application;

import com.clinicavet.assistant.conversation.domain.Intent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TurnResultClassifierTest {

    private static final String TAIL = ",\"knowledgeRefs\":[],\"knowledgeGap\":false}";

    private final TurnResultClassifier classifier = new TurnResultClassifier();

    private TurnOutcome classify(String content, String finishReason) {
        return classifier.classify(ModelReply.of(content, finishReason), 1);
    }

    @Test
    void validTurn_isAccepted_withReplyStripped() {
        TurnOutcome outcome = classify("{\"reply\":\"  Olá! Como posso ajudar?  \",\"intent\":\"GREETING\"" + TAIL, "STOP");

        assertThat(outcome).isEqualTo(new TurnOutcome.Valid(AssistantTurn.simple("Olá! Como posso ajudar?", Intent.GREETING), 1));
    }

    @Test
    void sourcesAndGap_areRead() {
        TurnOutcome outcome = classify("{\"reply\":\"Abrimos aos sábados das 8h às 12h.\",\"intent\":\"CLINIC_INFORMATION\","
                + "\"knowledgeRefs\":[\" hours.saturday \"],\"knowledgeGap\":true}", "STOP");

        assertThat(outcome).isInstanceOfSatisfying(TurnOutcome.Valid.class, valid -> {
            assertThat(valid.turn().knowledgeRefs()).containsExactly("hours.saturday");
            assertThat(valid.turn().knowledgeGap()).isTrue();
        });
    }

    @Test
    void unknownIntent_becomesOther_andIsNotAFailure() {
        TurnOutcome outcome = classify("{\"reply\":\"Ok.\",\"intent\":\"BOOK_NOW\"" + TAIL, "stop");

        assertThat(outcome).isInstanceOfSatisfying(TurnOutcome.Valid.class,
                valid -> assertThat(valid.turn().intent()).isEqualTo(Intent.OTHER));
    }

    @Test
    void refusalField_isRefusal_evenWithContent() {
        TurnOutcome outcome = classifier.classify(
                new ModelReply("{\"reply\":\"x\",\"intent\":\"OTHER\"" + TAIL, "STOP", "Não posso ajudar com isso.", null, null, null), 1);

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
            "{\"intent\":\"OTHER\",\"knowledgeRefs\":[],\"knowledgeGap\":false}",
            "{\"reply\":\"   \",\"intent\":\"OTHER\",\"knowledgeRefs\":[],\"knowledgeGap\":false}",
            "{\"reply\":42,\"intent\":\"OTHER\",\"knowledgeRefs\":[],\"knowledgeGap\":false}",
            "{\"reply\":\"Sem intenção.\",\"knowledgeRefs\":[],\"knowledgeGap\":false}",
            "{\"reply\":\"x\",\"intent\":null,\"knowledgeRefs\":[],\"knowledgeGap\":false}",
            "{\"reply\":\"Sem fontes.\",\"intent\":\"OTHER\",\"knowledgeGap\":false}",
            "{\"reply\":\"Sem lacuna.\",\"intent\":\"OTHER\",\"knowledgeRefs\":[]}",
            "{\"reply\":\"x\",\"intent\":\"OTHER\",\"knowledgeRefs\":\"hours.monday\",\"knowledgeGap\":false}",
            "{\"reply\":\"x\",\"intent\":\"OTHER\",\"knowledgeRefs\":[1],\"knowledgeGap\":false}",
            "{\"reply\":\"x\",\"intent\":\"OTHER\",\"knowledgeRefs\":[],\"knowledgeGap\":\"false\"}",
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
        assertThat(classify("{\"reply\":\"" + huge + "\",\"intent\":\"OTHER\"" + TAIL, "STOP"))
                .isEqualTo(new TurnOutcome.Failed(FailureKind.INVALID, 1));
    }

    @Test
    void attemptNumberIsKept() {
        assertThat(classifier.classify(ModelReply.of("x", "STOP"), 2).attempts()).isEqualTo(2);
    }

    @Test
    void refsListIsImmutableCopy() {
        TurnOutcome outcome = classify("{\"reply\":\"x\",\"intent\":\"OTHER\",\"knowledgeRefs\":[\"a\"],\"knowledgeGap\":false}", "STOP");
        assertThat(((TurnOutcome.Valid) outcome).turn().knowledgeRefs()).isEqualTo(List.of("a"));
    }
}
