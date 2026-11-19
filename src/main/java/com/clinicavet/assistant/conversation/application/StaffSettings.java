package com.clinicavet.assistant.conversation.application;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Textos do atendimento pela equipe (Etapa S2). PROVISÓRIOS (P-10).
 *
 * @param assumedReply enviado ao tutor quando um funcionário assume (DA-47). {@code {name}} e {@code {department}}
 *                     são trocados pelo nome do funcionário e do setor. Nunca diz "avisei" (RN-S01)
 */
@Validated
@ConfigurationProperties(prefix = "assistant.staff")
public record StaffSettings(@NotBlank String assumedReply) {

    public String assumed(String name, String department) {
        return assumedReply.replace("{name}", name).replace("{department}", department).strip();
    }
}
