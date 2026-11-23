package com.clinicavet.assistant.conversation.application;

import com.clinicavet.assistant.conversation.domain.Conversation;
import com.clinicavet.assistant.conversation.domain.ConversationRepository;
import com.clinicavet.assistant.servicerequest.domain.ConversationFacts;
import com.clinicavet.assistant.shared.domain.ConversationId;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Entrega ao módulo de solicitações a prioridade clínica das conversas (RF-S31). */
@Component
class ConversationFactsAdapter implements ConversationFacts {

    private final ConversationRepository conversations;

    ConversationFactsAdapter(ConversationRepository conversations) {
        this.conversations = conversations;
    }

    @Override
    public Map<ConversationId, Priority> priorities(Set<ConversationId> ids) {
        Map<ConversationId, Priority> result = new HashMap<>();
        for (Conversation c : conversations.findByIds(ids)) {
            result.put(c.id(), new Priority(c.clinicalPriority().name(), c.clinicalPriority().ordinal() + 1, c.clinicalRisk()));
        }
        return result;
    }

    @Override
    public Set<ConversationId> waitingForStaff(Set<ConversationId> ids) {
        return conversations.findByIds(ids).stream()
                .filter(c -> c.status() == com.clinicavet.assistant.conversation.domain.ConversationStatus.WAITING_HUMAN)
                .map(Conversation::id).collect(java.util.stream.Collectors.toSet());
    }
}
