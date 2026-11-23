package com.clinicavet.assistant.platform;

/**
 * Quanto o assistente faz em cada clínica (ADR-0025, DA-48, RF-S64). A prioridade, os sinais de alerta e a orientação
 * de emergência valem em todos os níveis. Quem muda o nível é a equipe da plataforma, com critério escrito.
 */
public enum ActivationLevel {

    /** 1. Organiza: classifica, guarda os dados e abre o pedido no setor. Não conversa: o tutor recebe o recebimento. */
    ORGANIZE(1, "Organiza"),
    /** 2. Informa: responde só sobre a clínica (horário, serviços, preços divulgados). O resto é encaminhado. */
    INFORM(2, "Informa"),
    /** 3. Completo: atendimento completo (coleta de dados, agendamento, encaminhamento resolutivo). */
    FULL(3, "Completo");

    private final int number;
    private final String label;

    ActivationLevel(int number, String label) {
        this.number = number;
        this.label = label;
    }

    public int number() {
        return number;
    }

    public String label() {
        return label;
    }

    public static ActivationLevel of(int number) {
        for (ActivationLevel level : values()) {
            if (level.number == number) {
                return level;
            }
        }
        throw new IllegalArgumentException("nível de ativação inválido: " + number);
    }
}
