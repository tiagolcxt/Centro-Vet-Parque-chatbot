package com.clinicavet.assistant.platform.security;

import com.clinicavet.assistant.platform.application.AccountService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.context.SecurityContextRepository;

import java.nio.charset.StandardCharsets;

/**
 * Três cadeias (ADR-0024):
 * <ol>
 *   <li>{@code /api/**}: chave de serviço ({@code ApiKeyFilter}), sem sessão nem CSRF. Canal de teste e integrações
 *       (ADR-0012 revisto). Na S2 as rotas da equipe saem daqui;</li>
 *   <li>{@code /app/api/**}: funcionários, com sessão por cookie e CSRF no padrão de SPA (cookie {@code XSRF-TOKEN},
 *       cabeçalho {@code X-XSRF-TOKEN}). Erros em JSON, nunca redirecionamento;</li>
 *   <li>o resto (a aplicação web estática, na S2): público.</li>
 * </ol>
 */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableMethodSecurity
class SecurityConfig {

    static final String[] PUBLIC_STAFF_ROUTES = {
            "/app/api/auth/login", "/app/api/auth/csrf", "/app/api/auth/accept-invite"};

    @Bean
    @Order(1)
    SecurityFilterChain serviceApi(HttpSecurity http) throws Exception {
        http.securityMatcher("/api/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a.anyRequest().permitAll())
                .httpBasic(b -> b.disable())
                .formLogin(f -> f.disable())
                .logout(l -> l.disable());
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain staffApi(HttpSecurity http, AccountService accounts, SecurityContextRepository contexts)
            throws Exception {
        http.securityMatcher("/app/api/**")
                .csrf(csrf -> csrf.spa())
                .securityContext(c -> c.securityContextRepository(contexts))
                .authorizeHttpRequests(a -> a.requestMatchers(PUBLIC_STAFF_ROUTES).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> problem(res, HttpStatus.UNAUTHORIZED,
                                "Não autorizado", "Faça login para continuar."))
                        .accessDeniedHandler((req, res, ex) -> problem(res, HttpStatus.FORBIDDEN,
                                "Acesso negado", "Requisição recusada (permissão ou token CSRF).")))
                .addFilterAfter(new StaffSessionFilter(accounts), SecurityContextHolderFilter.class)
                .httpBasic(b -> b.disable())
                .formLogin(f -> f.disable())
                .logout(l -> l.disable());
        return http.build();
    }

    @Bean
    @Order(3)
    SecurityFilterChain everythingElse(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(a -> a.anyRequest().permitAll())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(b -> b.disable())
                .formLogin(f -> f.disable())
                .logout(l -> l.disable());
        return http.build();
    }

    private static void problem(jakarta.servlet.http.HttpServletResponse response, HttpStatus status, String title,
                                String detail) throws java.io.IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"" + title + "\",\"status\":"
                + status.value() + ",\"detail\":\"" + detail + "\"}");
    }
}
