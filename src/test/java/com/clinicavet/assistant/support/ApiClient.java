package com.clinicavet.assistant.support;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Cliente HTTP real dos testes de integração: passa pelo filtro de API Key e pelo Tomcat de verdade. */
public final class ApiClient {

    public record Response(int status, JsonNode body) {
    }

    private static final ObjectMapper JSON = JsonMapper.builder().build();

    private final HttpClient http = HttpClient.newHttpClient();
    private final int port;
    private final String baseUrl;
    private final String apiKey;

    public ApiClient(int port, String apiKey) {
        this.port = port;
        this.baseUrl = "http://localhost:" + port;
        this.apiKey = apiKey;
    }

    public static ApiClient authenticated(int port) {
        return new ApiClient(port, IntegrationTest.API_KEY);
    }

    public ApiClient withKey(String otherKey) {
        return new ApiClient(port, otherKey);
    }

    public Response post(String path, Map<String, ?> body) {
        return send(request(path).POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body),
                StandardCharsets.UTF_8)).header("Content-Type", "application/json"));
    }

    public Response patch(String path, Map<String, ?> body) {
        return send(request(path).method("PATCH", HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body),
                StandardCharsets.UTF_8)).header("Content-Type", "application/json"));
    }

    public Response get(String path) {
        return send(request(path).GET());
    }

    /** Abre (ou recupera) a conversa do contato e devolve o id. */
    public String openConversation(String externalContactId) {
        return post("/api/conversations", Map.of("externalContactId", externalContactId)).body().path("id").asString();
    }

    public Response sendMessage(String conversationId, String text) {
        return post("/api/conversations/" + conversationId + "/messages", Map.of("text", text));
    }

    private HttpRequest.Builder request(String path) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path));
        if (apiKey != null) {
            builder.header("X-Api-Key", apiKey);
        }
        return builder;
    }

    private Response send(HttpRequest.Builder builder) {
        try {
            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            String body = response.body();
            return new Response(response.statusCode(), body == null || body.isBlank() ? null : JSON.readTree(body));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
