package com.clinicavet.assistant.conversation.application;

import com.clinicavet.assistant.conversation.domain.ConversationExpirationPolicy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Parâmetros da conversa ({@code assistant.conversation.*}). Todos obrigatórios: sem valor no
 * {@code application.yml}, a aplicação não sobe.
 *
 * @param expiration               prazos de inatividade por status (ADR-0009). <b>Pendentes (P-05)</b>
 * @param historyMaxMessages       mensagens da conversa enviadas ao modelo, contando a atual (ADR-0004)
 * @param historyTokenBudget       orçamento estimado de tokens das mensagens anteriores (ADR-0004, DA-28)
 * @param maxCustomerMessageLength tamanho máximo da mensagem do tutor, em caracteres (DA-28)
 * @param retryBackoff             espera antes da única nova tentativa do turno (DA-28)
 * @param fallbackReply            resposta segura padrão (ADR-0002). <b>Texto provisório (P-10)</b>
 * @param firstReplyIntro          apresentação acrescentada pelo backend à primeira resposta da conversa quando o modelo
 *                                 não se apresentou (DA-38). Nome do assistente pendente da clínica (P-10)
 */
@Validated
@ConfigurationProperties("assistant.conversation")
public record ConversationSettings(
        @NotNull @Valid Expiration expiration,
        @Positive int historyMaxMessages,
        @Positive int historyTokenBudget,
        @Positive int maxCustomerMessageLength,
        @NotNull Duration retryBackoff,
        @NotBlank String fallbackReply,
        @NotBlank String firstReplyIntro) {

    public record Expiration(@NotNull Duration open, @NotNull Duration waitingHuman) {
    }

    public ConversationExpirationPolicy expirationPolicy() {
        return new ConversationExpirationPolicy(expiration.open(), expiration.waitingHuman());
    }
}
