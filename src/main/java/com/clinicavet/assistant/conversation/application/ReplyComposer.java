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
 * Compõe o texto final ao tutor a partir da decisão do backend (ADR-0002 reconciliação, ADR-0007, H6–H8, DA-40).
 * <ul>
 *   <li><b>P1 / possível emergência:</b> orientação de emergência <b>primeiro e sozinha</b> (com a clínica fechada, o
 *       aviso e onde procurar fora do horário), depois o registro e o próximo passo. O texto do modelo não é usado.</li>
 *   <li><b>P2:</b> orientação curta de quando procurar atendimento imediato <b>antes</b> do resto (H6).</li>
 *   <li><b>Pedido explícito de humano:</b> só a confirmação de registro e o próximo passo, sem pedir justificativa
 *       nem tentar resolver pela IA (diretriz 3.3, CT-02).</li>
 *   <li><b>Transferência ou pedido registrado:</b> o texto do modelo, o aviso (conversa registrada / pedido
 *       registrado) e o <b>próximo passo</b> ({@link NextStep}: ligar para a recepção, conforme a clínica esteja
 *       aberta). Nenhum texto afirma notificação nem promete contato.</li>
 *   <li><b>Aguardando a equipe</b> ({@code WAITING_HUMAN}, DA-40): o modelo responde o que é da clínica; o backend
 *       põe a orientação antes, se houver risco, e o próximo passo quando é urgente ou o prazo venceu.</li>
 * </ul>
 * A orientação de emergência e o contato direto vêm da base (P-01, P-02); enquanto não aprovados, entram os textos
 * provisórios da configuração, aceitáveis só com dados fictícios (ADR-0014).
 */
@Component
class ReplyComposer {

    private final HandoffSettings.Replies texts;
    private final ClinicKnowledge knowledge;

    ReplyComposer(HandoffSettings settings, ClinicKnowledge knowledge) {
        this.texts = settings.replies();
        this.knowledge = knowledge;
    }

    // Lidos a cada resposta: a base é a da clínica atual e muda ao publicar (ADR-0026).
    private String emergencyText() {
        String policy = approved(knowledge, "approved-texts.emergency-policy");
        return policy != null ? policy : texts.emergencyPlaceholder().strip();
    }

    private String shortGuidanceText() {
        String policy = approved(knowledge, "approved-texts.emergency-policy");
        return policy != null ? policy : texts.shortGuidancePlaceholder().strip();
    }

    /**
     * O que o composer precisa saber do turno.
     *
     * @param nextStep        "ligue para a recepção…" ({@link NextStep}), ou {@code null} se a base não tem como dizer
     * @param emergencyClosed aviso de clínica fechada (e onde procurar) para emergência, ou {@code null}
     */
    record Plan(ClinicalPriority turnLevel, boolean possibleEmergency, boolean explicitRequest, boolean handoff,
                boolean register, boolean offerHuman, String nextStep, String emergencyClosed, String department) {

        Plan(ClinicalPriority turnLevel, boolean possibleEmergency, boolean explicitRequest, boolean handoff,
             boolean register, boolean offerHuman, String nextStep, String emergencyClosed) {
            this(turnLevel, possibleEmergency, explicitRequest, handoff, register, offerHuman, nextStep,
                    emergencyClosed, null);
        }

        Plan(ClinicalPriority turnLevel, boolean possibleEmergency, boolean explicitRequest, boolean handoff,
             boolean offerHuman) {
            this(turnLevel, possibleEmergency, explicitRequest, handoff, false, offerHuman, null, null);
        }

        boolean emergency() {
            return possibleEmergency || turnLevel == ClinicalPriority.P1_POSSIBLE_EMERGENCY;
        }
    }

    /**
     * Turno em {@code WAITING_HUMAN}.
     *
     * @param signals       sinais do turno
     * @param previousReply a última resposta do assistente nesta conversa, ou {@code null}
     */
    record WaitingPlan(ClinicalPriority turnLevel, boolean possibleEmergency, boolean overdue, Set<TurnSignal> signals,
                       String previousReply, String nextStep, String emergencyClosed) {

        boolean emergency() {
            return possibleEmergency || turnLevel == ClinicalPriority.P1_POSSIBLE_EMERGENCY;
        }
    }

    /** Modo principal, turno válido. */
    String composeValid(Plan plan, String modelReply) {
        if (plan.emergency()) {
            return join(emergencyText(), plan.emergencyClosed(), handoffText(plan), plan.nextStep());
        }
        List<String> parts = new ArrayList<>();
        if (plan.turnLevel() == ClinicalPriority.P2_URGENT) {
            parts.add(shortGuidanceText());
        }
        if (plan.explicitRequest()) {
            parts.add(handoffText(plan));
        } else {
            parts.add(modelReply);
            if (plan.handoff()) {
                parts.add(handoffText(plan));
            } else if (plan.register()) {
                parts.add(texts.register().strip());
            }
        }
        if (plan.explicitRequest() || plan.handoff() || plan.register()) {
            parts.add(plan.nextStep());
        }
        if (plan.offerHuman()) {
            parts.add(texts.humanOffer().strip());
        }
        return join(parts.toArray(String[]::new));
    }

