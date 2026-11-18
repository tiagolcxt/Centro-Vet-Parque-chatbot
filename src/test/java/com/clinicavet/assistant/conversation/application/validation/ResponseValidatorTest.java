package com.clinicavet.assistant.conversation.application.validation;

import com.clinicavet.assistant.clinicknowledge.domain.ClinicKnowledge;
import com.clinicavet.assistant.clinicknowledge.infrastructure.KnowledgeFileLoader;
import com.clinicavet.assistant.conversation.application.AssistantRequest;
import com.clinicavet.assistant.conversation.application.AssistantTurn;
import com.clinicavet.assistant.conversation.domain.ConversationStatus;
import com.clinicavet.assistant.conversation.domain.Intent;
import com.clinicavet.assistant.conversation.domain.MessageAuthor;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Conjunto versionado de respostas aprovadas e reprovadas (ADR-0008, seção 6; roadmap da Etapa 3).
 * Base fictícia: {@code config/clinic-knowledge.ficticia.yml}. "Agora" é segunda-feira, 16/11/2026, 09:00.
 */
class ResponseValidatorTest {

    private static final String FIXTURE = read();
    private static final ClinicKnowledge KNOWLEDGE = KnowledgeFileLoader.parse(FIXTURE, "ficticia");
    private static final ZonedDateTime NOW = ZonedDateTime.of(2026, 11, 16, 9, 0, 0, 0, ZoneId.of("America/Sao_Paulo"));

    private final ResponseValidator validator = new ResponseValidator(KNOWLEDGE);

