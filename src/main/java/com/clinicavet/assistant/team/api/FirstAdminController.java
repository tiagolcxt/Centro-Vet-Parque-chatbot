package com.clinicavet.assistant.team.api;

import com.clinicavet.assistant.team.application.TeamService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

/**
 * Primeiro administrador da clínica da chave de serviço (RF-S01). Usado pela equipe da plataforma ao instalar uma
 * clínica; só funciona enquanto a clínica não tem nenhuma conta ({@code 409} depois).
 */
@RestController
class FirstAdminController {

    private final TeamService team;

    FirstAdminController(TeamService team) {
        this.team = team;
    }

    @PostMapping("/api/clinic/first-admin")
    @ResponseStatus(HttpStatus.CREATED)
    Response create(@Valid @RequestBody Request body) {
        TeamService.Invited invited = team.inviteFirstAdmin(body.displayName(), body.email());
        return new Response(invited.employee().id(), invited.invite().token(), invited.invite().expiresAt());
    }

    record Request(@NotBlank @Size(max = 120) String displayName, @NotBlank @Email @Size(max = 254) String email) {
    }

    record Response(UUID userId, String inviteToken, Instant expiresAt) {
    }
}
