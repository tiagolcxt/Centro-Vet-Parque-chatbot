package com.clinicavet.assistant.shared.exception;

import java.util.List;

/** Tentativa de concluir algo sem os dados obrigatórios. Mapeada para HTTP 422. */
public class MissingRequiredFieldsException extends DomainException {

    private final List<String> missingFields;

    public MissingRequiredFieldsException(List<String> missingFields) {
        super("campos obrigatórios ausentes: " + String.join(", ", missingFields));
        this.missingFields = List.copyOf(missingFields);
    }

    public List<String> missingFields() {
        return missingFields;
    }
}
