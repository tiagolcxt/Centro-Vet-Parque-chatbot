package com.clinicavet.assistant.channel;

import com.clinicavet.assistant.platform.ClinicId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Contas do WhatsApp: credenciais da clínica atual e a busca da clínica pelo número que recebeu a mensagem. */
@Component
class ChannelAccounts {

    private final ChannelAccountJpaRepository accounts;
    private final JdbcTemplate jdbc;
    private final TokenCipher cipher;

    ChannelAccounts(ChannelAccountJpaRepository accounts, JdbcTemplate jdbc, TokenCipher cipher) {
        this.accounts = accounts;
        this.jdbc = jdbc;
        this.cipher = cipher;
    }

    /**
     * Clínica dona do número, com a conta ativa. Busca em todas as clínicas de propósito: é assim que o webhook descobre
     * a clínica, antes de existir sessão (ADR-0021). Só devolve o id; o resto é lido já dentro da clínica.
     */
    Optional<ClinicId> clinicOf(String phoneNumberId) {
        List<String> found = jdbc.queryForList("""
                SELECT BIN_TO_UUID(clinic_id) FROM channel_account
                WHERE phone_number_id = ? AND provider = ? AND status = 'ACTIVE'
                """, String.class, phoneNumberId, ChannelAccountEntity.PROVIDER);
        return found.stream().findFirst().map(ClinicId::of);
    }

    /** Clínica (qualquer uma) que já usa o número: o mesmo número não pode ficar em duas clínicas. */
    Optional<UUID> ownerOfNumber(String phoneNumberId) {
        return jdbc.queryForList("SELECT BIN_TO_UUID(clinic_id) FROM channel_account WHERE phone_number_id = ?",
                String.class, phoneNumberId).stream().findFirst().map(UUID::fromString);
    }

    /** Credenciais da clínica atual, se a conta existir e estiver ativa. */
    Optional<WhatsAppApi.Credentials> credentials() {
        return accounts.findFirstByProvider(ChannelAccountEntity.PROVIDER).filter(ChannelAccountEntity::active)
                .map(a -> new WhatsAppApi.Credentials(a.phoneNumberId(), cipher.decrypt(a.accessTokenEncrypted())));
    }
}
