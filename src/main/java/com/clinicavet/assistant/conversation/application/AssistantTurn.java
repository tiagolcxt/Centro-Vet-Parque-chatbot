package com.clinicavet.assistant.conversation.application;

import com.clinicavet.assistant.conversation.domain.Intent;

import java.util.List;
import java.util.Objects;

/**
 * Saída estruturada de um turno (ADR-0002).
 * <ul>
 *   <li>v1 (Etapa 2): {@code reply} e {@code intent}, esta só registrada;</li>
 *   <li>v2 (Etapa 3): {@code knowledgeRefs}, chaves da base usadas na resposta, e {@code knowledgeGap},
 *       quando o modelo não encontrou a informação ou ela é DESCONHECIDO (ADR-0008).</li>
 * </ul>
 * Encaminhamento e prioridade entram na Etapa 4.
 */
public record AssistantTurn(String reply, Intent intent, List<String> knowledgeRefs, boolean knowledgeGap) {

    public AssistantTurn {
        Objects.requireNonNull(reply, "reply");
        Objects.requireNonNull(intent, "intent");
        knowledgeRefs = List.copyOf(knowledgeRefs);
    }

    /** Turno sem fontes nem lacuna (ex.: saudação). */
    public static AssistantTurn simple(String reply, Intent intent) {
        return new AssistantTurn(reply, intent, List.of(), false);
    }
}
