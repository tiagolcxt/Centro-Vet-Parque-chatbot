package com.clinicavet.assistant.conversation.application;

/**
 * Falha técnica ao chamar o modelo. A mensagem nunca contém o conteúdo da conversa nem a chave.
 */
public class AssistantModelException extends RuntimeException {

    public enum Kind {
        /** Sem resposta dentro do prazo (DA-28: 30 s). Repetível. */
        TIMEOUT,
        /** 5xx, 429, falha de rede. Repetível. */
        TRANSIENT,
        /** 4xx (exceto 429): chave inválida, requisição recusada. Repetir não adianta. */
        PERMANENT
    }

    private final Kind kind;

    public AssistantModelException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
