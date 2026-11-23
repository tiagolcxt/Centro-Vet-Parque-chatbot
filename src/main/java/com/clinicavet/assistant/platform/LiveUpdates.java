package com.clinicavet.assistant.platform;

import java.util.UUID;

/**
 * Avisos em tempo real para as telas da equipe (RF-S29, SSE). Só levam o tipo e o id: a tela busca os dados pela API,
 * que aplica a permissão. Enviados depois do commit; perder um aviso não perde dado.
 */
public interface LiveUpdates {

    enum Topic {
        CONVERSATION, REQUEST, ALERT
    }

    /** Avisa as sessões da clínica atual que algo mudou (depois do commit da transação atual, se houver). */
    void changed(Topic topic, UUID id);
}
