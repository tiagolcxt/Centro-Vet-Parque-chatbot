package com.clinicavet.assistant.servicerequest.domain;

/**
 * Tipo de solicitação para a equipe. {@code APPOINTMENT} é um <b>pedido</b> de agendamento,
 * nunca um agendamento confirmado (ADR-0011).
 */
public enum RequestType {
    APPOINTMENT,
    INFORMATION,
    FOLLOW_UP,
    HUMAN_CONTACT
}
