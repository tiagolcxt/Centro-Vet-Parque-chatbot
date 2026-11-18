package com.clinicavet.assistant.support;

import java.util.ArrayList;
import java.util.List;

/**
 * JSON do {@code AssistantTurn} v4 (Etapa 5), como o modelo devolveria, para os testes. Sem configurar nada:
 * resposta sem fontes, sem lacuna, sem sinais, prioridade P6, sem dados do tutor.
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
    private String tutorName = "";
    private final List<String> pets = new ArrayList<>();
    private final List<String> confirmations = new ArrayList<>();
    private String appointment = "{\"petName\":\"\",\"reason\":\"\",\"preferredPeriod\":\"\"}";

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

    /** Nome do tutor extraído. */
    public TurnJson tutorName(String name) {
        this.tutorName = name;
        return this;
    }

    /** Pet extraído; {@code species} DOG, CAT, OTHER ou UNKNOWN. */
    public TurnJson pet(String name, String species) {
        return pet(name, species, "", "", "", false);
    }

    public TurnJson pet(String name, String species, String speciesDescription, String breed, String age,
                        boolean newPet) {
        pets.add("{\"name\":\"" + escape(name) + "\",\"species\":\"" + species + "\",\"speciesDescription\":\""
                + escape(speciesDescription) + "\",\"breed\":\"" + escape(breed) + "\",\"age\":\"" + escape(age)
                + "\",\"newPet\":" + newPet + "}");
        return this;
    }

    /** Confirmação de um campo ({@code TUTOR_NAME}, {@code PET_NAME}...); {@code petName} vazio para o tutor. */
    public TurnJson confirm(String field, String petName) {
        confirmations.add("{\"field\":\"" + field + "\",\"petName\":\"" + escape(petName) + "\"}");
        return this;
    }

    public TurnJson appointment(String petName, String reason, String preferredPeriod) {
        this.appointment = "{\"petName\":\"" + escape(petName) + "\",\"reason\":\"" + escape(reason)
                + "\",\"preferredPeriod\":\"" + escape(preferredPeriod) + "\"}";
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
                + ",\"clinicalUncertainty\":" + uncertainty
                + ",\"extracted\":{\"tutorName\":\"" + escape(tutorName) + "\",\"pets\":[" + String.join(",", pets) + "]}"
                + ",\"confirmations\":[" + String.join(",", confirmations) + "]"
                + ",\"appointmentRequest\":" + appointment + "}";
    }

    private static String array(List<String> values) {
        return "[" + String.join(",", values.stream().map(v -> "\"" + escape(v) + "\"").toList()) + "]";
    }

    private static String escape(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
