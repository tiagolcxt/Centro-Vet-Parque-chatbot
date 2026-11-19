package com.clinicavet.assistant.platform.security;

import com.clinicavet.assistant.platform.ClinicId;
import com.clinicavet.assistant.platform.CurrentClinic;
import com.clinicavet.assistant.platform.StaffUser;
import com.clinicavet.assistant.platform.application.AccountService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Para cada requisição com sessão (ADR-0024):
 * <ol>
 *   <li>recarrega a conta e o perfil. Conta desativada ou inexistente encerra a sessão (RF-S03);</li>
 *   <li>troca a autenticação da requisição por um {@link StaffUser} com as permissões atuais;</li>
 *   <li>roda o resto da requisição com a clínica do funcionário no {@link CurrentClinic} (ADR-0021).</li>
 * </ol>
 * A sessão continua guardando só o {@link SessionUser}: nada aqui é salvo nela.
 */
class StaffSessionFilter extends OncePerRequestFilter {

    private final AccountService accounts;

    StaffSessionFilter(AccountService accounts) {
        this.accounts = accounts;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof SessionUser session)) {
            chain.doFilter(request, response);
            return;
        }
        ClinicId clinic = new ClinicId(session.clinicId());
        Optional<StaffUser> user = accounts.loadActive(session.userId(), clinic);
        if (user.isEmpty()) {
            HttpSession httpSession = request.getSession(false);
            if (httpSession != null) {
                httpSession.invalidate();
            }
            SecurityContextHolder.clearContext();
            chain.doFilter(request, response);
            return;
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user.get(), null,
                user.get().permissions().stream().map(p -> new SimpleGrantedAuthority(p.code())).toList()));
        SecurityContextHolder.setContext(context);
        try (CurrentClinic.Scope ignored = CurrentClinic.open(clinic)) {
            chain.doFilter(request, response);
        }
    }
}
