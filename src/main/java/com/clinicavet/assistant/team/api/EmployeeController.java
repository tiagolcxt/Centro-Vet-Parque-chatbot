package com.clinicavet.assistant.team.api;

import com.clinicavet.assistant.platform.Accounts;
import com.clinicavet.assistant.platform.StaffUser;
import com.clinicavet.assistant.team.application.TeamService;
import com.clinicavet.assistant.team.infrastructure.EmployeeEntity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Equipe (RF-S03 a RF-S06). Administrar exige {@code team:manage}; ver os próprios dados e mudar a própria
 * disponibilidade, só estar logado ({@code /me}).
 */
@RestController
@RequestMapping("/app/api")
class EmployeeController {

    private final TeamService team;

    EmployeeController(TeamService team) {
        this.team = team;
    }

    @GetMapping("/employees")
    @PreAuthorize("hasAuthority('team:manage')")
    List<TeamService.EmployeeView> list() {
        return team.list();
    }

    @GetMapping("/employees/{id}")
    @PreAuthorize("hasAuthority('team:manage')")
    TeamService.EmployeeView get(@PathVariable UUID id) {
        return team.get(id);
    }

    /** Convida: a resposta traz o link de definição de senha, que o administrador entrega à pessoa. */
    @PostMapping("/employees")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('team:manage')")
    InvitedResponse invite(@Valid @RequestBody InviteRequest body) {
        return InvitedResponse.of(team.invite(new TeamService.EmployeeData(body.displayName(), body.email(),
                body.roleId(), body.departmentIds(), body.jobTitle(), body.professionalRegistration())));
    }

    @PutMapping("/employees/{id}")
    @PreAuthorize("hasAuthority('team:manage')")
    TeamService.EmployeeView update(@PathVariable UUID id, @Valid @RequestBody UpdateRequest body) {
        return team.update(id, new TeamService.EmployeeData(body.displayName(), null, body.roleId(),
                body.departmentIds(), body.jobTitle(), body.professionalRegistration()));
    }

    @PostMapping("/employees/{id}/disable")
    @PreAuthorize("hasAuthority('team:manage')")
    ResponseEntity<Void> disable(@PathVariable UUID id) {
        team.disable(id, StaffUser.current().orElseThrow());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/employees/{id}/enable")
    @PreAuthorize("hasAuthority('team:manage')")
    TeamService.EmployeeView enable(@PathVariable UUID id) {
        return team.enable(id);
    }

    /** Novo link de senha (convite perdido ou senha esquecida). */
    @PostMapping("/employees/{id}/invite")
    @PreAuthorize("hasAuthority('team:manage')")
    InviteResponse newInvite(@PathVariable UUID id) {
        return InviteResponse.of(team.newInvite(id));
    }

    @GetMapping("/me")
    TeamService.EmployeeView me() {
        return team.get(StaffUser.current().orElseThrow().userId());
    }

    @PatchMapping("/me/availability")
    TeamService.EmployeeView availability(@Valid @RequestBody AvailabilityRequest body) {
        return team.setAvailability(StaffUser.current().orElseThrow().userId(), body.availability());
    }

    record InviteRequest(@NotBlank @Size(max = 120) String displayName,
                         @NotBlank @Email @Size(max = 254) String email,
                         @NotNull UUID roleId,
                         @NotNull @Size(max = 20) Set<UUID> departmentIds,
                         @Size(max = 80) String jobTitle,
                         @Size(max = 40) String professionalRegistration) {
    }

    record UpdateRequest(@NotBlank @Size(max = 120) String displayName,
                         @NotNull UUID roleId,
                         @NotNull @Size(max = 20) Set<UUID> departmentIds,
                         @Size(max = 80) String jobTitle,
                         @Size(max = 40) String professionalRegistration) {
    }

    record AvailabilityRequest(@NotNull EmployeeEntity.Availability availability) {
    }

    record InviteResponse(String token, Instant expiresAt) {
        static InviteResponse of(Accounts.InviteLink link) {
            return new InviteResponse(link.token(), link.expiresAt());
        }
    }

    record InvitedResponse(TeamService.EmployeeView employee, InviteResponse invite) {
        static InvitedResponse of(TeamService.Invited invited) {
            return new InvitedResponse(invited.employee(), InviteResponse.of(invited.invite()));
        }
    }
}
