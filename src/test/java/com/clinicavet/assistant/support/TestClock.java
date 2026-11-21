package com.clinicavet.assistant.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Relógio controlável dos testes de integração, no fuso da clínica. Cada leitura avança 1 ms, para
 * que mensagens gravadas em sequência nunca tenham o mesmo instante (a ordem do histórico depende
 * disso). {@link #advance} simula a passagem do tempo (expiração).
 */
public final class TestClock extends Clock {

    public static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final Duration TICK = Duration.ofMillis(1);

    private final AtomicReference<Instant> now;

    public TestClock(Instant start) {
        this.now = new AtomicReference<>(start);
    }

    public void advance(Duration duration) {
        now.updateAndGet(current -> current.plus(duration));
    }

    @Override
    public Instant instant() {
        // O simulador de configuração fixa o horário só na própria thread (SimulatedTime), como o relógio real.
        var simulated = com.clinicavet.assistant.shared.config.SimulatedTime.current();
        if (simulated.isPresent()) {
            return simulated.get();
        }
        return now.getAndUpdate(current -> current.plus(TICK));
    }

    @Override
    public ZoneId getZone() {
        return ZONE;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        throw new UnsupportedOperationException("o relógio de teste usa sempre o fuso da clínica");
    }
}
