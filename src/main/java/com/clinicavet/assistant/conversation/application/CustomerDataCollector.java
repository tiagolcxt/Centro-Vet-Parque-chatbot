package com.clinicavet.assistant.conversation.application;

import com.clinicavet.assistant.conversation.domain.Conversation;
import com.clinicavet.assistant.conversation.domain.ConversationId;
import com.clinicavet.assistant.conversation.domain.Intent;
import com.clinicavet.assistant.conversation.domain.Message;
import com.clinicavet.assistant.conversation.domain.MessageAuthor;
import com.clinicavet.assistant.conversation.domain.TurnSignal;
import com.clinicavet.assistant.servicerequest.domain.RequestStatus;
import com.clinicavet.assistant.servicerequest.domain.RequestType;
import com.clinicavet.assistant.servicerequest.domain.ServiceRequest;
import com.clinicavet.assistant.servicerequest.domain.ServiceRequestRepository;
import com.clinicavet.assistant.shared.exception.DomainValidationException;
import com.clinicavet.assistant.tutor.domain.DataField;
import com.clinicavet.assistant.tutor.domain.NameKey;
import com.clinicavet.assistant.tutor.domain.Pet;
import com.clinicavet.assistant.tutor.domain.PetMatcher;
import com.clinicavet.assistant.tutor.domain.PetRepository;
import com.clinicavet.assistant.tutor.domain.Provenance;
import com.clinicavet.assistant.tutor.domain.ProvenanceRepository;
import com.clinicavet.assistant.tutor.domain.Species;
import com.clinicavet.assistant.tutor.domain.Tutor;
import com.clinicavet.assistant.tutor.domain.TutorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Aplica os dados de tutor e pet de um turno válido (Etapa 5), dentro da transação 2 do turno (ADR-0017).
 * <ol>
 *   <li><b>Normaliza e confere</b> o que o modelo extraiu ({@link CustomerDataNormalizer}); o resto é descartado.</li>
 *   <li><b>Tutor</b> (DA-36): só é criado quando há um dado aceito para guardar; a conversa é vinculada a ele. O
 *       vínculo vem do contato do canal, nunca de texto digitado.</li>
 *   <li><b>Pets</b> (DA-35): correspondência só entre os pets desse tutor. Sem correspondência, cria pet novo só se
 *       o tutor ainda não tem pets ou disse que é outro animal ({@code newPet}); ambiguidade e conflito de espécie
 *       não vinculam nada (o assistente pergunta).</li>
 *   <li><b>Proveniência</b> por campo: cada dado aceito fica MENCIONADO nesta conversa.</li>
 *   <li><b>Confirmações</b> (P-08 provisório): só valem se a mensagem anterior do assistente repetiu o valor
 *       gravado e o tutor não discordou. A confirmação marcada pelo modelo sozinha não basta.</li>
 *   <li><b>Agendamento</b> (P-06/P-07/P-13 provisórios): no máximo uma solicitação de agendamento em coleta por
 *       conversa, um pet por solicitação; com tudo presente e confirmado, passa a {@code PENDING}.</li>
 * </ol>
 * Um item inválido é ignorado e registrado no log só pelo motivo, sem conteúdo (DM-12).
 */
@Component
class CustomerDataCollector {

    private static final Logger log = LoggerFactory.getLogger(CustomerDataCollector.class);

    /** @param appointmentSubmitted a solicitação de agendamento passou a {@code PENDING} neste turno */
    record Outcome(boolean appointmentSubmitted) {
        static final Outcome NONE = new Outcome(false);
    }

    private record AcceptedPet(String name, Species species, String speciesDescription, String breed, String age,
                               boolean newPet) {
    }

    private final TutorRepository tutors;
    private final PetRepository pets;
    private final ProvenanceRepository provenance;
    private final ServiceRequestRepository serviceRequests;
    private final CustomerDataSettings settings;

