package com.clinicavet.assistant.conversation.application;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * O que o canal sabe de cada mensagem (Etapa S4): situação da entrega da resposta e o anexo que o tutor mandou. O
 * módulo do canal implementa; sem canal (API de testes), não há informação.
 */
public interface MessageChannelInfo {

    /** {@code status}: PENDING, SENT, DELIVERED, READ ou FAILED. */
    record Delivery(String status, int attempts, String errorCode) {
    }

    /** {@code kind}: IMAGE, DOCUMENT, AUDIO, VIDEO ou OTHER; {@code ref} abre o arquivo pela aplicação. */
    record Attachment(String kind, String mime, String name, UUID ref) {
    }

    record Info(Delivery delivery, Attachment attachment) {
    }

    Map<UUID, Info> forMessages(Collection<UUID> messageIds);
}
