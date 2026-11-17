package com.clinicavet.assistant.conversation.application.rules;

import com.clinicavet.assistant.clinicknowledge.domain.ClinicKnowledge;
import com.clinicavet.assistant.clinicknowledge.infrastructure.KnowledgeFileLoader;
import com.clinicavet.assistant.conversation.domain.AssistantMetadata;
import com.clinicavet.assistant.conversation.domain.ClinicalPriority;
import com.clinicavet.assistant.conversation.domain.ConversationId;
import com.clinicavet.assistant.conversation.domain.HandoffPolicy.Unproductive;
import com.clinicavet.assistant.conversation.domain.Intent;
import com.clinicavet.assistant.conversation.domain.Message;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AlertAndUnproductiveDetectorsTest {

    private static ClinicKnowledge fixture() {
        try {
            return KnowledgeFileLoader.parse(Files.readString(Path.of("config/clinic-knowledge.ficticia.yml")), "ficticia");
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    /** ADR-0019, DA-27, com a lista ilustrativa da base fictícia. */
    @Nested
    class AlertExpressions {

        private final AlertExpressionDetector detector = new AlertExpressionDetector(fixture());

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {
                "Socorro, meu cachorro não está respirando direito",
                "ELE ESTÁ CONVULSIONANDO",
                "Acho que comeu veneno de rato",
                "O cachorro foi atropelado agora",
                "ele está com muita dor e chorando"
        })
        void p1Expressions_imposeP1(String text) {
            assertThat(detector.floor(text)).contains(ClinicalPriority.P1_POSSIBLE_EMERGENCY);
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"Ele piorou desde ontem", "Minha gata não quer comer", "ele está sentindo dor na pata",
                "Ele parou de comer e está com dor"})
        void p2Expressions_imposeP2(String text) {
            assertThat(detector.floor(text)).contains(ClinicalPriority.P2_URGENT);
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"Quanto custa a consulta?", "Ele come bem e está ótimo", "Comprei ração nova"})
        void otherMessages_imposeNothing(String text) {
            assertThat(detector.floor(text)).isEmpty();
        }

        @Test
        void withoutApprovedList_neverTriggers() throws IOException {
            ClinicKnowledge empty = KnowledgeFileLoader.parse(
                    Files.readString(Path.of("config/clinic-knowledge.example.yml")), "example");

            assertThat(new AlertExpressionDetector(empty).floor("convulsionando")).isEmpty();
        }
    }

    /** DA-31: oferece na 2ª ocorrência, encaminha na 3ª. */
    @Nested
    class UnproductiveConversation {

        private final UnproductiveConversationDetector detector = new UnproductiveConversationDetector();
        private final ConversationId conversation = ConversationId.newId();
        private final List<Message> history = new ArrayList<>();
        private Instant t = Instant.parse("2026-11-17T12:00:00Z");

        private void customer(String text) {
            history.add(Message.fromCustomer(conversation, text, t = t.plusSeconds(1)));
        }

        private void assistant(String text, boolean fallback) {
            AssistantMetadata meta = new AssistantMetadata(fallback, null, null, Set.of(), List.of());
            history.add(Message.fromAssistant(conversation, text, fallback ? null : Intent.OTHER, meta, t = t.plusSeconds(1)));
        }

        @Test
        void consecutiveFailures_offerAtTwo_handOffAtThree() {
            assertThat(detector.evaluate(history, "Oi, tudo bem por aí?", true, null)).isEqualTo(Unproductive.NONE);
            customer("Oi, tudo bem por aí?");
            assistant("segura", true);
            assertThat(detector.evaluate(history, "Pode me ajudar com algo?", true, null)).isEqualTo(Unproductive.OFFER);
            customer("Pode me ajudar com algo?");
            assistant("segura", true);
            assertThat(detector.evaluate(history, "Alguém aí para ajudar?", true, null)).isEqualTo(Unproductive.HANDOFF);
        }

        @Test
        void validTurnInBetween_resetsTheFailureCount() {
            customer("Primeira pergunta aqui");
            assistant("segura", true);
            customer("Segunda pergunta aqui");
            assistant("Resposta válida.", false);
            customer("Terceira pergunta aqui");
            assistant("segura", true);

            assertThat(detector.evaluate(history, "Quarta pergunta aqui", true, null)).isEqualTo(Unproductive.OFFER);
        }

        @Test
        void tutorRepeatingTheSameMessage_offersThenHandsOff_ignoringCaseAndAccents() {
            customer("Quanto custa o banho e tosa?");
            assistant("Não tenho essa informação.", false);
            assertThat(detector.evaluate(history, "quanto custa o BANHO e tosa", false, "Outra resposta."))
                    .isEqualTo(Unproductive.OFFER);
            customer("quanto custa o BANHO e tosa");
            assistant("Outra resposta.", false);
            assertThat(detector.evaluate(history, "Quanto custa o banho e tosa??", false, "Mais uma."))
                    .isEqualTo(Unproductive.HANDOFF);
        }

        @Test
        void shortRepliesLikeOkOrYes_areNotRepetition() {
            customer("ok");
            assistant("Resposta 1.", false);
            customer("ok");
            assistant("Resposta 2.", false);

            assertThat(detector.evaluate(history, "ok", false, "Resposta 3.")).isEqualTo(Unproductive.NONE);
        }

        @Test
        void assistantRepeatingTheSameReply_offersThenHandsOff() {
            customer("Pergunta número um");
            assistant("Não entendi, pode repetir?", false);
            assertThat(detector.evaluate(history, "Pergunta número dois", false, "Não entendi, pode repetir?"))
                    .isEqualTo(Unproductive.OFFER);
            customer("Pergunta número dois");
            assistant("Não entendi, pode repetir?", false);
            assertThat(detector.evaluate(history, "Pergunta número três", false, "Não entendi, pode repetir?"))
                    .isEqualTo(Unproductive.HANDOFF);
        }

        @Test
        void onlyTheLastTenMessagesCount() {
            customer("Quanto custa o banho e tosa?");
            for (int i = 0; i < 10; i++) {
                assistant("Resposta " + i, false);
            }
            assertThat(detector.evaluate(history, "Quanto custa o banho e tosa?", false, "Nova")).isEqualTo(Unproductive.NONE);
        }
    }
}
