package com.clinicavet.assistant.tutor.domain;

/**
 * Campos do cadastro com proveniência própria (DA-35). Só dados persistentes (ADR-0010, item 3): motivo,
 * sintomas e preferências pertencem à solicitação. O telefone não é coletado pelo assistente: o contato já vem
 * do canal (minimização, DA-36).
 */
public enum DataField {
    TUTOR_NAME(DataSubject.TUTOR),
    PET_NAME(DataSubject.PET),
    PET_SPECIES(DataSubject.PET),
    PET_BREED(DataSubject.PET),
    PET_AGE(DataSubject.PET);

    private final DataSubject subject;

    DataField(DataSubject subject) {
        this.subject = subject;
    }

    public DataSubject subject() {
        return subject;
    }
}
