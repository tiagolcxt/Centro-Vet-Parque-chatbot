package com.clinicavet.assistant.ai;

import com.clinicavet.assistant.channel.AudioTranscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;

/**
 * Transcrição pela OpenAI ({@code POST /v1/audio/transcriptions}), o mesmo provedor do assistente (DA-50). Em
 * português, com a duração do áudio na resposta ({@code verbose_json}) para medir o custo por minuto. Nem o áudio nem
 * o texto vão para log (DM-12).
 */
@Component
class OpenAiAudioTranscriber implements AudioTranscriber {

    private static final Logger log = LoggerFactory.getLogger(OpenAiAudioTranscriber.class);

    private final RestClient http;
    private final String apiKey;
    private final String model;
    private final boolean enabled;

    OpenAiAudioTranscriber(RestClient.Builder builder,
                           @Value("${spring.ai.openai.api-key:not-configured}") String apiKey,
                           @Value("${assistant.audio.base-url:https://api.openai.com}") String baseUrl,
                           @Value("${assistant.audio.model:whisper-1}") String model,
                           @Value("${assistant.audio.enabled:false}") boolean enabled) {
        this.http = builder.baseUrl(baseUrl).build();
        this.apiKey = apiKey;
        this.model = model;
        this.enabled = enabled && apiKey != null && !apiKey.isBlank() && !apiKey.equals("not-configured");
    }

    @Override
    public Optional<Transcription> transcribe(byte[] audio, String mimeType) {
        if (!enabled || audio == null || audio.length == 0) {
            return Optional.empty();
        }
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new ByteArrayResource(audio) {
            @Override
            public String getFilename() {
                return "audio" + extension(mimeType);
            }
        });
        form.add("model", model);
        form.add("language", "pt");
        form.add("response_format", model.startsWith("whisper") ? "verbose_json" : "json");
        try {
            String body = http.post().uri("/v1/audio/transcriptions")
                    .headers(h -> h.setBearerAuth(apiKey))
                    .contentType(MediaType.MULTIPART_FORM_DATA).body(form).retrieve().body(String.class);
            JsonNode json = JsonMapper.builder().build().readTree(body == null ? "{}" : body);
            String text = json.path("text").asString("").strip();
            if (text.isEmpty()) {
                return Optional.empty();
            }
            Double seconds = json.has("duration") ? json.path("duration").asDouble() : null;
            return Optional.of(new Transcription(text, seconds));
        } catch (RuntimeException e) { // HTTP, rede ou resposta fora do formato: segue sem transcrição
            log.warn("Transcrição de áudio falhou [motivo={}]", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    /** O WhatsApp manda áudio de voz em OGG/Opus; a extensão ajuda o provedor a reconhecer o formato. */
    private static String extension(String mimeType) {
        if (mimeType == null) {
            return ".ogg";
        }
        String type = mimeType.toLowerCase();
        if (type.contains("mpeg") || type.contains("mp3")) {
            return ".mp3";
        }
        if (type.contains("mp4") || type.contains("m4a") || type.contains("aac")) {
            return ".m4a";
        }
        if (type.contains("wav")) {
            return ".wav";
        }
        if (type.contains("webm")) {
            return ".webm";
        }
        return ".ogg";
    }
}
