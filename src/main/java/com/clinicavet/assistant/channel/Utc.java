package com.clinicavet.assistant.channel;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Instantes para o JDBC em UTC (DM-06). {@code java.sql.Timestamp} seria convertido pelo fuso da JVM e ficaria
 * deslocado em relação ao que o Hibernate grava ({@code jdbc.time_zone: UTC}).
 */
final class Utc {

    private Utc() {
    }

    static LocalDateTime of(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
