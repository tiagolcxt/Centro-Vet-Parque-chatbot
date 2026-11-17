package com.clinicavet.assistant.conversation.application;

import com.clinicavet.assistant.conversation.domain.Conversation;
import com.clinicavet.assistant.conversation.domain.ConversationExpirationPolicy;
import com.clinicavet.assistant.conversation.domain.ConversationRepository;
import com.clinicavet.assistant.shared.domain.ContactIdentity;
import com.clinicavet.assistant.tutor.domain.TutorRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

/**
 * Regra de ADR-0009: no máximo uma conversa ativa por contato; a expiração é avaliada quando o
 * contato aparece. Deve rodar dentro de transação: a conversa ativa fica travada até o fim dela.
 * O índice único da migration V2 garante a regra mesmo se duas transações tentarem abrir uma
 * conversa ao mesmo tempo (a perdedora falha e é repetida pelo {@link TransactionRunner}).
 * <p>
 * Conversa nova de um contato que já tem cadastro já nasce vinculada ao tutor (DA-36).
 */
@Component
class ConversationLifecycle {

    record Resolved(Conversation conversation, boolean started) {
    }

    private final ConversationRepository conversations;
    private final ConversationExpirationPolicy policy;
    private final TutorRepository tutors;

    ConversationLifecycle(ConversationRepository conversations, ConversationExpirationPolicy policy,
                          TutorRepository tutors) {
        this.conversations = conversations;
        this.policy = policy;
        this.tutors = tutors;
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
        Conversation started = Conversation.start(contact, now);
        // DA-36: contato que já tem cadastro tem a conversa nova vinculada na abertura, pelo identificador do canal.
        tutors.findByContact(contact).ifPresent(tutor -> started.linkTutor(tutor.id()));
        return new Resolved(conversations.save(started), true);
    }
}