    CustomerDataCollector(TutorRepository tutors, PetRepository pets, ProvenanceRepository provenance,
                          ServiceRequestRepository serviceRequests, CustomerDataSettings settings) {
        this.tutors = tutors;
        this.pets = pets;
        this.provenance = provenance;
        this.serviceRequests = serviceRequests;
        this.settings = settings;
    }

    /**
     * @param recent    janela da conversa em ordem cronológica, incluindo {@code current}
     * @param handedOff o turno encaminhou a conversa: os dados são guardados, mas a coleta de agendamento para
     */
    Outcome collect(Conversation conversation, AssistantTurn turn, List<Message> recent, Message current,
                    boolean handedOff, Instant now) {
        ConversationId conversationId = conversation.id();
        List<Message> thisConversation = recent.stream()
                .filter(m -> m.conversationId().equals(conversationId)).toList();
        List<String> customerTexts = thisConversation.stream()
                .filter(m -> m.author() == MessageAuthor.CUSTOMER).map(Message::content).toList();
        String previousAssistant = previousAssistant(thisConversation, current);
        CustomerUpdate update = turn.customer();
        CustomerRecords records = conversation.tutorId()
                .map(id -> CustomerRecords.load(id, tutors, pets, provenance))
                .orElseGet(CustomerRecords::empty);

        String tutorName = acceptTutorName(update.tutorName(), customerTexts, records);
        List<AcceptedPet> accepted = acceptPets(withAppointmentPet(update), customerTexts, records);
        if (tutorName != null || !accepted.isEmpty()) {
            records = ensureTutor(conversation, records, now);
        }
        Optional<Tutor> tutor = records.tutor();
        if (tutorName != null && tutor.isPresent()) {
            applyTutorName(tutor.get(), tutorName, records, conversationId, now);
        }
        List<Pet> touched = new ArrayList<>();
        if (tutor.isPresent()) {
            for (AcceptedPet pet : accepted) {
                applyPet(tutor.get(), pet, records, conversationId, now).ifPresent(touched::add);
            }
        }

        List<ServiceRequest> requests = serviceRequests.findByConversation(conversationId);
        Optional<ServiceRequest> collecting = requests.stream()
                .filter(r -> r.type() == RequestType.APPOINTMENT && r.status() == RequestStatus.COLLECTING)
                .findFirst();
        if (previousAssistant != null && !CustomerDataNormalizer.disagrees(current.content())) {
            for (CustomerUpdate.Confirmation confirmation : update.confirmations()) {
                confirm(confirmation, previousAssistant, records, collecting, touched, conversationId, now);
            }
        }
        if (handedOff) {
            return Outcome.NONE;
        }
        return appointment(conversation, turn, update.appointment(), customerTexts, thisConversation, records, requests,
                collecting, touched, now);
    }

    /**
     * O modelo às vezes cita o pet só no pedido ({@code appointmentRequest.petName}) e não em {@code extracted}. O
     * nome passa pelas mesmas regras, como um pet citado sem espécie (CT-19).
     */
    private static List<CustomerUpdate.PetMention> withAppointmentPet(CustomerUpdate update) {
        String draftName = update.appointment().petName();
        if (draftName == null || update.pets().stream().anyMatch(p -> NameKey.same(p.name(), draftName))) {
            return update.pets();
        }
        List<CustomerUpdate.PetMention> pets = new ArrayList<>(update.pets());
        pets.add(new CustomerUpdate.PetMention(draftName, null, null, null, null, false));
        return pets;
    }

    // ---- tutor ----

    private static String acceptTutorName(String raw, List<String> customerTexts, CustomerRecords records) {
        String name = CustomerDataNormalizer.tutorName(raw);
        if (name == null) {
            return null;
        }
        boolean known = records.tutor().flatMap(Tutor::name).map(n -> NameKey.same(n, name)).orElse(false);
        if (!known && !CustomerDataNormalizer.appearsIn(name, customerTexts)) {
            log.info("Dado descartado [campo=TUTOR_NAME, motivo=nao_dito_pelo_tutor]");
            return null;
        }
        return name;
    }

