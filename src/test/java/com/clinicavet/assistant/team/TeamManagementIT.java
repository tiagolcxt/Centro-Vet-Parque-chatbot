package com.clinicavet.assistant.team;

import com.clinicavet.assistant.platform.ClinicId;
import com.clinicavet.assistant.platform.CurrentClinic;
import com.clinicavet.assistant.platform.RoleTemplate;
import com.clinicavet.assistant.platform.Roles;
import com.clinicavet.assistant.support.IntegrationTest;
import com.clinicavet.assistant.support.StaffClient;
import com.clinicavet.assistant.support.TestStaff;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Equipe pela tela (revisão pré-piloto, E1): convidar, editar, desativar, reativar, e a clínica nunca fica sem quem
 * administre a equipe. Clínica própria e fictícia por teste, para contar administradores sem interferência.
 */
@IntegrationTest
class TeamManagementIT {

    @Value("${local.server.port}")
    int port;

    @Autowired
    ApplicationContext context;

    @Autowired
    JdbcTemplate jdbc;

    private ClinicId newClinic() {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO clinic (id, name, time_zone, status, activation_level, created_at)
                VALUES (UUID_TO_BIN(?), 'Clínica Fictícia da Equipe', 'America/Sao_Paulo', 'ACTIVE', 3, UTC_TIMESTAMP(6))
                """, id.toString());
        return new ClinicId(id);
    }

    private UUID roleId(ClinicId clinic, RoleTemplate template) {
        return CurrentClinic.call(clinic, () -> context.getBean(Roles.class).ensureDefaults().get(template));
    }

    private String employeeId(StaffClient admin, String email) {
        for (JsonNode e : admin.get("/app/api/employees").body()) {
            if (e.path("email").asString().equals(email)) {
                return e.path("id").asString();
            }
        }
        throw new AssertionError("funcionário não encontrado: " + email);
    }

    @Test
    void inviteEditDisableAndReactivate_keepsTheSameAccount() {
        ClinicId clinic = newClinic();
        StaffClient admin = TestStaff.admin(port, context, clinic);
        String email = "recepcao-" + UUID.randomUUID() + "@ficticio.test";

        StaffClient.Response invited = admin.post("/app/api/employees", Map.of("displayName", "Recepção Fictícia",
                "email", email, "roleId", roleId(clinic, RoleTemplate.RECEPTION), "departmentIds", List.of()));
        assertThat(invited.status()).isEqualTo(201);
        String token = invited.body().path("invite").path("token").asString();
        assertThat(token).isNotBlank();
        String id = invited.body().path("employee").path("id").asString();

        StaffClient person = new StaffClient(port);
        assertThat(person.acceptInvite(token, TestStaff.PASSWORD).status()).isEqualTo(204);
        assertThat(person.login(email, TestStaff.PASSWORD).status()).isEqualTo(200);

        assertThat(admin.put("/app/api/employees/" + id, Map.of("displayName", "Recepção Fictícia 2",
                "roleId", roleId(clinic, RoleTemplate.RECEPTION), "departmentIds", List.of(),
                "jobTitle", "Recepcionista")).body().path("jobTitle").asString()).isEqualTo("Recepcionista");

        assertThat(admin.post("/app/api/employees/" + id + "/disable", Map.of()).status()).isEqualTo(204);
        assertThat(new StaffClient(port).login(email, TestStaff.PASSWORD).status()).isEqualTo(401);

        JsonNode back = admin.post("/app/api/employees/" + id + "/enable", Map.of()).body();
        assertThat(back.path("status").asString()).isEqualTo("ACTIVE");
        assertThat(back.path("id").asString()).isEqualTo(id);
        assertThat(new StaffClient(port).login(email, TestStaff.PASSWORD).status()).isEqualTo(200);
    }

    @Test
    void reactivatingSomeoneWhoNeverSetAPassword_goesBackToInvited() {
        ClinicId clinic = newClinic();
        StaffClient admin = TestStaff.admin(port, context, clinic);
        String email = "vet-" + UUID.randomUUID() + "@ficticio.test";
        String id = admin.post("/app/api/employees", Map.of("displayName", "Vet Fictícia", "email", email,
                "roleId", roleId(clinic, RoleTemplate.VETERINARIAN), "departmentIds", List.of()))
                .body().path("employee").path("id").asString();
        admin.post("/app/api/employees/" + id + "/disable", Map.of());

        assertThat(admin.post("/app/api/employees/" + id + "/enable", Map.of()).body().path("status").asString())
                .isEqualTo("INVITED");
        assertThat(admin.post("/app/api/employees/" + id + "/invite", Map.of()).status()).isEqualTo(200);
    }

    @Test
    void theClinicNeverLosesItsLastTeamManager() {
        ClinicId clinic = newClinic();
        StaffClient first = TestStaff.admin(port, context, clinic);
        StaffClient second = TestStaff.member(port, context, clinic, RoleTemplate.ADMIN, null);
        String secondId = employeeId(first, second.get("/app/api/me").body().path("email").asString());
        String firstId = employeeId(first, first.get("/app/api/me").body().path("email").asString());

        // Com dois administradores, um pode desativar o outro.
        assertThat(first.post("/app/api/employees/" + secondId + "/disable", Map.of()).status()).isEqualTo(204);

        // Sobrou um: ele não perde o acesso de administrar, nem por troca de perfil.
        StaffClient.Response demote = first.put("/app/api/employees/" + firstId, Map.of("displayName", "Admin",
                "roleId", roleId(clinic, RoleTemplate.RECEPTION), "departmentIds", List.of()));
        assertThat(demote.status()).isEqualTo(409);
        assertThat(demote.body().path("detail").asString()).contains("última pessoa que administra a equipe");
        assertThat(first.post("/app/api/employees/" + firstId + "/disable", Map.of()).status()).isEqualTo(409);
    }

    @Test
    void onlyTeamManagersSeeTheTeam() {
        ClinicId clinic = newClinic();
        TestStaff.admin(port, context, clinic);
        StaffClient reception = TestStaff.member(port, context, clinic, RoleTemplate.RECEPTION, null);
        assertThat(reception.get("/app/api/employees").status()).isEqualTo(403);
        assertThat(reception.post("/app/api/employees/" + UUID.randomUUID() + "/enable", Map.of()).status())
                .isEqualTo(403);
    }
}
