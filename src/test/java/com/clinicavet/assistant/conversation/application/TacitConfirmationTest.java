package com.clinicavet.assistant.conversation.application;

import com.clinicavet.assistant.tutor.domain.Pet;
import com.clinicavet.assistant.tutor.domain.Species;
import com.clinicavet.assistant.tutor.domain.TutorId;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** A1 (simulação de 1 mês): quando uma resposta à pergunta de confirmação vale como confirmação. Dados fictícios. */
class TacitConfirmationTest {

    private static final Instant T0 = Instant.parse("2026-11-01T12:00:00Z");
    private static final String QUESTION = "Só para confirmar: é para a Mel, uma gata, certo?";
    private final Pet mel = Pet.register(new TutorId(UUID.randomUUID()), "Mel", Species.CAT, null, T0);

    @ParameterizedTest
    @ValueSource(strings = {"Pode ser na terça de manhã", "Sou a Patrícia Gomes", "sim", "isso, de tarde se der",
            "A Mel tem 3 anos"})
    void answerWithoutCorrection_confirms(String answer) {
        assertThat(TacitConfirmation.applies(mel, QUESTION, answer)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Não", "não, é a Luna", "Está errado", "Na verdade é cachorra", "A Mel é cachorra",
            "me enganei, é outro pet", "Ela não é gata"})
    void answerWithCorrection_doesNotConfirm(String answer) {
        assertThat(TacitConfirmation.applies(mel, QUESTION, answer)).isFalse();
    }

    @Test
    void previousMessageMustBeAQuestionThatRepeatsNameAndSpecies() {
        assertThat(TacitConfirmation.applies(mel, "Só para confirmar: é para a Mel, certo?", "terça")).isFalse();
        assertThat(TacitConfirmation.applies(mel, "Só para confirmar: é para uma gata, certo?", "terça")).isFalse();
        assertThat(TacitConfirmation.applies(mel, "O pedido é para a Mel, uma gata.", "terça")).isFalse();
        assertThat(TacitConfirmation.applies(mel, null, "terça")).isFalse();
    }

    @Test
    void petWithoutSpecies_isNeverConfirmed() {
        Pet unknown = Pet.register(new TutorId(UUID.randomUUID()), "Mel", null, null, T0);
        assertThat(TacitConfirmation.applies(unknown, QUESTION, "terça")).isFalse();
    }
}
