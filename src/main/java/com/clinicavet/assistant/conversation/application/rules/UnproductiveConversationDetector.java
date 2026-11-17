package com.clinicavet.assistant.conversation.application.rules;

import com.clinicavet.assistant.conversation.domain.HandoffPolicy.Unproductive;
import com.clinicavet.assistant.conversation.domain.Message;
import com.clinicavet.assistant.conversation.domain.MessageAuthor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Conversa improdutiva (ADR-0007, item 10; limiares da DA-31). Determinístico, sem o modelo.
 * <p>
 * Olha as últimas {@link #WINDOW} mensagens anteriores e conta três coisas, por igualdade do texto
 * normalizado:
 * <ul>
 *   <li><b>falhas seguidas</b>: respostas seguras consecutivas, contando a deste turno se ele falhou; um
 *       turno válido zera;</li>
 *   <li><b>tutor repetindo</b>: a mensagem atual igual a mensagens anteriores do tutor;</li>
 *   <li><b>assistente repetindo</b>: a resposta deste turno igual a respostas anteriores, sem contar a segura.</li>
 * </ul>
 * Na {@link #OFFER_AT 2ª} ocorrência de qualquer uma, oferece atendimento humano; na {@link #HANDOFF_AT 3ª},
 * encaminha. Não roda em {@code WAITING_HUMAN} (quem chama garante).
 */
@Component
public class UnproductiveConversationDetector {

    static final int WINDOW = 10;
    static final int OFFER_AT = 2;
    static final int HANDOFF_AT = 3;
    /** Mensagens do tutor com menos palavras que isto não contam como repetição. */
    static final int MIN_WORDS_FOR_REPEAT = 3;

    /**
     * @param previous      mensagens anteriores da conversa, em ordem cronológica, sem a mensagem atual
     * @param customerText  mensagem atual do tutor
     * @param turnFailed    este turno terminou na resposta segura
     * @param assistantText resposta deste turno (ignorada se o turno falhou)
     */
    public Unproductive evaluate(List<Message> previous, String customerText, boolean turnFailed, String assistantText) {
        List<Message> window = previous.subList(Math.max(0, previous.size() - WINDOW), previous.size());

        int consecutiveFailures = turnFailed ? 1 : 0;
        if (turnFailed) {
            for (int i = window.size() - 1; i >= 0; i--) {
                Message message = window.get(i);
                if (message.author() != MessageAuthor.ASSISTANT) {
                    continue;
                }
                if (!message.isFallback()) {
                    break;
                }
                consecutiveFailures++;
            }
        }

        // Respostas curtas ("sim", "ok", "obrigado") se repetem naturalmente: ficam fora desta contagem.
        String current = RuleText.normalize(customerText);
        int customerRepeats = RuleText.wordCount(current) < MIN_WORDS_FOR_REPEAT ? 0 : 1 + (int) window.stream()
                .filter(m -> m.author() == MessageAuthor.CUSTOMER)
                .filter(m -> RuleText.normalize(m.content()).equals(current))
                .count();

        int assistantRepeats = 0;
        if (!turnFailed && assistantText != null) {
            String reply = RuleText.normalize(assistantText);
            assistantRepeats = 1 + (int) window.stream()
                    .filter(m -> m.author() == MessageAuthor.ASSISTANT && !m.isFallback())
                    .filter(m -> RuleText.normalize(m.content()).equals(reply))
                    .count();
        }

        int worst = Math.max(consecutiveFailures, Math.max(customerRepeats, assistantRepeats));
        if (worst >= HANDOFF_AT) {
            return Unproductive.HANDOFF;
        }
        return worst >= OFFER_AT ? Unproductive.OFFER : Unproductive.NONE;
    }
}
