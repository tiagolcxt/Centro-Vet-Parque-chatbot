package com.clinicavet.assistant.conversation.application;

/**
 * Porta para o modelo de linguagem (ADR-0002). A implementação real é o adaptador Spring AI do
 * módulo {@code ai}; nos testes, um <i>double</i>.
 * <p>
 * O adaptador só transporta: devolve o que o modelo respondeu, sem julgar. Quem decide se a
 * resposta serve é o {@link TurnResultClassifier}. Erros de chamada (rede, timeout, HTTP) viram
 * {@link AssistantModelException}.
 */
public interface AssistantModel {

    ModelReply respond(AssistantRequest request);
}
