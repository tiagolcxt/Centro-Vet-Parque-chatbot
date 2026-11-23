package com.clinicavet.assistant.channel;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contrato com a Meta: corpos no formato dos exemplos da documentação da Cloud API (Graph v27.0), com números e ids
 * fictícios. Se a Meta mudar o formato, é aqui que quebra primeiro.
 */
class WebhookContractTest {

    private static WebhookEvents.Parsed parse(String json) {
        return WebhookEvents.parse(JsonMapper.builder().build().readTree(json));
    }

    private static String change(String field, String value) {
        return """
                {"object":"whatsapp_business_account","entry":[{"id":"0","changes":[{"field":"%s","value":
                {"messaging_product":"whatsapp","metadata":{"display_phone_number":"550000000000",
                "phone_number_id":"100000000000001"},%s}}]}]}""".formatted(field, value);
    }

    @Test
    void textMessage_withWaIdAndBsuid() {
        WebhookEvents.Parsed parsed = parse(change("messages", """
                "contacts":[{"profile":{"name":"Fictício"},"wa_id":"5500900000001","user_id":"BR.ABC"}],
                "messages":[{"from":"5500900000001","from_user_id":"BR.ABC","id":"wamid.A","timestamp":"1795380203",
                "type":"text","text":{"body":"Oi"}}]"""));
        assertThat(parsed.inbound()).singleElement().satisfies(m -> {
            assertThat(m.phoneNumberId()).isEqualTo("100000000000001");
            assertThat(m.contact()).isEqualTo("5500900000001");
            assertThat(m.kind()).isEqualTo(WebhookEvents.Kind.TEXT);
            assertThat(m.text()).isEqualTo("Oi");
            assertThat(m.sentAt()).isEqualTo(Instant.ofEpochSecond(1795380203));
        });
    }

    @Test
    void contactWithoutPhoneNumber_usesTheBsuid() {
        WebhookEvents.Parsed parsed = parse(change("messages", """
                "contacts":[{"profile":{"name":"Fictício"},"user_id":"BR.SO-USUARIO"}],
                "messages":[{"from_user_id":"BR.SO-USUARIO","id":"wamid.B","timestamp":"1","type":"text",
                "text":{"body":"Oi"}}]"""));
        assertThat(parsed.inbound()).singleElement().extracting(WebhookEvents.Inbound::contact)
                .isEqualTo("BR.SO-USUARIO");
    }

    @Test
    void mediaButtonsAndUnsupportedTypes() {
        WebhookEvents.Parsed parsed = parse(change("messages", """
                "messages":[
                {"from":"5500900000001","id":"wamid.1","timestamp":"1","type":"document",
                 "document":{"id":"m1","mime_type":"application/pdf","filename":"exame.pdf","caption":"Resultado"}},
                {"from":"5500900000001","id":"wamid.2","timestamp":"2","type":"interactive",
                 "interactive":{"type":"button_reply","button_reply":{"id":"b1","title":"Sim"}}},
                {"from":"5500900000001","id":"wamid.3","timestamp":"3","type":"reaction",
                 "reaction":{"message_id":"wamid.X","emoji":"👍"}},
                {"from":"5500900000001","id":"wamid.4","timestamp":"4","type":"location",
                 "location":{"latitude":0,"longitude":0}}]"""));
        List<WebhookEvents.Inbound> inbound = parsed.inbound();
        assertThat(inbound).extracting(WebhookEvents.Inbound::kind).containsExactly(WebhookEvents.Kind.DOCUMENT,
                WebhookEvents.Kind.TEXT, WebhookEvents.Kind.OTHER);
        assertThat(inbound.get(0).mediaName()).isEqualTo("exame.pdf");
        assertThat(inbound.get(0).text()).isEqualTo("Resultado");
        assertThat(inbound.get(1).text()).isEqualTo("Sim");
    }

