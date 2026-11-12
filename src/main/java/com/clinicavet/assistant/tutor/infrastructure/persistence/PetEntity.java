package com.clinicavet.assistant.tutor.infrastructure.persistence;

import com.clinicavet.assistant.tutor.domain.Pet;
import com.clinicavet.assistant.tutor.domain.PetId;
import com.clinicavet.assistant.tutor.domain.Species;
import com.clinicavet.assistant.tutor.domain.TutorId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pet")
class PetEntity {

    @Id
    @Column(name = "id", columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "tutor_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID tutorId;

    @Column(name = "name", length = 80, nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "species", columnDefinition = "VARCHAR(10)", nullable = false)
    private Species species;

    @Column(name = "species_description", length = 80)
    private String speciesDescription;

    @Column(name = "breed", length = 80)
    private String breed;

    @Column(name = "age_description", length = 80)
    private String ageDescription;

    @Column(name = "created_at", columnDefinition = "DATETIME(6)", nullable = false)
    private Instant createdAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected PetEntity() {
    }

    static PetEntity from(Pet pet) {
        PetEntity entity = new PetEntity();
        entity.id = pet.id().value();
        entity.tutorId = pet.tutorId().value();
        entity.name = pet.name();
        entity.species = pet.species();
        entity.speciesDescription = pet.speciesDescription().orElse(null);
        entity.breed = pet.breed().orElse(null);
        entity.ageDescription = pet.ageDescription().orElse(null);
        entity.createdAt = pet.createdAt();
        entity.version = pet.version();
        return entity;
    }

    Pet toDomain() {
        return Pet.restore(new PetId(id), new TutorId(tutorId), name, species, speciesDescription, breed,
                ageDescription, createdAt, version);
    }
}
