package com.clinicavet.assistant.conversation.application;

import com.clinicavet.assistant.clinicknowledge.domain.ClinicKnowledge;
import com.clinicavet.assistant.clinicknowledge.domain.KnowledgeItem;
import com.clinicavet.assistant.conversation.domain.ClinicalPriority;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Compõe o texto final ao tutor a partir da decisão do backend (ADR-0002 reconciliação, ADR-0007, H6–H8).
 * <ul>
 *   <li><b>P1 / possível emergência:</b> orientação de emergência <b>primeiro e sozinha</b>, depois o registro
 *       para a equipe. O texto do modelo não é usado (nada administrativo antes da orientação).</li>
 *   <li><b>P2:</b> orientação curta de quando procurar atendimento imediato <b>antes</b> do resto (H6).</li>
 *   <li><b>Pedido explícito de humano:</b> só a confirmação de registro, sem pedir justificativa nem tentar
 *       resolver pela IA (diretriz 3.3, CT-02).</li>
 *   <li><b>Encaminhamento:</b> o texto padrão de registro é acrescentado. Nenhum texto afirma notificação.</li>
 * </ul>
 * A orientação de emergência e o contato direto vêm da base (P-01, P-02); enquanto não aprovados, entram os
 * <i>placeholders</i> explícitos, aceitáveis só com dados fictícios (ADR-0014).
 */
@Component
class ReplyComposer {

    private final HandoffSettings.Replies texts;
    private final String emergency;
    private final String shortGuidance;
    private final String overdue;
    private final String directContact;

    ReplyComposer(HandoffSettings settings, ClinicKnowledge knowledge) {
        this.texts = settings.replies();
        String policy = approved(knowledge, "approved-texts.emergency-policy");
        this.emergency = policy != null ? policy : texts.emergencyPlaceholder().strip();
        this.shortGuidance = policy != null ? policy : texts.shortGuidancePlaceholder().strip();
        this.directContact = approved(knowledge, "approved-texts.direct-contact");
        this.overdue = directContact != null ? directContact : texts.overduePlaceholder().strip();
    }

    /** O que o composer precisa saber do turno. */
    record Plan(ClinicalPriority turnLevel, boolean possibleEmergency, boolean explicitRequest, boolean handoff,
                boolean offerHuman) {

        boolean emergency() {
            return possibleEmergency || turnLevel == ClinicalPriority.P1_POSSIBLE_EMERGENCY;
        }
    }

    /** Modo principal, turno válido. */
    String composeValid(Plan plan, String modelReply) {
        if (plan.emergency()) {
            return join(emergency, handoffText());
        }
        List<String> parts = new ArrayList<>();
        if (plan.turnLevel() == ClinicalPriority.P2_URGENT) {
            parts.add(shortGuidance);
        }
        if (plan.explicitRequest()) {
            parts.add(handoffText());
        } else {
            parts.add(modelReply);
            if (plan.handoff()) {
                parts.add(handoffText());
            }
        }
        if (plan.offerHuman()) {
            parts.add(texts.humanOffer().strip());
        }
        return join(parts.toArray(String[]::new));
    }

    /**
     * Modo principal, turno com falha. Se a decisão é encaminhar (pedido explícito, prioridade, transferência
     * anunciada), a resposta segura é a de encaminhamento. Conversa em P1/P2: a resposta segura inclui a
     * orientação curta (H8).
     */
    String composeFailure(Plan plan, String fallbackReply, ClinicalPriority conversationLevel) {
        if (plan.emergency()) {
            return join(emergency, handoffText());
        }
        List<String> parts = new ArrayList<>();
        if (conversationLevel.isUrgentOrWorse() || plan.turnLevel() == ClinicalPriority.P2_URGENT) {
            parts.add(shortGuidance);
        }
        parts.add(plan.handoff() ? handoffText() : fallbackReply.strip());
        if (plan.offerHuman()) {
            parts.add(texts.humanOffer().strip());
        }
        return join(parts.toArray(String[]::new));
    }

    /**
     * Modo restrito ({@code WAITING_HUMAN}). Confirmação de recebimento, sem prometer notificação. Indício de
     * emergência: orientação no início. Falha: confirmação + orientação curta (fail-safe). Prazo vencido:
     * orientação de contato direto (P-02).
     */
    String composeWaiting(ClinicalPriority turnLevel, boolean possibleEmergency, boolean failed, boolean overdueNow) {
        List<String> parts = new ArrayList<>();
        if (possibleEmergency || turnLevel == ClinicalPriority.P1_POSSIBLE_EMERGENCY) {
            parts.add(emergency);
        } else if (turnLevel == ClinicalPriority.P2_URGENT) {
            parts.add(shortGuidance);
        }
        parts.add(texts.waitingConfirmation().strip());
        if (failed && parts.size() == 1) {
            parts.add(shortGuidance);
        }
        if (overdueNow) {
            parts.add(overdue);
        }
        return join(parts.toArray(String[]::new));
    }

    private String handoffText() {
        return texts.handoff().strip();
    }

    private static String join(String... parts) {
        return String.join("\n\n", java.util.Arrays.stream(parts).filter(p -> p != null && !p.isBlank()).toList());
    }

    private static String approved(ClinicKnowledge knowledge, String key) {
        return knowledge.item(key).filter(KnowledgeItem::isKnown).map(i -> i.value().toString().strip()).orElse(null);
    }
}
