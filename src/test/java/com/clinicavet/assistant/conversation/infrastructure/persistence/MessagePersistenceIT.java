package com.clinicavet.assistant.conversation.infrastructure.persistence;

import com.clinicavet.assistant.conversation.domain.Conversation;
import com.clinicavet.assistant.conversation.domain.ConversationRepository;
import com.clinicavet.assistant.conversation.domain.Intent;
import com.clinicavet.assistant.conversation.domain.Message;
import com.clinicavet.assistant.conversation.domain.MessageAuthor;
import com.clinicavet.assistant.conversation.domain.MessageRepository;
import com.clinicavet.assistant.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.stream.IntStream;

import static com.clinicavet.assistant.support.Fixtures.T0;
import static com.clinicavet.assistant.support.Fixtures.newContact;
import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class MessagePersistenceIT {

    @Autowired
    ConversationRepository conversations;

    @Autowired
    MessageRepository messages;

    @Test
    void findLatest_returnsTheMostRecentInChronologicalOrder() {
        Conversation conversation = conversations.save(Conversation.start(newContact(), T0));
        IntStream.rangeClosed(1, 25).forEach(i ->
                messages.save(Message.fromCustomer(conversation.id(), "mensagem " + i, T0.plusSeconds(i))));

        List<Message> latest = messages.findLatest(conversation.id(), 20);

        assertThat(latest).hasSize(20);
        assertThat(latest).extracting(Message::content)
                .containsExactlyElementsOf(IntStream.rangeClosed(6, 25).mapToObj(i -> "mensagem " + i).toList());
    }

    @Test
    void findLatest_neverMixesConversations() {
        Conversation a = conversations.save(Conversation.start(newContact(), T0));
        Conversation b = conversations.save(Conversation.start(newContact(), T0));
        messages.save(Message.fromCustomer(a.id(), "da conversa A", T0));
        messages.save(Message.fromCustomer(b.id(), "da conversa B", T0));

        assertThat(messages.findLatest(a.id(), 20)).extracting(Message::content).containsExactly("da conversa A");
    }

    @Test
    void roundTrip_preservesAuthorIntentAndUtf8Content() {
        Conversation conversation = conversations.save(Conversation.start(newContact(), T0));
        String content = "Olá! A Mel (cadelinha fictícia) está ótima 🐶 — obrigado, ç ã õ é";
        messages.save(Message.fromCustomer(conversation.id(), content, T0));
        messages.save(Message.fromAssistant(conversation.id(), "Que bom! 😊", Intent.GREETING, T0.plusSeconds(1)));

        List<Message> loaded = messages.findLatest(conversation.id(), 10);

        assertThat(loaded).extracting(Message::author).containsExactly(MessageAuthor.CUSTOMER, MessageAuthor.ASSISTANT);
        assertThat(loaded.get(0).content()).isEqualTo(content);
        assertThat(loaded.get(0).intent()).isEmpty();
        assertThat(loaded.get(1).intent()).contains(Intent.GREETING);
        assertThat(loaded.get(0).createdAt()).isEqualTo(T0.truncatedTo(java.time.temporal.ChronoUnit.MICROS));
    }
}
