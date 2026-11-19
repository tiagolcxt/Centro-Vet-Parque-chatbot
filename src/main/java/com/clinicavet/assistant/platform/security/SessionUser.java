package com.clinicavet.assistant.platform.security;

import java.io.Serializable;
import java.util.UUID;

/**
 * O que a sessão HTTP guarda depois do login: só quem é e de qual clínica. Permissões e estado da conta são
 * recarregados a cada requisição ({@link StaffSessionFilter}).
 */
record SessionUser(UUID userId, UUID clinicId) implements Serializable {
}
