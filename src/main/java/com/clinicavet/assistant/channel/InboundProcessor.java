package com.clinicavet.assistant.channel;

import com.clinicavet.assistant.conversation.application.HandleIncomingMessageUseCase;
import com.clinicavet.assistant.conversation.application.OpenConversationUseCase;
import com.clinicavet.assistant.conversation.application.RecordBusinessAppMessageUseCase;
import com.clinicavet.assistant.platform.ClinicId;
import com.clinicavet.assistant.platform.CurrentClinic;
import com.clinicavet.assistant.shared.domain.Channel;
import com.clinicavet.assistant.shared.domain.ContactIdentity;
import com.clinicavet.assistant.shared.exception.ConcurrentTurnException;
import com.clinicavet.assistant.shared.exception.DomainException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Leva as mensagens recebidas para a conversa (RF-S50). Um contato por vez e na ordem em que o tutor mandou: duas
 * mensagens seguidas do mesmo tutor não rodam o assistente em paralelo. Contatos diferentes andam juntos.
 * <p>
 * Uma mensagem só é marcada como processada depois que o turno gravou a mensagem do tutor. Se cair antes, a varredura
 * tenta de novo (até {@code max-attempts}). Mídia sem legenda entra como um texto curto ("[Imagem enviada sem texto]")
 * e o arquivo fica disponível para a equipe.
 */
@Component
class InboundProcessor {

    private static final Logger log = LoggerFactory.getLogger(InboundProcessor.class);

    private final InboundStore store;
    private final OpenConversationUseCase open;
    private final HandleIncomingMessageUseCase handle;
    private final RecordBusinessAppMessageUseCase businessApp;
    private final WhatsAppSettings settings;
    private final Clock clock;
    private final ChannelAccounts accounts;
    private final WhatsAppApi api;
    private final org.springframework.beans.factory.ObjectProvider<AudioTranscriber> transcriber;
    private final ConcurrentHashMap<InboundStore.ContactKey, ReentrantLock> locks = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    InboundProcessor(InboundStore store, OpenConversationUseCase open, HandleIncomingMessageUseCase handle,
                     RecordBusinessAppMessageUseCase businessApp, WhatsAppSettings settings, Clock clock,
                     ChannelAccounts accounts, WhatsAppApi api,
                     org.springframework.beans.factory.ObjectProvider<AudioTranscriber> transcriber) {
        this.accounts = accounts;
        this.api = api;
        this.transcriber = transcriber;
        this.store = store;
        this.open = open;
        this.handle = handle;
        this.businessApp = businessApp;
        this.settings = settings;
        this.clock = clock;
    }

    /** Processa o que estiver pendente do contato: em segundo plano, ou na hora (testes). */
    void schedule(InboundStore.ContactKey key) {
        if (settings.inboundAsync()) {
            executor.execute(() -> drain(key));
        } else {
            drain(key);
        }
    }

    void drain(InboundStore.ContactKey key) {
        ReentrantLock lock = locks.computeIfAbsent(key, k -> new ReentrantLock());
        lock.lock();
        try {
            CurrentClinic.run(key.clinic(), () -> {
                Optional<InboundStore.Pending> next;
                while ((next = store.nextPending(key.clinic(), key.contact(), settings.maxAttempts())).isPresent()) {
                    if (!processOne(key.clinic(), next.get())) {
                        return; // falhou: a varredura tenta de novo mais tarde
                    }
                }
            });
        } finally {
            lock.unlock();
        }
    }

