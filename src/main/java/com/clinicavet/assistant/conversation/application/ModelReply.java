package com.clinicavet.assistant.conversation.application;

/**
 * Resposta bruta do modelo, sem interpretação.
 *
 * @param content      texto devolvido (deveria ser o JSON do {@code AssistantTurn}); pode ser nulo
 * @param finishReason motivo de término informado pela API, em qualquer caixa ({@code STOP},
 *                     {@code LENGTH}...); pode ser nulo
 * @param refusal      texto de recusa da API, se houver
 * @param model        modelo que respondeu (o {@code gpt-6-luna} não tem snapshot datado, ADR-0016)
 * @param inputTokens  uso informado pela API, para métricas; pode ser nulo
 * @param outputTokens idem
 */
public record ModelReply(String content, String finishReason, String refusal, String model,
                         Integer inputTokens, Integer outputTokens) {

    public static ModelReply of(String content, String finishReason) {
        return new ModelReply(content, finishReason, null, null, null, null);
    }
}
