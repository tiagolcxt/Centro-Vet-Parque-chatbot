package com.clinicavet.assistant.platform.security;

import com.clinicavet.assistant.platform.ClinicId;
import com.clinicavet.assistant.platform.CurrentClinic;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Exige {@code X-Api-Key} em {@code /api/**} (ADR-0012, fase de testes com dados fictícios).
 * <ul>
 *   <li>Cada chave pertence a uma clínica. A requisição roda com essa clínica no {@link CurrentClinic} (ADR-0021):
 *       quem chama nunca escolhe a clínica por parâmetro;</li>
 *   <li>a comparação é feita sobre os SHA-256 das chaves com {@link MessageDigest#isEqual}, em tempo constante e
 *       sem revelar o tamanho da chave certa. Todas as chaves são comparadas, mesmo depois de achar a certa;</li>
 *   <li>nenhuma chave, certa ou errada, vai para log ou resposta.</li>
 * </ul>
 * Não substitui a autenticação individual exigida para dados reais (ADR-0024).
 */
class ApiKeyFilter extends OncePerRequestFilter {

    static final String HEADER = "X-Api-Key";

    private static final String UNAUTHORIZED_BODY = """
            {"type":"about:blank","title":"Não autorizado","status":401,\
            "detail":"Cabeçalho X-Api-Key ausente ou inválido."}""";

    private record Credential(byte[] digest, ClinicId clinic) {
    }

    private final Credential[] credentials;

    /** @param keys chave → clínica dona da chave */
    ApiKeyFilter(Map<String, ClinicId> keys) {
        Objects.requireNonNull(keys, "keys");
        if (keys.isEmpty()) {
            throw new IllegalArgumentException("Nenhuma chave configurada");
        }
        this.credentials = new LinkedHashMap<>(keys).entrySet().stream()
                .map(e -> new Credential(sha256(e.getKey()), Objects.requireNonNull(e.getValue(), "clinic")))
                .toArray(Credential[]::new);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String provided = request.getHeader(HEADER);
        ClinicId clinic = provided == null ? null : match(sha256(provided));
        if (clinic == null) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setHeader("WWW-Authenticate", "ApiKey header=\"" + HEADER + "\"");
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(UNAUTHORIZED_BODY);
            return;
        }
        try (CurrentClinic.Scope ignored = CurrentClinic.open(clinic)) {
            chain.doFilter(request, response);
        }
    }

    private ClinicId match(byte[] digest) {
        ClinicId found = null;
        for (Credential credential : credentials) {
            if (MessageDigest.isEqual(credential.digest(), digest) && found == null) {
                found = credential.clinic();
            }
        }
        return found;
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível na JVM", e);
        }
    }
}
