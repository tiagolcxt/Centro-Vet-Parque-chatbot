package com.clinicavet.assistant.tutor.domain;

import com.clinicavet.assistant.shared.domain.Channel;
import com.clinicavet.assistant.shared.domain.ContactIdentity;
import com.clinicavet.assistant.shared.exception.DomainValidationException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TutorAndPetTest {

    private static final Instant T0 = Instant.parse("2026-11-11T13:00:00Z");
    private static final ContactIdentity CONTACT = new ContactIdentity(Channel.API, "contato-ficticio-2");

    @Test
    void tutor_registersWithOnlyContact_andOptionalData() {
        Tutor tutor = Tutor.register(CONTACT, T0);

        assertThat(tutor.name()).isEmpty();
        tutor.updateName("  Maria Fictícia ");
        tutor.updatePhone("");

        assertThat(tutor.name()).contains("Maria Fictícia");
        assertThat(tutor.phone()).isEmpty();
    }

    @Test
    void tutor_markSeen_onlyMovesForward() {
        Tutor tutor = Tutor.register(CONTACT, T0);

        tutor.markSeen(T0.minusSeconds(10));
        assertThat(tutor.lastSeenAt()).isEqualTo(T0);
        tutor.markSeen(T0.plusSeconds(10));
        assertThat(tutor.lastSeenAt()).isEqualTo(T0.plusSeconds(10));
    }

    @Test
    void tutor_nameAboveLimit_isRejected() {
        Tutor tutor = Tutor.register(CONTACT, T0);

        assertThatThrownBy(() -> tutor.updateName("x".repeat(Tutor.MAX_NAME_LENGTH + 1)))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    void pet_requiresName() {
        assertThatThrownBy(() -> Pet.register(TutorId.newId(), " ", Species.DOG, null, T0))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    void pet_otherSpecies_requiresDescription() {
        assertThatThrownBy(() -> Pet.register(TutorId.newId(), "Pipoca", Species.OTHER, null, T0))
                .isInstanceOf(DomainValidationException.class);

        Pet pet = Pet.register(TutorId.newId(), "Pipoca", Species.OTHER, "coelho", T0);
        assertThat(pet.speciesDescription()).contains("coelho");
    }

    @Test
    void pet_knownSpecies_rejectsDescription() {
        assertThatThrownBy(() -> Pet.register(TutorId.newId(), "Mel", Species.DOG, "cachorro", T0))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    void pet_optionalFieldsAreNormalized() {
        Pet pet = Pet.register(TutorId.newId(), "Mel", Species.DOG, null, T0);

        pet.updateBreed("  SRD ");
        pet.updateAgeDescription("  ");

        assertThat(pet.breed()).contains("SRD");
        assertThat(pet.ageDescription()).isEmpty();
    }

    @Test
    void contactIdentity_isValidated() {
        assertThatThrownBy(() -> new ContactIdentity(Channel.API, " "))
                .isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> new ContactIdentity(Channel.API, "1".repeat(ContactIdentity.MAX_EXTERNAL_ID_LENGTH + 1)))
                .isInstanceOf(DomainValidationException.class);
        assertThat(new ContactIdentity(Channel.API, " 5511900000000 ").externalContactId()).isEqualTo("5511900000000");
    }
}
