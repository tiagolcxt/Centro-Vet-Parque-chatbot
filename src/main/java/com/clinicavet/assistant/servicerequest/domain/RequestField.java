package com.clinicavet.assistant.servicerequest.domain;

/**
 * Campos que podem ser exigidos para concluir a coleta ({@code submit()}).
 * <p>
 * Quais são obrigatórios para cada tipo é decisão da clínica (P-13); por isso o conjunto exigido é
 * passado de fora e não fixado aqui. Motivo e preferência de período entram na Etapa 5.
 */
public enum RequestField {
    SUMMARY,
    TUTOR,
    PET
}
