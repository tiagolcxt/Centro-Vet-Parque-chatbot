package com.clinicavet.assistant.conversation.application;

import com.clinicavet.assistant.conversation.application.rules.UnproductiveConversationDetector;
import com.clinicavet.assistant.conversation.domain.ClinicalPriority;
import com.clinicavet.assistant.conversation.domain.HandoffPolicy;
import com.clinicavet.assistant.conversation.domain.Message;
import com.clinicavet.assistant.conversation.domain.PriorityPolicy;
import com.clinicavet.assistant.conversation.domain.TurnSignal;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Junta, para um turno do modo principal, o que os componentes determinísticos decidem: prioridade
 * ({@link PriorityPolicy}), conversa improdutiva (DA-31) e encaminhamento ({@link HandoffPolicy}). Usado duas
 * vezes: na validação de cada tentativa (para a reconciliação "texto anuncia transferência") e na transação 2.
 */
@Component
class TurnDecider {

    /** O que o turno sabe antes da resposta do modelo. */
    record Context(ClinicalPriority conversationLevel, boolean offerAlreadyMade, List<Message> previous,
                   String customerText, Optional<String> explicitRequest, Optional<ClinicalPriority> alertFloor) {
    }

    record Decision(PriorityPolicy.Assessment priority, HandoffPolicy.Decision handoff, Set<TurnSignal> signals,
                    boolean explicitRequest) {
    }

    /**
     * Anúncio de transferência no texto do modelo (ADR-0002, reconciliação). "Encaminhei/transferi" já são
     * reprovados pela V6; aqui entram os anúncios no futuro ou no presente.
     */
    private static final Pattern TRANSFER = Pattern.compile(
            "\\b(vou|irei)\\s(te\\s|lhe\\s)?(transferir|encaminhar|passar|repassar)\\b"
                    + "|\\b(estou|to|vou estar)\\s(te\\s|lhe\\s)?(transferindo|encaminhando|passando)\\b"
                    + "|\\bvou\\schamar\\s(um|uma|alguem|a equipe|o veterinario|a veterinaria|um atendente)\\b");

    private final UnproductiveConversationDetector unproductive;

    TurnDecider(UnproductiveConversationDetector unproductive) {
        this.unproductive = unproductive;
    }

    /** Turno válido. */
    Decision decideValid(Context ctx, AssistantTurn turn) {
        Set<TurnSignal> signals = signalsWith(turn.signals(), ctx);
        PriorityPolicy.Assessment priority = PriorityPolicy.assess(ctx.conversationLevel(), new PriorityPolicy.Inputs(
                turn.priority(), turn.allIntents(), turn.signals(), turn.possibleEmergency(), turn.clinicalUncertainty(),
                ctx.alertFloor()));
        HandoffPolicy.Unproductive level = unproductive.evaluate(ctx.previous(), ctx.customerText(), false, turn.reply());
        if (level != HandoffPolicy.Unproductive.NONE) {
            signals.add(TurnSignal.UNPRODUCTIVE_CONVERSATION);
        }
        HandoffPolicy.Decision handoff = HandoffPolicy.decide(new HandoffPolicy.Inputs(
                ctx.explicitRequest().isPresent(), turn.allIntents(), turn.handoffRequested(), turn.possibleEmergency(),
                turn.signals(), priority.turnLevel(), level, false, ctx.offerAlreadyMade()));
        return new Decision(priority, handoff, signals, ctx.explicitRequest().isPresent());
    }

    /** Turno com falha: só o que é determinístico (detectores e nível atual), H8. */
    Decision decideFailure(Context ctx, boolean failureNeedsHandoff) {
        Set<TurnSignal> signals = signalsWith(Set.of(), ctx);
        PriorityPolicy.Assessment priority = PriorityPolicy.assess(ctx.conversationLevel(),
                PriorityPolicy.Inputs.deterministicOnly(ctx.alertFloor()));
        HandoffPolicy.Unproductive level = unproductive.evaluate(ctx.previous(), ctx.customerText(), true, null);
        if (level != HandoffPolicy.Unproductive.NONE) {
            signals.add(TurnSignal.UNPRODUCTIVE_CONVERSATION);
        }
        HandoffPolicy.Decision handoff = HandoffPolicy.decide(new HandoffPolicy.Inputs(
                ctx.explicitRequest().isPresent(), Set.of(), false, false, Set.of(), priority.turnLevel(), level,
                failureNeedsHandoff, ctx.offerAlreadyMade()));
        return new Decision(priority, handoff, signals, ctx.explicitRequest().isPresent());
    }

    /** O texto anuncia transferência mas a decisão não é encaminhar: inconsistente. */
    Optional<FailureKind> reconcile(Context ctx, AssistantTurn turn) {
        String text = com.clinicavet.assistant.conversation.application.rules.RuleText.normalize(turn.reply());
        if (TRANSFER.matcher(text).find() && !decideValid(ctx, turn).handoff().handsOff()) {
            return Optional.of(FailureKind.TRANSFER_WITHOUT_HANDOFF);
        }
        return Optional.empty();
    }

    private static Set<TurnSignal> signalsWith(Set<TurnSignal> base, Context ctx) {
        Set<TurnSignal> signals = EnumSet.noneOf(TurnSignal.class);
        signals.addAll(base);
        if (ctx.alertFloor().isPresent()) {
            signals.add(TurnSignal.ALERT_EXPRESSION);
        }
        return signals;
    }
}
