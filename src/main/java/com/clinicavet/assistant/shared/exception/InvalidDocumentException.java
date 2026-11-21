package com.clinicavet.assistant.shared.exception;

import java.util.List;

/**
 * Documento inválido, com todos os problemas de uma vez (ex.: configuração da clínica, ADR-0026). Cada problema
 * começa pela chave do campo ({@code hours.monday: ...}), para a tela marcar o campo. Mapeada para HTTP 422.
 */
public class InvalidDocumentException extends DomainException {

    private final List<String> problems;

    public InvalidDocumentException(String message, List<String> problems) {
        super(message);
        this.problems = List.copyOf(problems);
    }

    public List<String> problems() {
        return problems;
    }
}
