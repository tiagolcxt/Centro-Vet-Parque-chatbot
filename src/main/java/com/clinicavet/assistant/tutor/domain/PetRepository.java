package com.clinicavet.assistant.tutor.domain;

import java.util.List;
import java.util.Optional;

public interface PetRepository {

    Pet save(Pet pet);

    Optional<Pet> findById(PetId id);

    /** Pets do tutor, em ordem de cadastro. Nunca retorna pets de outro tutor. */
    List<Pet> findByTutor(TutorId tutorId);
}
