package com.clinicavet.assistant.platform;

import java.util.Arrays;
import java.util.Optional;

/**
 * Catálogo fixo de permissões (ADR-0024, DA-49), no formato {@code modulo:acao}. Os perfis da clínica são conjuntos
 * destas permissões: a clínica cria e edita perfis, mas não cria permissões.
 */
public enum Permission {
    CONVERSATION_VIEW("conversation:view"),
    CONVERSATION_ASSUME("conversation:assume"),
    CONVERSATION_REPLY("conversation:reply"),
    CONVERSATION_TRANSFER("conversation:transfer"),
    REQUEST_VIEW("request:view"),
    REQUEST_ASSIGN("request:assign"),
    REQUEST_CLOSE("request:close"),
    CUSTOMER_VIEW("customer:view"),
    CUSTOMER_EDIT("customer:edit"),
    CUSTOMER_EXPORT("customer:export"),
    CUSTOMER_ERASE("customer:erase"),
    CLINICAL_NOTES_VIEW("clinical-notes:view"),
    CONFIG_EDIT("config:edit"),
    CONFIG_PUBLISH("config:publish"),
    TEAM_MANAGE("team:manage"),
    AUDIT_VIEW("audit:view");

    private final String code;

    Permission(String code) {
        this.code = code;
    }

    /** Código usado na autorização (autoridade do Spring Security) e na API. */
    public String code() {
        return code;
    }

    public static Optional<Permission> fromCode(String code) {
        return Arrays.stream(values()).filter(p -> p.code.equals(code)).findFirst();
    }
}
