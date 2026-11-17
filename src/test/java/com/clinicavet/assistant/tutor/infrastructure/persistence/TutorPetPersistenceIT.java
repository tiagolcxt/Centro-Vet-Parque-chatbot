package com.clinicavet.assistant.tutor.infrastructure.persistence;

import com.clinicavet.assistant.shared.domain.ContactIdentity;
import com.clinicavet.assistant.support.IntegrationTest;
import com.clinicavet.assistant.tutor.domain.Pet;
import com.clinicavet.assistant.tutor.domain.PetRepository;
import com.clinicavet.assistant.tutor.domain.Species;
import com.clinicavet.assistant.tutor.domain.Tutor;
import com.clinicavet.assistant.tutor.domain.TutorRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import static com.clinicavet.assistant.support.Fixtures.T0;
import static com.clinicavet.assistant.support.Fixtures.newContact;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@IntegrationTest
class TutorPetPersistenceIT {

    @Autowired
    TutorRepository tutors;

    @Autowired
    PetRepository pets;

    @Test
    void tutor_roundTrip_andLookupByContact() {
        ContactIdentity contact = newContact();
        Tutor tutor = Tutor.register(contact, T0);
        tutor.updateName("Tutora Fictícia");
        tutors.save(tutor);

        Tutor loaded = tutors.findByContact(contact).orElseThrow();

        assertThat(loaded.id()).isEqualTo(tutor.id());
        assertThat(loaded.name()).contains("Tutora Fictícia");
        assertThat(loaded.phone()).isEmpty();
        assertThat(loaded.version()).isZero();
    }

    @Test
    void tutor_update_incrementsVersion() {
        Tutor saved = tutors.save(Tutor.register(newContact(), T0));
        saved.markSeen(T0.plusSeconds(60));

        Tutor updated = tutors.save(saved);

        assertThat(updated.version()).isEqualTo(1L);
        assertThat(updated.lastSeenAt()).isEqualTo(saved.lastSeenAt());
    }

    @Test
    void contactIsUnique() {
        ContactIdentity contact = newContact();
        tutors.save(Tutor.register(contact, T0));

        assertThatThrownBy(() -> tutors.save(Tutor.register(contact, T0)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void pets_areListedOnlyForTheirTutor_inRegistrationOrder() {
        Tutor tutor = tutors.save(Tutor.register(newContact(), T0));
        Tutor otherTutor = tutors.save(Tutor.register(newContact(), T0));
        Pet mel = Pet.register(tutor.id(), "Mel", Species.DOG, null, T0);
        mel.updateBreed("SRD");
        mel.updateAgeDescription("uns 3 anos");
        pets.save(mel);
        pets.save(Pet.register(tutor.id(), "Pipoca", Species.OTHER, "coelho", T0.plusSeconds(1)));
        pets.save(Pet.register(otherTutor.id(), "Mel", Species.CAT, null, T0));

        var tutorPets = pets.findByTutor(tutor.id());

        assertThat(tutorPets).extracting(Pet::name).containsExactly("Mel", "Pipoca");
        assertThat(tutorPets.get(0).species()).contains(Species.DOG);
        assertThat(tutorPets.get(0).breed()).contains("SRD");
        assertThat(tutorPets.get(0).ageDescription()).contains("uns 3 anos");
        assertThat(tutorPets.get(1).speciesDescription()).contains("coelho");
    }
}
