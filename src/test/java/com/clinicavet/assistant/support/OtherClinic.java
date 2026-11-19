package com.clinicavet.assistant.support;

import com.clinicavet.assistant.platform.ClinicId;
import org.springframework.jdbc.core.JdbcTemplate;

/** Segunda clínica fictícia dos testes de isolamento (ADR-0021). */
public final class OtherClinic {

    public static final ClinicId CLINIC = ClinicId.of(IntegrationTest.OTHER_CLINIC);

    private OtherClinic() {
    }

    /** Cria a clínica se ainda não existir. */
    public static void ensure(JdbcTemplate jdbc) {
        jdbc.update("""
                INSERT IGNORE INTO clinic (id, name, time_zone, status, activation_level, created_at)
                VALUES (UUID_TO_BIN(?), 'Outra Clínica Fictícia', 'America/Sao_Paulo', 'ACTIVE', 3, UTC_TIMESTAMP(6))
                """, IntegrationTest.OTHER_CLINIC);
    }
}
