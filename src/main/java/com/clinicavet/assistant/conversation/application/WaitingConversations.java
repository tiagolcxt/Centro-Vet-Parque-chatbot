package com.clinicavet.assistant.conversation.application;

import com.clinicavet.assistant.alert.ForgottenWork;
import com.clinicavet.assistant.clinicknowledge.domain.Departments;
import com.clinicavet.assistant.conversation.domain.Conversation;
import com.clinicavet.assistant.conversation.domain.ConversationRepository;
import com.clinicavet.assistant.conversation.domain.ConversationStatus;
import com.clinicavet.assistant.conversation.domain.HandoffReason;
import com.clinicavet.assistant.shared.domain.ClinicalRisk;
import com.clinicavet.assistant.tutor.domain.Tutor;
import com.clinicavet.assistant.tutor.domain.TutorRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Conversas em que o tutor espera alguém da equipe (E3). O prazo é o do nível da conversa (P-02, provisório): o
 * mesmo que pinta "passou do prazo" na fila.
 */
@Component
class WaitingConversations implements ForgottenWork {

    private final ConversationRepository conversations;
    private final TutorRepository tutors;
    private final Departments departments;
    private final HandoffSettings settings;

    WaitingConversations(ConversationRepository conversations, TutorRepository tutors, Departments departments,
                         HandoffSettings settings) {
        this.conversations = conversations;
        this.tutors = tutors;
        this.departments = departments;
        this.settings = settings;
    }

    @Override
    public Kind kind() {
        return Kind.CONVERSATION_WAITING;
    }

    @Override
    public List<Pending> pending(Instant now) {
        java.util.UUID defaultDepartment = departments.defaultDepartment().id();
        return conversations.findInbox(StaffConversationService.MAX_INBOX).stream()
                .filter(c -> c.status() == ConversationStatus.WAITING_HUMAN && c.handoffAt().isPresent())
                .map(c -> new Pending(c.id().value(), c.handoffAt().get(), settings.deadlineFor(c.clinicalPriority()),
                        c.waitingDepartmentId().orElse(defaultDepartment), title(c), what(c)))
                .toList();
    }

    private String title(Conversation c) {
        return c.tutorId().flatMap(tutors::findById).flatMap(Tutor::name).orElse("Tutor sem nome");
    }

    private static String what(Conversation c) {
        ClinicalRisk risk = c.clinicalRisk();
        return switch (risk) {
            case POSSIBLE_EMERGENCY -> "Possível emergência";
            case URGENT -> "Urgente";
            case CLINICAL_FOLLOW_UP -> "Acompanhamento clínico";
            default -> c.handoffReason().map(HandoffReason::label).orElse("Esperando resposta");
        };
    }
}
