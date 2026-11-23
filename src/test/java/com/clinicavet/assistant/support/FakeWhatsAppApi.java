package com.clinicavet.assistant.support;

import com.clinicavet.assistant.channel.WhatsAppApi;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

/** Cloud API falsa: guarda o que seria enviado e falha quando o teste pede. Sem rede, sem número real. */
public final class FakeWhatsAppApi implements WhatsAppApi {

    public record Sent(String phoneNumberId, String to, String text, String wamid) {
    }

    public static final byte[] MEDIA_BYTES = "imagem-ficticia".getBytes(StandardCharsets.US_ASCII);

    private final List<Sent> sent = new ArrayList<>();
    private final java.util.Map<String, Deque<ApiException>> failures = new java.util.HashMap<>();

    public synchronized void reset() {
        sent.clear();
        failures.clear();
    }

    /** As próximas {@code times} tentativas de envio para {@code to} falham com este código. */
    public synchronized FakeWhatsAppApi failNext(String to, int times, String code, boolean retryable) {
        Deque<ApiException> queue = failures.computeIfAbsent(to, k -> new ArrayDeque<>());
        for (int i = 0; i < times; i++) {
            queue.add(new ApiException(code, retryable, "falha simulada " + code));
        }
        return this;
    }

    public synchronized List<Sent> sentTo(String to) {
        return sent.stream().filter(s -> s.to().equals(to)).toList();
    }

    @Override
    public synchronized String sendText(Credentials credentials, String to, String text) {
        Deque<ApiException> queue = failures.get(to);
        ApiException failure = queue == null ? null : queue.poll();
        if (failure != null) {
            throw failure;
        }
        String wamid = "wamid.FICTICIO." + UUID.randomUUID();
        sent.add(new Sent(credentials.phoneNumberId(), to, text, wamid));
        return wamid;
    }

    @Override
    public PhoneInfo phoneInfo(Credentials credentials) {
        if (!credentials.accessToken().startsWith("token-ficticio")) {
            throw new ApiException("190", false, "token inválido");
        }
        return new PhoneInfo("+55 00 0000-0000", "Clínica Fictícia");
    }

    @Override
    public Media download(Credentials credentials, String mediaId) {
        return new Media(MEDIA_BYTES, "image/jpeg");
    }
}
