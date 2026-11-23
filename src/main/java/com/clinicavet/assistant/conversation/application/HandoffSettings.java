package com.clinicavet.assistant.conversation.application;

import com.clinicavet.assistant.conversation.domain.ClinicalPriority;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.EnumSet;
import java.util.Map;

/**
 * Encaminhamento ({@code assistant.handoff.*}). Obrigatório: sem valor, a aplicação não sobe.
 *
 * @param deadlines prazo de atendimento humano por nível (ADR-0007 item 6, ADR-0018 H11). <b>Provisório
 *                  (P-02)</b>: valores de desenvolvimento, sem decisão da clínica. Todos os seis níveis
 * @param replies   textos do sistema. <b>Provisórios (P-10, P-19)</b>; nenhum afirma notificação ou promete
 *                  contato. A orientação de emergência e o contato direto vêm da base (P-01, P-02)
 */
@Validated
@ConfigurationProperties("assistant.handoff")
public record HandoffSettings(@NotNull Map<ClinicalPriority, Duration> deadlines, @NotNull @Valid Replies replies) {

    public HandoffSettings {
        if (deadlines != null && !deadlines.keySet().containsAll(EnumSet.allOf(ClinicalPriority.class))) {
            throw new IllegalArgumentException("assistant.handoff.deadlines precisa dos seis níveis (P1 a P6)");
        }
        deadlines = deadlines == null ? null : Map.copyOf(deadlines);
    }

    /**
     * @param handoff                 depois de encaminhar: a conversa fica registrada para a equipe
     * @param waitingConfirmation     resposta em {@code WAITING_HUMAN}: mensagem recebida e registrada
     * @param humanOffer              oferta de atendimento humano (DA-31)
     * @param emergencyPlaceholder    no lugar da política de emergência enquanto P-01 não for aprovada
     * @param shortGuidancePlaceholder no lugar da orientação curta de quando procurar atendimento (P-01)
     * @param overduePlaceholder      no lugar da orientação de prazo vencido enquanto P-02 não for aprovada
     * @param waitingExplanation      em {@code WAITING_HUMAN}, quando o tutor mostra confusão ou insatisfação, ou para não
     *                                repetir a confirmação: explica, com honestidade, em que pé está a conversa (CT-19)
     * @param waitingExplanationRepeat variação da explicação, para não mandar o mesmo texto duas vezes seguidas
     * @param register                 pedido registrado para a equipe sem transferir a conversa (DA-40)
     * @param nextStepOpen             próximo passo com a clínica aberta; {@code {phone}} = telefone da recepção
     * @param nextStepClosed           próximo passo com a clínica fechada; {@code {when}} = próxima abertura
     * @param nextStepUnknown          próximo passo sem horário cadastrado para hoje
     * @param emergencyClosed          emergência com a clínica fechada (aviso)
     * @param emergencyReferral        onde procurar fora do horário; {@code {referral}} = local cadastrado (P-01)
     * @param handoffTo                encaminhamento com o setor (RF-S62); {@code {department}} = nome do setor. Sem
     *                                 valor, usa {@code handoff}. Diz para onde foi, nunca que alguém foi avisado (RN-S01)
     */
    public record Replies(@NotBlank String handoff, @NotBlank String waitingConfirmation, @NotBlank String humanOffer,
                          @NotBlank String emergencyPlaceholder, @NotBlank String shortGuidancePlaceholder,
                          @NotBlank String overduePlaceholder, @NotBlank String waitingExplanation,
                          @NotBlank String waitingExplanationRepeat, @NotBlank String register,
                          @NotBlank String nextStepOpen, @NotBlank String nextStepClosed,
                          @NotBlank String nextStepUnknown, @NotBlank String emergencyClosed,
                          @NotBlank String emergencyReferral, String handoffTo) {

        /** Texto de encaminhamento com o nome do setor, se houver modelo e setor. */
        public String handoffFor(String department) {
            if (department == null || department.isBlank() || handoffTo == null || handoffTo.isBlank()) {
                return handoff;
            }
            return handoffTo.replace("{department}", department.strip());
        }
    }

    public Duration deadlineFor(ClinicalPriority priority) {
        return deadlines.get(priority);
    }
}
