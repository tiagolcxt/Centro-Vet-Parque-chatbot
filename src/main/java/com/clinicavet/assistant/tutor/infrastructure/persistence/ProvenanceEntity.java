package com.clinicavet.assistant.tutor.infrastructure.persistence;

import com.clinicavet.assistant.conversation.domain.ConversationId;
import com.clinicavet.assistant.tutor.domain.DataField;
import com.clinicavet.assistant.tutor.domain.DataSubject;
import com.clinicavet.assistant.tutor.domain.Provenance;
import com.clinicavet.assistant.tutor.domain.ProvenanceRepository;
import com.clinicavet.assistant.tutor.domain.ProvenanceStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Proveniência por campo (DA-35, migration V4). */
@Entity
@Table(name = "data_provenance")
class ProvenanceEntity {

    @Id
    @Column(name = "id", columnDefinition = "BINARY(16)")
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "subject", columnDefinition = "VARCHAR(10)", nullable = false)
    private DataSubject subject;

    @Column(name = "subject_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID subjectId;

    @Enumerated(EnumType.STRING)
    @Column(name = "field", columnDefinition = "VARCHAR(20)", nullable = false)
    private DataField field;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", columnDefinition = "VARCHAR(12)", nullable = false)
    private ProvenanceStatus status;

    @Column(name = "conversation_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID conversationId;

    @Column(name = "confirmed_at", columnDefinition = "DATETIME(6)")
    private Instant confirmedAt;

    @Column(name = "updated_at", columnDefinition = "DATETIME(6)", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected ProvenanceEntity() {
    }

    static ProvenanceEntity from(Provenance provenance) {
        ProvenanceEntity entity = new ProvenanceEntity();
        entity.id = provenance.id();
        entity.subject = provenance.subject();
        entity.subjectId = provenance.subjectId();
        entity.field = provenance.field();
        entity.status = provenance.status();
        entity.conversationId = provenance.conversationId().value();
        entity.confirmedAt = provenance.confirmedAt().orElse(null);
        entity.updatedAt = provenance.updatedAt();
        entity.version = provenance.version();
        return entity;
    }

    Provenance toDomain() {
        return Provenance.restore(id, subject, subjectId, field, status, new ConversationId(conversationId),
                confirmedAt, updatedAt, version);
    }
}

interface ProvenanceJpaRepository extends JpaRepository<ProvenanceEntity, UUID> {

    List<ProvenanceEntity> findBySubjectIdIn(Collection<UUID> subjectIds);
}

@Repository
class JpaProvenanceRepository implements ProvenanceRepository {

    private final ProvenanceJpaRepository jpa;

    JpaProvenanceRepository(ProvenanceJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Provenance save(Provenance provenance) {
        return jpa.saveAndFlush(ProvenanceEntity.from(provenance)).toDomain();
    }

    @Override
    public List<Provenance> findBySubjects(Collection<UUID> subjectIds) {
        if (subjectIds.isEmpty()) {
            return List.of();
        }
        return jpa.findBySubjectIdIn(subjectIds).stream().map(ProvenanceEntity::toDomain).toList();
    }
}
