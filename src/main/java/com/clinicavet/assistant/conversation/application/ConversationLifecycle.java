package com.clinicavet.assistant.conversation.application;

import com.clinicavet.assistant.conversation.domain.Conversation;
import com.clinicavet.assistant.conversation.domain.ConversationExpirationPolicy;
import com.clinicavet.assistant.conversation.domain.ConversationRepository;
import com.clinicavet.assistant.shared.domain.ContactIdentity;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

/**
 * Regra de ADR-0009: no máximo uma conversa ativa por contato; a expiração é avaliada quando o
 * contato aparece. Deve rodar dentro de transação: a conversa ativa fica travada até o fim dela.
 * O índice único da migration V2 garante a regra mesmo se duas transações tentarem abrir uma
 * conversa ao mesmo tempo (a perdedora falha e é repetida pelo {@link TransactionRunner}).
 */
@Component
class ConversationLifecycle {

    record Resolved(Conversation conversation, boolean started) {
    }

    private final ConversationRepository conversations;
    private final ConversationExpirationPolicy policy;

    ConversationLifecycle(ConversationRepository conversations, ConversationExpirationPolicy policy) {
        this.conversations = conversations;
        this.policy = policy;
    }

    Resolved resolveActive(ContactIdentity contact, Instant now) {
        Optional<Conversation> active = conversations.findActiveByContactForUpdate(contact);
        if (active.isPresent()) {
            Conversation current = active.get();
            if (!current.isExpired(now, policy)) {
                return new Resolved(current, false);
            }
            current.expire(now, policy);
            conversations.save(current);   // CLOSED antes de inserir a nova: o índice único exige
        }
        return new Resolved(conversations.save(Conversation.start(contact, now)), true);
    }
}
