package com.clinicavet.assistant.channel;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.Set;

/**
 * Cloud API do WhatsApp pela Graph API. Envio: {@code POST /{versão}/{phone_number_id}/messages}, com o token no
 * cabeçalho {@code Authorization}. Nenhum token, texto ou número vai para log.
 */
class CloudWhatsAppApi implements WhatsAppApi {

    /**
     * Erros da Meta que passam se esperar: limites de envio (4, 80007, 130429, 131056), indisponibilidade temporária
     * (1, 2, 131000, 133004) e mensagem ainda em processamento (131048).
     */
    private static final Set<String> RETRYABLE_CODES = Set.of("1", "2", "4", "80007", "130429", "131000", "131048",
            "131056", "133004");

    private static final ObjectMapper JSON = JsonMapper.builder().build();

    private final RestClient http;
    private final String base;

    CloudWhatsAppApi(RestClient.Builder builder, WhatsAppSettings settings) {
        this.http = builder.build();
        this.base = settings.apiBaseUrl() + "/" + settings.apiVersion();
    }

    @Override
    public String sendText(Credentials credentials, String to, String text) {
        Map<String, Object> body = Map.of("messaging_product", "whatsapp", "recipient_type", "individual", "to", to,
                "type", "text", "text", Map.of("preview_url", false, "body", text));
        JsonNode response = call(() -> http.post().uri(base + "/{id}/messages", credentials.phoneNumberId())
                .headers(h -> h.setBearerAuth(credentials.accessToken()))
                .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(String.class));
        String wamid = response.path("messages").path(0).path("id").asString("");
        if (wamid.isBlank()) {
            throw new ApiException("NO_WAMID", true, "A Meta não devolveu o id da mensagem.");
        }
        return wamid;
    }

    @Override
    public PhoneInfo phoneInfo(Credentials credentials) {
        JsonNode response = call(() -> http.get()
                .uri(base + "/{id}?fields=display_phone_number,verified_name", credentials.phoneNumberId())
                .headers(h -> h.setBearerAuth(credentials.accessToken())).retrieve().body(String.class));
        return new PhoneInfo(response.path("display_phone_number").asString(null),
                response.path("verified_name").asString(null));
    }

    @Override
    public Media download(Credentials credentials, String mediaId) {
        JsonNode meta = call(() -> http.get().uri(base + "/{id}", mediaId)
                .headers(h -> h.setBearerAuth(credentials.accessToken())).retrieve().body(String.class));
        String url = meta.path("url").asString("");
        if (url.isBlank()) {
            throw new ApiException("NO_MEDIA_URL", false, "A Meta não devolveu o endereço do arquivo.");
        }
        try {
            ResponseEntity<byte[]> file = http.get().uri(java.net.URI.create(url))
                    .headers(h -> h.setBearerAuth(credentials.accessToken())).retrieve().toEntity(byte[].class);
            String mime = file.getHeaders().getContentType() != null ? file.getHeaders().getContentType().toString()
                    : meta.path("mime_type").asString("application/octet-stream");
            return new Media(file.getBody() == null ? new byte[0] : file.getBody(), mime);
        } catch (RestClientResponseException e) {
            throw new ApiException("HTTP_" + e.getStatusCode().value(), e.getStatusCode().is5xxServerError(),
                    "Falha ao baixar o arquivo.");
        } catch (ResourceAccessException e) {
            throw new ApiException("NETWORK", true, "Sem conexão com a Meta.");
        }
    }

    private interface Call {
        String run();
    }

    private static JsonNode call(Call call) {
        try {
            String body = call.run();
            return body == null || body.isBlank() ? JSON.missingNode() : JSON.readTree(body);
        } catch (RestClientResponseException e) {
            throw fromResponse(e);
        } catch (ResourceAccessException e) {
            throw new ApiException("NETWORK", true, "Sem conexão com a Meta.");
        }
    }

    /** Lê {@code error.code} e {@code error.message} do corpo; a mensagem da Meta não traz token nem texto enviado. */
    private static ApiException fromResponse(RestClientResponseException e) {
        int status = e.getStatusCode().value();
        String code = "HTTP_" + status;
        String message = "A Meta recusou a chamada (HTTP " + status + ").";
        try {
            JsonNode error = JSON.readTree(e.getResponseBodyAsString()).path("error");
            if (error.has("code")) {
                code = error.path("code").asString(code);
                message = error.path("message").asString(message);
            }
        } catch (RuntimeException ignored) {
            // corpo não é JSON: fica o código HTTP
        }
        boolean retryable = status == 429 || status >= 500 || RETRYABLE_CODES.contains(code);
        return new ApiException(code, retryable, message);
    }
}
