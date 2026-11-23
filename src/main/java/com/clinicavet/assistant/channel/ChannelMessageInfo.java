package com.clinicavet.assistant.channel;

import com.clinicavet.assistant.conversation.application.MessageChannelInfo;
import com.clinicavet.assistant.platform.CurrentClinic;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Situação da entrega das respostas e anexos recebidos, para a tela da conversa. */
@Component
class ChannelMessageInfo implements MessageChannelInfo {

    private final OutboundMessageJpaRepository outbound;
    private final InboundStore inbound;

    ChannelMessageInfo(OutboundMessageJpaRepository outbound, InboundStore inbound) {
        this.outbound = outbound;
        this.inbound = inbound;
    }

    @Override
    public Map<UUID, Info> forMessages(Collection<UUID> messageIds) {
        if (messageIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Delivery> deliveries = new HashMap<>();
        outbound.findByMessageIdIn(messageIds).forEach(o -> deliveries.put(o.messageId(),
                new Delivery(o.status().name(), o.attempts(), o.lastErrorCode())));
        Map<UUID, Attachment> attachments = new HashMap<>();
        inbound.mediaOf(CurrentClinic.require(), messageIds).forEach(m -> attachments.put(m.messageId(),
                new Attachment(m.kind().name(), m.mime(), m.name(), m.id())));
        Map<UUID, Info> result = new HashMap<>();
        for (UUID id : messageIds) {
            Delivery d = deliveries.get(id);
            Attachment a = attachments.get(id);
            if (d != null || a != null) {
                result.put(id, new Info(d, a));
            }
        }
        return result;
    }
}
