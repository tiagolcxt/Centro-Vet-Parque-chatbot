package com.clinicavet.assistant.channel;

import com.clinicavet.assistant.conversation.domain.MessageId;
import com.clinicavet.assistant.conversation.domain.MessageRepository;
import com.clinicavet.assistant.conversation.domain.OutgoingMessageRecorded;
import com.clinicavet.assistant.platform.ClinicId;
import com.clinicavet.assistant.platform.CurrentClinic;
import com.clinicavet.assistant.platform.LiveUpdates;
import com.clinicavet.assistant.shared.domain.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Envio das respostas pelo WhatsApp (outbox, RF-S52, RF-S55).
 * <ol>
 *   <li>A resposta do assistente ou da equipe é gravada; na mesma transação, entra aqui como {@code PENDING}. Se a
 *       transação desfaz (simulador, erro), nada fica para enviar;</li>
 *   <li>o despachante envia. Falha que passa com o tempo (rede, limite da Meta) espera e tenta de novo; falha que não
 *       passa (número inválido, fora da janela de 24 horas) ou o fim das tentativas marca {@code FAILED}, e a equipe
 *       vê "não enviada" na conversa;</li>
 *   <li>a Meta avisa pelo webhook: enviada, entregue, lida ou falhou.</li>
 * </ol>
 * Pelo menos uma vez: se o servidor cair entre enviar e gravar, a mensagem pode sair duas vezes.
 */
@Component
class Outbox {

    private static final Logger log = LoggerFactory.getLogger(Outbox.class);

    /** Tempo em que uma tentativa em curso fica reservada (se o servidor cair, volta a valer depois disso). */
    static final Duration LEASE = Duration.ofMinutes(2);

    private record Due(UUID id, ClinicId clinic) {
    }

    private record Claimed(UUID conversationId, String recipient, String text, WhatsAppApi.Credentials credentials) {
    }

    private final OutboundMessageJpaRepository outbound;
    private final MessageRepository messages;
    private final ChannelAccounts accounts;
    private final WhatsAppApi api;
    private final WhatsAppSettings settings;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final LiveUpdates live;
    private final Clock clock;

    Outbox(OutboundMessageJpaRepository outbound, MessageRepository messages, ChannelAccounts accounts, WhatsAppApi api,
           WhatsAppSettings settings, JdbcTemplate jdbc, TransactionTemplate tx, LiveUpdates live, Clock clock) {
        this.outbound = outbound;
        this.messages = messages;
        this.accounts = accounts;
        this.api = api;
        this.settings = settings;
        this.jdbc = jdbc;
        this.tx = tx;
        this.live = live;
        this.clock = clock;
    }

    /** Na transação de quem gravou a resposta: só conversas do WhatsApp têm o que enviar. */
    @EventListener
    void on(OutgoingMessageRecorded event) {
        if (event.contact().channel() != Channel.WHATSAPP) {
            return;
        }
        outbound.save(new OutboundMessageEntity(event.conversationId().value(), event.messageId().value(),
                event.contact().externalContactId(), clock.instant()));
    }

    /** Envia o que está na hora, de todas as clínicas. Devolve quantas tentativas fez. */
    int dispatchDue() {
        List<Due> due = jdbc.query("""
                SELECT BIN_TO_UUID(id) AS id, BIN_TO_UUID(clinic_id) AS clinic FROM outbound_message
                WHERE status = 'PENDING' AND next_attempt_at <= ? ORDER BY next_attempt_at LIMIT 50
                """, (rs, i) -> new Due(UUID.fromString(rs.getString("id")), ClinicId.of(rs.getString("clinic"))),
                Utc.of(clock.instant()));
        due.forEach(d -> CurrentClinic.run(d.clinic(), () -> dispatch(d.id())));
        return due.size();
    }

