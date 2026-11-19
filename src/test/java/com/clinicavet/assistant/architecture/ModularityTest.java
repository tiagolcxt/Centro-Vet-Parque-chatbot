package com.clinicavet.assistant.architecture;

import com.clinicavet.assistant.AssistantApplication;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Fronteiras entre módulos (ADR-0020, DA-52): sem ciclos e sem acesso a pacotes internos de outro módulo.
 * Um módulo é um subpacote direto de {@code com.clinicavet.assistant}; o que outro módulo pode usar está declarado
 * com {@code @NamedInterface} no {@code package-info.java} de cada pacote exposto.
 */
class ModularityTest {

    static final ApplicationModules MODULES = ApplicationModules.of(AssistantApplication.class);

    @Test
    void modulesRespectTheirBoundaries() {
        MODULES.verify();
    }
}
