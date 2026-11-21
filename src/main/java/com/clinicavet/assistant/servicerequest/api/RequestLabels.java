package com.clinicavet.assistant.servicerequest.api;

import com.clinicavet.assistant.servicerequest.domain.RequestOutcome;
import com.clinicavet.assistant.servicerequest.domain.RequestStatus;
import com.clinicavet.assistant.servicerequest.domain.RequestType;

/** Rótulos em português da planilha de pedidos (os mesmos da aplicação web, em {@code web/src/labels.ts}). */
final class RequestLabels {

    private RequestLabels() {
    }

    static String type(RequestType type) {
        return switch (type) {
            case APPOINTMENT -> "Agendamento";
            case INFORMATION -> "Informação";
            case FOLLOW_UP -> "Acompanhamento";
            case HUMAN_CONTACT -> "Atendimento humano";

        };
    }

    static String status(RequestStatus status) {
        return switch (status) {
            case COLLECTING -> "Coletando dados";
            case PENDING -> "Pendente";
            case IN_PROGRESS -> "Em andamento";
            case RESOLVED -> "Resolvida";
            case CANCELLED -> "Cancelada";
        };
    }

    static String outcome(RequestOutcome outcome) {
        return switch (outcome) {
            case SCHEDULED_BY_STAFF -> "Agendado pela equipe";
            case INFORMATION_PROVIDED -> "Informação dada";
            case HANDLED_OUTSIDE -> "Resolvido por fora (telefone, balcão)";
            case NO_AVAILABILITY -> "Sem disponibilidade";
            case CUSTOMER_GAVE_UP -> "Tutor desistiu";
            case NO_RESPONSE -> "Tutor não respondeu";
            case DUPLICATE -> "Duplicada";
            case OTHER -> "Outro";
        };
    }
}
