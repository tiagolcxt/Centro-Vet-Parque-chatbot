package com.clinicavet.assistant.shared.exception;

/**
 * Outra mensagem ou ação alterou a conversa enquanto o modelo respondia (DA-21). Vira {@code 409}:
 * a mensagem do tutor já está gravada e entra no histórico do turno seguinte; quem chamou não reenvia.
 */
public class ConcurrentTurnException extends DomainException {

    public ConcurrentTurnException() {
        super("A conversa recebeu outra mensagem ou foi alterada durante o processamento. "
                + "A mensagem foi registrada, sem resposta; não é preciso reenviar.");
    }
}