    /** DA-36: cria o tutor (ou reaproveita o do contato) e vincula a conversa. */
    private CustomerRecords ensureTutor(Conversation conversation, CustomerRecords records, Instant now) {
        if (records.tutor().isPresent()) {
            return records;
        }
        Optional<Tutor> existing = tutors.findByContact(conversation.contact());
        CustomerRecords loaded;
        if (existing.isPresent()) {
            loaded = CustomerRecords.load(existing.get().id(), tutors, pets, provenance);
        } else {
            loaded = CustomerRecords.empty();
            loaded.setTutor(tutors.save(Tutor.register(conversation.contact(), now)));
        }
        Tutor tutor = loaded.tutor().orElseThrow();
        conversation.linkTutor(tutor.id());
        // Solicitações já abertas nesta conversa (informação, contato humano) passam a apontar para o tutor.
        for (ServiceRequest request : serviceRequests.findByConversation(conversation.id())) {
            if (!request.status().isFinal() && request.tutorId().isEmpty()) {
                request.attachTutor(tutor.id(), now);
                serviceRequests.save(request);
            }
        }
        return loaded;
    }

    private void applyTutorName(Tutor tutor, String name, CustomerRecords records, ConversationId conversation,
                                Instant now) {
        boolean changed = tutor.name().map(n -> !NameKey.same(n, name)).orElse(true);
        if (changed) {
            tutor.updateName(name);
        }
        tutor.markSeen(now);
        records.setTutor(tutors.save(tutor));
        mention(records, DataField.TUTOR_NAME, tutor.id().value(), changed, conversation, now);
    }

    // ---- pets ----

    private static List<AcceptedPet> acceptPets(List<CustomerUpdate.PetMention> mentions, List<String> customerTexts,
                                                CustomerRecords records) {
        List<AcceptedPet> accepted = new ArrayList<>();
        for (CustomerUpdate.PetMention mention : mentions) {
            String name = CustomerDataNormalizer.petName(mention.name());
            if (name == null) {
                log.info("Dado descartado [campo=PET_NAME, motivo=formato]");
                continue;
            }
            boolean known = records.pets().stream().anyMatch(p -> NameKey.same(p.name(), name));
            if (!known && !CustomerDataNormalizer.appearsIn(name, customerTexts)) {
                log.info("Dado descartado [campo=PET_NAME, motivo=nao_dito_pelo_tutor]");
                continue;
            }
            Species species = mention.species();
            String description = species == Species.OTHER
                    ? CustomerDataNormalizer.freeText(mention.speciesDescription(), Pet.MAX_TEXT_LENGTH) : null;
            if (species == Species.OTHER && description == null) {
                species = null;   // "outro" sem dizer qual: espécie fica em aberto
            }
            accepted.add(new AcceptedPet(name, species, description,
                    CustomerDataNormalizer.freeText(mention.breed(), Pet.MAX_TEXT_LENGTH),
                    CustomerDataNormalizer.freeText(mention.age(), Pet.MAX_TEXT_LENGTH), mention.newPet()));
        }
        return accepted;
    }

