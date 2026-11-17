package com.clinicavet.assistant.clinicknowledge.infrastructure;

import java.util.List;

/** O arquivo da base é inválido: a aplicação não sobe (ADR-0003, fail-fast). Lista todos os problemas. */
public class KnowledgeValidationException extends RuntimeException {

    private final List<String> problems;

    public KnowledgeValidationException(String source, List<String> problems) {
        super("Base institucional inválida (" + source + "):\n  - " + String.join("\n  - ", problems));
        this.problems = List.copyOf(problems);
    }

    public List<String> problems() {
        return problems;
    }
}
