package com.clinicavet.assistant.platform.infrastructure;

import com.clinicavet.assistant.platform.ClinicId;
import com.clinicavet.assistant.platform.CurrentClinic;
import org.hibernate.cfg.MultiTenancySettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.UUID;

/**
 * Liga o {@link CurrentClinic} ao {@code @TenantId} do Hibernate (ADR-0021). Verificado no Hibernate 7.2.7 em
 * 2026-11-18: o filtro vale para {@code findById}, {@code em.find}, JPQL, consultas derivadas e exclusões; consultas
 * nativas <b>não</b> são filtradas e recebem {@code clinic_id} explicitamente (teste em {@code TenancyRulesTest}).
 */
@Configuration
class TenantConfig {

    @Bean
    HibernatePropertiesCustomizer clinicTenantResolver() {
        CurrentTenantIdentifierResolver<UUID> resolver = new CurrentTenantIdentifierResolver<>() {
            @Override
            public UUID resolveCurrentTenantIdentifier() {
                return CurrentClinic.get().map(ClinicId::value).orElse(CurrentClinic.NONE);
            }

            @Override
            public boolean validateExistingCurrentSessions() {
                return false;
            }
        };
        return properties -> properties.put(MultiTenancySettings.MULTI_TENANT_IDENTIFIER_RESOLVER, resolver);
    }
}
