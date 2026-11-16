package com.clinicavet.assistant.conversation.application;

import com.clinicavet.assistant.conversation.domain.Intent;

import java.util.Objects;

/**
 * Saída estruturada de um turno, versão 1 (Etapa 2, ADR-0002): o texto ao tutor e a intenção, que
 * por enquanto só é registrada. Fontes e lacunas entram na Etapa 3; encaminhamento e prioridade,
 * na Etapa 4.
 */
public record AssistantTurn(String reply, Intent intent) {

    public AssistantTurn {
        Objects.requireNonNull(reply, "reply");
        Objects.requireNonNull(intent, "intent");
    }
}
