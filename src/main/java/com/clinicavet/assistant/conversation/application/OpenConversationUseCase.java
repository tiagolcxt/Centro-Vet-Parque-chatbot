package com.clinicavet.assistant.conversation.application;

import com.clinicavet.assistant.conversation.domain.Conversation;
import com.clinicavet.assistant.shared.domain.ContactIdentity;
import org.springframework.stereotype.Service;

import java.time.Clock;

/** {@code POST /api/conversations}: devolve a conversa ativa do contato ou abre uma nova (ADR-0009). */
@Service
public class OpenConversationUseCase {

    public record Result(Conversation conversation, boolean started) {
    }

    private final ConversationLifecycle lifecycle;
    private final TransactionRunner tx;
    private final Clock clock;

    OpenConversationUseCase(ConversationLifecycle lifecycle, TransactionRunner tx, Clock clock) {
        this.lifecycle = lifecycle;
        this.tx = tx;
        this.clock = clock;
    }

    public Result open(ContactIdentity contact) {
        ConversationLifecycle.Resolved resolved = tx.withRetry(() -> lifecycle.resolveActive(contact, clock.instant()));
        return new Result(resolved.conversation(), resolved.started());
    }
}
