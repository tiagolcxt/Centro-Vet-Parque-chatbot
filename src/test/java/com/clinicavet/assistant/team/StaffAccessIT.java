package com.clinicavet.assistant.team;

import com.clinicavet.assistant.platform.ClinicId;
import com.clinicavet.assistant.platform.CurrentClinic;
import com.clinicavet.assistant.support.ApiClient;
import com.clinicavet.assistant.support.IntegrationTest;
import com.clinicavet.assistant.support.OtherClinic;
import com.clinicavet.assistant.support.StaffClient;
import com.clinicavet.assistant.team.application.TeamService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Login individual, perfis e permissões, convites e desativação (Etapa S1; RF-S01 a RF-S07; ADR-0024). Cada teste
 * cria uma clínica fictícia nova, para não depender dos outros.
 */
@IntegrationTest
class StaffAccessIT {

    private static final String PASSWORD = "senha-ficticia-123";

    @Value("${local.server.port}")
    int port;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TeamService team;

    /** Clínica nova com o primeiro administrador já com senha. Devolve o navegador do administrador logado. */
    private record Clinic(ClinicId id, StaffClient admin, String adminEmail) {
    }

    private Clinic newClinic() {
        ClinicId id = new ClinicId(UUID.randomUUID());
        jdbc.update("""
                INSERT INTO clinic (id, name, time_zone, status, activation_level, created_at)
                VALUES (UUID_TO_BIN(?), 'Clínica Fictícia de Teste', 'America/Sao_Paulo', 'ACTIVE', 3, UTC_TIMESTAMP(6))
                """, id.toString());
        String email = "admin-" + UUID.randomUUID() + "@ficticio.test";
        String token = CurrentClinic.call(id, () -> team.inviteFirstAdmin("Admin Fictício", email)).invite().token();
        StaffClient admin = new StaffClient(port);
        assertThat(admin.acceptInvite(token, PASSWORD).status()).isEqualTo(204);
        assertThat(admin.login(email, PASSWORD).status()).isEqualTo(200);
        return new Clinic(id, admin, email);
    }

    private static String roleId(StaffClient admin, String name) {
        for (JsonNode role : admin.get("/app/api/roles").body()) {
            if (role.path("name").asString().equals(name)) {
                return role.path("id").asString();
            }
        }
        throw new AssertionError("perfil não encontrado: " + name);
    }

    private static String departmentId(StaffClient client, String name) {
        for (JsonNode d : client.get("/app/api/departments").body()) {
            if (d.path("name").asString().equals(name)) {
                return d.path("id").asString();
            }
        }
        throw new AssertionError("setor não encontrado: " + name);
    }

    /** Convida, aceita o convite e loga. Devolve o navegador do novo funcionário e o id dele. */
    private record Member(StaffClient client, String id, String email) {
    }

    private Member member(Clinic clinic, String roleName, String department) {
        String email = "func-" + UUID.randomUUID() + "@ficticio.test";
        StaffClient.Response invited = clinic.admin().post("/app/api/employees", Map.of(
                "displayName", "Funcionário Fictício", "email", email, "roleId", roleId(clinic.admin(), roleName),
                "departmentIds", List.of(departmentId(clinic.admin(), department)), "jobTitle", "Recepcionista"));
        assertThat(invited.status()).isEqualTo(201);
        StaffClient browser = new StaffClient(port);
        assertThat(browser.acceptInvite(invited.body().path("invite").path("token").asString(), PASSWORD).status())
                .isEqualTo(204);
        assertThat(browser.login(email, PASSWORD).status()).isEqualTo(200);
        return new Member(browser, invited.body().path("employee").path("id").asString(), email);
    }

