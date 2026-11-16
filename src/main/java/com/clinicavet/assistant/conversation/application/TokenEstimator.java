package com.clinicavet.assistant.conversation.application;

/** Estimativa de tokens de um texto, para o orçamento do histórico (ADR-0004). */
public interface TokenEstimator {

    int estimate(String text);
}
