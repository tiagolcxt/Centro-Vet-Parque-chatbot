package com.clinicavet.assistant.conversation.application.validation;

import java.util.Objects;

/**
 * Um valor factual encontrado num texto, já normalizado (ADR-0008, seção 3; DA-29). Dois fatos com o
 * mesmo tipo e valor representam a mesma informação, ainda que escritos de jeitos diferentes
 * ({@code R$ 120} e {@code 120,00 reais} → {@code MONEY 12000}).
 */
public record Fact(Type type, String value) {

    public enum Type {
        /** Centavos. */
        MONEY,
        /** HH:MM. */
        TIME,
        /** "24 horas": afirma atendimento 24 h. */
        ALL_DAY,
        /** segunda, terca, quarta, quinta, sexta, sabado, domingo. */
        WEEKDAY,
        WEEKEND,
        HOLIDAY,
        /** dd/MM. */
        DATE,
        /** Só dígitos. */
        PHONE,
        EMAIL,
        /** Sem protocolo, sem www e sem barra final. */
        URL,
        HANDLE,
        /** Só dígitos. */
        ZIP,
        PERCENT,
        INSTALLMENTS,
        /** "N unidade": 48 hora, 2 dia util, 30 minuto. */
        DURATION,
        /** Dose de medicamento (V7). */
        DOSE
    }

    public Fact {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(value, "value");
    }

    /** Mesma informação? Telefone vale com ou sem DDD/+55 (sufixo de pelo menos 8 dígitos). */
    public boolean sameAs(Fact other) {
        if (type != other.type) {
            return false;
        }
        if (type == Type.PHONE) {
            String a = value;
            String b = other.value;
            return a.equals(b) || (Math.min(a.length(), b.length()) >= 8 && (a.endsWith(b) || b.endsWith(a)));
        }
        return value.equals(other.value);
    }
}