    @Test
    void statusesWithError() {
        WebhookEvents.Parsed parsed = parse(change("messages", """
                "statuses":[{"id":"wamid.S","status":"failed","timestamp":"1","recipient_id":"5500900000001",
                "errors":[{"code":130497,"title":"Business account is restricted from messaging users in this country."}]}]
                """));
        assertThat(parsed.statuses()).singleElement().satisfies(s -> {
            assertThat(s.wamid()).isEqualTo("wamid.S");
            assertThat(s.status()).isEqualTo("failed");
            assertThat(s.errorCode()).isEqualTo("130497");
        });
    }

    @Test
    void echoesFromTheBusinessApp_ignoringEditsAndRevokes() {
        WebhookEvents.Parsed parsed = parse(change("smb_message_echoes", """
                "message_echoes":[
                {"from":"550000000000","to":"5500900000001","id":"wamid.E1","timestamp":"1","type":"text",
                 "text":{"body":"Pode vir às 14h"}},
                {"from":"550000000000","to":"5500900000001","id":"wamid.E2","timestamp":"2","type":"edit"},
                {"from":"550000000000","to":"5500900000001","id":"wamid.E3","timestamp":"3","type":"revoke"}]"""));
        assertThat(parsed.inbound()).singleElement().satisfies(m -> {
            assertThat(m.kind()).isEqualTo(WebhookEvents.Kind.ECHO);
            assertThat(m.contact()).isEqualTo("5500900000001");
            assertThat(m.text()).isEqualTo("Pode vir às 14h");
        });
    }

    @Test
    void otherObjectsAreIgnored() {
        assertThat(parse("{\"object\":\"page\",\"entry\":[]}").inbound()).isEmpty();
    }

    @Test
    void signature_isHmacOfTheRawBody() {
        byte[] body = "{\"a\": 1}".getBytes(StandardCharsets.UTF_8);
        String header = WebhookSignature.sign(body, "segredo");
        assertThat(header).startsWith("sha256=").hasSize(7 + 64);
        assertThat(WebhookSignature.valid(body, header, "segredo")).isTrue();
        assertThat(WebhookSignature.valid(body, header.toUpperCase().replace("SHA256=", "sha256="), "segredo")).isTrue();
        assertThat(WebhookSignature.valid("{\"a\":1}".getBytes(StandardCharsets.UTF_8), header, "segredo")).isFalse();
        assertThat(WebhookSignature.valid(body, header, "outro")).isFalse();
        assertThat(WebhookSignature.valid(body, null, "segredo")).isFalse();
    }

    @Test
    void tokenCipher_roundTrip_andRandomIv() {
        WhatsAppSettings settings = new WhatsAppSettings(null, null, "ZmljdGljaWEtY2hhdmUtZGUtdGVzdGUtMzItYnl0ZXM=",
                null, null, null, null, null, null, null);
        TokenCipher cipher = new TokenCipher(settings);
        String a = cipher.encrypt("token-ficticio");
        String b = cipher.encrypt("token-ficticio");
        assertThat(a).isNotEqualTo(b).doesNotContain("token-ficticio");
        assertThat(cipher.decrypt(a)).isEqualTo("token-ficticio");

        TokenCipher otherKey = new TokenCipher(new WhatsAppSettings(null, null,
                "b3V0cmEtY2hhdmUtZmljdGljaWEtZGUtMzItYnl0ZXM=", null, null, null, null, null, null, null));
        assertThatThrownBy(() -> otherKey.decrypt(a)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void backoffGrowsAndRepeatsTheLastDelay() {
        WhatsAppSettings settings = new WhatsAppSettings(null, null, null, null, null, null, null, null, null, null);
        assertThat(settings.delayAfter(1)).hasSeconds(10);
        assertThat(settings.delayAfter(5)).hasMinutes(30);
        assertThat(settings.delayAfter(9)).hasMinutes(30);
        assertThat(settings.maxAttempts()).isEqualTo(5);
    }
}
