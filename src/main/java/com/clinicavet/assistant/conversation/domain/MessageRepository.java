package com.clinicavet.assistant.conversation.domain;

import java.util.List;

public interface MessageRepository {

    Message save(Message message);

    /**
     * As {@code limit} mensagens mais recentes da conversa, em ordem cronológica (da mais antiga
     * para a mais nova). Base da janela de histórico (ADR-0004).
     */
    List<Message> findLatest(ConversationId conversationId, int limit);

    /** Uma página do histórico completo, em ordem cronológica. {@code page} começa em 0. */
    List<Message> findPage(ConversationId conversationId, int page, int size);

    long countByConversation(ConversationId conversationId);
}
