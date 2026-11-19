package com.clinicavet.assistant.platform.application;

import com.clinicavet.assistant.platform.ClinicId;
import com.clinicavet.assistant.platform.Clinics;
import com.clinicavet.assistant.platform.infrastructure.persistence.ClinicJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
class ClinicService implements Clinics {

    private final ClinicJpaRepository clinics;

    ClinicService(ClinicJpaRepository clinics) {
        this.clinics = clinics;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClinicId> active() {
        return clinics.findByStatus("ACTIVE").stream().map(c -> new ClinicId(c.id())).toList();
    }
}
