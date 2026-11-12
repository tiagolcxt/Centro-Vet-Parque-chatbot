package com.clinicavet.assistant.shared.exception;

/** Recurso inexistente. Mapeada para HTTP 404. */
public class ResourceNotFoundException extends RuntimeException {

    private final String resource;
    private final String id;

    public ResourceNotFoundException(String resource, Object id) {
        super(resource + " não encontrado: " + id);
        this.resource = resource;
        this.id = String.valueOf(id);
    }

    public String resource() {
        return resource;
    }

    public String id() {
        return id;
    }
}
