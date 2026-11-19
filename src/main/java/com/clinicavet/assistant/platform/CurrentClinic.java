package com.clinicavet.assistant.platform;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Clínica da execução atual (ADR-0021). Definida por quem recebe a chamada, nunca pelo conteúdo dela:
 * <ul>
 *   <li>web: pela credencial (chave de serviço hoje; usuário logado depois, ADR-0024);</li>
 *   <li>webhook do WhatsApp: pelo número de destino (Etapa S4);</li>
 *   <li>tarefas agendadas: explicitamente, com {@link #call}.</li>
 * </ul>
 * O Hibernate lê este valor para filtrar e preencher {@code clinic_id} ({@code @TenantId}). Sem clínica definida,
 * ele recebe {@link #NONE}: consultas não encontram nada e gravações falham pela FK. Nada vaza, e o erro aparece.
 * <p>
 * Guardado por thread: o turno do assistente roda inteiro na thread da requisição.
 */
public final class CurrentClinic {

    /** Valor entregue ao Hibernate quando nenhuma clínica foi definida. Não existe em {@code clinic}. */
    public static final UUID NONE = new UUID(0L, 0L);

    private static final ThreadLocal<ClinicId> CURRENT = new ThreadLocal<>();

    private CurrentClinic() {
    }

    public static Optional<ClinicId> get() {
        return Optional.ofNullable(CURRENT.get());
    }

    /** A clínica atual; falha se não houver (erro de programação, não valor padrão). */
    public static ClinicId require() {
        ClinicId clinic = CURRENT.get();
        if (clinic == null) {
            throw new IllegalStateException("Nenhuma clínica definida nesta execução (ADR-0021).");
        }
        return clinic;
    }

    /** Executa com a clínica informada e restaura a anterior no fim. */
    public static <T> T call(ClinicId clinic, Supplier<T> work) {
        try (Scope ignored = open(clinic)) {
            return work.get();
        }
    }

    public static void run(ClinicId clinic, Runnable work) {
        call(clinic, () -> {
            work.run();
            return null;
        });
    }

    /** Define a clínica até o {@link Scope#close()}, que restaura a anterior. Para filtros e testes. */
    public static Scope open(ClinicId clinic) {
        Objects.requireNonNull(clinic, "clinic");
        ClinicId previous = CURRENT.get();
        CURRENT.set(clinic);
        return () -> {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        };
    }

    @FunctionalInterface
    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }
}
