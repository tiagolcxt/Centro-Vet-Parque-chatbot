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
class AuditService implements AuditLog, com.clinicavet.assistant.platform.AuditTrail {

    static final int MAX_SUMMARY = 500;

    private final AuditEventJpaRepository events;
    private final Clock clock;

    AuditService(AuditEventJpaRepository events, Clock clock) {
        this.events = events;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Page search(Filter filter, int page, int size) {
        java.time.Instant from = filter.from() == null ? java.time.Instant.EPOCH : filter.from();
        java.time.Instant to = filter.to() == null ? java.time.Instant.parse("9999-12-31T00:00:00Z") : filter.to();
        String action = filter.actionPrefix() == null || filter.actionPrefix().isBlank() ? null
                : filter.actionPrefix().strip().replace("%", "").replace("_", "\\_") + "%";
        org.springframework.data.domain.Page<AuditEventEntity> found = events.search(from, to, filter.actorUserId(),
                action, blankToNull(filter.targetType()), blankToNull(filter.targetId()), filter.withoutLogins(),
                org.springframework.data.domain.PageRequest.of(page, size));
        return new Page(found.getContent().stream().map(e -> new Entry(e.id(), e.occurredAt(), e.actorUserId(),
                e.action(), e.targetType(), e.targetId(), e.summary())).toList(), page, size, found.getTotalElements());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
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
