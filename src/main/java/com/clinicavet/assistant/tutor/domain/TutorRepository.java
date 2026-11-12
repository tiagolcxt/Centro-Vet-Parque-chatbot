package com.clinicavet.assistant.tutor.domain;

import com.clinicavet.assistant.shared.domain.ContactIdentity;

import java.util.Optional;

public interface TutorRepository {

    Tutor save(Tutor tutor);

    Optional<Tutor> findById(TutorId id);

    Optional<Tutor> findByContact(ContactIdentity contact);
}
