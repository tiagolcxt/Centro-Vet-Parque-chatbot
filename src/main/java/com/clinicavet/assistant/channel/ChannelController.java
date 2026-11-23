package com.clinicavet.assistant.channel;

import com.clinicavet.assistant.platform.AuditLog;
import com.clinicavet.assistant.platform.CurrentClinic;
import com.clinicavet.assistant.shared.exception.ConflictException;
import com.clinicavet.assistant.shared.exception.DomainValidationException;
import com.clinicavet.assistant.shared.exception.ResourceNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Canal WhatsApp da clínica na aplicação web (Configuração > Canal). Ligar ou trocar o número exige
 * {@code config:publish}: muda quem fala com os tutores. O token nunca volta para a tela nem vai para a auditoria.
 */
@RestController
@RequestMapping("/app/api/channel")
class ChannelController {

    /**
     * @param webhookReady   o servidor tem App Secret e token de verificação (sem eles, nada chega)
     * @param encryptionReady o servidor tem a chave que cifra o token (sem ela, não dá para salvar)
     */
    record ChannelView(boolean configured, String phoneNumberId, String displayPhone, String wabaId, boolean active,
                       boolean tokenSaved, Instant updatedAt, boolean webhookReady, boolean encryptionReady,
                       String webhookPath) {
    }

    /** @param accessToken só ao criar ou trocar; vazio mantém o atual */
    record ChannelRequest(@NotBlank @Pattern(regexp = "\\d{5,40}", message = "só números") String phoneNumberId,
                          @Size(max = 30) String displayPhone,
                          @Pattern(regexp = "\\d{0,40}", message = "só números") String wabaId,
                          @Size(max = 1000) String accessToken, boolean active) {
    }

    record TestResult(boolean ok, String displayPhone, String verifiedName, String errorCode, String error) {
    }

    private final ChannelAccountJpaRepository repository;
    private final ChannelAccounts accounts;
    private final TokenCipher cipher;
    private final WhatsAppApi api;
    private final WhatsAppSettings settings;
    private final InboundStore inbound;
    private final Outbox outbox;
    private final AuditLog audit;
    private final Clock clock;

