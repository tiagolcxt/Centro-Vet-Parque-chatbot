package com.clinicavet.assistant.support;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Teste que chama a OpenAI de verdade (DM-13). Roda só pela task {@code aiTest} e só se houver
 * {@code OPENAI_API_KEY} como variável de ambiente <b>ou</b> no {@code .env} do projeto.
 * Sem chave, o teste aparece como <i>desabilitado</i>, com o motivo, em vez de passar em silêncio.
 * Somente dados fictícios.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Tag("ai")
@ExtendWith(RequiresOpenAiKey.Condition.class)
public @interface RequiresOpenAiKey {

    final class Condition implements ExecutionCondition {

        @Override
        public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
            String fromEnv = System.getenv("OPENAI_API_KEY");
            if (fromEnv != null && !fromEnv.isBlank()) {
                return ConditionEvaluationResult.enabled("OPENAI_API_KEY na variável de ambiente");
            }
            if (keyInDotEnv()) {
                return ConditionEvaluationResult.enabled("OPENAI_API_KEY no .env");
            }
            return ConditionEvaluationResult.disabled(
                    "OPENAI_API_KEY ausente (nem variável de ambiente, nem .env): teste com IA real não executado");
        }

        private static boolean keyInDotEnv() {
            Path dotEnv = Path.of(".env");
            if (!Files.isRegularFile(dotEnv)) {
                return false;
            }
            try {
                return Files.readAllLines(dotEnv, StandardCharsets.UTF_8).stream()
                        .map(String::strip)
                        .anyMatch(line -> line.startsWith("OPENAI_API_KEY=")
                                && line.length() > "OPENAI_API_KEY=".length());
            } catch (IOException e) {
                return false;
            }
        }
    }
}
