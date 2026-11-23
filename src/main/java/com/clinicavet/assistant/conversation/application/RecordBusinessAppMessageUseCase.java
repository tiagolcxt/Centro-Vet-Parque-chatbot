package com.clinicavet.assistant.conversation.application;

import com.clinicavet.assistant.conversation.domain.Conversation;
import com.clinicavet.assistant.conversation.domain.ConversationRepository;
import com.clinicavet.assistant.conversation.domain.Message;
import com.clinicavet.assistant.conversation.domain.MessageRepository;
import com.clinicavet.assistant.platform.LiveUpdates;
import com.clinicavet.assistant.shared.domain.ContactIdentity;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

/**
 * Coexistência (DA-53): a clínica respondeu pelo app do WhatsApp Business no celular. A mensagem entra na conversa
 * ativa do contato (ou abre uma), marcada como "respondida pelo celular", para a equipe não responder de novo. Não
 * roda o assistente e não é reenviada.
 */
@Service
public class RecordBusinessAppMessageUseCase {

    private final ConversationLifecycle lifecycle;
    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final TransactionRunner tx;
    private final LiveUpdates live;
    private final Clock clock;

    RecordBusinessAppMessageUseCase(ConversationLifecycle lifecycle, ConversationRepository conversations,
                                    MessageRepository messages, TransactionRunner tx, LiveUpdates live, Clock clock) {
        this.lifecycle = lifecycle;
        this.conversations = conversations;
        this.messages = messages;
        this.tx = tx;
        this.live = live;
        this.clock = clock;
    }

    public Message record(ContactIdentity contact, String text, Instant sentAt) {
        Message saved = tx.withRetry(() -> {
            Instant now = clock.instant();
            Conversation conversation = lifecycle.resolveActive(contact, now).conversation();
            conversation.registerActivity(now);
            conversations.save(conversation);
            return messages.save(Message.fromBusinessApp(conversation.id(), text, sentAt));
        });
        live.changed(LiveUpdates.Topic.CONVERSATION, saved.conversationId().value());
        return saved;
    }
}
