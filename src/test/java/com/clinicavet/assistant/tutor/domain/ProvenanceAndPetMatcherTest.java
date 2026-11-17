package com.clinicavet.assistant.tutor.domain;

import com.clinicavet.assistant.conversation.domain.ConversationId;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Proveniência por campo e correspondência de pets (ADR-0010, DA-35). Dados fictícios. */
class ProvenanceAndPetMatcherTest {

    private static final Instant T0 = Instant.parse("2026-11-17T13:00:00Z");
    private static final Duration VALIDITY = Duration.ofDays(365);

    @Nested
    class ProvenanceTransitions {

        private final ConversationId first = ConversationId.newId();
        private final ConversationId second = ConversationId.newId();

        private Provenance mentioned() {
            return Provenance.mentioned(DataField.PET_NAME, UUID.randomUUID(), first, T0);
        }

        @Test
        void newValue_isMentionedInThisConversation_andToReconfirmInAnother() {
            Provenance p = mentioned();

            assertThat(p.statusFor(first, T0, VALIDITY)).isEqualTo(ProvenanceStatus.MENTIONED);
            assertThat(p.statusFor(second, T0, VALIDITY)).isEqualTo(ProvenanceStatus.TO_RECONFIRM);
            assertThat(p.confirmedAt()).isEmpty();
        }

        @Test
        void confirmed_isConfirmedHere_andRegisteredElsewhereWithinValidity() {
            Provenance p = mentioned();
            p.confirm(first, T0.plusSeconds(10));

            assertThat(p.statusFor(first, T0, VALIDITY)).isEqualTo(ProvenanceStatus.CONFIRMED);
            assertThat(p.statusFor(second, T0.plus(Duration.ofDays(30)), VALIDITY)).isEqualTo(ProvenanceStatus.REGISTERED);
        }

        @Test
        void registered_becomesToReconfirm_afterTheValidity() {
            Provenance p = mentioned();
            p.confirm(first, T0);

            assertThat(p.statusFor(second, T0.plus(VALIDITY), VALIDITY)).isEqualTo(ProvenanceStatus.REGISTERED);
            assertThat(p.statusFor(second, T0.plus(VALIDITY).plusSeconds(1), VALIDITY))
                    .isEqualTo(ProvenanceStatus.TO_RECONFIRM);
        }

        @Test
        void registered_neverBecomesConfirmed_withoutConfirmationInTheCurrentConversation() {
            Provenance p = mentioned();
            p.confirm(first, T0);

            p.mention(second, false, T0.plusSeconds(60));   // citado de novo na conversa nova, mesmo valor

            assertThat(p.statusFor(second, T0.plusSeconds(60), VALIDITY)).isEqualTo(ProvenanceStatus.MENTIONED);
            // A confirmação antiga continua servindo para conversas futuras como CADASTRADA, nunca como CONFIRMADA.
            assertThat(p.statusFor(ConversationId.newId(), T0.plusSeconds(60), VALIDITY))
                    .isEqualTo(ProvenanceStatus.REGISTERED);

            p.confirm(second, T0.plusSeconds(120));
            assertThat(p.statusFor(second, T0.plusSeconds(120), VALIDITY)).isEqualTo(ProvenanceStatus.CONFIRMED);
        }

        @Test
        void changedValue_dropsThePreviousConfirmation() {
            Provenance p = mentioned();
            p.confirm(first, T0);

            p.mention(second, true, T0.plusSeconds(60));

            assertThat(p.confirmedAt()).isEmpty();
            assertThat(p.statusFor(second, T0, VALIDITY)).isEqualTo(ProvenanceStatus.MENTIONED);
            assertThat(p.statusFor(ConversationId.newId(), T0, VALIDITY)).isEqualTo(ProvenanceStatus.TO_RECONFIRM);
        }

        @Test
        void sameValueAgain_inTheSameConversation_keepsTheConfirmation() {
            Provenance p = mentioned();
            p.confirm(first, T0);

            p.mention(first, false, T0.plusSeconds(60));

            assertThat(p.statusFor(first, T0, VALIDITY)).isEqualTo(ProvenanceStatus.CONFIRMED);
        }

        @Test
        void onlyMentionedOrConfirmed_canBeStored_andFieldMustBelongToTheSubject() {
            UUID id = UUID.randomUUID();
            assertThatThrownBy(() -> Provenance.restore(UUID.randomUUID(), DataSubject.PET, id, DataField.PET_NAME,
                    ProvenanceStatus.REGISTERED, first, T0, T0, 0L)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> Provenance.restore(UUID.randomUUID(), DataSubject.TUTOR, id, DataField.PET_NAME,
                    ProvenanceStatus.MENTIONED, first, null, T0, 0L)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> Provenance.restore(UUID.randomUUID(), DataSubject.PET, id, DataField.PET_NAME,
                    ProvenanceStatus.CONFIRMED, first, null, T0, 0L)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class Matching {

        private final TutorId tutor = TutorId.newId();
        private final Pet mel = Pet.register(tutor, "Mel", Species.CAT, null, T0);
        private final Pet thor = Pet.register(tutor, "Thor", Species.DOG, null, T0);

        @Test
        void nameIsCompared_ignoringCaseAccentsAndSpaces_butNeverApproximately() {
            assertThat(PetMatcher.match(List.of(mel, thor), "  mél ", null).pet()).contains(mel);
            assertThat(PetMatcher.match(List.of(mel, thor), "Melzinha", null).kind()).isEqualTo(PetMatcher.Result.Kind.NONE);
            assertThat(PetMatcher.match(List.of(mel, thor), "Tor", null).kind()).isEqualTo(PetMatcher.Result.Kind.NONE);
        }

        @Test
        void differentSpecies_isAConflict_notTheSamePet() {
            assertThat(PetMatcher.match(List.of(mel), "Mel", Species.DOG).kind())
                    .isEqualTo(PetMatcher.Result.Kind.SPECIES_CONFLICT);
            assertThat(PetMatcher.match(List.of(mel), "Mel", Species.CAT).pet()).contains(mel);
        }

        @Test
        void homonyms_areAmbiguous_unlessTheSpeciesTellsThemApart() {
            Pet otherMel = Pet.register(tutor, "Mel", Species.DOG, null, T0);

            assertThat(PetMatcher.match(List.of(mel, otherMel), "Mel", null).kind())
                    .isEqualTo(PetMatcher.Result.Kind.AMBIGUOUS);
            assertThat(PetMatcher.match(List.of(mel, otherMel), "Mel", Species.DOG).pet()).contains(otherMel);
            assertThat(PetMatcher.match(List.of(mel, otherMel), "Mel", Species.OTHER).kind())
                    .isEqualTo(PetMatcher.Result.Kind.AMBIGUOUS);
        }

        @Test
        void petWithoutSpecies_acceptsTheSpeciesToldNow() {
            Pet unknown = Pet.register(tutor, "Bidu", null, null, T0);

            assertThat(PetMatcher.match(List.of(unknown), "bidu", Species.DOG).pet()).contains(unknown);
        }

        @Test
        void onlyTheGivenListIsSearched() {
            assertThat(PetMatcher.match(List.of(), "Mel", null).kind()).isEqualTo(PetMatcher.Result.Kind.NONE);
        }
    }
}
