package com.clinicavet.assistant.conversation.application.rules;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/** DA-30: cada grupo de expressões dispara, com variações de escrita; as frases de controle não disparam. */
class ExplicitHumanRequestDetectorTest {

    private final ExplicitHumanRequestDetector detector = new ExplicitHumanRequestDetector();

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            // pedido + falar/conversar com pessoa
            "Quero falar com um atendente",
            "QUERO FALAR COM UM ATENDENTE!!!",
            "quero   falar com   um atendente",
            "Gostaria de falar com a recepção, por favor.",
            "Preciso muito conversar com alguém da clínica",
            "Tem como eu falar com o veterinário?",
            "dá pra falar com uma pessoa?",
            "Queria falar com a doutora sobre o resultado",
            "posso falar com o responsável?",
            "Falar com atendente pfv",
            // transferência
            "Me passa pra alguém aí",
            "me transfere para um atendente",
            "Vc pode me encaminhar para a equipe?",
            "Transferência para atendente, por favor",
            // atendimento humano
            "Quero atendimento humano",
            "Tem atendente humano?",
            "Preciso de uma pessoa de verdade",
            "quero falar com gente real",
            "Prefiro falar com um ser humano",
            // recusa do robô
            "Não quero falar com robô",
            "nao quero bot",
            "Chega de robô!",
            "Não quero falar com inteligência artificial",
            // pedido de ligação
            "Me liga, por favor",
            "Podem me ligar?",
            "Alguém pode me ligar hoje?",
            // mensagem curta inteira
            "Atendente",
            "atendente por favor",
            "Humano!",
            "Uma pessoa",
            "falar com alguém",
            "já falei, quero uma pessoa"
    })
    void requestsForAHuman_trigger(String text) {
        assertThat(detector.detect(text)).as(text).isPresent();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "A atendente de ontem foi muito atenciosa.",
            "Já falei com a atendente ontem e ela marcou.",
            "Não precisa chamar atendente, só quero saber o horário.",
            "Não quero falar com atendente, prefiro resolver por aqui.",
            "Posso falar com você sobre vacinas?",
            "Quero falar sobre a consulta.",
            "O veterinário falou para voltar em 15 dias.",
            "A pessoa que me atendeu disse que estava tudo bem.",
            "Humano também pega essa doença?",
            "Você é um robô?",
            "Não precisa me ligar.",
            "Obrigado!",
            "Meu cachorro gosta de pessoas.",
            "Quanto custa a consulta?"
    })
    void controlPhrases_doNotTrigger(String text) {
        assertThat(detector.detect(text)).as(text).isEmpty();
    }
}
