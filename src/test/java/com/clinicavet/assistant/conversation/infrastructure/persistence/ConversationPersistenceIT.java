package com.clinicavet.assistant.conversation.infrastructure.persistence;

import com.clinicavet.assistant.conversation.domain.CloseReason;
import com.clinicavet.assistant.conversation.domain.Conversation;
import com.clinicavet.assistant.conversation.domain.ConversationRepository;
import com.clinicavet.assistant.conversation.domain.ConversationStatus;
import com.clinicavet.assistant.conversation.domain.Intent;
import com.clinicavet.assistant.shared.domain.ContactIdentity;
import com.clinicavet.assistant.support.IntegrationTest;
import com.clinicavet.assistant.tutor.domain.Tutor;
import com.clinicavet.assistant.tutor.domain.TutorRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.time.Instant;

import static com.clinicavet.assistant.support.Fixtures.T0;
import static com.clinicavet.assistant.support.Fixtures.newContact;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@IntegrationTest
class ConversationPersistenceIT {

    @Autowired
    ConversationRepository conversations;

    @Autowired
    TutorRepository tutors;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void savesAndReadsBack_preservingIdTimestampsAndState() {
        ContactIdentity contact = newContact();
        Tutor tutor = tutors.save(Tutor.register(contact, T0));
        Conversation conversation = Conversation.start(contact, T0);
        conversation.linkTutor(tutor.id());
        conversation.recordIntent(Intent.APPOINTMENT_REQUEST);

        Conversation saved = conversations.save(conversation);
        Conversation loaded = conversations.findById(conversation.id()).orElseThrow();

        assertThat(saved.version()).isZero();
        assertThat(loaded.id()).isEqualTo(conversation.id());
        assertThat(loaded.contact()).isEqualTo(contact);
        assertThat(loaded.tutorId()).contains(tutor.id());
        assertThat(loaded.status()).isEqualTo(ConversationStatus.OPEN);
        assertThat(loaded.currentIntent()).contains(Intent.APPOINTMENT_REQUEST);
        assertThat(loaded.startedAt()).isEqualTo(Instant.parse("2026-11-11T13:00:00.123456Z"));
        assertThat(loaded.lastActivityAt()).isEqualTo(loaded.startedAt());
    }

    @Test
    void timestampsAreStoredInUtc() {
        Conversation conversation = conversations.save(Conversation.start(newContact(), T0));

        String stored = jdbc.queryForObject(
                "SELECT DATE_FORMAT(started_at, '%Y-%m-%dT%H:%i:%s.%f') FROM conversation WHERE id = UUID_TO_BIN(?)",
                String.class, conversation.id().value().toString());

        assertThat(stored).isEqualTo("2026-11-11T13:00:00.123456");
    }

    @Test
    void closingIsPersisted() {
        Conversation conversation = conversations.save(Conversation.start(newContact(), T0));
        conversation.closeByStaff(T0.plusSeconds(30));

        conversations.save(conversation);
        Conversation loaded = conversations.findById(conversation.id()).orElseThrow();

        assertThat(loaded.status()).isEqualTo(ConversationStatus.CLOSED);
        assertThat(loaded.closeReason()).contains(CloseReason.CLOSED_BY_STAFF);
        assertThat(loaded.closedAt()).isPresent();
        assertThat(loaded.version()).isEqualTo(1L);
    }

    @Test
    void staleVersion_failsWithOptimisticLock() {
        Conversation saved = conversations.save(Conversation.start(newContact(), T0));
        Conversation copyA = conversations.findById(saved.id()).orElseThrow();
        Conversation copyB = conversations.findById(saved.id()).orElseThrow();

        copyA.waitForHuman(T0.plusSeconds(1));
        conversations.save(copyA);
        copyB.closeByStaff(T0.plusSeconds(2));

        assertThatThrownBy(() -> conversations.save(copyB))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
        assertThat(conversations.findById(saved.id()).orElseThrow().status())
                .isEqualTo(ConversationStatus.WAITING_HUMAN);
    }

    @Test
    void findActiveByContact_returnsOnlyActiveConversationOfThatContact() {
        ContactIdentity contact = newContact();
        ContactIdentity otherContact = newContact();
        Conversation old = Conversation.start(contact, T0);
        old.closeByStaff(T0.plusSeconds(1));
        conversations.save(old);
        Conversation current = conversations.save(Conversation.start(contact, T0.plusSeconds(2)));
        conversations.save(Conversation.start(otherContact, T0));

        assertThat(conversations.findActiveByContact(contact)).map(Conversation::id).contains(current.id());

        current.waitForHuman(T0.plusSeconds(3));
        conversations.save(current);
        assertThat(conversations.findActiveByContact(contact)).map(Conversation::status)
                .contains(ConversationStatus.WAITING_HUMAN);

        Conversation reloaded = conversations.findById(current.id()).orElseThrow();
        reloaded.closeByStaff(T0.plusSeconds(4));
        conversations.save(reloaded);
        assertThat(conversations.findActiveByContact(contact)).isEmpty();
    }
}
