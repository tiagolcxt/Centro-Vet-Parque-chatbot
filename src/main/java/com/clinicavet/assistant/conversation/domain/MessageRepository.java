package com.clinicavet.assistant.conversation.domain;

import com.clinicavet.assistant.shared.domain.ConversationId;
import java.util.List;

public interface MessageRepository {

    Message save(Message message);

    /** Uma mensagem da clínica atual (o canal lê o texto da resposta para enviar, Etapa S4). */
    java.util.Optional<Message> findById(MessageId id);

    /**
     * As {@code limit} mensagens mais recentes da conversa, em ordem cronológica (da mais antiga
     * para a mais nova). Base da janela de histórico (ADR-0004).
     */
    List<Message> findLatest(ConversationId conversationId, int limit);

    /** Uma página do histórico completo, em ordem cronológica. {@code page} começa em 0. */
    List<Message> findPage(ConversationId conversationId, int page, int size);

    long countByConversation(ConversationId conversationId);

    /** Quando o tutor escreveu pela última vez: abre a janela de 24 horas do WhatsApp (ADR-0023, item 6). */
    java.util.Optional<java.time.Instant> lastCustomerMessageAt(ConversationId conversationId);
}