    private static String read() {
        try {
            return Files.readString(Path.of("config/clinic-knowledge.ficticia.yml"));
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private static AssistantRequest request(String customerMessage, String... previousCustomerMessages) {
        List<AssistantRequest.HistoryEntry> history = java.util.Arrays.stream(previousCustomerMessages)
                .map(text -> new AssistantRequest.HistoryEntry(MessageAuthor.CUSTOMER, text)).toList();
        return new AssistantRequest(new AssistantRequest.OperationalContext(ConversationStatus.OPEN, NOW),
                history, customerMessage);
    }

    private List<String> checks(ResponseValidator v, String reply, Intent intent, List<String> refs, boolean gap,
                                AssistantRequest request) {
        return v.validate(new AssistantTurn(reply, intent, refs, gap), request).stream()
                .map(ResponseValidator.Violation::check).distinct().toList();
    }

    private List<String> checks(String reply, Intent intent, List<String> refs, boolean gap, AssistantRequest request) {
        return checks(validator, reply, intent, refs, gap, request);
    }

    private List<String> checks(String reply, Intent intent, List<String> refs, boolean gap) {
        return checks(reply, intent, refs, gap, request("Oi"));
    }

    @Nested
    class V1References {

        @Test
        void knownItem_and_knownEntry_areAccepted() {
            assertThat(checks("Aos sábados abrimos das 8h às 12h.", Intent.CLINIC_INFORMATION,
                    List.of("hours.saturday"), false)).isEmpty();
            assertThat(checks("Temos consulta clínica, por R$ 150,00.", Intent.SERVICE_INFORMATION,
                    List.of("services.consulta"), false)).isEmpty();
        }

        @Test
        void nonexistentReference_isRejected() {
            assertThat(checks("Temos acupuntura.", Intent.SERVICE_INFORMATION, List.of("services.acupuntura"), false))
                    .contains("V1");
        }

        @Test
        void unknownSourceCited_isRejected() {
            assertThat(checks("Abrimos no feriado.", Intent.CLINIC_INFORMATION, List.of("hours.holidays"), false))
                    .contains("V1");
        }

        @Test
        void internalApprovedText_isNotASource() {
            assertThat(checks("Ok.", Intent.OTHER, List.of("approved-texts.emergency-policy"), false)).contains("V1");
        }
    }

    @Nested
    class V2AuthorizedValues {

        @Test
        void priceWithItsSource_isAccepted() {
            assertThat(checks("A consulta custa R$ 150,00.", Intent.PRICE_INQUIRY,
                    List.of("services.consulta.price-cents"), false)).isEmpty();
            assertThat(checks("A vacina V10 sai por 90 reais por dose.", Intent.PRICE_INQUIRY,
                    List.of("services.vacina-v10"), false)).isEmpty();
        }

        @Test
        void priceWithoutSource_isRejected() {
            assertThat(checks("O banho e tosa custa R$ 80.", Intent.PRICE_INQUIRY,
                    List.of("services.banho-e-tosa.name"), false)).containsExactly("V2");
        }

        @Test
        void correctPriceOfAnotherItem_isRejected() {
            assertThat(checks("A vacina custa R$ 150.", Intent.PRICE_INQUIRY,
                    List.of("services.vacina-v10.price-cents"), false)).containsExactly("V2");
        }

        @Test
        void dayNotCoveredByTheCitedSource_isRejected() {
            assertThat(checks("Abrimos no domingo das 8h às 12h.", Intent.CLINIC_INFORMATION,
                    List.of("hours.saturday"), false)).containsExactly("V2");
        }

        @Test
        void echoOfTheTutorsPreference_isAccepted() {
            AssistantRequest request = request("Prefiro sábado às 9h, pode ser?", "Quero marcar uma consulta para o Thor.");

            assertThat(checks("Anotei sua preferência por sábado às 9h; a equipe confirma a disponibilidade.",
                    Intent.APPOINTMENT_REQUEST, List.of(), false, request)).isEmpty();
        }

        @Test
        void operationalDateAndWeekday_areAccepted() {
            assertThat(checks("Hoje é segunda-feira, 16/11, e amanhã é terça.", Intent.OTHER, List.of(), false)).isEmpty();
        }

        @Test
        void phoneFromTheBase_withoutAreaCode_isAccepted_inventedPhoneIsRejected() {
            assertThat(checks("O telefone é 3000-1000.", Intent.CLINIC_INFORMATION,
                    List.of("identification.phone"), false)).isEmpty();
            assertThat(checks("O telefone é (11) 4000-2000.", Intent.CLINIC_INFORMATION,
                    List.of("identification.phone"), false)).containsExactly("V2");
        }

        @Test
        void inventedAllDayService_isRejected_butDeniedWithSource() {
            assertThat(checks("Atendemos 24 horas.", Intent.CLINIC_INFORMATION, List.of("hours.monday"), false))
                    .containsExactly("V2");
            assertThat(checks("Não atendemos 24 horas.", Intent.CLINIC_INFORMATION,
                    List.of("hours.twenty-four-hours"), false)).isEmpty();
        }

        @Test
        void pricesNotDisclosed_noMoneyIsAuthorized() {
            ClinicKnowledge hidden = KnowledgeFileLoader.parse(
                    FIXTURE.replace("prices-disclosed: true", "prices-disclosed: false"), "teste");
            ResponseValidator strict = new ResponseValidator(hidden);

            assertThat(checks(strict, "A consulta custa R$ 150,00.", Intent.PRICE_INQUIRY,
                    List.of("services.consulta"), false, request("Quanto custa?"))).containsExactly("V2");
        }
    }

    @Nested
    class V3GapCoherence {

        @Test
        void partialAnswer_gapPlusACitedValue_isAccepted_da41() {
            assertThat(checks("Não tenho essa informação, mas aos sábados abrimos às 8h.", Intent.CLINIC_INFORMATION,
                    List.of("hours.saturday"), true)).isEmpty();
            // Sem a fonte citada, o mesmo valor continua reprovado pela V2.
            assertThat(checks("Não tenho essa informação, mas aos sábados abrimos às 8h.", Intent.CLINIC_INFORMATION,
                    List.of(), true)).contains("V2");
        }

        @Test
        void gapWithoutValues_orEchoingTheTutor_isAccepted() {
            assertThat(checks("Não tenho essa informação; a equipe da clínica pode confirmar.",
                    Intent.CLINIC_INFORMATION, List.of(), true)).isEmpty();
            assertThat(checks("Não tenho a informação sobre o feriado de 25/12; a equipe pode confirmar.",
                    Intent.CLINIC_INFORMATION, List.of(), true, request("Vocês abrem no feriado de 25/12?"))).isEmpty();
        }
    }

    @Nested
    class V4InstitutionalWithoutSource {

        @ParameterizedTest
        @ValueSource(strings = {"CLINIC_INFORMATION", "SERVICE_INFORMATION", "PRICE_INQUIRY"})
        void institutionalAnswerWithoutSourceOrGap_isRejected(String intent) {
            assertThat(checks("Sim, fazemos isso.", Intent.valueOf(intent), List.of(), false)).containsExactly("V4");
        }

        @Test
        void sameAnswerDeclaringGap_orNonInstitutionalIntent_isAccepted() {
            assertThat(checks("Não tenho essa informação.", Intent.SERVICE_INFORMATION, List.of(), true)).isEmpty();
            assertThat(checks("Olá! Como posso ajudar?", Intent.GREETING, List.of(), false)).isEmpty();
        }
    }

    @Nested
    class V5Confirmation {

        @ParameterizedTest
        @ValueSource(strings = {
                "Sua consulta está marcada.",
                "Pronto, o agendamento foi confirmado.",
                "Já agendei para você.",
                "Está tudo marcado!",
                "Seu horário está reservado.",
                "Fica combinado então.",
                "Esperamos você amanhã."
        })
        void confirmation_isRejected(String reply) {
            assertThat(checks(reply, Intent.APPOINTMENT_REQUEST, List.of(), false, request("Quero marcar amanhã")))
                    .containsExactly("V5");
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "Ainda não está confirmado: a equipe da clínica confirma o horário.",
                "Não consigo confirmar agendamentos; a equipe faz isso.",
                "Anotei seu pedido de consulta."
        })
        void negatedOrNeutral_isAccepted(String reply) {
            assertThat(checks(reply, Intent.APPOINTMENT_REQUEST, List.of(), false)).isEmpty();
        }
    }

    @Nested
    class V6PromiseWithoutProcess {

        @ParameterizedTest
        @ValueSource(strings = {
                "Já avisei a equipe.",
                "Encaminhei sua dúvida para o veterinário.",
                "A equipe já foi avisada.",
                "Sua mensagem foi encaminhada.",
                "A equipe vai te ligar.",
                "Entraremos em contato em breve.",
                "Aguarde nosso retorno.",
                "Já está resolvido.",
                "Pronto, está tudo certo.",
                "Sua solicitação foi concluída."
        })
        void promise_isRejected(String reply) {
            assertThat(checks(reply, Intent.OTHER, List.of(), false)).containsExactly("V6");
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "Tudo certo?",
                "Está tudo certo?",
                "Você pode falar diretamente com a equipe da clínica.",
                "Não avisei ninguém: eu não tenho como notificar a equipe."
        })
        void questionOrHonestStatement_isAccepted(String reply) {
            assertThat(checks(reply, Intent.OTHER, List.of(), false)).isEmpty();
        }
    }

