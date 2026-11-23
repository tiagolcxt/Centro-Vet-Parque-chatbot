package com.clinicavet.assistant.shared.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** D9: regra inicial da prioridade da fila (revisão pré-piloto 3.2). */
class OperationalPriorityTest {

    @Test
    void riskDecidesFirst() {
        assertThat(OperationalPriority.derive(ClinicalRisk.POSSIBLE_EMERGENCY, true, false))
                .isEqualTo(OperationalPriority.IMMEDIATE);
        assertThat(OperationalPriority.derive(ClinicalRisk.URGENT, true, false)).isEqualTo(OperationalPriority.HIGH);
        assertThat(OperationalPriority.derive(ClinicalRisk.CLINICAL_FOLLOW_UP, true, false))
                .isEqualTo(OperationalPriority.NORMAL);
    }

    @Test
    void notAssessedIsNeverLow() {
        assertThat(OperationalPriority.derive(ClinicalRisk.NOT_ASSESSED, true, false))
                .isEqualTo(OperationalPriority.NORMAL);
    }

    @Test
    void lowImpactWithoutClinicalSign_isLow_andOverdueRaisesOneLevel() {
        assertThat(OperationalPriority.derive(ClinicalRisk.NONE_IDENTIFIED, true, false))
                .isEqualTo(OperationalPriority.LOW);
        assertThat(OperationalPriority.derive(ClinicalRisk.NONE_IDENTIFIED, false, false))
                .isEqualTo(OperationalPriority.NORMAL);
        assertThat(OperationalPriority.derive(ClinicalRisk.NONE_IDENTIFIED, true, true))
                .isEqualTo(OperationalPriority.NORMAL);
        assertThat(OperationalPriority.derive(ClinicalRisk.CLINICAL_FOLLOW_UP, false, true))
                .isEqualTo(OperationalPriority.HIGH);
    }

    @Test
    void overdueNeverMakesSomethingImmediate() {
        assertThat(OperationalPriority.derive(ClinicalRisk.URGENT, false, true)).isEqualTo(OperationalPriority.HIGH);
    }
}