    ChannelController(ChannelAccountJpaRepository repository, ChannelAccounts accounts, TokenCipher cipher,
                      WhatsAppApi api, WhatsAppSettings settings, InboundStore inbound, Outbox outbox, AuditLog audit,
                      Clock clock) {
        this.repository = repository;
        this.accounts = accounts;
        this.cipher = cipher;
        this.api = api;
        this.settings = settings;
        this.inbound = inbound;
        this.outbox = outbox;
        this.audit = audit;
        this.clock = clock;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('config:edit') or hasAuthority('config:publish')")
    @Transactional(readOnly = true)
    ChannelView get() {
        return view();
    }

    @PutMapping
    @PreAuthorize("hasAuthority('config:publish')")
    @Transactional
    ChannelView save(@Valid @RequestBody ChannelRequest body) {
        String phoneNumberId = body.phoneNumberId().strip();
        UUID clinic = CurrentClinic.require().value();
        if (accounts.ownerOfNumber(phoneNumberId).filter(owner -> !owner.equals(clinic)).isPresent()) {
            throw new ConflictException("Esse número já está ligado a outra clínica.");
        }
        String token = body.accessToken() == null ? "" : body.accessToken().strip();
        Instant now = clock.instant();
        ChannelAccountEntity account = repository.findFirstByProvider(ChannelAccountEntity.PROVIDER).orElse(null);
        if (account == null) {
            if (token.isEmpty()) {
                throw new DomainValidationException("accessToken", "informe o token de acesso");
            }
            account = new ChannelAccountEntity(phoneNumberId, cipher.encrypt(token), now);
        } else if (!token.isEmpty()) {
            account.replaceToken(cipher.encrypt(token), now);
        }
        account.change(phoneNumberId, blankToNull(body.displayPhone()), blankToNull(body.wabaId()), body.active(), now);
        repository.save(account);
        audit.record("channel.updated", "channel_account", account.id(),
                "WhatsApp " + (body.active() ? "ligado" : "desligado") + (token.isEmpty() ? "" : ", token trocado"));
        return view();
    }

    /** Confere número e token na Meta, sem enviar mensagem. */
    @PostMapping("/test")
    @PreAuthorize("hasAuthority('config:publish')")
    TestResult test() {
        WhatsAppApi.Credentials credentials = repository.findFirstByProvider(ChannelAccountEntity.PROVIDER)
                .map(a -> new WhatsAppApi.Credentials(a.phoneNumberId(), cipher.decrypt(a.accessTokenEncrypted())))
                .orElseThrow(() -> new ConflictException("Salve o número e o token antes de testar."));
        try {
            WhatsAppApi.PhoneInfo info = api.phoneInfo(credentials);
            return new TestResult(true, info.displayPhone(), info.verifiedName(), null, null);
        } catch (WhatsAppApi.ApiException e) {
            return new TestResult(false, null, null, e.code(), e.getMessage());
        }
    }

    /** Arquivo que o tutor mandou, buscado na Meta na hora (não fica guardado aqui). */
    @GetMapping("/media/{ref}")
    @PreAuthorize("hasAuthority('conversation:view')")
    ResponseEntity<byte[]> media(@PathVariable UUID ref) {
        InboundStore.MediaRef media = inbound.media(CurrentClinic.require(), ref)
                .orElseThrow(() -> new ResourceNotFoundException("Arquivo", ref));
        WhatsAppApi.Credentials credentials = accounts.credentials()
                .orElseThrow(() -> new ConflictException("O canal do WhatsApp está desligado."));
        WhatsAppApi.Media file;
        try {
            file = api.download(credentials, media.mediaId());
        } catch (WhatsAppApi.ApiException e) {
            throw new ConflictException("Não foi possível baixar o arquivo da Meta (" + e.code()
                    + "). Arquivos ficam disponíveis por tempo limitado.");
        }
        String name = media.name() != null ? media.name() : "arquivo-" + ref.toString().substring(0, 8);
        return ResponseEntity.ok()
                .contentType(safeType(file.mimeType() != null ? file.mimeType() : media.mime()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + name.replaceAll("[^\\w.\\- ]", "_")
                        + "\"")
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(file.bytes());
    }

    /** Tenta de novo uma resposta que não foi enviada. */
    @PostMapping("/messages/{messageId}/resend")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('conversation:reply')")
    void resend(@PathVariable UUID messageId) {
        if (!outbox.retry(messageId)) {
            throw new ConflictException("Essa mensagem não está marcada como não enviada.");
        }
    }

    private ChannelView view() {
        return repository.findFirstByProvider(ChannelAccountEntity.PROVIDER)
                .map(a -> new ChannelView(true, a.phoneNumberId(), a.displayPhone().orElse(null),
                        a.wabaId().orElse(null), a.active(), true, a.updatedAt(), settings.webhookReady(),
                        settings.encryptionReady(), "/webhooks/whatsapp"))
                .orElseGet(() -> new ChannelView(false, null, null, null, false, false, null, settings.webhookReady(),
                        settings.encryptionReady(), "/webhooks/whatsapp"));
    }

    /** Só tipos que o navegador mostra sem executar; o resto vira download. */
    private static MediaType safeType(String mime) {
        try {
            MediaType type = MediaType.parseMediaType(mime);
            String t = type.getType();
            boolean safe = (t.equals("image") && !type.getSubtype().contains("svg")) || t.equals("audio")
                    || t.equals("video") || type.isCompatibleWith(MediaType.APPLICATION_PDF);
            return safe ? type : MediaType.APPLICATION_OCTET_STREAM;
        } catch (RuntimeException e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
