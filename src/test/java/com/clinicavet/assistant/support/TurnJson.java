package com.clinicavet.assistant.support;

import java.util.ArrayList;
import java.util.List;

/**
 * JSON do {@code AssistantTurn} v3 (Etapa 4), como o modelo devolveria, para os testes. Sem configurar nada:
 * resposta sem fontes, sem lacuna, sem sinais, prioridade P6.
 */
public final class TurnJson {

    private final String reply;
    private final String intent;
    private final List<String> secondaryIntents = new ArrayList<>();
    private final List<String> refs = new ArrayList<>();
    private final List<String> signals = new ArrayList<>();
    private boolean gap;
    private boolean handoff;
    private boolean emergency;
    private boolean uncertainty;
    private String priority = "P6_RELATIONSHIP";

    private TurnJson(String reply, String intent) {
        this.reply = reply;
        this.intent = intent;
    }

    public static TurnJson of(String reply, String intent) {
        return new TurnJson(reply, intent);
    }

    public TurnJson refs(String... keys) {
        refs.addAll(List.of(keys));
        return this;
    }

    public TurnJson gap() {
        this.gap = true;
        return this;
    }

    public TurnJson handoff() {
        this.handoff = true;
        return this;
    }

    public TurnJson emergency() {
        this.emergency = true;
        return this;
    }

    public TurnJson uncertainty() {
        this.uncertainty = true;
        return this;
    }

    public TurnJson priority(String level) {
        this.priority = level;
        return this;
    }

    public TurnJson secondary(String... intents) {
        secondaryIntents.addAll(List.of(intents));
        return this;
    }

    public TurnJson signals(String... values) {
        signals.addAll(List.of(values));
        return this;
    }

    public String json() {
        return "{\"reply\":\"" + escape(reply) + "\",\"intent\":\"" + intent + "\""
                + ",\"secondaryIntents\":" + array(secondaryIntents)
                + ",\"knowledgeRefs\":" + array(refs)
                + ",\"knowledgeGap\":" + gap
                + ",\"handoffRequested\":" + handoff
                + ",\"possibleEmergency\":" + emergency
                + ",\"priority\":\"" + priority + "\""
                + ",\"signals\":" + array(signals)
                + ",\"clinicalUncertainty\":" + uncertainty + "}";
    }

    /** JSON do modo restrito ({@code WAITING_HUMAN}). */
    public static String restricted(boolean emergency, boolean humanRequest, String priority, String... signals) {
        return "{\"possibleEmergency\":" + emergency + ",\"explicitHumanRequest\":" + humanRequest
                + ",\"priority\":\"" + priority + "\",\"signals\":" + array(List.of(signals)) + "}";
    }

    private static String array(List<String> values) {
        return "[" + String.join(",", values.stream().map(v -> "\"" + escape(v) + "\"").toList()) + "]";
    }

    private static String escape(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