    @Nested
    class V7Dose {

        @ParameterizedTest
        @ValueSource(strings = {"Dê 5 mg pela manhã.", "Pode dar meio comprimido.", "Repita de 8 em 8 horas."})
        void dose_isRejected(String reply) {
            assertThat(checks(reply, Intent.MEDICAL_CONCERN, List.of(), false)).containsExactly("V7");
        }

        @Test
        void echoOfTheDoseWrittenByTheTutor_isAccepted() {
            AssistantRequest request = request("O veterinário receitou 5 mg, posso dar mais?");

            assertThat(checks("Entendo que foram receitados 5 mg, mas não posso orientar sobre doses; fale com o veterinário.",
                    Intent.MEDICAL_CONCERN, List.of(), false, request)).isEmpty();
        }
    }

    @Test
    void violationsCarryOnlyCodes_neverContent() {
        List<ResponseValidator.Violation> violations = validator.validate(
                new AssistantTurn("Custa R$ 999 e já avisei a equipe.", Intent.PRICE_INQUIRY, List.of(), false), request("Oi"));

        assertThat(violations).extracting(Object::toString)
                .containsExactlyInAnyOrder("V2:MONEY", "V4:PRICE_INQUIRY", "V6:v6a-avisei")
                .noneMatch(text -> text.contains("999"));
    }
}
