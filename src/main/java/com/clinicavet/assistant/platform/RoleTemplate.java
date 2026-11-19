package com.clinicavet.assistant.platform;

import java.util.EnumSet;
import java.util.Set;

import static com.clinicavet.assistant.platform.Permission.*;

/**
 * Os seis perfis iniciais do briefing (seção 7), criados em cada clínica como modelos editáveis (DA-49). São pontos
 * de partida: os perfis reais da clínica dependem da P-17.
 */
public enum RoleTemplate {
    ADMIN("Administrador", SectorScope.ALL, EnumSet.allOf(Permission.class)),
    MANAGER("Gerente", SectorScope.ALL,
            EnumSet.complementOf(EnumSet.of(CONFIG_PUBLISH, TEAM_MANAGE, CUSTOMER_ERASE))),
    RECEPTION("Secretária", SectorScope.OWN, EnumSet.of(CONVERSATION_VIEW, CONVERSATION_ASSUME, CONVERSATION_REPLY,
            CONVERSATION_TRANSFER, REQUEST_VIEW, REQUEST_ASSIGN, REQUEST_CLOSE, CUSTOMER_VIEW, CUSTOMER_EDIT)),
    VETERINARIAN("Veterinário", SectorScope.OWN, EnumSet.of(CONVERSATION_VIEW, CONVERSATION_ASSUME,
            CONVERSATION_REPLY, CONVERSATION_TRANSFER, REQUEST_VIEW, REQUEST_ASSIGN, REQUEST_CLOSE, CUSTOMER_VIEW,
            CLINICAL_NOTES_VIEW)),
    PETSHOP_ATTENDANT("Atendente do pet shop", SectorScope.OWN, EnumSet.of(CONVERSATION_VIEW, CONVERSATION_ASSUME,
            CONVERSATION_REPLY, CONVERSATION_TRANSFER, REQUEST_VIEW, REQUEST_ASSIGN, REQUEST_CLOSE, CUSTOMER_VIEW)),
    GROOMER("Tosador", SectorScope.OWN, EnumSet.of(REQUEST_VIEW, REQUEST_ASSIGN, REQUEST_CLOSE, CUSTOMER_VIEW));

    private final String displayName;
    private final SectorScope scope;
    private final Set<Permission> permissions;

    RoleTemplate(String displayName, SectorScope scope, Set<Permission> permissions) {
        this.displayName = displayName;
        this.scope = scope;
        this.permissions = Set.copyOf(permissions);
    }

    public String displayName() {
        return displayName;
    }

    public SectorScope scope() {
        return scope;
    }

    public Set<Permission> permissions() {
        return permissions;
    }
}
