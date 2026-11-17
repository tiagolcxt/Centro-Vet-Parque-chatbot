package com.clinicavet.assistant.conversation.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Coleta de dados do tutor e do pet ({@code assistant.customer-data.*}, Etapa 5). Obrigatório: sem valor, a
 * aplicação não sobe.
 *
 * @param registeredValidity         por quanto tempo uma confirmação de outra conversa vale como CADASTRADA;
 *                                   depois, A_RECONFIRMAR (ADR-0010). <b>Provisório (P-08)</b>
 * @param appointmentRegisteredReply texto do sistema acrescentado quando a solicitação de agendamento passa a
 *                                   {@code PENDING}. Não confirma dia nem horário. <b>Provisório (P-10)</b>
 */
@Validated
@ConfigurationProperties("assistant.customer-data")
public record CustomerDataSettings(@NotNull Duration registeredValidity, @NotBlank String appointmentRegisteredReply) {
}
