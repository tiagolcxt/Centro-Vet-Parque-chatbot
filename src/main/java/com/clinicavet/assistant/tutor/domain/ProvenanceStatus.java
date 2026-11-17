package com.clinicavet.assistant.tutor.domain;

/**
 * Status de proveniência de uma informação (ADR-0010). Só {@link #MENTIONED} e {@link #CONFIRMED} são gravados,
 * sempre junto com a conversa em que aconteceram. {@link #REGISTERED} e {@link #TO_RECONFIRM} são calculados para
 * as outras conversas (DA-35): informação antiga nunca vale como confirmação atual.
 */
public enum ProvenanceStatus {
    /** MENCIONADA: dita nesta conversa, sem confirmação. */
    MENTIONED,
    /** CONFIRMADA: confirmada pelo tutor nesta conversa (critério de P-08). */
    CONFIRMED,
    /** CADASTRADA: confirmada em outra conversa, dentro da validade. Serve para reconhecer e perguntar. */
    REGISTERED,
    /** A_RECONFIRMAR: nunca confirmada ou fora da validade. Só pode aparecer como pergunta. */
    TO_RECONFIRM;

    /** Status gravável: dado desta conversa. */
    public boolean currentConversation() {
        return this == MENTIONED || this == CONFIRMED;
    }
}
