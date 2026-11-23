package com.clinicavet.assistant.channel;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Assinatura do webhook da Meta: {@code X-Hub-Signature-256: sha256=<HMAC-SHA256 do corpo bruto com o App Secret>}.
 * Calculada sobre os bytes exatamente como chegaram (reformatar o JSON muda a assinatura) e comparada em tempo
 * constante.
 */
final class WebhookSignature {

    static final String HEADER = "X-Hub-Signature-256";
    private static final String PREFIX = "sha256=";

    private WebhookSignature() {
    }

    static String sign(byte[] body, String appSecret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return PREFIX + HexFormat.of().formatHex(mac.doFinal(body));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 indisponível", e);
        }
    }

    static boolean valid(byte[] body, String header, String appSecret) {
        if (header == null || !header.startsWith(PREFIX)) {
            return false;
        }
        byte[] expected = sign(body, appSecret).getBytes(StandardCharsets.US_ASCII);
        byte[] given = header.strip().toLowerCase().getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, given);
    }
}
