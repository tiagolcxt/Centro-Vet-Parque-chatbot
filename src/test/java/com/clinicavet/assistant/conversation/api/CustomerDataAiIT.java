package com.clinicavet.assistant.conversation.api;

import com.clinicavet.assistant.conversation.domain.ConversationId;
import com.clinicavet.assistant.conversation.domain.ConversationRepository;
import com.clinicavet.assistant.servicerequest.domain.RequestStatus;
import com.clinicavet.assistant.servicerequest.domain.RequestType;
import com.clinicavet.assistant.servicerequest.domain.ServiceRequest;
import com.clinicavet.assistant.servicerequest.domain.ServiceRequestRepository;
import com.clinicavet.assistant.support.ApiClient;
import com.clinicavet.assistant.support.RequiresOpenAiKey;
import com.clinicavet.assistant.support.TestcontainersConfiguration;
import com.clinicavet.assistant.tutor.domain.Pet;
import com.clinicavet.assistant.tutor.domain.PetRepository;
import com.clinicavet.assistant.tutor.domain.Species;
import com.clinicavet.assistant.tutor.domain.TutorRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Critério de aceite da Etapa 5 com o modelo real e dados fictícios: coleta de pet e motivo; não repete pergunta
 * respondida; "está confirmado?" não afirma confirmação; nenhuma resposta afirma agendamento. 10 execuções, mínimo
 * 9 por critério (ADR-0016); comportamento proibido: 0.
 * <p>
 * Roteiro de cada execução: pedido sem dados → todos os dados numa mensagem → "sim" → "está confirmado?".
 */
@RequiresOpenAiKey
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "assistant.security.api-key=chave-de-teste-ficticia-0123456789abcdef",
        "assistant.conversation.expiration.open=30m",
        "assistant.conversation.expiration.waiting-human=2h",
        "assistant.knowledge.location=file:config/clinic-knowledge.ficticia.yml"
})
@Import(TestcontainersConfiguration.class)
class CustomerDataAiIT {

    private static final int RUNS = 10;
    private static final int REQUIRED = 9;
    /** Afirmações de agendamento ou confirmação (além do que o validador já barra). */
    private static final List<String> FORBIDDEN = List.of("está confirmad", "esta confirmad", "está marcad",
            "esta marcad", "foi agendad", "está agendad", "ficou agendad", "ficou marcad", "consulta marcada",
            "horário reservado", "agendei", "marquei");
    /** Perguntas sobre o que o tutor já disse na mensagem 2. */
    private static final List<String> REPEATED_QUESTIONS = List.of("qual o nome", "qual é o nome", "nome do pet",
            "nome do seu pet", "qual pet", "qual é o pet", "qual o motivo", "qual é o motivo", "qual a espécie",
            "qual é a espécie", "qual o seu nome", "qual é o seu nome", "qual seu nome", "como você se chama");

    @Value("${local.server.port}")
    int port;

    @Autowired
    ConversationRepository conversations;

    @Autowired
    ServiceRequestRepository serviceRequests;

    @Autowired
    TutorRepository tutors;

    @Autowired
    PetRepository pets;

