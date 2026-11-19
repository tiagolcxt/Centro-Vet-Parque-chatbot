package com.clinicavet.assistant.platform.security;

import com.clinicavet.assistant.platform.Permission;
import com.clinicavet.assistant.platform.StaffUser;
import com.clinicavet.assistant.platform.application.AccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Login individual por e-mail e senha (RF-S02, ADR-0024). A aplicação web chama {@code GET /csrf} antes do primeiro
 * POST para receber o cookie {@code XSRF-TOKEN}.
 */
@RestController
@RequestMapping("/app/api/auth")
class AuthController {

    private final AccountService accounts;
    private final SecurityContextRepository contexts;

    AuthController(AccountService accounts, SecurityContextRepository contexts) {
        this.accounts = accounts;
        this.contexts = contexts;
    }

    /** Gera o cookie {@code XSRF-TOKEN} (o token chega pelo cookie, não pelo corpo). */
    @GetMapping("/csrf")
    ResponseEntity<Void> csrf(CsrfToken token) {
        token.getToken();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/login")
    SessionResponse login(@Valid @RequestBody LoginRequest body, HttpServletRequest request,
                          HttpServletResponse response) {
        StaffUser user = accounts.authenticate(body.email(), body.password());
        HttpSession old = request.getSession(false);
        if (old != null) {
            old.invalidate();      // sessão nova a cada login (fixação de sessão)
        }
        request.getSession(true);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new SessionUser(user.userId(), user.clinic().value()), null, List.of()));
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);
        return SessionResponse.of(user);
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/session")
    SessionResponse session() {
        return SessionResponse.of(StaffUser.current().orElseThrow());
    }

    @PostMapping("/accept-invite")
    ResponseEntity<Void> acceptInvite(@Valid @RequestBody AcceptInviteRequest body) {
        accounts.acceptInvite(body.token(), body.password());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/change-password")
    ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest body) {
        accounts.changePassword(StaffUser.current().orElseThrow(), body.currentPassword(), body.newPassword());
        return ResponseEntity.noContent().build();
    }

    record LoginRequest(@NotBlank @Size(max = 254) String email, @NotBlank @Size(max = 200) String password) {
    }

    record AcceptInviteRequest(@NotBlank @Size(max = 100) String token, @NotBlank @Size(max = 200) String password) {
    }

    record ChangePasswordRequest(@NotBlank @Size(max = 200) String currentPassword,
                                 @NotBlank @Size(max = 200) String newPassword) {
    }

    record SessionResponse(UUID userId, UUID clinicId, String email, UUID roleId, String sectorScope,
                           List<String> permissions) {
        static SessionResponse of(StaffUser user) {
            return new SessionResponse(user.userId(), user.clinic().value(), user.email(), user.roleId(),
                    user.scope().name(), user.permissions().stream().map(Permission::code).sorted().toList());
        }
    }
}
