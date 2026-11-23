package com.clinicavet.assistant.conversation.application;

import com.clinicavet.assistant.clinicknowledge.domain.ClinicKnowledge;
import com.clinicavet.assistant.clinicknowledge.domain.Departments;
import com.clinicavet.assistant.clinicknowledge.domain.ItemType;
import com.clinicavet.assistant.clinicknowledge.domain.KnowledgeItem;
import com.clinicavet.assistant.conversation.domain.Intent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** RF-S37, RF-S61: setor da solicitação pelas regras publicadas (serviço → setor), sem regra = padrão. */
class DepartmentRouterTest {

    static final UUID RECEPTION = UUID.randomUUID();
    static final UUID CLINIC = UUID.randomUUID();
    static final UUID PETSHOP = UUID.randomUUID();

    static final ClinicKnowledge KNOWLEDGE = new ClinicKnowledge(List.of(
            new KnowledgeItem("services.banho.name", ItemType.TEXT, "Banho e tosa"),
            new KnowledgeItem("services.banho.department", ItemType.TEXT, "PETSHOP"),
            new KnowledgeItem("services.consulta.name", ItemType.TEXT, "Consulta clínica"),
            new KnowledgeItem("services.consulta.department", ItemType.TEXT, "CLINICAL"),
            new KnowledgeItem("services.racao.name", ItemType.TEXT, "Ração"),
            new KnowledgeItem("services.racao.department", ItemType.TEXT, "PETSHOP")), Set.of());

    static Departments departments(boolean petshopActive) {
        List<Departments.Department> list = List.of(
                new Departments.Department(RECEPTION, "Recepção", Departments.Kind.RECEPTION, true, true),
                new Departments.Department(CLINIC, "Clínica", Departments.Kind.CLINICAL, true, false),
                new Departments.Department(PETSHOP, "Pet shop", Departments.Kind.PETSHOP, petshopActive, false));
        return new Departments() {
            public List<Department> list() {
                return list;
            }

            public Optional<Department> find(UUID id) {
                return list.stream().filter(d -> d.id().equals(id)).findFirst();
            }

            public Department create(String name, Kind kind) {
                throw new UnsupportedOperationException();
            }

            public Department rename(UUID id, String name) {
                throw new UnsupportedOperationException();
            }

            public Department setActive(UUID id, boolean active) {
                throw new UnsupportedOperationException();
            }

            public Department defaultDepartment() {
                return list.getFirst();
            }

            public Set<UUID> activeAmong(Set<UUID> ids) {
                return ids;
            }

            public void ensureDefaults() {
            }
        };
    }

    final DepartmentRouter router = new DepartmentRouter(KNOWLEDGE, departments(true));

    @Test
    void serviceTheAssistantConsulted_decides() {
        assertThat(router.route(List.of("services.banho.price-cents"), "quanto custa?", Optional.of(Intent.PRICE_INQUIRY)))
                .isEqualTo(PETSHOP);
    }

    @Test
    void serviceNamedByTheTutor_decides_ignoringAccentsAndCase() {
        assertThat(router.route(List.of(), "Quero marcar BANHO E TOSA pro Rex", Optional.of(Intent.APPOINTMENT_REQUEST)))
                .isEqualTo(PETSHOP);
        assertThat(router.route(List.of(), "queria uma consulta clinica", Optional.empty())).isEqualTo(CLINIC);
    }

    @Test
    void shortNamesDoNotMatchInsideOtherWords() {
        // "Ração" tem 5 letras e só vale como palavra inteira.
        assertThat(router.route(List.of(), "minha geração de cães", Optional.empty())).isNull();
    }

    @Test
    void healthConcern_goesToTheClinicalDepartment() {
        assertThat(router.route(List.of(), "ele está vomitando", Optional.of(Intent.MEDICAL_CONCERN))).isEqualTo(CLINIC);
        assertThat(router.route(List.of(), "e o remédio?", Optional.of(Intent.TREATMENT_FOLLOW_UP))).isEqualTo(CLINIC);
    }

    @Test
    void noRule_goesToTheDefault() {
        assertThat(router.route(List.of(), "oi", Optional.of(Intent.GREETING))).isNull();
        assertThat(router.nameFor(null)).isEqualTo("Recepção");
        assertThat(router.nameFor(CLINIC)).isEqualTo("Clínica");
    }

    @Test
    void inactiveDepartment_fallsBackToTheDefault() {
        DepartmentRouter withPetshopOff = new DepartmentRouter(KNOWLEDGE, departments(false));
        assertThat(withPetshopOff.route(List.of("services.banho.name"), "", Optional.empty())).isNull();
    }
}
