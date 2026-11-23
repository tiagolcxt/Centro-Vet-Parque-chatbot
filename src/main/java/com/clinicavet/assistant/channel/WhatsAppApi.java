package com.clinicavet.assistant.channel;

/**
 * Chamadas à Cloud API do WhatsApp (ADR-0023). A implementação real usa a Graph API; os testes usam uma falsa, sem
 * rede e sem número real.
 */
public interface WhatsAppApi {

    /** Número da clínica na Meta e o token que autoriza falar por ele. O token nunca vai para log. */
    record Credentials(String phoneNumberId, String accessToken) {

        @Override
        public String toString() {
            return "Credentials[phoneNumberId=" + phoneNumberId + ", accessToken=***]";
        }
    }

    record PhoneInfo(String displayPhone, String verifiedName) {
    }

    record Media(byte[] bytes, String mimeType) {
    }

    /**
     * Falha devolvida pela Meta ou pela rede.
     *
     * @param code      código de erro da Meta (ex.: {@code 131047}) ou {@code HTTP_503}, {@code NETWORK}
     * @param retryable vale tentar de novo mais tarde (rede, limite de envio, erro do lado da Meta)
     */
    final class ApiException extends RuntimeException {

        private final String code;
        private final boolean retryable;

        public ApiException(String code, boolean retryable, String message) {
            super(message);
            this.code = code;
            this.retryable = retryable;
        }

        public String code() {
            return code;
        }

        public boolean retryable() {
            return retryable;
        }
    }

    /** Envia texto e devolve o id da mensagem na Meta ({@code wamid}). */
    String sendText(Credentials credentials, String to, String text);

    /** Confere o número e o token ("Testar conexão" na tela de canal). */
    PhoneInfo phoneInfo(Credentials credentials);

    /** Baixa um arquivo que o tutor mandou. */
    Media download(Credentials credentials, String mediaId);
}
