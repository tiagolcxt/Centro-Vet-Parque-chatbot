package com.clinicavet.assistant.support;

import com.clinicavet.assistant.platform.ClinicId;
import com.clinicavet.assistant.platform.CurrentClinic;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Todo teste roda, na thread do teste, com a clínica fictícia criada pela V5 (ADR-0021). Registrada para todos os
 * testes em {@code META-INF/services}. Testes de isolamento trocam de clínica com {@link CurrentClinic#call}.
 * As requisições HTTP rodam na thread do servidor, com a clínica da chave usada.
 */
public class FictitiousClinicExtension implements BeforeEachCallback, AfterEachCallback {

    /** Clínica fictícia da V5, dona da chave {@link IntegrationTest#API_KEY}. */
    public static final ClinicId CLINIC = ClinicId.of("00000000-0000-4000-8000-000000000001");

    private static final ExtensionContext.Namespace NS = ExtensionContext.Namespace.create(FictitiousClinicExtension.class);

    @Override
    public void beforeEach(ExtensionContext context) {
        context.getStore(NS).put("scope", CurrentClinic.open(CLINIC));
    }

    @Override
    public void afterEach(ExtensionContext context) {
        CurrentClinic.Scope scope = context.getStore(NS).remove("scope", CurrentClinic.Scope.class);
        if (scope != null) {
            scope.close();
        }
    }
}
