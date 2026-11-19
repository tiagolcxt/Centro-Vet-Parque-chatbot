package com.clinicavet.assistant.tutor.infrastructure.persistence;

import com.clinicavet.assistant.shared.domain.ContactIdentity;
import com.clinicavet.assistant.tutor.domain.Pet;
import com.clinicavet.assistant.tutor.domain.PetId;
import com.clinicavet.assistant.tutor.domain.PetRepository;
import com.clinicavet.assistant.tutor.domain.Tutor;
import com.clinicavet.assistant.tutor.domain.TutorId;
import com.clinicavet.assistant.tutor.domain.TutorRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
class JpaTutorRepository implements TutorRepository {

    private final TutorJpaRepository jpa;

    JpaTutorRepository(TutorJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Tutor save(Tutor tutor) {
        return jpa.saveAndFlush(TutorEntity.from(tutor)).toDomain();
    }

    @Override
    public Optional<Tutor> findById(TutorId id) {
        return jpa.findById(id.value()).map(TutorEntity::toDomain);
    }

    @Override
    public List<Tutor> search(String text, int limit) {
        String clean = text == null ? "" : text.strip().toLowerCase(java.util.Locale.ROOT).replaceAll("[%_\\\\]", "");
        if (clean.length() < 2) {
            return List.of();
        }
        return jpa.search("%" + clean + "%", org.springframework.data.domain.Limit.of(limit)).stream()
                .map(TutorEntity::toDomain).toList();
    }

    @Override
    public Optional<Tutor> findByContact(ContactIdentity contact) {
        return jpa.findByChannelAndExternalContactId(contact.channel(), contact.externalContactId())
                .map(TutorEntity::toDomain);
    }
}

@Repository
class JpaPetRepository implements PetRepository {

    private final PetJpaRepository jpa;

    JpaPetRepository(PetJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Pet save(Pet pet) {
        return jpa.saveAndFlush(PetEntity.from(pet)).toDomain();
    }

    @Override
    public Optional<Pet> findById(PetId id) {
        return jpa.findById(id.value()).map(PetEntity::toDomain);
    }

    @Override
    public List<Pet> findByTutor(TutorId tutorId) {
        return jpa.findByTutorIdOrderByCreatedAtAsc(tutorId.value()).stream().map(PetEntity::toDomain).toList();
    }
}
