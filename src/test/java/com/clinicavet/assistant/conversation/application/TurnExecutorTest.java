package com.clinicavet.assistant.conversation.application;

import com.clinicavet.assistant.conversation.domain.ConversationStatus;
import com.clinicavet.assistant.conversation.domain.Intent;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/** DA-28: no máximo uma nova tentativa por turno; recusa e erro permanente não se repetem. */
class TurnExecutorTest {

    private static final String VALID = "{\"reply\":\"Oi!\",\"intent\":\"GREETING\"}";
    private static final AssistantRequest REQUEST = new AssistantRequest(
            new AssistantRequest.OperationalContext(ConversationStatus.OPEN,
                    ZonedDateTime.of(2026, 11, 16, 9, 0, 0, 0, ZoneId.of("America/Sao_Paulo"))),
            List.of(), "Oi");

    private final Deque<Supplier<ModelReply>> script = new ArrayDeque<>();
    private int calls;

    private TurnOutcome run() {
        AssistantModel model = request -> {
            calls++;
            return script.isEmpty() ? ModelReply.of(VALID, "STOP") : script.pollFirst().get();
        };
        ConversationSettings settings = new ConversationSettings(
                new ConversationSettings.Expiration(Duration.ofMinutes(30), Duration.ofHours(2)),
                20, 4000, 2000, Duration.ZERO, "fallback");
        return new TurnExecutor(model, new TurnResultClassifier(), settings, Clock.systemUTC()).execute(REQUEST);
    }

    private static Supplier<ModelReply> failWith(AssistantModelException.Kind kind) {
        return () -> {
            throw new AssistantModelException(kind, "simulada", null);
        };
    }

    @Test
    void validFirstAnswer_singleCall() {
        assertThat(run()).isEqualTo(new TurnOutcome.Valid(new AssistantTurn("Oi!", Intent.GREETING), 1));
        assertThat(calls).isEqualTo(1);
    }

    @Test
    void invalidThenValid_retriesOnce_andUsesTheSecond() {
        script.add(() -> ModelReply.of("não é json", "STOP"));

        assertThat(run()).isInstanceOfSatisfying(TurnOutcome.Valid.class, v -> assertThat(v.attempts()).isEqualTo(2));
        assertThat(calls).isEqualTo(2);
    }

    @Test
    void twoTimeouts_failAfterExactlyTwoCalls() {
        script.add(failWith(AssistantModelException.Kind.TIMEOUT));
        script.add(failWith(AssistantModelException.Kind.TIMEOUT));
        script.add(() -> ModelReply.of(VALID, "STOP"));   // nunca deve ser usada

        assertThat(run()).isEqualTo(new TurnOutcome.Failed(FailureKind.TIMEOUT, 2));
        assertThat(calls).isEqualTo(2);
    }

    @Test
    void truncatedThenTransient_reportsTheLastFailure() {
        script.add(() -> ModelReply.of("{\"reply\":\"x", "LENGTH"));
        script.add(failWith(AssistantModelException.Kind.TRANSIENT));

        assertThat(run()).isEqualTo(new TurnOutcome.Failed(FailureKind.TECHNICAL_TRANSIENT, 2));
    }

    @Test
    void refusal_isNotRetried() {
        script.add(() -> new ModelReply(null, "STOP", "recusado", null, null, null));

        assertThat(run()).isEqualTo(new TurnOutcome.Failed(FailureKind.REFUSAL, 1));
        assertThat(calls).isEqualTo(1);
    }

    @Test
    void permanentError_isNotRetried() {
        script.add(failWith(AssistantModelException.Kind.PERMANENT));

        assertThat(run()).isEqualTo(new TurnOutcome.Failed(FailureKind.TECHNICAL_PERMANENT, 1));
        assertThat(calls).isEqualTo(1);
    }

    @Test
    void unexpectedAdapterBug_failsSafely_withoutRetry() {
        script.add(() -> {
            throw new IllegalStateException("defeito");
        });

        assertThat(run()).isEqualTo(new TurnOutcome.Failed(FailureKind.TECHNICAL_PERMANENT, 1));
        assertThat(calls).isEqualTo(1);
    }
}
