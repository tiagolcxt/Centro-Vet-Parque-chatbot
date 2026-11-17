package com.clinicavet.assistant.tutor.domain;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ProvenanceRepository {

    Provenance save(Provenance provenance);

    /** Proveniência dos campos desses sujeitos (o tutor e os pets de um mesmo tutor). */
    List<Provenance> findBySubjects(Collection<UUID> subjectIds);
}
