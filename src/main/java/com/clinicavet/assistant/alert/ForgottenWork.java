package com.clinicavet.assistant.alert;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Fonte de trabalho que pode ser esquecido (E3, achado A10 da simulação de 1 mês). Cada módulo dono do trabalho
 * implementa: o atendimento (conversa aguardando a equipe) e as solicitações (solicitação sem responsável). O módulo de
 * alertas só compara o tempo de espera com o prazo e guarda o alerta; não conhece conversa nem solicitação.
 */
public interface ForgottenWork {

    enum Kind {
        /** O tutor está esperando alguém da equipe responder. */
        CONVERSATION_WAITING,
        /** Uma tarefa está pendente e ninguém pegou para si. */
        REQUEST_UNASSIGNED
    }

    /**
     * Uma pendência agora, na clínica atual.
     *
     * @param since        desde quando está pendente; identifica o episódio
     * @param deadline     prazo para alguém pegar
     * @param departmentId setor responsável (decide quem vê o alerta antes de escalar)
     * @param title        quem ou o quê, para a equipe (ex.: nome do tutor)
     * @param what         o motivo em poucas palavras (ex.: "Urgente")
     */
    record Pending(UUID subjectId, Instant since, Duration deadline, UUID departmentId, String title, String what) {
    }

    Kind kind();

    List<Pending> pending(Instant now);
}
