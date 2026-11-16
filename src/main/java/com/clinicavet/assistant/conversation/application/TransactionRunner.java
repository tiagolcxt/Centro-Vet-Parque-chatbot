package com.clinicavet.assistant.conversation.application;

import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/**
 * Transações explícitas do turno (o turno tem duas, com a chamada ao modelo entre elas, fora de
 * transação). {@link #withRetry} repete a transação inteira quando outra operação simultânea sobre o
 * mesmo contato a derrubou: duas aberturas de conversa disputando o índice único da V2, deadlock ou
 * versão desatualizada. Na repetição, tudo é relido do banco.
 */
@Component
class TransactionRunner {

    static final int MAX_ATTEMPTS = 3;

    private final TransactionTemplate template;

    TransactionRunner(PlatformTransactionManager transactionManager) {
        this.template = new TransactionTemplate(transactionManager);
    }

    <T> T inTransaction(Supplier<T> work) {
        return template.execute(status -> work.get());
    }

    <T> T withRetry(Supplier<T> work) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return inTransaction(work);
            } catch (DataIntegrityViolationException | ConcurrencyFailureException e) {
                last = e;
            }
        }
        throw last;
    }
}
