package com.clinicavet.assistant;

import com.clinicavet.assistant.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * O contexto só sobe se o Flyway aplicar a V1 e o Hibernate validar o schema
 * ({@code ddl-auto=validate}). Aqui verificamos também as restrições do banco.
 */
@IntegrationTest
class SchemaMigrationIT {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void flywayAppliedV1_andHibernateValidatedTheSchema() {
        List<String> versions = jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank", String.class);

        assertThat(versions).containsExactly("1");
        assertThat(jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE()", String.class))
                .map(String::toLowerCase)
                .contains("conversation", "message", "tutor", "pet", "service_request");
    }

    @Test
    void databaseRejectsClosedConversationWithoutReason() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO conversation (id, channel, external_contact_id, status, started_at, last_activity_at, version)
                VALUES (UUID_TO_BIN(?), 'API', 'ficticio-ck', 'CLOSED', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0)
                """, UUID.randomUUID().toString()))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("ck_conversation");
    }

    @Test
    void databaseRejectsUnknownStatus() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO conversation (id, channel, external_contact_id, status, started_at, last_activity_at, version)
                VALUES (UUID_TO_BIN(?), 'API', 'ficticio-ck2', 'CONFIRMED', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0)
                """, UUID.randomUUID().toString()))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("ck_conversation");
    }
}
