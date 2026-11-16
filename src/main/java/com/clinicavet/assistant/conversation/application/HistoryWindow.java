package com.clinicavet.assistant.conversation.application;

import com.clinicavet.assistant.conversation.domain.Message;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Corta o histórico pelos dois limites de ADR-0004, o que for atingido primeiro: quantidade de
 * mensagens e orçamento estimado de tokens. Remove a partir da mais antiga. A mensagem atual do tutor
 * não passa por aqui: ela nunca é cortada.
 */
final class HistoryWindow {

    /** Acréscimo por mensagem (papel e separadores no formato de chat), na estimativa. */
    static final int PER_MESSAGE_OVERHEAD_TOKENS = 4;

    private HistoryWindow() {
    }

    /**
     * @param previous    mensagens anteriores à atual, em ordem cronológica
     * @param maxMessages quantas mensagens anteriores cabem, no máximo
     * @param tokenBudget orçamento estimado para todas elas
     * @return as mais recentes que cabem, em ordem cronológica
     */
    static List<Message> fit(List<Message> previous, int maxMessages, int tokenBudget, TokenEstimator estimator) {
        List<Message> kept = new ArrayList<>();
        int used = 0;
        for (int i = previous.size() - 1; i >= 0 && kept.size() < maxMessages; i--) {
            Message message = previous.get(i);
            int cost = estimator.estimate(message.content()) + PER_MESSAGE_OVERHEAD_TOKENS;
            if (used + cost > tokenBudget) {
                break;   // daqui para trás só há mensagens mais antigas: ficam de fora também
            }
            used += cost;
            kept.add(message);
        }
        Collections.reverse(kept);
        return List.copyOf(kept);
    }
}
