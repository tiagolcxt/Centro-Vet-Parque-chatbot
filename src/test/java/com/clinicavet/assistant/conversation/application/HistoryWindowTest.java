package com.clinicavet.assistant.conversation.application;

import com.clinicavet.assistant.shared.domain.ConversationId;
import com.clinicavet.assistant.conversation.domain.Intent;
import com.clinicavet.assistant.conversation.domain.Message;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HistoryWindowTest {

    /** Estimativa simples e previsível para o teste: 1 token por caractere. */
    private static final TokenEstimator ONE_PER_CHAR = String::length;
    private static final int OVERHEAD = HistoryWindow.PER_MESSAGE_OVERHEAD_TOKENS;

    private final ConversationId conversation = ConversationId.newId();

    private List<Message> messages(int count, int length) {
        List<Message> list = new ArrayList<>();
        Instant t = Instant.parse("2026-11-16T12:00:00Z");
        for (int i = 0; i < count; i++) {
            String content = ("m" + i + "-").repeat(length).substring(0, length);
            list.add(i % 2 == 0
                    ? Message.fromCustomer(conversation, content, t.plusSeconds(i))
                    : Message.fromAssistant(conversation, content, Intent.OTHER, t.plusSeconds(i)));
        }
        return list;
    }

    @Test
    void keepsTheMostRecent_upToTheMessageLimit_inChronologicalOrder() {
        List<Message> previous = messages(30, 10);

        List<Message> window = HistoryWindow.fit(previous, 19, 100_000, ONE_PER_CHAR);

        assertThat(window).hasSize(19).containsExactlyElementsOf(previous.subList(11, 30));
    }

    @Test
    void tokenBudget_cutsOldestFirst_beforeTheMessageLimit() {
        List<Message> previous = messages(10, 1_000);   // 1.004 "tokens" cada

        List<Message> window = HistoryWindow.fit(previous, 19, 4_000, ONE_PER_CHAR);

        assertThat(window).hasSize(3).containsExactlyElementsOf(previous.subList(7, 10));
        assertThat(window.stream().mapToInt(m -> m.content().length() + OVERHEAD).sum()).isLessThanOrEqualTo(4_000);
    }

    @Test
    void stopsAtTheFirstMessageThatDoesNotFit_neverSkippingToOlderOnes() {
        List<Message> previous = new ArrayList<>(messages(3, 10));
        Message huge = Message.fromCustomer(conversation, "x".repeat(5_000), Instant.parse("2026-11-16T13:00:00Z"));
        Message last = Message.fromAssistant(conversation, "ok", Intent.OTHER, Instant.parse("2026-11-16T13:00:01Z"));
        previous.add(huge);
        previous.add(last);

        assertThat(HistoryWindow.fit(previous, 19, 4_000, ONE_PER_CHAR)).containsExactly(last);
    }

    @Test
    void emptyHistory_staysEmpty() {
        assertThat(HistoryWindow.fit(List.of(), 19, 4_000, ONE_PER_CHAR)).isEmpty();
    }
}
