package com.clinicavet.assistant.conversation.api;

import com.clinicavet.assistant.conversation.application.StaffConversationService;
import com.clinicavet.assistant.conversation.domain.ClinicalPriority;
import com.clinicavet.assistant.conversation.domain.Conversation;
import com.clinicavet.assistant.conversation.domain.ConversationId;
import com.clinicavet.assistant.conversation.domain.ConversationStatus;
import com.clinicavet.assistant.conversation.domain.HandoffReason;
import com.clinicavet.assistant.conversation.domain.PriorityChange;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Operações da equipe sobre conversas (Etapa 4). Mesma API Key do canal até a Etapa 7, quando entram
 * autenticação individual e perfis (ADR-0012, P-17).
 */
@Validated
@RestController
@RequestMapping("/api/conversations")
class StaffConversationController {

    private final StaffConversationService staff;

    StaffConversationController(StaffConversationService staff) {
        this.staff = staff;
    }

    /** Fila: prioridade mais urgente primeiro e, no mesmo nível, quem espera há mais tempo (H10). */
    @GetMapping
    List<QueueItemResponse> queue(@RequestParam(defaultValue = "WAITING_HUMAN") ConversationStatus status,
                                  @RequestParam(defaultValue = "100") @Min(1) @Max(200) int limit) {
        return staff.queue(status, limit).stream().map(QueueItemResponse::from).toList();
    }

    @PostMapping("/{id}/close")
    StaffConversationResponse close(@PathVariable UUID id) {
        return StaffConversationResponse.from(staff.close(new ConversationId(id)));
    }

    @PostMapping("/{id}/return-to-assistant")
    StaffConversationResponse returnToAssistant(@PathVariable UUID id) {
        return StaffConversationResponse.from(staff.returnToAssistant(new ConversationId(id)));
    }

    /** Único caminho de rebaixamento (H4). Também pode elevar. Fica registrado. */
    @PatchMapping("/{id}/priority")
    StaffConversationResponse changePriority(@PathVariable UUID id, @Valid @RequestBody ChangePriorityRequest body) {
        return StaffConversationResponse.from(
                staff.changePriority(new ConversationId(id), body.priority(), body.note()));
    }

    @GetMapping("/{id}/priority-changes")
    List<PriorityChangeResponse> priorityChanges(@PathVariable UUID id) {
        return staff.priorityHistory(new ConversationId(id)).stream().map(PriorityChangeResponse::from).toList();
    }

    /** @param note motivo da mudança; não escrever dados de saúde nem pessoais além do necessário (LGPD) */
    record ChangePriorityRequest(@NotNull ClinicalPriority priority, @Size(max = PriorityChange.MAX_NOTE) String note) {
    }

    record StaffConversationResponse(UUID id, ConversationStatus status, ClinicalPriority priority,
                                     HandoffReason handoffReason, Instant handoffAt, Instant lastActivityAt) {

        static StaffConversationResponse from(Conversation c) {
            return new StaffConversationResponse(c.id().value(), c.status(), c.clinicalPriority(),
                    c.handoffReason().orElse(null), c.handoffAt().orElse(null), c.lastActivityAt());
        }
    }

    /**
     * @param waitingMinutes minutos desde o encaminhamento ({@code null} se não houve)
     * @param overdue        passou do prazo do nível sem ação da equipe (prazos provisórios, P-02)
     */
    record QueueItemResponse(UUID id, ConversationStatus status, ClinicalPriority priority, HandoffReason handoffReason,
                             Instant handoffAt, Long waitingMinutes, boolean overdue, Instant lastActivityAt) {

        static QueueItemResponse from(StaffConversationService.QueueItem item) {
            Conversation c = item.conversation();
            return new QueueItemResponse(c.id().value(), c.status(), c.clinicalPriority(),
                    c.handoffReason().orElse(null), c.handoffAt().orElse(null),
                    item.waiting().map(java.time.Duration::toMinutes).orElse(null), item.overdue(), c.lastActivityAt());
        }
    }

    record PriorityChangeResponse(ClinicalPriority from, ClinicalPriority to, String changedBy, String note,
                                  Instant changedAt) {

        static PriorityChangeResponse from(PriorityChange change) {
            return new PriorityChangeResponse(change.from(), change.to(), change.changedBy(), change.note(),
                    change.changedAt());
        }
    }
}
