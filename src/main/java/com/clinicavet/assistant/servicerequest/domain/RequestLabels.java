package com.clinicavet.assistant.servicerequest.domain;

/**
 * Rótulos em português das solicitações: planilha, resumo da auditoria e mensagens. Os mesmos da aplicação web
 * ({@code web/src/labels.ts}); vocabulário em docs/plans/revisao-linguagem.md.
 */
public final class RequestLabels {

    private RequestLabels() {
    }

    public static String type(RequestType type) {
        return switch (type) {
            case APPOINTMENT -> "Agendamento";
            case INFORMATION -> "Informação";
            case FOLLOW_UP -> "Acompanhamento";
            case HUMAN_CONTACT -> "Atendimento humano";

        };
    }

    public static String status(RequestStatus status) {
        return switch (status) {
            case COLLECTING -> "Aguardando dados do tutor";
            case PENDING -> "Pendente";
            case IN_PROGRESS -> "Em andamento";
            case RESOLVED -> "Concluída";
            case CANCELLED -> "Cancelada";
        };
    }

    public static String outcome(RequestOutcome outcome) {
        return switch (outcome) {
            case SCHEDULED_BY_STAFF -> "Agendado pela equipe";
            case INFORMATION_PROVIDED -> "Informação passada ao tutor";
            case HANDLED_OUTSIDE -> "Resolvido fora do sistema (telefone, balcão)";
            case NO_AVAILABILITY -> "Sem disponibilidade";
            case CUSTOMER_GAVE_UP -> "Tutor desistiu";
            case NO_RESPONSE -> "Tutor não respondeu";
            case DUPLICATE -> "Solicitação repetida";
            case OTHER -> "Outro";
        };
    }

    public static String demandType(DemandType type) {
        return switch (type) {
            case APPOINTMENT -> "Agendamento";
            case CLINICAL_QUESTION -> "Dúvida clínica";
            case ADMINISTRATIVE_INFO -> "Informação administrativa";
            case COMMERCIAL -> "Produto ou serviço do pet shop";
            case GROOMING -> "Banho e tosa";
            case COMPLAINT -> "Reclamação";
            case HUMAN_CONTACT -> "Atendimento humano";
            case OTHER -> "Outros";
        };
    }
}
