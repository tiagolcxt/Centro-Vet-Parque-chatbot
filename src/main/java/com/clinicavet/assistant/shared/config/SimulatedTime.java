package com.clinicavet.assistant.shared.config;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Horário simulado do simulador de configuração (S3-7, ADR-0026 item 7): "como o assistente responderia sábado às
 * 20h?". Vale só na thread que chama {@link #call}; o resto da aplicação continua no relógio real.
 * <p>
 * O relógio da aplicação ({@link ClockConfig}) consulta este valor. Fora do simulador ele é sempre vazio.
 */
public final class SimulatedTime {

    private static final ThreadLocal<Instant> FIXED = new ThreadLocal<>();

    private SimulatedTime() {
    }

    public static <T> T call(Instant at, Supplier<T> work) {
        Objects.requireNonNull(at, "at");
        Instant previous = FIXED.get();
        FIXED.set(at);
        try {
            return work.get();
        } finally {
            if (previous == null) {
                FIXED.remove();
            } else {
                FIXED.set(previous);
            }
        }
    }

    /** O instante simulado nesta thread, se houver (para relógios de teste que substituem o da aplicação). */
    public static java.util.Optional<Instant> current() {
        return java.util.Optional.ofNullable(FIXED.get());
    }

    /** Relógio que devolve o instante simulado, se houver, e senão o do {@code real}. */
    static Clock aware(Clock real) {
        return new Aware(real);
    }

    private static final class Aware extends Clock {

        private final Clock real;

        private Aware(Clock real) {
            this.real = real;
        }

        @Override
        public ZoneId getZone() {
            return real.getZone();
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new Aware(real.withZone(zone));
        }

        @Override
        public Instant instant() {
            Instant fixed = FIXED.get();
            return fixed != null ? fixed : real.instant();
        }

        @Override
        public long millis() {
            return instant().toEpochMilli();
        }
    }
}
