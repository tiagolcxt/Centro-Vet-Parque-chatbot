package com.clinicavet.assistant.channel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(WhatsAppSettings.class)
class ChannelConfig {

    @Bean
    TokenCipher tokenCipher(WhatsAppSettings settings) {
        return new TokenCipher(settings);
    }

    @Bean
    WhatsAppApi whatsAppApi(RestClient.Builder builder, WhatsAppSettings settings) {
        return new CloudWhatsAppApi(builder, settings);
    }
}

/**
 * Tarefas agendadas do canal: o despachante de respostas e a varredura de mensagens recebidas paradas. Desligadas
 * com {@code assistant.whatsapp.scheduling=false} (os testes chamam direto, com o relógio de teste).
 */
@Component
class ChannelJobs {

    private static final Logger log = LoggerFactory.getLogger(ChannelJobs.class);

    /** Mensagem recebida há mais que isso e ainda não processada: o processamento caiu ou falhou. */
    static final Duration STALLED_AFTER = Duration.ofMinutes(1);

    private final Outbox outbox;
    private final InboundStore store;
    private final InboundProcessor processor;
    private final WhatsAppSettings settings;
    private final Clock clock;

    ChannelJobs(Outbox outbox, InboundStore store, InboundProcessor processor, WhatsAppSettings settings, Clock clock) {
        this.outbox = outbox;
        this.store = store;
        this.processor = processor;
        this.settings = settings;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${assistant.whatsapp.dispatch-interval:2s}", initialDelayString = "10s")
    void dispatch() {
        if (!settings.scheduling()) {
            return;
        }
        try {
            outbox.dispatchDue();
        } catch (RuntimeException e) {
            log.warn("Despachante do WhatsApp falhou; tenta de novo no próximo ciclo", e);
        }
    }

    @Scheduled(fixedDelayString = "${assistant.whatsapp.sweep-interval:1m}", initialDelayString = "30s")
    void sweep() {
        if (!settings.scheduling()) {
            return;
        }
        sweepNow();
    }

    /** Retoma os contatos com mensagem parada. */
    void sweepNow() {
        try {
            store.stalled(clock.instant().minus(STALLED_AFTER), settings.maxAttempts()).forEach(processor::schedule);
        } catch (RuntimeException e) {
            log.warn("Varredura de mensagens do WhatsApp falhou", e);
        }
    }
}
