package com.clinicavet.assistant.conversation.application;

import com.clinicavet.assistant.shared.domain.ConversationId;
import com.clinicavet.assistant.servicerequest.domain.RequestField;
import com.clinicavet.assistant.servicerequest.domain.ServiceRequest;
import com.clinicavet.assistant.tutor.domain.DataField;
import com.clinicavet.assistant.tutor.domain.Pet;
import com.clinicavet.assistant.tutor.domain.ProvenanceStatus;
import com.clinicavet.assistant.tutor.domain.Tutor;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * O que falta para registrar uma solicitação de agendamento. <b>Provisório (P-13 e P-08)</b>, só para dados
 * fictícios:
 * <ul>
 *   <li>obrigatórios: pet (nome e espécie), motivo e nome do tutor; a preferência de dia ou período é opcional;</li>
 *   <li>nome e espécie do pet <b>confirmados nesta conversa</b> (o assistente repete, o tutor concorda);</li>
 *   <li>nome do tutor dito nesta conversa ou CADASTRADO dentro da validade; A_RECONFIRMAR não basta.</li>
 * </ul>
 */
final class AppointmentRequirements {

    /** Campos que o {@code submit()} confere no agregado. */
    static final Set<RequestField> REQUIRED = EnumSet.of(RequestField.SUMMARY, RequestField.TUTOR, RequestField.PET,
            RequestField.REASON);

    enum Gap {
        PET("qual é o pet (nome)"),
        PET_SPECIES("espécie do pet"),
        REASON("motivo do atendimento"),
        TUTOR_NAME("nome do tutor"),
        PET_CONFIRMATION("confirmação do nome e da espécie do pet pelo tutor (repita e pergunte se está certo)");

        private final String label;

        Gap(String label) {
            this.label = label;
        }

        String label() {
            return label;
        }
    }

    private AppointmentRequirements() {
    }

    static List<Gap> missing(ServiceRequest request, CustomerRecords records, ConversationId conversation,
                             Instant now, Duration validity) {
        List<Gap> gaps = new ArrayList<>();
        Optional<Pet> pet = request.petId().flatMap(records::pet);
        if (pet.isEmpty()) {
            gaps.add(Gap.PET);
        } else if (pet.get().species().isEmpty()) {
            gaps.add(Gap.PET_SPECIES);
        }
        if (request.reason().isEmpty()) {
            gaps.add(Gap.REASON);
        }
        Optional<Tutor> tutor = records.tutor();
        boolean tutorNameOk = tutor.flatMap(Tutor::name).isPresent()
                && records.status(tutor.get().id().value(), DataField.TUTOR_NAME, conversation, now, validity)
                .map(s -> s != ProvenanceStatus.TO_RECONFIRM).orElse(false);
        if (!tutorNameOk) {
            gaps.add(Gap.TUTOR_NAME);
        }
        if (pet.isPresent() && pet.get().species().isPresent()) {
            boolean confirmed = confirmed(records, pet.get(), DataField.PET_NAME, conversation, now, validity)
                    && confirmed(records, pet.get(), DataField.PET_SPECIES, conversation, now, validity);
            if (!confirmed) {
                gaps.add(Gap.PET_CONFIRMATION);
            }
        }
        return gaps;
    }

    private static boolean confirmed(CustomerRecords records, Pet pet, DataField field, ConversationId conversation,
                                     Instant now, Duration validity) {
        return records.status(pet.id().value(), field, conversation, now, validity)
                .map(s -> s == ProvenanceStatus.CONFIRMED).orElse(false);
    }
}
