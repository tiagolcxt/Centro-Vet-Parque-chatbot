package com.clinicavet.assistant.support;

import com.clinicavet.assistant.clinicknowledge.domain.Departments;
import com.clinicavet.assistant.platform.ClinicId;
import com.clinicavet.assistant.platform.CurrentClinic;
import com.clinicavet.assistant.platform.RoleTemplate;
import com.clinicavet.assistant.platform.Roles;
import com.clinicavet.assistant.team.application.TeamService;
import org.springframework.context.ApplicationContext;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Funcionários fictícios logados para os testes da equipe ({@code /app/api}, ADR-0024). Um administrador por clínica e
 * por servidor de teste, criado na primeira vez e reaproveitado.
 */
public final class TestStaff {

    public static final String PASSWORD = "senha-ficticia-123";

    private static final Map<String, StaffClient> ADMINS = new ConcurrentHashMap<>();

    private TestStaff() {
    }

    /** Administrador (todas as permissões, todos os setores) da clínica fictícia. */
    public static StaffClient admin(int port, ApplicationContext context) {
        return admin(port, context, FictitiousClinicExtension.CLINIC);
    }

    public static StaffClient admin(int port, ApplicationContext context, ClinicId clinic) {
        return ADMINS.computeIfAbsent(port + "/" + clinic, key -> member(port, context, clinic, RoleTemplate.ADMIN,
                null));
    }

    /**
     * Funcionário novo com um perfil-modelo, nos setores informados ({@code null} = todos). Cada chamada cria outro.
     */
    public static StaffClient member(int port, ApplicationContext context, ClinicId clinic, RoleTemplate template,
                                     Set<UUID> departmentIds) {
        TeamService team = context.getBean(TeamService.class);
        Roles roles = context.getBean(Roles.class);
        Departments departments = context.getBean(Departments.class);
        String email = template.name().toLowerCase() + "-" + UUID.randomUUID() + "@ficticio.test";
        String token = CurrentClinic.call(clinic, () -> {
            departments.ensureDefaults();
            UUID role = roles.ensureDefaults().get(template);
            Set<UUID> sectors = departmentIds != null ? departmentIds
                    : departments.list().stream().map(Departments.Department::id).collect(Collectors.toSet());
            return team.invite(new TeamService.EmployeeData("Fictício " + template.displayName(), email, role, sectors,
                    null, null)).invite().token();
        });
        StaffClient client = new StaffClient(port);
        if (client.acceptInvite(token, PASSWORD).status() != 204 || client.login(email, PASSWORD).status() != 200) {
            throw new IllegalStateException("não foi possível logar o funcionário de teste");
        }
        return client;
    }
}