    /**
     * Modo principal, turno com falha. Se a decisão é encaminhar (pedido explícito, prioridade, transferência
     * anunciada), a resposta segura é a de encaminhamento, com o próximo passo. Conversa em P1/P2: a resposta segura
     * inclui a orientação curta (H8).
     */
    String composeFailure(Plan plan, String fallbackReply, ClinicalPriority conversationLevel) {
        if (plan.emergency()) {
            return join(emergencyText(), plan.emergencyClosed(), handoffText(plan), plan.nextStep());
        }
        List<String> parts = new ArrayList<>();
        if (conversationLevel.isUrgentOrWorse() || plan.turnLevel() == ClinicalPriority.P2_URGENT) {
            parts.add(shortGuidanceText());
        }
        if (plan.handoff()) {
            parts.add(handoffText(plan));
            parts.add(plan.nextStep());
        } else {
            parts.add(fallbackReply.strip());
            if (plan.register()) {
                parts.add(texts.register().strip());
            }
            // DA-41: a resposta segura nunca é um beco sem saída; o tutor sempre recebe como falar com a equipe.
            parts.add(plan.nextStep());
        }
        if (plan.offerHuman()) {
            parts.add(texts.humanOffer().strip());
        }
        return join(parts.toArray(String[]::new));
    }

    /**
     * {@code WAITING_HUMAN}, turno válido (DA-40). Risco: orientação primeiro e sozinha, sem o texto do modelo. Senão o
     * texto do modelo (que responde o que é da clínica), com a orientação curta antes em P2. O próximo passo entra
     * quando é urgente ou o prazo venceu, sem repetir o da resposta anterior.
     */
    String composeWaitingValid(WaitingPlan plan, String modelReply) {
        if (plan.emergency()) {
            return join(emergencyText(), plan.emergencyClosed(), texts.waitingConfirmation().strip(),
                    overdueText(plan.nextStep()));
        }
        List<String> parts = new ArrayList<>();
        boolean urgent = plan.turnLevel() == ClinicalPriority.P2_URGENT;
        if (urgent) {
            parts.add(shortGuidanceText());
        }
        parts.add(modelReply);
        String step = overdueText(plan.nextStep());
        boolean repeated = plan.previousReply() != null && plan.previousReply().contains(step);
        if (plan.overdue() || (urgent && !repeated)) {   // prazo vencido: sempre; urgência: sem repetir
            parts.add(step);
        }
        return join(parts.toArray(String[]::new));
    }

    /**
     * {@code WAITING_HUMAN}, falha do modelo (fail-safe). Confirmação de recebimento, sem prometer notificação; com
     * confusão, insatisfação ou sofrimento, uma explicação honesta; o mesmo texto nunca vai duas vezes seguidas
     * (CT-19). Indício de emergência: orientação no início. Prazo vencido ou emergência: contato direto ou próximo
     * passo.
     */
    String composeWaiting(ClinicalPriority turnLevel, boolean possibleEmergency, boolean failed, boolean overdueNow) {
        return composeWaiting(new WaitingPlan(turnLevel, possibleEmergency, overdueNow, Set.of(), null, null, null),
                failed);
    }

    String composeWaiting(WaitingPlan plan, boolean failed) {
        List<String> parts = new ArrayList<>();
        if (plan.emergency()) {
            parts.add(emergencyText());
            parts.add(plan.emergencyClosed());
        } else if (plan.turnLevel() == ClinicalPriority.P2_URGENT) {
            parts.add(shortGuidanceText());
        }
        parts.add(waitingText(plan.signals(), plan.previousReply()));
        if (failed && parts.stream().filter(p -> p != null).count() == 1) {
            parts.add(shortGuidanceText());
        }
        if (plan.overdue() || (plan.emergency() && plan.nextStep() != null)) {
            parts.add(overdueText(plan.nextStep()));
        }
        return join(parts.toArray(String[]::new));
    }

    /** Prazo vencido: o texto aprovado (P-02); senão o próximo passo; senão o provisório da configuração. */
    private String overdueText(String nextStep) {
        String directContact = approved(knowledge, "approved-texts.direct-contact");
        if (directContact != null) {
            return directContact;
        }
        return nextStep != null ? nextStep : texts.overduePlaceholder().strip();
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

    private String handoffText(Plan plan) {
        return texts.handoffFor(plan.department()).strip();
    }

    private static String join(String... parts) {
        List<String> kept = new ArrayList<>();
        for (String part : parts) {
            if (part != null && !part.isBlank() && !kept.contains(part)) {
                kept.add(part);
            }
        }
        return String.join("\n\n", kept);
    }

    private static String approved(ClinicKnowledge knowledge, String key) {
        return knowledge.item(key).filter(KnowledgeItem::isKnown).map(i -> i.value().toString().strip()).orElse(null);
    }
}
