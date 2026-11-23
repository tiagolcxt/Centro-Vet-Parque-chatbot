package com.clinicavet.assistant.shared.domain;

/**
 * Prioridade operacional: a ordem das filas (D9, revisão pré-piloto 3.2). Derivada por regra do risco clínico, do tipo
 * da demanda e do atraso; a equipe pode trocar (auditado). Nunca rebaixa um risco urgente por conta própria.
 * <p>
 * Regra inicial, provisória até a clínica revisar:
 * <ul>
 *   <li>possível emergência → imediata; urgente → alta;</li>
 *   <li>acompanhamento clínico, sem sinal ou não avaliado → normal;</li>
 *   <li>sem sinal clínico em demanda de pouco impacto (informação administrativa, outros) → baixa;</li>
 *   <li>prazo vencido sobe um nível (até alta; imediata só por risco).</li>
 * </ul>
 */
public enum OperationalPriority {
    IMMEDIATE, HIGH, NORMAL, LOW;

    /** 1 = mais à frente na fila. */
    public int rank() {
        return ordinal() + 1;
    }

    /**
     * @param lowImpact demanda de pouco impacto (decidido por quem chama, pelo tipo)
     * @param overdue   passou do prazo de atendimento
     */
    public static OperationalPriority derive(ClinicalRisk risk, boolean lowImpact, boolean overdue) {
        OperationalPriority base = switch (risk) {
            case POSSIBLE_EMERGENCY -> IMMEDIATE;
            case URGENT -> HIGH;
            case CLINICAL_FOLLOW_UP, NOT_ASSESSED -> NORMAL;
            case NONE_IDENTIFIED -> lowImpact ? LOW : NORMAL;
        };
        if (overdue && base != IMMEDIATE && base != HIGH) {
            return base == LOW ? NORMAL : HIGH;
        }
        return base;
    }

    public static OperationalPriority parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return valueOf(raw.strip().toUpperCase(java.util.Locale.ROOT));
    }
}
