package com.clinicavet.assistant.tutor.domain;

import com.clinicavet.assistant.shared.domain.Texts;
import com.clinicavet.assistant.shared.domain.Timestamps;
import com.clinicavet.assistant.shared.exception.DomainValidationException;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Animal de um tutor. Agregado próprio, vinculado ao tutor por {@link TutorId}.
 * Idade é guardada como texto, do jeito que o tutor informou ("uns 3 anos").
 */
public final class Pet {

    public static final int MAX_TEXT_LENGTH = 80;

    private final PetId id;
    private final TutorId tutorId;
    private String name;
    private Species species;
    private String speciesDescription;
    private String breed;
    private String ageDescription;
    private final Instant createdAt;
    private final Long version;

    private Pet(PetId id, TutorId tutorId, String name, Species species, String speciesDescription,
                String breed, String ageDescription, Instant createdAt, Long version) {
        this.id = Objects.requireNonNull(id, "id");
        this.tutorId = Objects.requireNonNull(tutorId, "tutorId");
        this.name = Texts.required("name", name, MAX_TEXT_LENGTH);
        applySpecies(species, speciesDescription);
        this.breed = Texts.optional("breed", breed, MAX_TEXT_LENGTH);
        this.ageDescription = Texts.optional("ageDescription", ageDescription, MAX_TEXT_LENGTH);
        this.createdAt = Timestamps.normalize(createdAt);
        this.version = version;
    }

    public static Pet register(TutorId tutorId, String name, Species species,
                               String speciesDescription, Instant now) {
        return new Pet(PetId.newId(), tutorId, name, species, speciesDescription, null, null, now, null);
    }

    public static Pet restore(PetId id, TutorId tutorId, String name, Species species,
                              String speciesDescription, String breed, String ageDescription,
                              Instant createdAt, Long version) {
        return new Pet(id, tutorId, name, species, speciesDescription, breed, ageDescription,
                createdAt, version);
    }

    public void rename(String name) {
        this.name = Texts.required("name", name, MAX_TEXT_LENGTH);
    }

    public void changeSpecies(Species species, String speciesDescription) {
        applySpecies(species, speciesDescription);
    }

    public void updateBreed(String breed) {
        this.breed = Texts.optional("breed", breed, MAX_TEXT_LENGTH);
    }

    public void updateAgeDescription(String ageDescription) {
        this.ageDescription = Texts.optional("ageDescription", ageDescription, MAX_TEXT_LENGTH);
    }

    private void applySpecies(Species species, String description) {
        Objects.requireNonNull(species, "species");
        String normalized = Texts.optional("speciesDescription", description, MAX_TEXT_LENGTH);
        if (species == Species.OTHER && normalized == null) {
            throw new DomainValidationException("speciesDescription", "obrigatória quando a espécie é OTHER");
        }
        if (species != Species.OTHER && normalized != null) {
            throw new DomainValidationException("speciesDescription", "só é usada quando a espécie é OTHER");
        }
        this.species = species;
        this.speciesDescription = normalized;
    }

    public PetId id() {
        return id;
    }

    public TutorId tutorId() {
        return tutorId;
    }

    public String name() {
        return name;
    }

    public Species species() {
        return species;
    }

    public Optional<String> speciesDescription() {
        return Optional.ofNullable(speciesDescription);
    }

    public Optional<String> breed() {
        return Optional.ofNullable(breed);
    }

    public Optional<String> ageDescription() {
        return Optional.ofNullable(ageDescription);
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Long version() {
        return version;
    }
}
