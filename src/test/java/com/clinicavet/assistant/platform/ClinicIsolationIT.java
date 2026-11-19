package com.clinicavet.assistant.platform;

import com.clinicavet.assistant.servicerequest.domain.ServiceRequest;
import com.clinicavet.assistant.servicerequest.domain.ServiceRequestRepository;
import com.clinicavet.assistant.shared.domain.Channel;
import com.clinicavet.assistant.shared.domain.ContactIdentity;
import com.clinicavet.assistant.shared.domain.ConversationId;
import com.clinicavet.assistant.support.ApiClient;
import com.clinicavet.assistant.support.FakeAssistantModel;
import com.clinicavet.assistant.support.IntegrationTest;
import com.clinicavet.assistant.support.OtherClinic;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Isolamento entre clínicas (ADR-0021, RNF-S01): cada chave pertence a uma clínica, e nada de uma clínica aparece
 * para a outra, nem por id direto (404), nem em listas, nem na consulta nativa.
 */
@IntegrationTest
class ClinicIsolationIT {

    @Value("${local.server.port}")
    int port;

    @Autowired
    FakeAssistantModel model;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ServiceRequestRepository serviceRequests;

    ApiClient clinicA;
    ApiClient clinicB;
    com.clinicavet.assistant.support.StaffClient staffA;
    com.clinicavet.assistant.support.StaffClient staffB;

    @Autowired
    org.springframework.context.ApplicationContext context;

    @BeforeEach
    void setUp() {
        model.reset();
        OtherClinic.ensure(jdbc);
        clinicA = ApiClient.authenticated(port);
        clinicB = clinicA.withKey(IntegrationTest.OTHER_CLINIC_API_KEY);
        staffA = com.clinicavet.assistant.support.TestStaff.admin(port, context);
        staffB = com.clinicavet.assistant.support.TestStaff.admin(port, context, OtherClinic.CLINIC);
    }

    /** Conversa encaminhada (pedido explícito), com solicitação aberta. */
    private String handedOff(ApiClient api, String contact) {
        String id = api.openConversation(contact);
        api.sendMessage(id, "quero falar com um atendente");
        return id;
    }

    private static List<String> ids(JsonNode list) {
        List<String> ids = new ArrayList<>();
        list.forEach(item -> ids.add(item.path("id").asString()));
        return ids;
    }

    @Test
    void conversationOfAnotherClinic_isNotFound() {
        String id = handedOff(clinicA, "ficticio-" + UUID.randomUUID());

        assertThat(clinicA.get("/api/conversations/" + id + "/messages").status()).isEqualTo(200);
        assertThat(clinicB.get("/api/conversations/" + id + "/messages").status()).isEqualTo(404);
        assertThat(clinicB.sendMessage(id, "oi").status()).isEqualTo(404);
        assertThat(staffB.get("/app/api/conversations/" + id).status()).isEqualTo(404);
        assertThat(staffB.post("/app/api/conversations/" + id + "/assume", Map.of()).status()).isEqualTo(404);
        assertThat(staffB.post("/app/api/conversations/" + id + "/close", Map.of()).status()).isEqualTo(404);
        assertThat(staffB.post("/app/api/conversations/" + id + "/return-to-assistant", Map.of()).status())
                .isEqualTo(404);
        assertThat(staffB.post("/app/api/conversations/" + id + "/notes", Map.of("text", "x")).status()).isEqualTo(404);
        assertThat(staffA.get("/app/api/conversations/" + id).body().path("status").asString())
                .isEqualTo("WAITING_HUMAN");
        assertThat(clinicA.get("/api/conversations/" + id + "/messages").status())
                .as("nada do que B tentou mudou a conversa de A").isEqualTo(200);
    }

    @Test
    void queuesAndRequests_showOnlyTheOwnClinic() {
        String fromA = handedOff(clinicA, "ficticio-" + UUID.randomUUID());
        String fromB = handedOff(clinicB, "ficticio-" + UUID.randomUUID());

        List<String> queueA = ids(staffA.get("/app/api/conversations?filter=WAITING").body());
        List<String> queueB = ids(staffB.get("/app/api/conversations?filter=WAITING").body());
        assertThat(queueA).contains(fromA).doesNotContain(fromB);
        assertThat(queueB).contains(fromB).doesNotContain(fromA);

        List<String> requestsB = new ArrayList<>();
        staffB.get("/app/api/service-requests?status=PENDING").body()
                .forEach(r -> requestsB.add(r.path("conversationId").asString()));
        assertThat(requestsB).contains(fromB).doesNotContain(fromA);
    }

    @Test
    void requestOfAnotherClinic_cannotBeUpdated() {
        String fromA = handedOff(clinicA, "ficticio-" + UUID.randomUUID());
        String requestId = serviceRequests.findByConversation(new ConversationId(UUID.fromString(fromA))).getFirst()
                .id().value().toString();

        assertThat(staffB.patch("/app/api/service-requests/" + requestId, Map.of("status", "IN_PROGRESS")).status())
                .isEqualTo(404);
        assertThat(serviceRequests.findByConversation(new ConversationId(UUID.fromString(fromA))).getFirst().status())
                .as("continua como estava").hasToString("PENDING");
    }

    /** O mesmo telefone pode ser cliente das duas clínicas: são tutores e conversas separados. */
    @Test
    void sameContact_inTwoClinics_areSeparateConversations() {
        String contact = "ficticio-" + UUID.randomUUID();
        String inA = clinicA.openConversation(contact);
        String inB = clinicB.openConversation(contact);

        assertThat(inA).isNotEqualTo(inB);
        assertThat(clinicA.sendMessage(inA, "oi").status()).isEqualTo(200);
        assertThat(clinicB.sendMessage(inB, "oi").status()).isEqualTo(200);
    }

    /** Consulta nativa (DA-37): o Hibernate não a filtra; a clínica vai explícita (ADR-0021). */
    @Test
    void nativeQuery_isFilteredByClinic() {
        String contact = "ficticio-" + UUID.randomUUID();
        handedOff(clinicA, contact);
        ContactIdentity identity = new ContactIdentity(Channel.API, contact);
        ConversationId other = ConversationId.newId();

        List<ServiceRequest> seenByA = serviceRequests.findOpenOfContactExcept(identity, other, 3);
        List<ServiceRequest> seenByB = CurrentClinic.call(OtherClinic.CLINIC,
                () -> serviceRequests.findOpenOfContactExcept(identity, other, 3));

        assertThat(seenByA).isNotEmpty();
        assertThat(seenByB).isEmpty();
    }

    @Test
    void wrongKey_is401_andNoClinicIsChosenByTheCaller() {
        assertThat(clinicA.withKey("chave-que-nao-existe-0123456789abcdef").get("/api/conversations/" + UUID.randomUUID() + "/messages").status())
                .isEqualTo(401);
    }
}
