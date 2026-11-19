package com.clinicavet.assistant.platform;

import java.util.List;

/** Clínicas cadastradas (tabela global). Na S1, novas clínicas são criadas pela equipe da plataforma (RF-S01). */
public interface Clinics {

    List<ClinicId> active();
}
