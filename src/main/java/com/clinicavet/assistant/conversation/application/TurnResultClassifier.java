package com.clinicavet.assistant.conversation.application;

import com.clinicavet.assistant.conversation.domain.Intent;
import com.clinicavet.assistant.conversation.domain.Message;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.Locale;

/**
 * Classifica a resposta bruta do modelo (ADR-0002, tabela de resultados). Só o resultado
 * {@link TurnOutcome.Valid válido} segue adiante. Intenção fora do enum vira {@code OTHER} e não é
 * falha; todo o resto que fuja do contrato é.
 * <p>
 * O {@code finishReason} chega do Spring AI como nome de enum em maiúsculas ({@code STOP},
 * {@code LENGTH}), não como o valor da API: a comparação ignora caixa (ADR-0016, achados).
 */
@Component
public class TurnResultClassifier {

    private final ObjectMapper json = JsonMapper.builder().build();

    public TurnOutcome classify(ModelReply reply, int attempt) {
        String finish = reply.finishReason() == null ? "" : reply.finishReason().strip().toLowerCase(Locale.ROOT);
        if ((reply.refusal() != null && !reply.refusal().isBlank()) || finish.equals("content_filter")) {
            return new TurnOutcome.Failed(FailureKind.REFUSAL, attempt);
        }
        if (finish.equals("length")) {
            return new TurnOutcome.Failed(FailureKind.TRUNCATED, attempt);
        }
        if (reply.content() == null || reply.content().isBlank()) {
            return new TurnOutcome.Failed(FailureKind.INVALID, attempt);
        }
        JsonNode root;
        try {
            root = json.readTree(reply.content());
        } catch (JacksonException e) {
            return new TurnOutcome.Failed(FailureKind.INVALID, attempt);
        }
        if (root == null || !root.isObject()) {
            return new TurnOutcome.Failed(FailureKind.INVALID, attempt);
        }
        JsonNode text = root.get("reply");
        JsonNode intent = root.get("intent");
        if (text == null || !text.isString() || text.asString().isBlank()
                || text.asString().strip().length() > Message.MAX_CONTENT_LENGTH
                || intent == null || !intent.isString()) {
            return new TurnOutcome.Failed(FailureKind.INVALID, attempt);
        }
        return new TurnOutcome.Valid(new AssistantTurn(text.asString().strip(), Intent.parse(intent.asString())),
                attempt);
    }
}
