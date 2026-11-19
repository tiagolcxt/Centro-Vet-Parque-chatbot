package com.clinicavet.assistant.platform;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Contas de usuário da clínica atual (ADR-0024). Usada pelo módulo de equipe. */
public interface Accounts {

    /** Ciclo de vida da conta: convidada → ativa → desativada. Nunca apagada (preserva a autoria). */
    enum Status {
        INVITED, ACTIVE, DISABLED
    }

    record View(UUID id, String email, UUID roleId, Status status) {
    }

    /**
     * Convite de uso único para definir a senha. O token só existe aqui, na resposta a quem convidou; o banco guarda
     * o hash. Na S1 o administrador entrega o link; o envio por e-mail fica para depois.
     */
    record InviteLink(UUID userId, String token, Instant expiresAt) {
    }

    InviteLink invite(String email, UUID roleId);

    /** Novo link para quem ainda não definiu a senha ou a esqueceu (redefinição feita pelo administrador). */
    InviteLink newInvite(UUID userId);

    void changeRole(UUID userId, UUID roleId);

    void disable(UUID userId);

    Optional<View> find(UUID userId);

    List<View> list();

    /** Quantas contas a clínica tem. O primeiro administrador só é criado numa clínica sem contas. */
    long count();
}