    @Test
    void newClinic_getsDefaultDepartmentsAndRoles_andAdminSeesEverything() {
        Clinic clinic = newClinic();

        List<String> departments = new ArrayList<>();
        clinic.admin().get("/app/api/departments").body().forEach(d -> departments.add(d.path("name").asString()));
        List<String> roles = new ArrayList<>();
        clinic.admin().get("/app/api/roles").body().forEach(r -> roles.add(r.path("name").asString()));
        JsonNode session = clinic.admin().get("/app/api/auth/session").body();

        assertThat(departments).containsExactlyInAnyOrder("Recepção", "Clínica", "Pet shop");
        assertThat(roles).containsExactlyInAnyOrder("Administrador", "Gerente", "Secretária", "Veterinário",
                "Atendente do pet shop", "Tosador");
        assertThat(session.path("clinicId").asString()).isEqualTo(clinic.id().toString());
        assertThat(session.path("permissions").toString()).contains("team:manage", "config:publish");
        assertThat(clinic.admin().get("/app/api/me").body().path("displayName").asString()).isEqualTo("Admin Fictício");
    }

    @Test
    void firstAdminByServiceKey_onlyWhileTheClinicHasNoAccounts() {
        OtherClinic.ensure(jdbc);
        ApiClient service = ApiClient.authenticated(port).withKey(IntegrationTest.OTHER_CLINIC_API_KEY);
        long before = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_account WHERE clinic_id = UUID_TO_BIN(?)", Long.class,
                IntegrationTest.OTHER_CLINIC);

        ApiClient.Response first = service.post("/api/clinic/first-admin",
                Map.of("displayName", "Admin Fictício", "email", "admin-" + UUID.randomUUID() + "@ficticio.test"));
        ApiClient.Response second = service.post("/api/clinic/first-admin",
                Map.of("displayName", "Outro", "email", "outro-" + UUID.randomUUID() + "@ficticio.test"));

