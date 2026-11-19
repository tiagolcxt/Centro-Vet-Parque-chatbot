package com.clinicavet.assistant.platform.application;

import com.clinicavet.assistant.platform.AuditLog;
import com.clinicavet.assistant.platform.CurrentClinic;
import com.clinicavet.assistant.platform.StaffUser;
import com.clinicavet.assistant.platform.infrastructure.persistence.AuditEventEntity;
import com.clinicavet.assistant.platform.infrastructure.persistence.AuditEventJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
class AuditService implements AuditLog {

    static final int MAX_SUMMARY = 500;

    private final AuditEventJpaRepository events;
    private final Clock clock;

    AuditService(AuditEventJpaRepository events, Clock clock) {
        this.events = events;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void record(String action, String targetType, Object targetId, String summary) {
        CurrentClinic.require();
        UUID actor = StaffUser.current().map(StaffUser::userId).orElse(null);
        String trimmed = summary == null || summary.length() <= MAX_SUMMARY ? summary : summary.substring(0, MAX_SUMMARY);
        events.save(new AuditEventEntity(clock.instant(), actor, action, targetType,
                targetId == null ? null : targetId.toString(), trimmed));
    }
}
