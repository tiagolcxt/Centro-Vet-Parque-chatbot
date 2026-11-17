package com.clinicavet.assistant.conversation.application;

import com.clinicavet.assistant.conversation.domain.ClinicalPriority;
import com.clinicavet.assistant.conversation.domain.Conversation;
import com.clinicavet.assistant.conversation.domain.ConversationId;
import com.clinicavet.assistant.conversation.domain.ConversationRepository;
import com.clinicavet.assistant.conversation.domain.ConversationStatus;
import com.clinicavet.assistant.conversation.domain.PriorityChange;
import com.clinicavet.assistant.shared.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Operações da equipe sobre conversas (ADR-0007 item 7, ADR-0011, ADR-0018). Não alteram solicitações.
 * Transição inválida vira {@code 409}. Até a Etapa 7, quem chama é identificado só pela API Key (ADR-0012).
 */
@Service
public class StaffConversationService {

    /** Uma conversa na fila, com o atraso calculado pelo prazo do nível (H10, H11). */
    public record QueueItem(Conversation conversation, Optional<Duration> waiting, boolean overdue) {
    }

    static final int MAX_QUEUE = 200;

    private final ConversationRepository conversations;
    private final TransactionRunner tx;
    private final HandoffSettings settings;
    private final Clock clock;

    StaffConversationService(ConversationRepository conversations, TransactionRunner tx, HandoffSettings settings,
                             Clock clock) {
        this.conversations = conversations;
        this.tx = tx;
        this.settings = settings;
        this.clock = clock;
    }

    /** Fila ordenada por prioridade e, no mesmo nível, por tempo de espera (H10). */
    public List<QueueItem> queue(ConversationStatus status, int limit) {
        Instant now = clock.instant();
        return conversations.findByStatusInQueueOrder(status, Math.min(Math.max(limit, 1), MAX_QUEUE)).stream()
                .map(c -> {
                    Optional<Duration> waiting = c.handoffAt().map(at -> Duration.between(at, now));
                    boolean overdue = waiting.map(w -> w.compareTo(settings.deadlineFor(c.clinicalPriority())) > 0)
                            .orElse(false);
                    return new QueueItem(c, waiting, overdue);
                })
                .toList();
    }

    public Conversation close(ConversationId id) {
        return tx.inTransaction(() -> {
            Conversation conversation = load(id);
            conversation.closeByStaff(clock.instant());
            return conversations.save(conversation);
        });
    }

    public Conversation returnToAssistant(ConversationId id) {
        return tx.inTransaction(() -> {
            Conversation conversation = load(id);
            conversation.returnToAssistant(clock.instant());
            return conversations.save(conversation);
        });
    }

    /** Único caminho de rebaixamento (H4): registra nível anterior, novo, autor e momento. */
    public Conversation changePriority(ConversationId id, ClinicalPriority priority, String note) {
        return tx.inTransaction(() -> {
            Conversation conversation = load(id);
            Instant now = clock.instant();
            ClinicalPriority previous = conversation.changePriorityByStaff(priority);
            Conversation saved = conversations.save(conversation);
            conversations.savePriorityChange(PriorityChange.byStaff(id, previous, priority, note, now));
            return saved;
        });
    }

    public List<PriorityChange> priorityHistory(ConversationId id) {
        conversations.findById(id).orElseThrow(() -> new ResourceNotFoundException("Conversa", id.value()));
        return conversations.findPriorityChanges(id);
    }

    private Conversation load(ConversationId id) {
        return conversations.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Conversa", id.value()));
    }
}
