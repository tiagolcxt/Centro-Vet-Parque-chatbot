package com.clinicavet.assistant.platform;

import java.util.List;

/** Clínicas cadastradas (tabela global). Na S1, novas clínicas são criadas pela equipe da plataforma (RF-S01). */
public interface Clinics {

    List<ClinicId> active();

    /** Nível de ativação do assistente na clínica (ADR-0025). Clínica desconhecida: o nível mais restrito. */
    ActivationLevel activationLevel(ClinicId clinic);
}
