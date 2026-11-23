package com.clinicavet.assistant.platform.application;

import com.clinicavet.assistant.platform.ActivationLevel;
import com.clinicavet.assistant.platform.ClinicId;
import com.clinicavet.assistant.platform.Clinics;
import com.clinicavet.assistant.platform.infrastructure.persistence.ClinicJpaRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
class ClinicService implements Clinics {

    private final ClinicJpaRepository clinics;
    private final JdbcTemplate jdbc;

    ClinicService(ClinicJpaRepository clinics, JdbcTemplate jdbc) {
        this.clinics = clinics;
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClinicId> active() {
        return clinics.findByStatus("ACTIVE").stream().map(c -> new ClinicId(c.id())).toList();
    }

    /** Lido a cada turno: a mudança de nível vale na mensagem seguinte. */
    @Override
    public ActivationLevel activationLevel(ClinicId clinic) {
        return jdbc.queryForList("SELECT activation_level FROM clinic WHERE id = UUID_TO_BIN(?)", Integer.class,
                        clinic.toString())
                .stream().findFirst().map(ActivationLevel::of).orElse(ActivationLevel.ORGANIZE);
    }
}
