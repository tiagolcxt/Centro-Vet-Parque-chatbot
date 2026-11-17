package com.clinicavet.assistant.conversation.application;

import com.clinicavet.assistant.clinicknowledge.domain.ClinicKnowledge;
import com.clinicavet.assistant.clinicknowledge.domain.KnowledgeItem;
import com.clinicavet.assistant.conversation.domain.ClinicalPriority;
import com.clinicavet.assistant.conversation.domain.TurnSignal;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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
        return composeWaiting(turnLevel, possibleEmergency, failed, overdueNow, Set.of(), null);
    }

    /**
     * Com confusão, insatisfação ou sofrimento, a confirmação vira uma explicação honesta do estado da conversa; e o
     * mesmo texto nunca vai duas vezes seguidas (CT-19, opção A aprovada em 2026-11-17). O modelo continua sem
     * escrever texto livre em {@code WAITING_HUMAN}.
     *
     * @param signals       sinais do modo restrito neste turno
     * @param previousReply a última resposta do assistente nesta conversa, ou {@code null}
     */
    String composeWaiting(ClinicalPriority turnLevel, boolean possibleEmergency, boolean failed, boolean overdueNow,
                          Set<TurnSignal> signals, String previousReply) {
        List<String> parts = new ArrayList<>();
        if (possibleEmergency || turnLevel == ClinicalPriority.P1_POSSIBLE_EMERGENCY) {
            parts.add(emergency);
        } else if (turnLevel == ClinicalPriority.P2_URGENT) {
            parts.add(shortGuidance);
        }
        parts.add(waitingText(signals, previousReply));
        if (failed && parts.size() == 1) {
            parts.add(shortGuidance);
        }
        if (overdueNow) {
            parts.add(overdue);
        }
        return join(parts.toArray(String[]::new));
    }

    private static final Set<TurnSignal> FRUSTRATION = Set.of(TurnSignal.CONFUSION, TurnSignal.DISSATISFACTION,
            TurnSignal.EMOTIONAL_DISTRESS);

    private String waitingText(Set<TurnSignal> signals, String previousReply) {
        String confirmation = texts.waitingConfirmation().strip();
        String explanation = texts.waitingExplanation().strip();
        String repeat = texts.waitingExplanationRepeat().strip();
        String chosen = signals.stream().anyMatch(FRUSTRATION::contains) ? explanation : confirmation;
        if (previousReply != null && previousReply.contains(chosen)) {
            chosen = chosen.equals(explanation) ? repeat : explanation;
            if (previousReply.contains(chosen)) {
                chosen = chosen.equals(repeat) ? explanation : repeat;
            }
        }
        return chosen;
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
