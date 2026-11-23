package com.clinicavet.assistant.channel;

import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Lê o corpo do webhook da Meta ({@code object = whatsapp_business_account}). Cada {@code entry[].changes[]} traz o
 * número da clínica em {@code value.metadata.phone_number_id} e, conforme {@code field}:
 * <ul>
 *   <li>{@code messages}: mensagens do tutor ({@code value.messages[]}) e situação das respostas
 *       ({@code value.statuses[]});</li>
 *   <li>{@code smb_message_echoes}: respostas dadas pelo app do WhatsApp Business no celular (coexistência).</li>
 * </ul>
 * O resto é ignorado. Nada daqui vai para log: tem número e texto do tutor.
 */
final class WebhookEvents {

    enum Kind { TEXT, IMAGE, DOCUMENT, AUDIO, VIDEO, OTHER, ECHO }

    /**
     * @param contact quem é o tutor: o número ({@code wa_id}) ou, sem ele, o identificador de usuário da Meta (BSUID)
     * @param text    texto, legenda ou o título do botão escolhido; vazio quando não há
     */
    record Inbound(String phoneNumberId, String wamid, String contact, Kind kind, String text, String mediaId,
                   String mediaMime, String mediaName, Instant sentAt) {
    }

    record Status(String phoneNumberId, String wamid, String status, String errorCode) {
    }

    record Parsed(List<Inbound> inbound, List<Status> statuses) {
    }

    private WebhookEvents() {
    }

    static Parsed parse(JsonNode root) {
        List<Inbound> inbound = new ArrayList<>();
        List<Status> statuses = new ArrayList<>();
        if (!"whatsapp_business_account".equals(root.path("object").asString(""))) {
            return new Parsed(inbound, statuses);
        }
        for (JsonNode entry : root.path("entry")) {
            for (JsonNode change : entry.path("changes")) {
                String field = change.path("field").asString("");
                JsonNode value = change.path("value");
                String phoneNumberId = value.path("metadata").path("phone_number_id").asString("");
                if (phoneNumberId.isBlank()) {
                    continue;
                }
                if (field.equals("messages")) {
                    for (JsonNode message : value.path("messages")) {
                        customerMessage(phoneNumberId, message, value.path("contacts")).ifPresent(inbound::add);
                    }
                    for (JsonNode status : value.path("statuses")) {
                        String id = status.path("id").asString("");
                        if (!id.isBlank()) {
                            String error = status.path("errors").path(0).path("code").asString(null);
                            statuses.add(new Status(phoneNumberId, id, status.path("status").asString(""), error));
                        }
                    }
                } else if (field.equals("smb_message_echoes")) {
                    for (JsonNode echo : value.path("message_echoes")) {
                        echo(phoneNumberId, echo).ifPresent(inbound::add);
                    }
                }
            }
        }
        return new Parsed(inbound, statuses);
    }

    private static java.util.Optional<Inbound> customerMessage(String phoneNumberId, JsonNode m, JsonNode contacts) {
        String wamid = m.path("id").asString("");
        String contact = firstNonBlank(m.path("from").asString(""), m.path("from_user_id").asString(""),
                contacts.path(0).path("wa_id").asString(""), contacts.path(0).path("user_id").asString(""));
        String type = m.path("type").asString("");
        // Reações e avisos do sistema (troca de número etc.) não são mensagens para responder.
        if (wamid.isBlank() || contact.isBlank() || type.equals("reaction") || type.equals("system")) {
            return java.util.Optional.empty();
        }
        Instant sentAt = timestamp(m);
        return java.util.Optional.of(switch (type) {
            case "text" -> new Inbound(phoneNumberId, wamid, contact, Kind.TEXT, m.path("text").path("body").asString(""),
                    null, null, null, sentAt);
            case "button" -> new Inbound(phoneNumberId, wamid, contact, Kind.TEXT,
                    m.path("button").path("text").asString(""), null, null, null, sentAt);
            case "interactive" -> new Inbound(phoneNumberId, wamid, contact, Kind.TEXT, firstNonBlank(
                    m.path("interactive").path("button_reply").path("title").asString(""),
                    m.path("interactive").path("list_reply").path("title").asString("")), null, null, null, sentAt);
            case "image", "document", "audio", "video", "sticker" -> media(phoneNumberId, wamid, contact, type, m, sentAt);
            default -> new Inbound(phoneNumberId, wamid, contact, Kind.OTHER, "", null, null, null, sentAt);
        });
    }

    private static Inbound media(String phoneNumberId, String wamid, String contact, String type, JsonNode m,
                                 Instant sentAt) {
        JsonNode media = m.path(type);
        Kind kind = switch (type) {
            case "image", "sticker" -> Kind.IMAGE;
            case "document" -> Kind.DOCUMENT;
            case "audio" -> Kind.AUDIO;
            default -> Kind.VIDEO;
        };
        return new Inbound(phoneNumberId, wamid, contact, kind, media.path("caption").asString(""),
                media.path("id").asString(null), media.path("mime_type").asString(null),
                media.path("filename").asString(null), sentAt);
    }

    private static java.util.Optional<Inbound> echo(String phoneNumberId, JsonNode e) {
        String wamid = e.path("id").asString("");
        String to = firstNonBlank(e.path("to").asString(""), e.path("to_user_id").asString(""));
        String type = e.path("type").asString("");
        // Edição e apagamento de mensagem já enviada: a conversa guarda o que foi mandado.
        if (wamid.isBlank() || to.isBlank() || type.equals("edit") || type.equals("revoke")
                || type.equals("reaction")) {
            return java.util.Optional.empty();
        }
        String text = type.equals("text") ? e.path("text").path("body").asString("")
                : e.path(type).path("caption").asString("");
        return java.util.Optional.of(new Inbound(phoneNumberId, wamid, to, Kind.ECHO, text, null, null, null,
                timestamp(e)));
    }

    private static Instant timestamp(JsonNode node) {
        try {
            return Instant.ofEpochSecond(Long.parseLong(node.path("timestamp").asString("0")));
        } catch (NumberFormatException e) {
            return Instant.EPOCH;
        }
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v.strip();
            }
        }
        return "";
    }
}
