package com.clinicavet.assistant.conversation.api;

import com.clinicavet.assistant.conversation.application.HandleIncomingMessageUseCase;
import com.clinicavet.assistant.conversation.domain.ClinicalPriority;
import com.clinicavet.assistant.conversation.domain.Conversation;
import com.clinicavet.assistant.conversation.domain.ConversationStatus;
import com.clinicavet.assistant.conversation.domain.HandoffReason;
import com.clinicavet.assistant.conversation.domain.Intent;
import com.clinicavet.assistant.conversation.domain.Message;
import com.clinicavet.assistant.conversation.domain.MessageAuthor;
import com.clinicavet.assistant.shared.domain.ContactIdentity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// DTOs próprios da API (DM-11). O domínio nunca é serializado diretamente.

/** @param externalContactId identificador do contato no canal (no canal API, informado por quem chama) */
record OpenConversationRequest(
        @NotBlank @Size(max = ContactIdentity.MAX_EXTERNAL_ID_LENGTH) String externalContactId) {
}

/**
 * O tamanho máximo do texto (2.000 caracteres, DA-28) é configuração e é verificado no caso de uso;
 * o {@code @Size} aqui só barra corpos absurdos antes disso.
 */
record SendMessageRequest(@NotBlank @Size(max = Message.MAX_CONTENT_LENGTH) String text) {
}

record ConversationResponse(UUID id, ConversationStatus status, Instant startedAt, Instant lastActivityAt) {

    static ConversationResponse from(Conversation conversation) {
        return new ConversationResponse(conversation.id().value(), conversation.status(),
                conversation.startedAt(), conversation.lastActivityAt());
    }
}

/**
 * @param conversationId  conversa em que a mensagem entrou. Pode ser outra que a da URL, se aquela estava
 *                        encerrada ou expirada (ADR-0009)
 * @param newConversation a conversa foi aberta por esta mensagem
 * @param intent          intenção do turno, só registro na Etapa 2; {@code null} se o turno falhou
 * @param fallback        {@code true} quando o modelo falhou e o texto foi composto só pelo backend (ADR-0002)
 * @param priority        prioridade clínica da conversa depois do turno (ADR-0018)
 * @param handoffReason   motivo do encaminhamento, se a conversa está com a equipe (ADR-0007)
 */
record TurnResponse(UUID conversationId, boolean newConversation, String reply, ConversationStatus status,
                    Intent intent, boolean fallback, ClinicalPriority priority, HandoffReason handoffReason) {

    static TurnResponse from(HandleIncomingMessageUseCase.Result result) {
        return new TurnResponse(result.conversationId().value(), result.newConversation(), result.reply(),
                result.status(), result.intent().orElse(null), result.fallback(), result.priority(),
                result.handoffReason().orElse(null));
    }
}

record MessageResponse(UUID id, MessageAuthor author, String content, Intent intent, Instant createdAt) {

    static MessageResponse from(Message message) {
        return new MessageResponse(message.id().value(), message.author(), message.content(),
                message.intent().orElse(null), message.createdAt());
    }
}

record MessagePageResponse(UUID conversationId, List<MessageResponse> content, int page, int size,
                           long totalElements) {
}
