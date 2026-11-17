package com.clinicavet.assistant.conversation.application;

import com.clinicavet.assistant.conversation.domain.ClinicalPriority;
import com.clinicavet.assistant.conversation.domain.Intent;
import com.clinicavet.assistant.conversation.domain.Message;
import com.clinicavet.assistant.conversation.domain.TurnSignal;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Classifica a resposta bruta do modelo (ADR-0002, tabela de resultados). Só o resultado
 * {@link TurnOutcome.Valid válido} segue adiante. Valores fora das listas fechadas (intenção, prioridade,
 * sinal) são tolerados: intenção vira {@code OTHER}, prioridade vira "sem sugestão", sinal é ignorado.
 * Campo ausente ou de tipo errado é falha.
 * <p>
 * O {@code finishReason} chega do Spring AI como nome de enum em maiúsculas ({@code STOP},
 * {@code LENGTH}), não como o valor da API: a comparação ignora caixa (ADR-0016, achados).
 */
@Component
public class TurnResultClassifier {

    private final ObjectMapper json = JsonMapper.builder().build();

    public TurnOutcome classify(ModelReply reply, int attempt) {
        Optional<FailureKind> technical = technicalFailure(reply);
        if (technical.isPresent()) {
            return new TurnOutcome.Failed(technical.get(), attempt);
        }
        JsonNode root = parse(reply.content());
        if (root == null) {
            return new TurnOutcome.Failed(FailureKind.INVALID, attempt);
        }
        JsonNode text = root.get("reply");
        if (text == null || !text.isString() || text.asString().isBlank()
                || text.asString().strip().length() > Message.MAX_CONTENT_LENGTH
                || !isString(root, "intent")
                || !isArray(root, "secondaryIntents") || !isArray(root, "knowledgeRefs")
                || !isArray(root, "signals") || !isString(root, "priority")
                || !isBoolean(root, "knowledgeGap") || !isBoolean(root, "handoffRequested")
                || !isBoolean(root, "possibleEmergency") || !isBoolean(root, "clinicalUncertainty")) {
            return new TurnOutcome.Failed(FailureKind.INVALID, attempt);
        }
        List<String> refs = strings(root.get("knowledgeRefs"));
        List<String> secondary = strings(root.get("secondaryIntents"));
        List<String> signals = strings(root.get("signals"));
        if (refs == null || secondary == null || signals == null) {
            return new TurnOutcome.Failed(FailureKind.INVALID, attempt);
        }
        AssistantTurn turn = new AssistantTurn(
                text.asString().strip(),
                Intent.parse(root.get("intent").asString()),
                secondary.stream().map(Intent::parse).filter(i -> i != Intent.OTHER).toList(),
                refs.stream().map(String::strip).toList(),
                root.get("knowledgeGap").asBoolean(),
                root.get("handoffRequested").asBoolean(),
                root.get("possibleEmergency").asBoolean(),
                ClinicalPriority.parse(root.get("priority").asString()),
                modelSignals(signals),
                root.get("clinicalUncertainty").asBoolean());
        return new TurnOutcome.Valid(turn, attempt);
    }

    /** Modo restrito: só sinais. */
    public RestrictedOutcome classifyRestricted(ModelReply reply) {
        Optional<FailureKind> technical = technicalFailure(reply);
        if (technical.isPresent()) {
            return new RestrictedOutcome.Failed(technical.get());
        }
        JsonNode root = parse(reply.content());
        if (root == null || !isBoolean(root, "possibleEmergency") || !isBoolean(root, "explicitHumanRequest")
                || !isString(root, "priority") || !isArray(root, "signals")) {
            return new RestrictedOutcome.Failed(FailureKind.INVALID);
        }
        List<String> signals = strings(root.get("signals"));
        if (signals == null) {
            return new RestrictedOutcome.Failed(FailureKind.INVALID);
        }
        return new RestrictedOutcome.Valid(new RestrictedSignals(
                root.get("possibleEmergency").asBoolean(),
                root.get("explicitHumanRequest").asBoolean(),
                ClinicalPriority.parse(root.get("priority").asString()),
                modelSignals(signals)));
    }

    private static Optional<FailureKind> technicalFailure(ModelReply reply) {
        String finish = reply.finishReason() == null ? "" : reply.finishReason().strip().toLowerCase(Locale.ROOT);
        if ((reply.refusal() != null && !reply.refusal().isBlank()) || finish.equals("content_filter")) {
            return Optional.of(FailureKind.REFUSAL);
        }
        if (finish.equals("length")) {
            return Optional.of(FailureKind.TRUNCATED);
        }
        if (reply.content() == null || reply.content().isBlank()) {
            return Optional.of(FailureKind.INVALID);
        }
        return Optional.empty();
    }

    private JsonNode parse(String content) {
        try {
            JsonNode root = json.readTree(content);
            return root != null && root.isObject() ? root : null;
        } catch (JacksonException e) {
            return null;
        }
    }

    private static Set<TurnSignal> modelSignals(List<String> raw) {
        Set<TurnSignal> signals = EnumSet.noneOf(TurnSignal.class);
        raw.forEach(s -> TurnSignal.parse(s).filter(signal -> !signal.backendOnly()).ifPresent(signals::add));
        return signals;
    }

    private static List<String> strings(JsonNode array) {
        List<String> values = new ArrayList<>();
        for (JsonNode node : array) {
            if (!node.isString()) {
                return null;
            }
            values.add(node.asString());
        }
        return values;
    }

    private static boolean isString(JsonNode root, String field) {
        JsonNode node = root.get(field);
        return node != null && node.isString();
    }

    private static boolean isArray(JsonNode root, String field) {
        JsonNode node = root.get(field);
        return node != null && node.isArray();
    }

    private static boolean isBoolean(JsonNode root, String field) {
        JsonNode node = root.get(field);
        return node != null && node.isBoolean();
    }
}
