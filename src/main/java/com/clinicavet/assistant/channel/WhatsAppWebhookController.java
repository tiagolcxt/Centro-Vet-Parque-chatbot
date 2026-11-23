package com.clinicavet.assistant.channel;

import com.clinicavet.assistant.platform.ClinicId;
import com.clinicavet.assistant.platform.CurrentClinic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Webhook da Meta (ADR-0023). Público, mas só aceita o que vier assinado com o App Secret. A clínica é descoberta pelo
 * número que recebeu ({@code phone_number_id}), nunca pelo conteúdo. Responde 200 logo depois de gravar: o assistente
 * roda fora da requisição, e a Meta reenvia se não receber 200 a tempo (o reenvio é descartado pelo wamid).
 */
@RestController
@RequestMapping("/webhooks/whatsapp")
class WhatsAppWebhookController {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppWebhookController.class);
    private static final ObjectMapper JSON = JsonMapper.builder().build();

    private final WhatsAppSettings settings;
    private final ChannelAccounts accounts;
    private final InboundStore store;
    private final InboundProcessor processor;
    private final Outbox outbox;
    private final Clock clock;

    WhatsAppWebhookController(WhatsAppSettings settings, ChannelAccounts accounts, InboundStore store,
                              InboundProcessor processor, Outbox outbox, Clock clock) {
        this.settings = settings;
        this.accounts = accounts;
        this.store = store;
        this.processor = processor;
        this.outbox = outbox;
        this.clock = clock;
    }

    /** Verificação do endereço, feita pela Meta ao configurar o webhook: devolve o {@code hub.challenge}. */
    @GetMapping(produces = MediaType.TEXT_PLAIN_VALUE)
    ResponseEntity<String> verify(@RequestParam(name = "hub.mode", required = false) String mode,
                                  @RequestParam(name = "hub.verify_token", required = false) String token,
                                  @RequestParam(name = "hub.challenge", required = false) String challenge) {
        if (!settings.webhookReady()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        boolean ok = "subscribe".equals(mode) && token != null && challenge != null && MessageDigest.isEqual(
                token.getBytes(StandardCharsets.UTF_8), settings.verifyToken().getBytes(StandardCharsets.UTF_8));
        return ok ? ResponseEntity.ok(challenge) : ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    @PostMapping(consumes = MediaType.ALL_VALUE)
    ResponseEntity<Void> receive(@RequestBody(required = false) byte[] body,
                                 @RequestHeader(name = WebhookSignature.HEADER, required = false) String signature) {
        if (!settings.webhookReady()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        if (body == null || !WebhookSignature.valid(body, signature, settings.appSecret())) {
            log.warn("Webhook do WhatsApp recusado: assinatura ausente ou inválida");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        JsonNode root;
        try {
            root = JSON.readTree(body);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
        WebhookEvents.Parsed parsed = WebhookEvents.parse(root);
        Instant now = clock.instant();
        Set<InboundStore.ContactKey> touched = new LinkedHashSet<>();
        for (WebhookEvents.Inbound m : parsed.inbound()) {
            Optional<ClinicId> clinic = accounts.clinicOf(m.phoneNumberId());
            if (clinic.isEmpty()) {
                log.warn("Mensagem para um número sem clínica ativa; ignorada [phoneNumberId={}]", m.phoneNumberId());
                continue;
            }
            if (store.insert(clinic.get(), m, now)) {
                touched.add(new InboundStore.ContactKey(clinic.get(), m.contact()));
            }
        }
        for (WebhookEvents.Status s : parsed.statuses()) {
            accounts.clinicOf(s.phoneNumberId()).ifPresent(c -> CurrentClinic.run(c, () -> outbox.report(s)));
        }
        touched.forEach(processor::schedule);
        return ResponseEntity.ok().build();
    }
}
