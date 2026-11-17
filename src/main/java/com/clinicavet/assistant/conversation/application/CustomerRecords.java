package com.clinicavet.assistant.conversation.application;

import com.clinicavet.assistant.conversation.domain.ConversationId;
import com.clinicavet.assistant.tutor.domain.DataField;
import com.clinicavet.assistant.tutor.domain.Pet;
import com.clinicavet.assistant.tutor.domain.PetRepository;
import com.clinicavet.assistant.tutor.domain.Provenance;
import com.clinicavet.assistant.tutor.domain.ProvenanceRepository;
import com.clinicavet.assistant.tutor.domain.ProvenanceStatus;
import com.clinicavet.assistant.tutor.domain.Tutor;
import com.clinicavet.assistant.tutor.domain.TutorId;
import com.clinicavet.assistant.tutor.domain.TutorRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Cadastro de <b>um</b> tutor (o vinculado à conversa) com os pets e a proveniência de cada campo. As consultas
 * são sempre pelo {@link TutorId} da conversa, nunca por texto (ADR-0010, item 5).
 */
final class CustomerRecords {

    private record Key(UUID subjectId, DataField field) {
    }

    private Tutor tutor;
    private final List<Pet> pets;
    private final Map<Key, Provenance> provenance = new HashMap<>();

    private CustomerRecords(Tutor tutor, List<Pet> pets, List<Provenance> provenance) {
        this.tutor = tutor;
        this.pets = new ArrayList<>(pets);
        provenance.forEach(this::put);
    }

    static CustomerRecords empty() {
        return new CustomerRecords(null, List.of(), List.of());
    }

    static CustomerRecords load(TutorId tutorId, TutorRepository tutors, PetRepository pets,
                                ProvenanceRepository provenance) {
        Optional<Tutor> tutor = tutors.findById(tutorId);
        if (tutor.isEmpty()) {
            return empty();
        }
        List<Pet> tutorPets = pets.findByTutor(tutorId);
        List<UUID> subjects = new ArrayList<>();
        subjects.add(tutorId.value());
        tutorPets.forEach(p -> subjects.add(p.id().value()));
        return new CustomerRecords(tutor.get(), tutorPets, provenance.findBySubjects(subjects));
    }

    Optional<Tutor> tutor() {
        return Optional.ofNullable(tutor);
    }

    void setTutor(Tutor tutor) {
        this.tutor = tutor;
    }

    List<Pet> pets() {
        return List.copyOf(pets);
    }

    void putPet(Pet pet) {
        pets.removeIf(p -> p.id().equals(pet.id()));
        pets.add(pet);
    }

    Optional<Pet> pet(com.clinicavet.assistant.tutor.domain.PetId id) {
        return pets.stream().filter(p -> p.id().equals(id)).findFirst();
    }

    Optional<Provenance> provenance(UUID subjectId, DataField field) {
        return Optional.ofNullable(provenance.get(new Key(subjectId, field)));
    }

    void put(Provenance value) {
        provenance.put(new Key(value.subjectId(), value.field()), value);
    }

    /** Status do campo nesta conversa; vazio se não há registro de proveniência. */
    Optional<ProvenanceStatus> status(UUID subjectId, DataField field, ConversationId conversation, Instant now,
                                      Duration validity) {
        return provenance(subjectId, field).map(p -> p.statusFor(conversation, now, validity));
    }
}
