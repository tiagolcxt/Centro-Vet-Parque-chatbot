package com.clinicavet.assistant.conversation.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;

/**
 * Chama o modelo e classifica a resposta, com no máximo uma nova tentativa por turno (DA-28), só
 * para falhas repetíveis: timeout, erro transitório, resposta truncada, inválida ou inconsistente.
 * Recusa e erro permanente não são repetidos.
 * <p>
 * O log tem só metadados (resultado, latência, tokens, modelo), nunca conteúdo (DM-12).
 */
@Component
class TurnExecutor {

    private static final Logger log = LoggerFactory.getLogger(TurnExecutor.class);

    static final int MAX_ATTEMPTS = 2;

    private final AssistantModel model;
    private final TurnResultClassifier classifier;
    private final Duration backoff;
    private final Clock clock;

    TurnExecutor(AssistantModel model, TurnResultClassifier classifier, ConversationSettings settings, Clock clock) {
        this.model = model;
        this.classifier = classifier;
        this.backoff = settings.retryBackoff();
        this.clock = clock;
    }

    TurnOutcome execute(AssistantRequest request) {
        TurnOutcome outcome = attempt(request, 1);
        if (outcome instanceof TurnOutcome.Failed failed && failed.kind().retryable()) {
            pause();
            outcome = attempt(request, MAX_ATTEMPTS);
        }
        return outcome;
    }

    private TurnOutcome attempt(AssistantRequest request, int attempt) {
        long start = System.nanoTime();
        try {
            ModelReply reply = model.respond(request);
            TurnOutcome outcome = classifier.classify(reply, attempt);
            log.info("Chamada ao modelo [tentativa={}, resultado={}, finishReason={}, modelo={}, "
                            + "tokensEntrada={}, tokensSaida={}, ms={}]",
                    attempt, describe(outcome), reply.finishReason(), reply.model(),
                    reply.inputTokens(), reply.outputTokens(), elapsedMillis(start));
            return outcome;
        } catch (AssistantModelException e) {
            FailureKind kind = switch (e.kind()) {
                case TIMEOUT -> FailureKind.TIMEOUT;
                case TRANSIENT -> FailureKind.TECHNICAL_TRANSIENT;
                case PERMANENT -> FailureKind.TECHNICAL_PERMANENT;
            };
            log.warn("Chamada ao modelo falhou [tentativa={}, resultado={}, motivo={}, ms={}]",
                    attempt, kind, e.getMessage(), elapsedMillis(start));
            return new TurnOutcome.Failed(kind, attempt);
        } catch (RuntimeException e) {
            // Defeito inesperado no adaptador: falha segura, sem repetir. Só o tipo no log.
            log.error("Erro inesperado na chamada ao modelo [tentativa={}, tipo={}]", attempt, e.getClass().getName());
            return new TurnOutcome.Failed(FailureKind.TECHNICAL_PERMANENT, attempt);
        }
    }

    private void pause() {
        if (backoff.isZero() || backoff.isNegative()) {
            return;
        }
        try {
            Thread.sleep(backoff);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    private static String describe(TurnOutcome outcome) {
        return switch (outcome) {
            case TurnOutcome.Valid v -> "VALID";
            case TurnOutcome.Failed f -> f.kind().name();
        };
    }
}
