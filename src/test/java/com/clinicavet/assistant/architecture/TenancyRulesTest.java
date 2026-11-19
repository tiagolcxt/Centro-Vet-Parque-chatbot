package com.clinicavet.assistant.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import jakarta.persistence.Entity;
import org.hibernate.annotations.TenantId;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regras do multi-tenant que o compilador não pega (ADR-0021):
 * <ul>
 *   <li>toda entidade JPA de dados da clínica tem um campo {@code @TenantId};</li>
 *   <li>toda consulta nativa usa {@code clinic_id}, porque o Hibernate não a filtra (verificado em 2026-11-18).</li>
 * </ul>
 */
class TenancyRulesTest {

    /** Entidades que não pertencem a uma clínica (a própria clínica e tabelas globais da plataforma). */
    private static final Set<String> GLOBAL_ENTITIES = Set.of("ClinicEntity", "UserAccountEntity");

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.clinicavet.assistant");

    @Test
    void everyClinicEntity_hasTenantId() {
        List<String> missing = new ArrayList<>();
        List<String> entities = new ArrayList<>();
        for (JavaClass type : CLASSES) {
            if (!type.isAnnotatedWith(Entity.class) || GLOBAL_ENTITIES.contains(type.getSimpleName())) {
                continue;
            }
            entities.add(type.getSimpleName());
            if (type.getAllFields().stream().noneMatch(f -> f.isAnnotatedWith(TenantId.class))) {
                missing.add(type.getName());
            }
        }
        assertThat(entities).as("o teste encontrou as entidades").isNotEmpty();
        assertThat(missing).as("entidades sem @TenantId").isEmpty();
    }

    @Test
    void everyNativeQuery_filtersByClinic() {
        List<String> unsafe = new ArrayList<>();
        int nativeQueries = 0;
        for (JavaClass type : CLASSES) {
            for (JavaMethod method : type.getMethods()) {
                if (!method.isAnnotatedWith(Query.class)) {
                    continue;
                }
                Query query = method.getAnnotationOfType(Query.class);
                if (query.nativeQuery()) {
                    nativeQueries++;
                    if (!query.value().contains("clinic_id = :clinic")) {
                        unsafe.add(type.getSimpleName() + "." + method.getName());
                    }
                }
            }
        }
        assertThat(nativeQueries).as("o teste encontrou as consultas nativas").isPositive();
        assertThat(unsafe).as("consultas nativas sem clinic_id").isEmpty();
    }
}
