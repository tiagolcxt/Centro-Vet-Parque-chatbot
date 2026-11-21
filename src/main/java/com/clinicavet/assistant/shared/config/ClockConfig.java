package com.clinicavet.assistant.shared.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Relógio único da aplicação, no fuso da clínica (DM-06). Nenhum código chama
 * {@code Instant.now()} diretamente: todos recebem este {@link Clock}, o que torna o tempo testável.
 */
@Configuration
public class ClockConfig {

    @Bean
    Clock clock(@Value("${assistant.time-zone}") String timeZone) {
        // O simulador de configuração pode fixar o horário só na própria thread (SimulatedTime).
        return SimulatedTime.aware(Clock.system(ZoneId.of(timeZone)));
    }
}
