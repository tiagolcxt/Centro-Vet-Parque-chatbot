package com.clinicavet.assistant.channel;

import com.clinicavet.assistant.shared.exception.ConflictException;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Cifra o token de acesso da Meta antes de gravar (AES-256-GCM, IV aleatório de 12 bytes junto do texto cifrado). A
 * chave vem de {@code assistant.whatsapp.token-key}, fora do banco: quem copiar o banco não leva o token.
 */
class TokenCipher {

    private static final String PREFIX = "v1:";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecureRandom random = new SecureRandom();
    private final WhatsAppSettings settings;

    TokenCipher(WhatsAppSettings settings) {
        this.settings = settings;
    }

    String encrypt(String token) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, iv));
            byte[] sealed = cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));
            return PREFIX + Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + sealed.length)
                    .put(iv).put(sealed).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao cifrar o token", e);
        }
    }

    String decrypt(String stored) {
        if (stored == null || !stored.startsWith(PREFIX)) {
            throw new IllegalStateException("Token gravado em formato desconhecido");
        }
        try {
            byte[] all = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, all, 0, IV_BYTES));
            return new String(cipher.doFinal(all, IV_BYTES, all.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Não foi possível abrir o token: a chave mudou?", e);
        }
    }

    private SecretKeySpec key() {
        if (!settings.encryptionReady()) {
            throw new ConflictException("Falta configurar a chave de cifra do canal (WHATSAPP_TOKEN_KEY) no servidor.");
        }
        byte[] key = Base64.getDecoder().decode(settings.tokenKey().strip());
        if (key.length != 32) {
            throw new IllegalStateException("WHATSAPP_TOKEN_KEY deve ter 32 bytes em Base64");
        }
        return new SecretKeySpec(key, "AES");
    }
}
