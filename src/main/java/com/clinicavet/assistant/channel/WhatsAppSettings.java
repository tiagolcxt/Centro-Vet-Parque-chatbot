package com.clinicavet.assistant.channel;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * Canal WhatsApp pela Cloud API (Etapa S4, ADR-0023). Segredos só em variável de ambiente ou no {@code .env} não
 * versionado, nunca no repositório (DM-12). Sem eles a aplicação sobe, mas o canal fica desligado: o webhook responde
 * 503 e a tela de canal avisa o que falta.
 *
 * @param appSecret        App Secret do app na Meta: confere a assinatura {@code X-Hub-Signature-256} do webhook
 * @param verifyToken      texto combinado com a Meta na verificação do webhook ({@code hub.verify_token})
 * @param tokenKey         chave AES-256 em Base64 que cifra o token de acesso guardado no banco
 * @param apiBaseUrl       endereço da Graph API
 * @param apiVersion       versão da Graph API (ex.: {@code v27.0})
 * @param scheduling       liga o despachante e a varredura agendados (os testes chamam direto)
 * @param inboundAsync     processa as mensagens recebidas fora da requisição do webhook (a Meta espera 200 rápido)
 * @param maxAttempts      tentativas de envio antes de marcar a resposta como não enviada
 * @param backoff          espera antes de cada nova tentativa (a última se repete)
 * @param maxTextLength    tamanho máximo do texto do tutor passado ao assistente (o resto é cortado)
 */
@ConfigurationProperties(prefix = "assistant.whatsapp")
public record WhatsAppSettings(String appSecret, String verifyToken, String tokenKey, String apiBaseUrl,
                               String apiVersion, Boolean scheduling, Boolean inboundAsync, Integer maxAttempts,
                               List<Duration> backoff, Integer maxTextLength) {

    public WhatsAppSettings {
        apiBaseUrl = blank(apiBaseUrl) ? "https://graph.facebook.com" : apiBaseUrl.strip();
        apiVersion = blank(apiVersion) ? "v27.0" : apiVersion.strip();
        scheduling = scheduling == null || scheduling;
        inboundAsync = inboundAsync == null || inboundAsync;
        maxAttempts = maxAttempts == null || maxAttempts < 1 ? 5 : maxAttempts;
        backoff = backoff == null || backoff.isEmpty()
                ? List.of(Duration.ofSeconds(10), Duration.ofSeconds(30), Duration.ofMinutes(2), Duration.ofMinutes(10),
                Duration.ofMinutes(30))
                : List.copyOf(backoff);
        maxTextLength = maxTextLength == null || maxTextLength < 1 ? 2000 : maxTextLength;
    }

    boolean webhookReady() {
        return !blank(appSecret) && !blank(verifyToken);
    }

    boolean encryptionReady() {
        return !blank(tokenKey);
    }

    /** Espera antes da tentativa seguinte à de número {@code attempt} (1 = primeira falha). */
    Duration delayAfter(int attempt) {
        return backoff.get(Math.min(Math.max(attempt, 1), backoff.size()) - 1);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
