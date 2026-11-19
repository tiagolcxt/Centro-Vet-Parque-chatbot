package com.clinicavet.assistant.team.application;

import com.clinicavet.assistant.clinicknowledge.domain.Departments;
import com.clinicavet.assistant.platform.ClinicId;
import com.clinicavet.assistant.platform.Clinics;
import com.clinicavet.assistant.platform.CurrentClinic;
import com.clinicavet.assistant.platform.Roles;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Na inicialização, garante em cada clínica ativa os setores padrão (Recepção, Clínica, Pet shop) e os seis
 * perfis-modelo (DA-42, DA-49). Idempotente: o que já existe não é tocado.
 */
@Component
class ClinicDefaults implements ApplicationRunner {

    private final Clinics clinics;
    private final Departments departments;
    private final Roles roles;

    ClinicDefaults(Clinics clinics, Departments departments, Roles roles) {
        this.clinics = clinics;
        this.departments = departments;
        this.roles = roles;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (ClinicId clinic : clinics.active()) {
            CurrentClinic.run(clinic, () -> {
                departments.ensureDefaults();
                roles.ensureDefaults();
            });
        }
    }
}