    private void dispatch(UUID id) {
        Claimed claimed;
        try {
            claimed = tx.execute(status -> claim(id));
        } catch (org.springframework.orm.ObjectOptimisticLockingFailureException e) {
            return; // outro despachante pegou
        }
        if (claimed == null) {
            return;
        }
        String wamid = null;
        WhatsAppApi.ApiException failure = null;
        if (claimed.credentials() == null) {
            failure = new WhatsAppApi.ApiException("NO_ACCOUNT", true, "Canal do WhatsApp não configurado ou desligado.");
        } else {
            try {
                wamid = api.sendText(claimed.credentials(), claimed.recipient(), claimed.text());
            } catch (WhatsAppApi.ApiException e) {
                failure = e;
            } catch (RuntimeException e) {
                failure = new WhatsAppApi.ApiException("UNEXPECTED", true, "Falha inesperada no envio.");
                log.warn("Falha inesperada ao enviar pelo WhatsApp [outbound={}]", id, e);
            }
        }
        String sentWamid = wamid;
        WhatsAppApi.ApiException error = failure;
        tx.executeWithoutResult(status -> outbound.findById(id).ifPresent(o -> {
            Instant now = clock.instant();
            if (error == null) {
                o.sent(sentWamid, now);
            } else {
                boolean last = o.attempts() + 1 >= settings.maxAttempts();
                Optional<Duration> retry = error.retryable() && !last
                        ? Optional.of(settings.delayAfter(o.attempts() + 1)) : Optional.empty();
                o.failed(error.code(), retry, now);
                log.warn("Envio pelo WhatsApp falhou [outbound={}, código={}, tentativa={}, desistiu={}]", id,
                        error.code(), o.attempts(), retry.isEmpty());
            }
        }));
        live.changed(LiveUpdates.Topic.CONVERSATION, claimed.conversationId());
    }

    private Claimed claim(UUID id) {
        OutboundMessageEntity o = outbound.findById(id).orElse(null);
        Instant now = clock.instant();
        if (o == null || o.status() != OutboundMessageEntity.Status.PENDING) {
            return null;
        }
        o.claim(now, LEASE);
        outbound.flush();
        String text = messages.findById(new MessageId(o.messageId())).map(m -> m.content()).orElse(null);
        if (text == null) {
            o.failed("NO_MESSAGE", Optional.empty(), now);
            return null;
        }
        WhatsAppApi.Credentials credentials;
        try {
            credentials = accounts.credentials().orElse(null);
        } catch (RuntimeException e) {
            credentials = null; // chave de cifra ausente ou trocada: tenta de novo depois, a equipe vê o erro
            log.warn("Credenciais do WhatsApp ilegíveis [motivo={}]", e.getClass().getSimpleName());
        }
        return new Claimed(o.conversationId(), o.recipient(), text, credentials);
    }

    /** Aviso de situação do webhook, já na clínica dona do número. */
    void report(WebhookEvents.Status status) {
        OutboundMessageEntity.Status reported = switch (status.status()) {
            case "sent" -> OutboundMessageEntity.Status.SENT;
            case "delivered" -> OutboundMessageEntity.Status.DELIVERED;
            case "read" -> OutboundMessageEntity.Status.READ;
            case "failed" -> OutboundMessageEntity.Status.FAILED;
            default -> null;
        };
        if (reported == null) {
            return;
        }
        UUID conversation = tx.execute(s -> outbound.findFirstByWamid(status.wamid())
                .filter(o -> o.report(reported, status.errorCode(), clock.instant()))
                .map(OutboundMessageEntity::conversationId).orElse(null));
        if (conversation != null) {
            live.changed(LiveUpdates.Topic.CONVERSATION, conversation);
        }
    }

    /** A equipe pede para tentar de novo uma resposta que não saiu. */
    boolean retry(UUID messageId) {
        UUID conversation = tx.execute(s -> outbound.findByMessageId(messageId)
                .filter(o -> o.status() == OutboundMessageEntity.Status.FAILED)
                .map(o -> {
                    o.retryNow(clock.instant());
                    return o.conversationId();
                }).orElse(null));
        if (conversation == null) {
            return false;
        }
        live.changed(LiveUpdates.Topic.CONVERSATION, conversation);
        return true;
    }
}