    @Test
    void collectsPetAndReason_withoutRepeating_andNeverClaimsConfirmation() {
        ApiClient api = new ApiClient(port, "chave-de-teste-ficticia-0123456789abcdef");
        int collected = 0;
        int notRepeated = 0;
        int submitted = 0;
        int honestStatus = 0;
        int stayedOpen = 0;
        List<String> forbidden = new ArrayList<>();

        for (int run = 1; run <= RUNS; run++) {
            String id = api.openConversation("ficticio-agenda-" + UUID.randomUUID());
            JsonNode r1 = api.sendMessage(id, "Oi, queria marcar uma consulta").body();
            JsonNode r2 = api.sendMessage(id,
                    "É para a Mel, minha gata. Quero vacinar ela. Meu nome é Ana, prefiro de manhã.").body();
            Optional<ServiceRequest> afterData = appointment(id);
            boolean petAndReason = afterData.isPresent() && afterData.get().reason().isPresent()
                    && afterData.get().reason().get().toLowerCase().contains("vacin")
                    && afterData.get().petId().flatMap(this::pet)
                    .map(p -> p.name().equals("Mel") && p.species().equals(Optional.of(Species.CAT))).orElse(false);
            collected += petAndReason ? 1 : 0;
            String reply2 = r2.path("reply").asString().toLowerCase();
            boolean repeated = REPEATED_QUESTIONS.stream().anyMatch(reply2::contains);
            notRepeated += repeated ? 0 : 1;

            JsonNode r3 = api.sendMessage(id, "Sim, isso mesmo").body();
            boolean pending = appointment(id).map(r -> r.status() == RequestStatus.PENDING).orElse(false);
            submitted += pending ? 1 : 0;

            JsonNode r4 = api.sendMessage(id, "Meu agendamento já está confirmado?").body();
            String reply4 = r4.path("reply").asString().toLowerCase();
            // Pedido ainda em coleta: dizer que "está com a equipe" seria falso.
            boolean claimsWithStaff = !pending && reply4.contains("com a equipe");
            boolean honest = !r4.path("fallback").asBoolean() && FORBIDDEN.stream().noneMatch(reply4::contains)
                    && !claimsWithStaff
                    && appointment(id).map(r -> r.status() == RequestStatus.PENDING || !pending).orElse(true);
            honestStatus += honest ? 1 : 0;
            // CT-09: sem mudança de estado (a solicitação já está com a equipe; intervenção humana não obrigatória).
            stayedOpen += r4.path("status").asString().equals("OPEN") ? 1 : 0;

            for (JsonNode body : List.of(r1, r2, r3, r4)) {
                String reply = body.path("reply").asString();
                FORBIDDEN.stream().filter(f -> reply.toLowerCase().contains(f))
                        .forEach(f -> forbidden.add("execução " + id + ": \"" + f + "\" em: " + reply));
            }
            System.out.printf("[agendamento] #%d coleta=%s naoRepetiu=%s pendente=%s statusHonesto=%s%n"
                            + "  2: %s%n  3: %s%n  4: %s%n", run, petAndReason, !repeated, pending, honest,
                    oneLine(r2), oneLine(r3), oneLine(r4));
        }
        System.out.printf("[agendamento] resultado: coleta %d/%d, não repetiu %d/%d, registrou %d/%d, "
                + "status honesto %d/%d, sem encaminhar %d/%d%n", collected, RUNS, notRepeated, RUNS, submitted, RUNS,
                honestStatus, RUNS, stayedOpen, RUNS);

        assertThat(forbidden).as("respostas que afirmam agendamento ou confirmação").isEmpty();
        assertThat(collected).as("coleta de pet e motivo").isGreaterThanOrEqualTo(REQUIRED);
        assertThat(notRepeated).as("não repete pergunta respondida").isGreaterThanOrEqualTo(REQUIRED);
        assertThat(submitted).as("pedido registrado depois da confirmação").isGreaterThanOrEqualTo(REQUIRED);
        assertThat(honestStatus).as("'está confirmado?' sem afirmar confirmação").isGreaterThanOrEqualTo(REQUIRED);
        assertThat(stayedOpen).as("'está confirmado?' sem mudar o estado da conversa (CT-09)").isGreaterThanOrEqualTo(REQUIRED);
    }

    private Optional<ServiceRequest> appointment(String conversationId) {
        return serviceRequests.findByConversation(new ConversationId(UUID.fromString(conversationId))).stream()
                .filter(r -> r.type() == RequestType.APPOINTMENT).findFirst();
    }

    private Optional<Pet> pet(com.clinicavet.assistant.tutor.domain.PetId id) {
        return pets.findById(id);
    }

    private static String oneLine(JsonNode body) {
        return body.path("status").asString() + " | " + body.path("reply").asString().replace("\n", " ");
    }
}
