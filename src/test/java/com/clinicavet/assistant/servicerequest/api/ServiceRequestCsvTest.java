package com.clinicavet.assistant.servicerequest.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Células da planilha de pedidos (S3-18): o Excel não pode executar nada que o tutor escreveu. */
class ServiceRequestCsvTest {

    @Test
    void cell_quotesSeparators_flattensLines_andNeutralizesFormulas() {
        assertThat(ServiceRequestController.cell(null)).isEmpty();
        assertThat(ServiceRequestController.cell("banho; tosa")).isEqualTo("\"banho; tosa\"");
        assertThat(ServiceRequestController.cell("linha 1\nlinha 2")).isEqualTo("linha 1 linha 2");
        assertThat(ServiceRequestController.cell("=1+1")).isEqualTo("'=1+1");
        assertThat(ServiceRequestController.cell("@SOMA(A1)")).startsWith("'");
        assertThat(ServiceRequestController.cell("diz \"oi\"")).isEqualTo("\"diz \"\"oi\"\"\"");
    }
}
