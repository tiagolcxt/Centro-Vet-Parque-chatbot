package com.clinicavet.assistant.conversation.application;

import com.clinicavet.assistant.servicerequest.domain.RequestStatus;
import com.clinicavet.assistant.servicerequest.domain.RequestType;
import com.clinicavet.assistant.servicerequest.domain.ServiceRequest;
import com.clinicavet.assistant.shared.domain.ConversationId;
import com.clinicavet.assistant.conversation.application.rules.RuleText;
import com.clinicavet.assistant.tutor.domain.DataField;
import com.clinicavet.assistant.tutor.domain.Pet;
import com.clinicavet.assistant.tutor.domain.Provenance;
import com.clinicavet.assistant.tutor.domain.ProvenanceStatus;
import com.clinicavet.assistant.tutor.domain.Species;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Confirmação tácita do pet do agendamento (achado A1 da simulação de 1 mês; P-08 provisório).
 * <p>
 * O assistente pergunta <b>uma vez</b> ("Só para confirmar: é para a Mel, uma gata, certo?"). Se o tutor responde
 * sem corrigir (manda o período, o nome dele ou outro dado), o nome e a espécie do pet da solicitação em coleta
 * passam a CONFIRMADOS. Sem isso o assistente repetia a pergunta até o tutor escrever "sim", e o pedido nunca
 * chegava à equipe.
 * <p>
 * Vale só quando:
 * <ul>
 *   <li>há solicitação de agendamento em coleta com pet e espécie;</li>
 *   <li>a mensagem anterior do assistente é uma pergunta e repetiu o nome e a espécie gravados;</li>
 *   <li>o tutor não discordou nem corrigiu, e não citou outra espécie.</li>
 * </ul>
 * A equipe confirma todo agendamento com o tutor ({@code policies.scheduling}), então um engano aqui ainda é
 * visto por uma pessoa antes de virar horário.
 */
final class TacitConfirmation {

    /** Expressões de correção que {@link CustomerDataNormalizer#disagrees} não cobre. */
    private static final List<String> CORRECTIONS = List.of("na verdade", "engano", "enganei", "corrigindo",
            "corrigir", "nao e", "nao eh");

    private TacitConfirmation() {
    }

    /**
     * Confirma em memória ({@code records}) o nome e a espécie do pet da solicitação em coleta, quando a regra vale.
     *
     * @return os registros de proveniência alterados (vazio se a regra não vale ou já estavam confirmados); quem
     * chama decide se grava
     */
    static List<Provenance> apply(CustomerRecords records, Optional<ServiceRequest> collecting,
                                  String previousAssistant, String customerText, ConversationId conversation,
                                  Instant now, java.time.Duration validity) {
        Optional<Pet> pet = collecting
                .filter(r -> r.type() == RequestType.APPOINTMENT && r.status() == RequestStatus.COLLECTING)
                .flatMap(ServiceRequest::petId).flatMap(records::pet);
        if (pet.isEmpty() || !applies(pet.get(), previousAssistant, customerText)) {
            return List.of();
        }
        List<Provenance> changed = new ArrayList<>();
        for (DataField field : List.of(DataField.PET_NAME, DataField.PET_SPECIES)) {
            Optional<Provenance> value = records.provenance(pet.get().id().value(), field);
            boolean alreadyConfirmed = records.status(pet.get().id().value(), field, conversation, now, validity)
                    .map(s -> s == ProvenanceStatus.CONFIRMED).orElse(false);
            if (value.isPresent() && !alreadyConfirmed) {
                value.get().confirm(conversation, now);
                changed.add(value.get());
            }
        }
        return changed;
    }

    static boolean applies(Pet pet, String previousAssistant, String customerText) {
        if (previousAssistant == null || customerText == null || pet.species().isEmpty()
                || !previousAssistant.contains("?")) {
            return false;
        }
        Species species = pet.species().get();
        String description = pet.speciesDescription().orElse(null);
        boolean repeated = CustomerDataNormalizer.appearsIn(pet.name(), List.of(previousAssistant))
                && CustomerDataNormalizer.speciesAppearsIn(species, description, previousAssistant);
        if (!repeated || CustomerDataNormalizer.disagrees(customerText)) {
            return false;
        }
        String normalized = " " + RuleText.normalize(customerText) + " ";
        if (CORRECTIONS.stream().anyMatch(c -> normalized.contains(" " + c + " "))) {
            return false;
        }
        for (Species other : List.of(Species.DOG, Species.CAT)) {
            if (other != species && CustomerDataNormalizer.speciesAppearsIn(other, null, customerText)) {
                return false;   // citou outra espécie: pode ser correção ou outro pet; o assistente pergunta
            }
        }
        return true;
    }
}
