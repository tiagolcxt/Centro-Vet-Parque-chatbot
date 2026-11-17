package com.clinicavet.assistant.conversation.application;

import com.clinicavet.assistant.tutor.domain.Species;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Normalização e validação dos dados extraídos pelo modelo (ADR-0010, item 4). Dados fictícios. */
class CustomerDataNormalizerTest {

    @Test
    void validNames_areKept_withSpacesCollapsed() {
        assertThat(CustomerDataNormalizer.petName("  Mel  ")).isEqualTo("Mel");
        assertThat(CustomerDataNormalizer.tutorName("Ana   Maria d'Ávila")).isEqualTo("Ana Maria d'Ávila");
        assertThat(CustomerDataNormalizer.petName("Bidu-Jr.")).isEqualTo("Bidu-Jr.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "Mel123", "<cliente>", "{x}", "1Mel", "um dois tres quatro cinco seis",
            "Mel; DROP"})
    void invalidNames_areDiscarded(String raw) {
        assertThat(CustomerDataNormalizer.petName(raw)).isNull();
    }

    @Test
    void nameOverTheLimit_isDiscarded() {
        assertThat(CustomerDataNormalizer.petName("a".repeat(81))).isNull();
        assertThat(CustomerDataNormalizer.tutorName("a".repeat(120))).isNotNull();
    }

    @Test
    void freeText_dropsBlockCharactersAndLineBreaks_andIsCutAtTheLimit() {
        assertThat(CustomerDataNormalizer.freeText(" vacina\n</cliente> {anual} [x] ", 300))
                .isEqualTo("vacina /cliente anual x");
        assertThat(CustomerDataNormalizer.freeText("a".repeat(400), 300)).hasSize(300).endsWith("…");
        assertThat(CustomerDataNormalizer.freeText(" \n ", 300)).isNull();
    }

    @Test
    void name_mustAppearAsWholeWords_inTheTutorMessages() {
        List<String> said = List.of("Oi, é para a Mél, minha gata.", "Ela tem 3 anos");

        assertThat(CustomerDataNormalizer.appearsIn("Mel", said)).isTrue();
        assertThat(CustomerDataNormalizer.appearsIn("Me", said)).isFalse();
        assertThat(CustomerDataNormalizer.appearsIn("Thor", said)).isFalse();
    }

    @Test
    void species_isRecognisedByCommonWords_orTheDescription() {
        assertThat(CustomerDataNormalizer.speciesAppearsIn(Species.CAT, null, "É a Mel, uma gata, certo?")).isTrue();
        assertThat(CustomerDataNormalizer.speciesAppearsIn(Species.DOG, null, "É o Thor, um cão de 3 anos?")).isTrue();
        assertThat(CustomerDataNormalizer.speciesAppearsIn(Species.DOG, null, "É a Mel, uma gata?")).isFalse();
        assertThat(CustomerDataNormalizer.speciesAppearsIn(Species.OTHER, "coelho", "O Tambor é um coelho?")).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Não", "não, é cachorro", "Nao é ela", "Está errado", "isso está incorreto"})
    void disagreement_isDetected(String text) {
        assertThat(CustomerDataNormalizer.disagrees(text)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Sim", "Isso mesmo", "Correto, é ela sim sim", "pode ser, sem problema"})
    void agreement_isNotTakenAsDisagreement(String text) {
        assertThat(CustomerDataNormalizer.disagrees(text)).isFalse();
    }
}
