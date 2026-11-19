package com.clinicavet.assistant.platform;

import java.util.UUID;

/**
 * Evento publicado na transação que desativa uma conta (RN-S07). Os módulos de atendimento e solicitações devolvem
 * ao setor o que estava com a pessoa, na mesma transação e com a clínica já definida.
 */
public record AccountDisabled(UUID userId, ClinicId clinic) {
}
