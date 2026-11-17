package com.clinicavet.assistant.conversation.application.validation;

import com.clinicavet.assistant.conversation.application.validation.Fact.Type;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/** Cada formato da DA-29 com casos detectados e casos que não devem ser detectados. */
class FactExtractorTest {

    private static Fact only(String text) {
        var facts = FactExtractor.extract(text);
        assertThat(facts).as("fatos em: " + text).hasSize(1);
        return facts.getFirst();
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
            "A consulta custa R$ 120.|12000",
            "Fica R$120,00.|12000",
            "Fica R$ 1.200,50 no total.|120050",
            "São 120 reais.|12000",
            "São 120,00 reais.|12000",
            "Fica R$ 90,5.|9050"
    })
    void money(String text, String cents) {
        assertThat(only(text)).isEqualTo(new Fact(Type.MONEY, cents));
    }

    @Test
    void moneyRange_yieldsBothEnds() {
        assertThat(FactExtractor.extract("Entre R$ 90 a R$ 150, a partir de R$ 90."))
                .containsExactly(new Fact(Type.MONEY, "9000"), new Fact(Type.MONEY, "15000"), new Fact(Type.MONEY, "9000"));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
            "Abrimos às 8h.|08:00",
            "Abrimos às 8h30.|08:30",
            "Abrimos às 08:00.|08:00",
            "Abrimos 8:30.|08:30",
            "Abrimos às 8.|08:00",
            "Fechamos às 18 horas.|18:00",
            "Fechamos ao meio-dia.|12:00",
            "Até a meia-noite.|00:00",
            "Abrimos até 18h.|18:00"
    })
    void time(String text, String hhmm) {
        assertThat(only(text)).isEqualTo(new Fact(Type.TIME, hhmm));
    }

    @Test
    void timeRange_dasAs() {
        assertThat(FactExtractor.extract("Funcionamos das 8 às 18."))
                .containsExactly(new Fact(Type.TIME, "08:00"), new Fact(Type.TIME, "18:00"));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"Atendemos 24h.", "Atendimento 24 horas."})
    void allDay(String text) {
        assertThat(only(text)).isEqualTo(new Fact(Type.ALL_DAY, "24h"));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
            "Abrimos na segunda.|segunda",
            "Abrimos na terça-feira.|terca",
            "Abrimos às quartas.|quarta",
            "Abrimos no sábado.|sabado",
            "Fechado no domingo.|domingo",
            "Só na qui. pela manhã.|quinta"
    })
    void weekday(String text, String day) {
        assertThat(FactExtractor.extract(text)).contains(new Fact(Type.WEEKDAY, day));
    }

    @Test
    void weekdayAbbreviationRange() {
        assertThat(FactExtractor.extract("Atendemos de seg a sex."))
                .containsExactly(new Fact(Type.WEEKDAY, "segunda"), new Fact(Type.WEEKDAY, "sexta"));
    }

    @Test
    void weekendAndHoliday() {
        assertThat(FactExtractor.extract("No fim de semana e nos feriados, não."))
                .containsExactly(new Fact(Type.WEEKEND, "fim-de-semana"), new Fact(Type.HOLIDAY, "feriado"));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
            "Fechado em 25/12.|25/12",
            "Fechado em 25/12/2026.|25/12",
            "Fechado em 25 de dezembro.|25/12",
            "Abre em 2 de março.|02/03"
    })
    void date(String text, String ddmm) {
        assertThat(only(text)).isEqualTo(new Fact(Type.DATE, ddmm));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
            "Ligue (11) 3000-1000.|1130001000",
            "Ligue 3000-1000.|30001000",
            "WhatsApp +55 11 90000-1000.|5511900001000",
            "WhatsApp 11 900001000.|11900001000"
    })
    void phone(String text, String digits) {
        assertThat(only(text)).isEqualTo(new Fact(Type.PHONE, digits));
    }

    @Test
    void phoneWithAndWithoutAreaCode_areTheSame() {
        assertThat(new Fact(Type.PHONE, "1130001000").sameAs(new Fact(Type.PHONE, "30001000"))).isTrue();
        assertThat(new Fact(Type.PHONE, "551130001000").sameAs(new Fact(Type.PHONE, "1130001000"))).isTrue();
        assertThat(new Fact(Type.PHONE, "1130001000").sameAs(new Fact(Type.PHONE, "1130001001"))).isFalse();
    }

    @Test
    void emailSiteAndHandle() {
        assertThat(FactExtractor.extract("Escreva para Contato@Clinica.example, veja https://www.clinica.example/ ou @clinica_pet."))
                .containsExactly(new Fact(Type.EMAIL, "contato@clinica.example"),
                        new Fact(Type.URL, "clinica.example"),
                        new Fact(Type.HANDLE, "@clinica_pet"));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"CEP 01000-000.", "Fica no 01000-000.", "cep: 01000000"})
    void zip(String text) {
        assertThat(only(text)).isEqualTo(new Fact(Type.ZIP, "01000000"));
    }

    @Test
    void discountAndInstallments() {
        assertThat(FactExtractor.extract("Damos 10% de desconto e parcelamos em 3x ou em até 10 vezes."))
                .containsExactly(new Fact(Type.PERCENT, "10"), new Fact(Type.INSTALLMENTS, "3"),
                        new Fact(Type.INSTALLMENTS, "10"));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
            "O resultado sai em 48 horas.|48 hora",
            "Responderemos em até 2 dias úteis.|2 dia util",
            "Chega em 30 minutos.|30 minuto",
            "Avise com 24 horas de antecedência.|24 hora",
            "Pronto em 48h.|48 hora"
    })
    void duration(String text, String value) {
        assertThat(only(text)).isEqualTo(new Fact(Type.DURATION, value));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
            "Dê 5 mg.|5mg",
            "Dê 2,5 ml.|2.5ml",
            "Use 0,5 mg/kg.|0.5mg/kg",
            "Pingue 3 gotas.|3gotas",
            "Dê meio comprimido.|meio comprimido",
            "Dê 2 comprimidos.|2 comprimidos",
            "Repita de 8 em 8 horas.|de 8 em 8 horas"
    })
    void dose(String text, String value) {
        assertThat(only(text)).isEqualTo(new Fact(Type.DOSE, value));
    }

    /** Falsos positivos que a lista deixa de fora de propósito (DA-29). */
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "Seu cachorro tem 3 anos.",
            "Ele come 2 vezes ao dia.",
            "Vou pensar em as 3 opções.",
            "Você pode ter mais informações com a equipe.",
            "Das 3 vacinas, falta uma.",
            "Pode me contar um pouco mais?",
            "Ele pesa 12 quilos."
    })
    void notFacts(String text) {
        assertThat(FactExtractor.extract(text)).isEmpty();
    }
}
