package com.clinicavet.assistant;

import com.clinicavet.assistant.support.FictitiousClinicExtension;
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
 * O contexto só sobe se o Flyway aplicar as migrations e o Hibernate validar o schema
 * ({@code ddl-auto=validate}). Aqui verificamos também as restrições do banco.
 */
@IntegrationTest
class SchemaMigrationIT {

    private static final String CLINIC = FictitiousClinicExtension.CLINIC.toString();

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void flywayAppliedAllMigrations_andHibernateValidatedTheSchema() {
        List<String> versions = jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank", String.class);

        assertThat(versions).containsExactly("1", "2", "3", "4", "5", "6", "7", "8");
        assertThat(jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE()", String.class))
                .map(String::toLowerCase)
                .contains("clinic", "conversation", "message", "tutor", "pet", "service_request", "department", "role",
                        "role_permission", "user_account", "employee", "employee_department", "audit_event",
                        "config_version", "quick_reply");
    }

    @Test
    void databaseRejectsClosedConversationWithoutReason() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO conversation (id, clinic_id, channel, external_contact_id, status, started_at, last_activity_at,
                                          version)
                VALUES (UUID_TO_BIN(?), UUID_TO_BIN(?), 'API', 'ficticio-ck', 'CLOSED', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0)
                """, UUID.randomUUID().toString(), CLINIC))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("ck_conversation");
    }

    @Test
    void databaseRejectsUnknownStatus() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO conversation (id, clinic_id, channel, external_contact_id, status, started_at, last_activity_at,
                                          version)
                VALUES (UUID_TO_BIN(?), UUID_TO_BIN(?), 'API', 'ficticio-ck2', 'CONFIRMED', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0)
                """, UUID.randomUUID().toString(), CLINIC))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("ck_conversation");
    }

    /** V2: o banco impede duas conversas ativas do mesmo contato (ADR-0009), inclusive OPEN + WAITING_HUMAN. */
    @Test
    void databaseRejectsSecondActiveConversationForSameContact() {
        String contact = "ficticio-uk-" + UUID.randomUUID();
        insertConversation(contact, "OPEN");

        assertThatThrownBy(() -> insertConversation(contact, "OPEN"))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("uk_conversation_active_contact");
        assertThatThrownBy(() -> insertConversation(contact, "WAITING_HUMAN"))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("uk_conversation_active_contact");
    }

    /** Encerradas não contam: o mesmo contato pode ter várias, mais uma ativa. */
    @Test
    void databaseAllowsManyClosedConversationsPlusOneActive() {
        String contact = "ficticio-uk-" + UUID.randomUUID();
        insertClosedConversation(contact);
        insertClosedConversation(contact);
        insertConversation(contact, "OPEN");

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM conversation WHERE external_contact_id = ?", Integer.class, contact))
                .isEqualTo(3);
    }

    /** V5: o mesmo contato pode ter uma conversa ativa em cada clínica, e toda conversa tem clínica (ADR-0021). */
    @Test
    void activeConversationIsUniquePerClinic_andClinicIsRequired() {
        String other = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO clinic (id, name, time_zone, status, activation_level, created_at)
                VALUES (UUID_TO_BIN(?), 'Outra Clínica Fictícia', 'America/Sao_Paulo', 'ACTIVE', 1, UTC_TIMESTAMP(6))
                """, other);
        String contact = "ficticio-uk-" + UUID.randomUUID();
        insertConversation(contact, "OPEN");
        insertConversation(other, contact, "OPEN");

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM conversation WHERE external_contact_id = ?", Integer.class, contact))
                .isEqualTo(2);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO conversation (id, channel, external_contact_id, status, started_at, last_activity_at,
                                          version)
                VALUES (UUID_TO_BIN(?), 'API', ?, 'OPEN', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0)
                """, UUID.randomUUID().toString(), "ficticio-sem-clinica"))
                .isInstanceOf(DataAccessException.class);
    }

    private void insertConversation(String contact, String status) {
        insertConversation(CLINIC, contact, status);
    }

    private void insertConversation(String clinic, String contact, String status) {
        boolean waiting = status.equals("WAITING_HUMAN");   // V3: aguardando exige motivo e momento
        jdbc.update("""
                INSERT INTO conversation (id, clinic_id, channel, external_contact_id, status, started_at, last_activity_at,
                                          handoff_reason, handoff_at, version)
                VALUES (UUID_TO_BIN(?), UUID_TO_BIN(?), 'API', ?, ?, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), ?, ?, 0)
                """, UUID.randomUUID().toString(), clinic, contact, status, waiting ? "EXPLICIT_REQUEST" : null,
                waiting ? java.sql.Timestamp.from(java.time.Instant.now()) : null);
    }

    private void insertClosedConversation(String contact) {
        jdbc.update("""
                INSERT INTO conversation (id, clinic_id, channel, external_contact_id, status, close_reason, started_at,
                                          last_activity_at, closed_at, version)
                VALUES (UUID_TO_BIN(?), UUID_TO_BIN(?), 'API', ?, 'CLOSED', 'EXPIRED', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6),
                        UTC_TIMESTAMP(6), 0)
                """, UUID.randomUUID().toString(), CLINIC, contact);
    }
}
