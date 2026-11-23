package com.clinicavet.assistant.alert.infrastructure;

import com.clinicavet.assistant.alert.ForgottenWork;
import com.clinicavet.assistant.alert.domain.WorkAlert;
import com.clinicavet.assistant.alert.domain.WorkAlertRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface WorkAlertJpaRepository extends JpaRepository<WorkAlertEntity, UUID> {

    List<WorkAlertEntity> findByClosedAtIsNullOrderByRaisedAtAsc();

    boolean existsByKindAndSubjectIdAndPendingSince(ForgottenWork.Kind kind, UUID subjectId, Instant pendingSince);
}

@Repository
class JpaWorkAlertRepository implements WorkAlertRepository {

    private final WorkAlertJpaRepository jpa;

    JpaWorkAlertRepository(WorkAlertJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public WorkAlert save(WorkAlert alert) {
        return jpa.saveAndFlush(WorkAlertEntity.from(alert)).toDomain();
    }

    @Override
    public Optional<WorkAlert> findById(UUID id) {
        return jpa.findById(id).map(WorkAlertEntity::toDomain);
    }

    @Override
    public List<WorkAlert> findOpen() {
        return jpa.findByClosedAtIsNullOrderByRaisedAtAsc().stream().map(WorkAlertEntity::toDomain).toList();
    }

    @Override
    public boolean exists(ForgottenWork.Kind kind, UUID subjectId, Instant pendingSince) {
        return jpa.existsByKindAndSubjectIdAndPendingSince(kind, subjectId, pendingSince);
    }
}
