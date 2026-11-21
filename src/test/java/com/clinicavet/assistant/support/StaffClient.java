package com.clinicavet.assistant.support;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Cliente HTTP de um funcionário na aplicação web ({@code /app/api}, ADR-0024): guarda o cookie de sessão e manda o
 * token CSRF no padrão de SPA (cookie {@code XSRF-TOKEN} → cabeçalho {@code X-XSRF-TOKEN}). Um cliente = um
 * navegador.
 */
public final class StaffClient {

    public record Response(int status, JsonNode body) {
    }

    private static final ObjectMapper JSON = JsonMapper.builder().build();

    private final String base;
    private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
    private final HttpClient http;

    public StaffClient(int port) {
        this.base = "http://localhost:" + port;
        this.http = HttpClient.newBuilder().cookieHandler(cookies).build();
    }

    /** Pede o cookie CSRF, como a aplicação web faz antes do primeiro POST. */
    public StaffClient csrf() {
        get("/app/api/auth/csrf");
        return this;
    }

    public Response login(String email, String password) {
        csrf();
        return post("/app/api/auth/login", Map.of("email", email, "password", password));
    }

    public Response acceptInvite(String token, String password) {
        csrf();
        return post("/app/api/auth/accept-invite", Map.of("token", token, "password", password));
    }

    public Response get(String path) {
        return send(HttpRequest.newBuilder(URI.create(base + path)).GET());
    }

    public Response post(String path, Object body) {
        return send(withBody(path, "POST", body));
    }

    public Response put(String path, Object body) {
        return send(withBody(path, "PUT", body));
    }

    public Response delete(String path) {
        return send(withBody(path, "DELETE", Map.of()));
    }

    public Response patch(String path, Object body) {
        return send(withBody(path, "PATCH", body));
    }

    /** POST sem o cabeçalho CSRF: deve ser recusado. */
    public Response postWithoutCsrf(String path, Object body) {
        return send(HttpRequest.newBuilder(URI.create(base + path)).header("Content-Type", "application/json")
                .method("POST", HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))));
    }

    private HttpRequest.Builder withBody(String path, String method, Object body) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + path))
                .header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
        String token = xsrf();
        if (token != null) {
            builder.header("X-XSRF-TOKEN", token);
        }
        return builder;
    }

    /** GET sem ler o corpo como JSON (arquivos, como a planilha de pedidos). */
    public HttpResponse<String> raw(String path) {
        try {
            return http.send(HttpRequest.newBuilder(URI.create(base + path)).GET().build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private String xsrf() {
        return cookies.getCookieStore().getCookies().stream()
                .filter(c -> c.getName().equals("XSRF-TOKEN")).map(HttpCookie::getValue).findFirst().orElse(null);
    }

    private Response send(HttpRequest.Builder builder) {
        try {
            HttpResponse<String> response = http.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            String body = response.body();
            return new Response(response.statusCode(), body == null || body.isBlank() ? null : JSON.readTree(body));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
