package com.clinicavet.assistant.shared.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Erros para pessoas (revisão de linguagem, C3): sem estado interno, campo técnico nem id na mensagem. */
class UserTextTest {

    enum State { ASSIGNED, CLOSED }

    @Test
    void invalidTransition_explainsInPortuguese_andKeepsTheHint() {
        var e = new InvalidStateTransitionException("Conversa", State.ASSIGNED, "assumir (já assumida por Ana)");
        assertThat(e.getMessage())
                .isEqualTo("Não é possível fazer isso agora: a conversa está em atendimento. Já assumida por Ana.")
                .doesNotContain("ASSIGNED");
        assertThat(e.from()).isEqualTo("ASSIGNED"); // o código continua disponível para o suporte
    }

    @Test
    void validation_usesTheFieldNameOnScreen() {
        assertThat(new DomainValidationException("departmentId", "setor inexistente ou inativo").getMessage())
                .isEqualTo("Setor: setor inexistente ou inativo");
        assertThat(new DomainValidationException("campoDesconhecido", "valor fora do limite").getMessage())
                .isEqualTo("Valor fora do limite");
    }

    @Test
    void noInternalCodeLeaks() {
        var e = new InvalidStateTransitionException("Conta", State.CLOSED, "novo convite");
        assertThat(e.getMessage()).doesNotContainPattern("[A-Z]{2,}_?[A-Z]*").contains("encerrada");
    }
}