    private Optional<Pet> applyPet(Tutor tutor, AcceptedPet mention, CustomerRecords records,
                                   ConversationId conversation, Instant now) {
        PetMatcher.Result match = PetMatcher.match(records.pets(), mention.name(), mention.species());
        Optional<Pet> correction = match.kind() == PetMatcher.Result.Kind.SPECIES_CONFLICT
                ? speciesCorrection(mention.name(), records, conversation, now) : Optional.empty();
        if (correction.isPresent()) {
            return correctSpecies(correction.get(), mention, records, conversation, now);
        }
        boolean create = switch (match.kind()) {
            case FOUND -> false;
            case NONE -> records.pets().isEmpty() || mention.newPet();
            case AMBIGUOUS, SPECIES_CONFLICT -> mention.newPet();
        };
        if (match.kind() != PetMatcher.Result.Kind.FOUND && !create) {
            log.info("Pet não vinculado [motivo={}]", match.kind());
            return Optional.empty();
        }
        try {
            Pet pet = create
                    ? Pet.register(tutor.id(), mention.name(), mention.species(), mention.speciesDescription(), now)
                    : match.pet().orElseThrow();
            boolean speciesChanged = create;
            if (!create && mention.species() != null && pet.species().isEmpty()) {
                pet.changeSpecies(mention.species(), mention.speciesDescription());
                speciesChanged = true;
            }
            boolean breedChanged = mention.breed() != null
                    && pet.breed().map(b -> !NameKey.same(b, mention.breed())).orElse(true);
            if (breedChanged) {
                pet.updateBreed(mention.breed());
            }
            boolean ageChanged = mention.age() != null
                    && pet.ageDescription().map(a -> !NameKey.same(a, mention.age())).orElse(true);
            if (ageChanged) {
                pet.updateAgeDescription(mention.age());
            }
            Pet saved = pets.save(pet);
            records.putPet(saved);
            UUID id = saved.id().value();
            mention(records, DataField.PET_NAME, id, create, conversation, now);
            if (mention.species() != null) {
                mention(records, DataField.PET_SPECIES, id, speciesChanged, conversation, now);
            }
            if (mention.breed() != null) {
                mention(records, DataField.PET_BREED, id, breedChanged, conversation, now);
            }
            if (mention.age() != null) {
                mention(records, DataField.PET_AGE, id, ageChanged, conversation, now);
            }
            return Optional.of(saved);
        } catch (DomainValidationException e) {
            log.info("Dado descartado [campo=PET, motivo=validacao:{}]", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Espécie divergente de um pet cujo dado de espécie foi só MENCIONADO nesta mesma conversa: é o tutor corrigindo
     * o que acabou de dizer ("na verdade ela é cachorro"), não outro animal. Dado de outra conversa nunca é trocado
     * assim (DA-35): aí é conflito, e o assistente pergunta.
     */
    private Optional<Pet> speciesCorrection(String name, CustomerRecords records, ConversationId conversation,
                                            Instant now) {
        List<Pet> sameName = records.pets().stream().filter(p -> NameKey.same(p.name(), name)).toList();
        if (sameName.size() != 1) {
            return Optional.empty();
        }
        Pet pet = sameName.getFirst();
        boolean mentionedHere = records.status(pet.id().value(), DataField.PET_SPECIES, conversation, now,
                settings.registeredValidity()).map(s -> s == com.clinicavet.assistant.tutor.domain.ProvenanceStatus.MENTIONED)
                .orElse(false);
        return mentionedHere ? Optional.of(pet) : Optional.empty();
    }

    private Optional<Pet> correctSpecies(Pet pet, AcceptedPet mention, CustomerRecords records,
                                         ConversationId conversation, Instant now) {
        try {
            pet.changeSpecies(mention.species(), mention.speciesDescription());
            Pet saved = pets.save(pet);
            records.putPet(saved);
            mention(records, DataField.PET_NAME, saved.id().value(), false, conversation, now);
            mention(records, DataField.PET_SPECIES, saved.id().value(), true, conversation, now);
            return Optional.of(saved);
        } catch (DomainValidationException e) {
            log.info("Dado descartado [campo=PET_SPECIES, motivo=validacao:{}]", e.getMessage());
            return Optional.empty();
        }
    }

    private void mention(CustomerRecords records, DataField field, UUID subjectId, boolean changed,
                         ConversationId conversation, Instant now) {
        Optional<Provenance> existing = records.provenance(subjectId, field);
        Provenance value;
        if (existing.isEmpty()) {
            value = Provenance.mentioned(field, subjectId, conversation, now);
        } else {
            value = existing.get();
            value.mention(conversation, changed, now);
        }
        records.put(provenance.save(value));
    }

    // ---- confirmações (P-08 provisório) ----

    private void confirm(CustomerUpdate.Confirmation confirmation, String previousAssistant, CustomerRecords records,
                         Optional<ServiceRequest> collecting, List<Pet> touched, ConversationId conversation,
                         Instant now) {
        UUID subject;
        boolean repeated;
        if (confirmation.field() == DataField.TUTOR_NAME) {
            Optional<Tutor> tutor = records.tutor();
            Optional<String> name = tutor.flatMap(Tutor::name);
            if (name.isEmpty()) {
                return;
            }
            subject = tutor.get().id().value();
            repeated = CustomerDataNormalizer.appearsIn(name.get(), List.of(previousAssistant));
        } else {
            Optional<Pet> pet = petFor(confirmation.petName(), records, collecting, touched);
            if (pet.isEmpty()) {
                return;
            }
            subject = pet.get().id().value();
            repeated = switch (confirmation.field()) {
                case PET_NAME -> CustomerDataNormalizer.appearsIn(pet.get().name(), List.of(previousAssistant));
                case PET_SPECIES -> pet.get().species()
                        .map(s -> CustomerDataNormalizer.speciesAppearsIn(s, pet.get().speciesDescription().orElse(null),
                                previousAssistant))
                        .orElse(false);
                case PET_BREED -> pet.get().breed()
                        .map(b -> CustomerDataNormalizer.appearsIn(b, List.of(previousAssistant))).orElse(false);
                case PET_AGE -> pet.get().ageDescription()
                        .map(a -> CustomerDataNormalizer.appearsIn(a, List.of(previousAssistant))).orElse(false);
                case TUTOR_NAME -> false;
            };
        }
        Optional<Provenance> existing = records.provenance(subject, confirmation.field());
        if (!repeated || existing.isEmpty()) {
            log.info("Confirmação ignorada [campo={}, motivo={}]", confirmation.field(),
                    repeated ? "sem_registro" : "valor_nao_repetido_pelo_assistente");
            return;
        }
        Provenance value = existing.get();
        value.confirm(conversation, now);
        records.put(provenance.save(value));
    }

    /** Pet da confirmação: pelo nome; senão o da solicitação em coleta; senão o único citado neste turno. */
    private static Optional<Pet> petFor(String petName, CustomerRecords records, Optional<ServiceRequest> collecting,
                                        List<Pet> touched) {
        if (petName != null) {
            PetMatcher.Result match = PetMatcher.match(records.pets(), petName, null);
            return match.pet();
        }
        Optional<Pet> fromRequest = collecting.flatMap(ServiceRequest::petId).flatMap(records::pet);
        if (fromRequest.isPresent()) {
            return fromRequest;
        }
        return touched.size() == 1 ? Optional.of(touched.getFirst()) : Optional.empty();
    }

    // ---- agendamento (P-06, P-07, P-13 provisórios) ----

    private Outcome appointment(Conversation conversation, AssistantTurn turn, CustomerUpdate.AppointmentDraft draft,
                                List<String> customerTexts, List<Message> thisConversation, CustomerRecords records,
                                List<ServiceRequest> requests,
                                Optional<ServiceRequest> collecting, List<Pet> touched, Instant now) {
        String reason = CustomerDataNormalizer.freeText(draft.reason(), ServiceRequest.MAX_REASON_LENGTH);
        String period = CustomerDataNormalizer.freeText(draft.preferredPeriod(), ServiceRequest.MAX_PERIOD_LENGTH);
        Optional<Pet> pet = draftPet(draft.petName(), customerTexts, records, touched);
        boolean wantsAppointment = turn.allIntents().contains(Intent.APPOINTMENT_REQUEST);
        ServiceRequest request;
        if (collecting.isPresent()) {
            request = collecting.get();
        } else {
            if (!wantsAppointment || (reason == null && period == null && pet.isEmpty())) {
                return Outcome.NONE;
            }
            if (sensitive(turn, thisConversation)) {
                // CT-19: em assunto sensível o assistente acolhe e oferece a equipe; não abre coleta por conta própria.
                log.info("Pedido de agendamento não aberto [motivo=assunto_sensivel]");
                return Outcome.NONE;
            }
            boolean alreadyRegistered = pet.isPresent() && requests.stream()
                    .anyMatch(r -> r.type() == RequestType.APPOINTMENT && !r.status().isFinal()
                            && r.petId().map(id -> id.equals(pet.get().id())).orElse(false));
            if (alreadyRegistered) {
                return Outcome.NONE;   // mesmo pet já com solicitação registrada nesta conversa
            }
            request = ServiceRequest.open(conversation.id(), RequestType.APPOINTMENT, now);
        }
        try {
            records.tutor().filter(t -> !request.tutorId().map(id -> id.equals(t.id())).orElse(false))
                    .ifPresent(t -> request.attachTutor(t.id(), now));
            pet.filter(p -> !request.petId().map(id -> id.equals(p.id())).orElse(false))
                    .ifPresent(p -> request.attachPet(p.id(), now));
            request.describeReason(reason, now);
            request.describePreferredPeriod(period, now);
            request.describe(summary(request, records), now);
            List<AppointmentRequirements.Gap> missing = AppointmentRequirements.missing(request, records,
                    conversation.id(), now, settings.registeredValidity());
            boolean submitted = false;
            if (missing.isEmpty()) {
                request.submit(AppointmentRequirements.REQUIRED, now);
                submitted = true;
            }
            serviceRequests.save(request);
            log.info("Solicitação de agendamento [status={}, faltando={}]", request.status(), missing);
            return new Outcome(submitted);
        } catch (DomainValidationException e) {
            log.info("Detalhe de agendamento descartado [motivo=validacao:{}]", e.getMessage());
            serviceRequests.save(request);
            return Outcome.NONE;
        }
    }

    /** Assunto sensível neste turno ou em resposta anterior do assistente nesta conversa (sinal gravado na mensagem). */
    private static boolean sensitive(AssistantTurn turn, List<Message> thisConversation) {
        return turn.signals().contains(TurnSignal.SENSITIVE_TOPIC) || thisConversation.stream()
                .anyMatch(m -> m.author() == MessageAuthor.ASSISTANT
                        && m.metadata().signals().contains(TurnSignal.SENSITIVE_TOPIC));
    }

    /** Pet do pedido: o nome citado (se corresponde a um pet do tutor); senão o único pet citado neste turno. */
    private static Optional<Pet> draftPet(String rawName, List<String> customerTexts, CustomerRecords records,
                                          List<Pet> touched) {
        String name = CustomerDataNormalizer.petName(rawName);
        if (name != null) {
            PetMatcher.Result match = PetMatcher.match(records.pets(), name, null);
            if (match.kind() == PetMatcher.Result.Kind.FOUND) {
                return match.pet();
            }
            return Optional.empty();
        }
        return touched.size() == 1 ? Optional.of(touched.getFirst()) : Optional.empty();
    }

    /** Resumo para a equipe, montado pelo backend a partir dos campos aceitos. */
    static String summary(ServiceRequest request, CustomerRecords records) {
        List<String> parts = new ArrayList<>();
        request.petId().flatMap(records::pet).ifPresent(p -> parts.add("pet: " + p.name()
                + p.species().map(s -> " (" + speciesLabel(s, p.speciesDescription().orElse(null)) + ")").orElse("")));
        request.reason().ifPresent(r -> parts.add("motivo: " + r));
        request.preferredPeriod().ifPresent(p -> parts.add("preferência: " + p));
        String text = "Pedido de agendamento" + (parts.isEmpty() ? "" : " — " + String.join("; ", parts));
        int max = ServiceRequest.MAX_SUMMARY_LENGTH;
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }

    static String speciesLabel(Species species, String description) {
        return switch (species) {
            case DOG -> "cachorro";
            case CAT -> "gato";
            case OTHER -> Objects.requireNonNullElse(description, "outra espécie");
        };
    }

    private static String previousAssistant(List<Message> thisConversation, Message current) {
        String last = null;
        for (Message message : thisConversation) {
            if (message.id().equals(current.id())) {
                break;
            }
            if (message.author() == MessageAuthor.ASSISTANT) {
                last = message.content();
            }
        }
        return last;
    }
}
