package com.clinicavet.assistant.servicerequest.infrastructure.persistence;

import com.clinicavet.assistant.conversation.domain.Conversation;
import com.clinicavet.assistant.conversation.domain.ConversationRepository;
import com.clinicavet.assistant.servicerequest.domain.RequestField;
import com.clinicavet.assistant.servicerequest.domain.RequestStatus;
import com.clinicavet.assistant.servicerequest.domain.RequestType;
import com.clinicavet.assistant.servicerequest.domain.ServiceRequest;
import com.clinicavet.assistant.servicerequest.domain.ServiceRequestRepository;
import com.clinicavet.assistant.support.IntegrationTest;
import com.clinicavet.assistant.tutor.domain.Pet;
import com.clinicavet.assistant.tutor.domain.PetRepository;
import com.clinicavet.assistant.tutor.domain.Species;
import com.clinicavet.assistant.tutor.domain.Tutor;
import com.clinicavet.assistant.tutor.domain.TutorRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.util.EnumSet;
import java.util.Set;

import static com.clinicavet.assistant.support.Fixtures.T0;
import static com.clinicavet.assistant.support.Fixtures.newContact;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@IntegrationTest
class ServiceRequestPersistenceIT {

    @Autowired
    ServiceRequestRepository requests;

    @Autowired
    ConversationRepository conversations;

    @Autowired
    TutorRepository tutors;

    @Autowired
    PetRepository pets;

    @Test
    void roundTrip_withTutorPetAndLifecycle() {
        var contact = newContact();
        Tutor tutor = tutors.save(Tutor.register(contact, T0));
        Pet pet = pets.save(Pet.register(tutor.id(), "Mel", Species.DOG, null, T0));
        Conversation conversation = conversations.save(Conversation.start(contact, T0));

        ServiceRequest request = ServiceRequest.open(conversation.id(), RequestType.APPOINTMENT, T0);
        request.describe("Consulta de rotina (fictícia)", T0.plusSeconds(1));
        request.attachTutor(tutor.id(), T0.plusSeconds(1));
        request.attachPet(pet.id(), T0.plusSeconds(1));
        request.submit(EnumSet.of(RequestField.SUMMARY, RequestField.PET), T0.plusSeconds(2));
        requests.save(request);

        ServiceRequest loaded = requests.findById(request.id()).orElseThrow();

        assertThat(loaded.status()).isEqualTo(RequestStatus.PENDING);
        assertThat(loaded.type()).isEqualTo(RequestType.APPOINTMENT);
        assertThat(loaded.tutorId()).contains(tutor.id());
        assertThat(loaded.petId()).contains(pet.id());
        assertThat(loaded.summary()).contains("Consulta de rotina (fictícia)");
        assertThat(loaded.updatedAt()).isEqualTo(request.updatedAt());
    }

    @Test
    void findByStatus_andByConversation() {
        Conversation conversation = conversations.save(Conversation.start(newContact(), T0));
        ServiceRequest info = ServiceRequest.open(conversation.id(), RequestType.INFORMATION, T0);
        info.submit(Set.of(), T0);
        ServiceRequest human = ServiceRequest.open(conversation.id(), RequestType.HUMAN_CONTACT, T0.plusSeconds(1));
        requests.save(info);
        requests.save(human);

        assertThat(requests.findByConversation(conversation.id())).extracting(ServiceRequest::type)
                .containsExactly(RequestType.INFORMATION, RequestType.HUMAN_CONTACT);
        assertThat(requests.findByStatus(RequestStatus.PENDING)).extracting(ServiceRequest::id).contains(info.id());
        assertThat(requests.findByStatus(RequestStatus.PENDING)).extracting(ServiceRequest::id).doesNotContain(human.id());
    }

    @Test
    void staleVersion_failsWithOptimisticLock() {
        Conversation conversation = conversations.save(Conversation.start(newContact(), T0));
        ServiceRequest saved = requests.save(ServiceRequest.open(conversation.id(), RequestType.INFORMATION, T0));
        ServiceRequest copyA = requests.findById(saved.id()).orElseThrow();
        ServiceRequest copyB = requests.findById(saved.id()).orElseThrow();

        copyA.cancel(T0.plusSeconds(1));
        requests.save(copyA);
        copyB.describe("alteração concorrente", T0.plusSeconds(2));

        assertThatThrownBy(() -> requests.save(copyB)).isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }
}