        if (before == 0) {
            assertThat(first.status()).isEqualTo(201);
            assertThat(first.body().path("inviteToken").asString()).hasSizeGreaterThan(30);
        }
        assertThat(second.status()).isEqualTo(409);
    }

    @Test
    void loginFailures_areGeneric_andFiveWrongPasswordsLockTheAccount() {
        Clinic clinic = newClinic();
        StaffClient browser = new StaffClient(port);

        StaffClient.Response unknown = browser.login("ninguem-" + UUID.randomUUID() + "@ficticio.test", PASSWORD);
        StaffClient.Response wrong = browser.login(clinic.adminEmail(), "senha-errada-123");
        assertThat(unknown.status()).isEqualTo(401);
        assertThat(wrong.status()).isEqualTo(401);
        assertThat(unknown.body().path("detail").asString()).isEqualTo(wrong.body().path("detail").asString());

        for (int i = 0; i < 4; i++) {
            browser.login(clinic.adminEmail(), "senha-errada-123");
        }
        assertThat(browser.login(clinic.adminEmail(), PASSWORD).status())
                .as("bloqueada mesmo com a senha certa").isEqualTo(401);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE clinic_id = UUID_TO_BIN(?) "
                + "AND action = 'auth.login_failed'", Long.class, clinic.id().toString())).isGreaterThanOrEqualTo(5);
    }

    @Test
    void withoutSession_is401_andWithoutCsrf_is403() {
        Clinic clinic = newClinic();
        StaffClient anonymous = new StaffClient(port);

        assertThat(anonymous.get("/app/api/me").status()).isEqualTo(401);
        assertThat(ApiClient.authenticated(port).get("/app/api/me").status())
                .as("a chave de serviço não abre as rotas da equipe").isEqualTo(401);
        assertThat(clinic.admin().postWithoutCsrf("/app/api/roles",
                Map.of("name", "X", "sectorScope", "OWN", "permissions", List.of())).status()).isEqualTo(403);
    }

    @Test
    void permissions_comeFromTheRole_andChangesApplyImmediately() {
        Clinic clinic = newClinic();
        StaffClient.Response role = clinic.admin().post("/app/api/roles", Map.of("name", "Recepção restrita",
                "sectorScope", "OWN", "permissions", List.of("conversation:view")));
        assertThat(role.status()).isEqualTo(201);
        Member member = member(clinic, "Recepção restrita", "Recepção");

        assertThat(member.client().get("/app/api/me").status()).isEqualTo(200);
        assertThat(member.client().patch("/app/api/me/availability", Map.of("availability", "AWAY")).body()
                .path("availability").asString()).isEqualTo("AWAY");
        assertThat(member.client().get("/app/api/employees").status()).isEqualTo(403);
        assertThat(member.client().post("/app/api/departments", Map.of("name", "Novo", "kind", "OTHER")).status())
                .isEqualTo(403);

        clinic.admin().put("/app/api/roles/" + role.body().path("id").asString(), Map.of("name", "Recepção restrita",
                "sectorScope", "OWN", "permissions", List.of("conversation:view", "team:manage")));
        assertThat(member.client().get("/app/api/employees").status())
                .as("sem novo login").isEqualTo(200);
    }

    @Test
    void disabledEmployee_losesTheSessionAtOnce_andKeepsTheHistory() {
        Clinic clinic = newClinic();
        Member member = member(clinic, "Secretária", "Recepção");
        assertThat(member.client().get("/app/api/me").status()).isEqualTo(200);

        assertThat(clinic.admin().post("/app/api/employees/" + member.id() + "/disable", Map.of()).status())
                .isEqualTo(204);

        assertThat(member.client().get("/app/api/me").status()).isEqualTo(401);
        assertThat(new StaffClient(port).login(member.email(), PASSWORD).status()).isEqualTo(401);
        JsonNode listed = clinic.admin().get("/app/api/employees/" + member.id()).body();
        assertThat(listed.path("status").asString()).as("a conta continua existindo").isEqualTo("DISABLED");
        assertThat(clinic.admin().post("/app/api/employees/" + clinic.admin().get("/app/api/me").body()
                .path("id").asString() + "/disable", Map.of()).status())
                .as("ninguém desativa a si mesmo").isEqualTo(409);
    }

    @Test
    void anotherClinicsEmployeesAndDepartments_areInvisible() {
        Clinic one = newClinic();
        Clinic other = newClinic();
        Member member = member(other, "Secretária", "Recepção");
        String otherDepartment = departmentId(other.admin(), "Clínica");

        assertThat(one.admin().get("/app/api/employees/" + member.id()).status()).isEqualTo(404);
        assertThat(one.admin().post("/app/api/employees/" + member.id() + "/disable", Map.of()).status())
                .isEqualTo(404);
        assertThat(one.admin().patch("/app/api/departments/" + otherDepartment, Map.of("name", "Invadido")).status())
                .isEqualTo(404);
        assertThat(one.admin().post("/app/api/employees", Map.of("displayName", "X", "email",
                "x-" + UUID.randomUUID() + "@ficticio.test", "roleId", roleId(one.admin(), "Secretária"),
                "departmentIds", List.of(otherDepartment))).status())
                .as("setor de outra clínica não pode ser usado").isEqualTo(422);
        assertThat(member.client().get("/app/api/me").status()).isEqualTo(200);
    }

    @Test
    void invite_isSingleUse_andEmailIsUnique() {
        Clinic clinic = newClinic();
        StaffClient.Response invited = clinic.admin().post("/app/api/employees", Map.of(
                "displayName", "Fictício", "email", "unico-" + UUID.randomUUID() + "@ficticio.test",
                "roleId", roleId(clinic.admin(), "Tosador"), "departmentIds", List.of()));
        String token = invited.body().path("invite").path("token").asString();

        assertThat(new StaffClient(port).acceptInvite(token, PASSWORD).status()).isEqualTo(204);
        assertThat(new StaffClient(port).acceptInvite(token, PASSWORD).status()).isEqualTo(422);
        assertThat(new StaffClient(port).acceptInvite("token-inventado", PASSWORD).status()).isEqualTo(422);
        assertThat(clinic.admin().post("/app/api/employees", Map.of("displayName", "Repetido",
                "email", clinic.adminEmail(), "roleId", roleId(clinic.admin(), "Tosador"),
                "departmentIds", List.of())).status()).isEqualTo(409);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE clinic_id = UUID_TO_BIN(?) "
                + "AND action IN ('user.invited', 'auth.invite_accepted', 'auth.login')", Long.class,
                clinic.id().toString())).isGreaterThanOrEqualTo(5);
    }
}
