package com.clinicavet.assistant.conversation.application;

import com.clinicavet.assistant.tutor.domain.DataField;
import com.clinicavet.assistant.tutor.domain.Species;

import java.util.List;
import java.util.Objects;

/**
 * Parte do {@link AssistantTurn} v4 (Etapa 5) com os dados de tutor e pet que o modelo <b>diz</b> ter lido nesta
 * conversa ({@code extracted}), as confirmações do turno ({@code confirmations[]}) e os detalhes do pedido de
 * agendamento ({@code appointmentRequest}). Tudo é sugestão: o backend normaliza, valida, confere na conversa e
 * descarta o que não passar ({@code CustomerDataCollector}, ADR-0010 item 4). O modelo nunca grava.
 * <p>
 * Textos vazios chegam como {@code null}. Limites de quantidade aplicados aqui; os de conteúdo, no coletor.
 */
public record CustomerUpdate(String tutorName, List<PetMention> pets, List<Confirmation> confirmations,
                             AppointmentDraft appointment) {

    public static final int MAX_PETS = 3;
    public static final int MAX_CONFIRMATIONS = 10;
    public static final CustomerUpdate NONE = new CustomerUpdate(null, List.of(), List.of(), AppointmentDraft.EMPTY);

    public CustomerUpdate {
        pets = pets == null ? List.of() : pets.stream().filter(Objects::nonNull).limit(MAX_PETS).toList();
        confirmations = confirmations == null ? List.of()
                : confirmations.stream().filter(Objects::nonNull).distinct().limit(MAX_CONFIRMATIONS).toList();
        appointment = appointment == null ? AppointmentDraft.EMPTY : appointment;
    }

    /**
     * Pet citado pelo tutor.
     *
     * @param species {@code null} se o tutor não disse
     * @param newPet  o tutor disse que é um animal diferente dos cadastrados (DA-35: sem isso, um nome que não
     *                corresponde a nenhum pet cadastrado não cria pet novo quando o tutor já tem pets)
     */
    public record PetMention(String name, Species species, String speciesDescription, String breed, String age,
                             boolean newPet) {
    }

    /**
     * O tutor concordou, nesta mensagem, com um dado que o assistente repetiu na mensagem anterior (P-08
     * provisório). {@code petName} identifica o pet nos campos de pet; {@code null} para o nome do tutor.
     */
    public record Confirmation(DataField field, String petName) {

        public Confirmation {
            Objects.requireNonNull(field, "field");
        }
    }

    /** Detalhes do pedido de agendamento citados neste turno. Nunca data/hora confirmada (DA-07). */
    public record AppointmentDraft(String petName, String reason, String preferredPeriod) {

        public static final AppointmentDraft EMPTY = new AppointmentDraft(null, null, null);

        public boolean isEmpty() {
            return petName == null && reason == null && preferredPeriod == null;
        }
    }

    public boolean isEmpty() {
        return tutorName == null && pets.isEmpty() && confirmations.isEmpty() && appointment.isEmpty();
    }
}
