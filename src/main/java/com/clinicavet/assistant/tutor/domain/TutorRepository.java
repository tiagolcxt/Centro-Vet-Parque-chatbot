package com.clinicavet.assistant.tutor.domain;

import com.clinicavet.assistant.shared.domain.ContactIdentity;

import java.util.List;
import java.util.Optional;

public interface TutorRepository {

    Tutor save(Tutor tutor);

    Optional<Tutor> findById(TutorId id);

    Optional<Tutor> findByContact(ContactIdentity contact);

    /** Busca da equipe por nome, telefone, contato ou nome de pet (RF-S42). Mínimo de 2 caracteres. */
    List<Tutor> search(String text, int limit);
}
