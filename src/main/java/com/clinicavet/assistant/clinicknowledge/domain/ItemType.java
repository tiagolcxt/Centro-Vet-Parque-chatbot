package com.clinicavet.assistant.clinicknowledge.domain;

/**
 * Tipo de um item da base institucional (ADR-0003, item 5). O tipo define como o valor é validado no
 * arquivo e como aparece no prompt. O conteúdo é da clínica (P-11); o formato é técnico.
 */
public enum ItemType {
    /** Texto livre (até 1.000 caracteres). */
    TEXT,
    /** Telefone: 8 a 13 dígitos, com ou sem +55, DDD e separadores. Entre aspas no YAML. */
    PHONE,
    EMAIL,
    /** Site, com ou sem {@code https://}. */
    URL,
    /** Perfil de rede social: {@code @perfil}. */
    HANDLE,
    /** CEP: {@code 00000-000} ou {@code 00000000}. */
    ZIP,
    BOOLEAN,
    /** Preço inteiro em centavos ({@code 15000} = R$ 150,00). */
    PRICE_CENTS,
    /** Lista de intervalos {@code HH:mm-HH:mm}; lista vazia = fechado. */
    HOURS,
    /** Lista de textos (ex.: expressões de alerta, ADR-0019). Lista vazia não é aceita: use null. */
    TEXT_LIST
}