    private boolean processOne(ClinicId clinic, InboundStore.Pending m) {
        store.countAttempt(clinic, m.id());
        ContactIdentity contact = new ContactIdentity(Channel.WHATSAPP, m.contact());
        try {
            UUID messageId;
            if (m.kind() == WebhookEvents.Kind.ECHO) {
                String text = m.text() == null || m.text().isBlank() ? "[Mensagem enviada pelo celular da clínica]"
                        : m.text().strip();
                messageId = businessApp.record(contact, text, clock.instant()).id().value();
            } else {
                String text = m.kind() == WebhookEvents.Kind.AUDIO ? transcribed(clinic, m).orElseGet(() -> textFor(m))
                        : textFor(m);
                var conversation = open.open(contact).conversation();
                messageId = handle.handleTurn(conversation.id(), text).customerMessageId().value();
            }
            store.processed(clinic, m.id(), messageId, clock.instant());
            return true;
        } catch (ConcurrentTurnException e) {
            // A mensagem do tutor já foi gravada; a equipe mexeu na conversa no meio do turno. Não repetir.
            store.processed(clinic, m.id(), null, clock.instant());
            log.info("Mensagem do WhatsApp gravada sem resposta: a conversa mudou durante o turno [inbound={}]", m.id());
            return true;
        } catch (DomainException e) {
            store.processed(clinic, m.id(), null, clock.instant());
            log.warn("Mensagem do WhatsApp descartada [inbound={}, motivo={}]", m.id(), e.getClass().getSimpleName());
            return true;
        } catch (RuntimeException e) {
            log.warn("Falha ao processar mensagem do WhatsApp; nova tentativa pela varredura [inbound={}, tentativa={}]",
                    m.id(), m.attempts() + 1, e);
            return false;
        }
    }

    /**
     * Áudio (S5, DA-53): baixa da Meta, transcreve e devolve "[Áudio transcrito] texto", que o assistente trata como
     * texto e a equipe lê na conversa (o original continua no anexo). Sem transcrição, vazio.
     */
    private Optional<String> transcribed(ClinicId clinic, InboundStore.Pending m) {
        AudioTranscriber service = transcriber.getIfAvailable();
        if (service == null || m.mediaId() == null) {
            return Optional.empty();
        }
        try {
            Optional<WhatsAppApi.Credentials> credentials = accounts.credentials();
            if (credentials.isEmpty()) {
                return Optional.empty();
            }
            WhatsAppApi.Media media = api.download(credentials.get(), m.mediaId());
            Optional<AudioTranscriber.Transcription> result = service.transcribe(media.bytes(),
                    media.mimeType() != null ? media.mimeType() : m.mediaMime());
            result.ifPresent(t -> store.audioSeconds(clinic, m.id(), t.seconds()));
            String caption = m.text() == null || m.text().isBlank() ? "" : "\n" + m.text().strip();
            return result.map(t -> cut("[Áudio transcrito] " + t.text() + caption));
        } catch (RuntimeException e) {
            log.warn("Áudio não transcrito [inbound={}, motivo={}]", m.id(), e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private String cut(String text) {
        return text.length() > settings.maxTextLength() ? text.substring(0, settings.maxTextLength()) : text;
    }

    /** O texto passado ao assistente. Nunca vazio: mídia sem legenda vira uma descrição curta. */
    private String textFor(InboundStore.Pending m) {
        String text = m.text() == null ? "" : m.text().strip();
        String result = switch (m.kind()) {
            case IMAGE -> text.isEmpty() ? "[Imagem enviada sem texto]" : "[Imagem anexada] " + text;
            case DOCUMENT -> text.isEmpty() ? "[Documento enviado sem texto]" : "[Documento anexado] " + text;
            case AUDIO -> text.isEmpty() ? "[Áudio enviado. O assistente ainda não ouve áudios]"
                    : "[Áudio anexado] " + text;
            case VIDEO -> text.isEmpty() ? "[Vídeo enviado sem texto]" : "[Vídeo anexado] " + text;
            case OTHER -> "[Mensagem de um tipo que o sistema ainda não lê]";
            default -> text.isEmpty() ? "[Mensagem vazia]" : text;
        };
        return result.length() > settings.maxTextLength() ? result.substring(0, settings.maxTextLength()) : result;
    }
}
