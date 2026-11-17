package com.clinicavet.assistant.servicerequest.domain;

/**
 * Campos que podem ser exigidos para concluir a coleta ({@code submit()}).
 * <p>
 * Quais são obrigatórios para cada tipo é decisão da clínica (P-13); por isso o conjunto exigido é
 * passado de fora e não fixado aqui. A preferência de período é opcional (P-13 provisório) e não entra aqui.
 */
public enum RequestField {
    SUMMARY,
    TUTOR,
    PET,
    REASON
}
