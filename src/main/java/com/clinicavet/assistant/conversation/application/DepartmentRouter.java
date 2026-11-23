package com.clinicavet.assistant.conversation.application;

import com.clinicavet.assistant.clinicknowledge.domain.ClinicKnowledge;
import com.clinicavet.assistant.clinicknowledge.domain.Departments;
import com.clinicavet.assistant.conversation.domain.Intent;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Setor da solicitação pelas regras publicadas (RF-S37, RF-S61, RF-S14). Determinístico: o modelo não escolhe setor.
 * <ol>
 *   <li><b>Serviço citado.</b> O serviço que o assistente usou na resposta ({@code knowledgeRefs}
 *       {@code services.<id>...}) ou cujo nome aparece no texto do tutor. Vale o setor configurado no serviço
 *       (Configuração &gt; Serviços e preços &gt; Setor);</li>
 *   <li><b>Assunto clínico.</b> Preocupação de saúde ou acompanhamento de tratamento vai para o setor clínico;</li>
 *   <li><b>Sem regra:</b> o setor padrão (a Recepção).</li>
 * </ol>
 * Setor do tipo certo inativo ou inexistente também cai no padrão. Devolve {@code null} para o padrão: quem grava
 * resolve (a solicitação e a conversa já fazem isso).
 */
@Component
public class DepartmentRouter {

    private final ClinicKnowledge knowledge;
    private final Departments departments;

    DepartmentRouter(ClinicKnowledge knowledge, Departments departments) {
        this.knowledge = knowledge;
        this.departments = departments;
    }

    /**
     * @param refs   referências à base usadas pelo assistente neste turno (vazio se não houve turno válido)
     * @param text   texto do tutor e, se houver, o motivo do pedido
     * @param intent intenção principal do turno, se houver
     */
    public UUID route(Collection<String> refs, String text, Optional<Intent> intent) {
        return kindFor(refs, text, intent).flatMap(this::activeOfKind).orElse(null);
    }

    /** Nome do setor para o texto ao tutor; {@code null} = o setor padrão. */
    public String nameFor(UUID departmentId) {
        try {
            return (departmentId == null ? Optional.<Departments.Department>empty() : departments.find(departmentId))
                    .or(() -> Optional.of(departments.defaultDepartment())).map(Departments.Department::name)
                    .orElse(null);
        } catch (RuntimeException e) {
            return null; // sem setores cadastrados: texto genérico
        }
    }

    Optional<Departments.Kind> kindFor(Collection<String> refs, String text, Optional<Intent> intent) {
        Map<String, String> byService = knowledge.serviceDepartments();
        if (!byService.isEmpty()) {
            for (String ref : refs) {
                Optional<Departments.Kind> kind = serviceOf(ref).map(byService::get).flatMap(DepartmentRouter::kind);
                if (kind.isPresent()) {
                    return kind;
                }
            }
            String normalized = normalize(text);
            if (!normalized.isBlank()) {
                for (Map.Entry<String, String> entry : byService.entrySet()) {
                    Optional<String> name = knowledge.item("services." + entry.getKey() + ".name")
                            .filter(i -> i.isKnown()).map(i -> normalize(i.value().toString()));
                    if (name.filter(n -> n.length() >= 4 && containsWord(normalized, n)).isPresent()) {
                        Optional<Departments.Kind> kind = kind(entry.getValue());
                        if (kind.isPresent()) {
                            return kind;
                        }
                    }
                }
            }
        }
        if (intent.filter(i -> i == Intent.MEDICAL_CONCERN || i == Intent.TREATMENT_FOLLOW_UP).isPresent()) {
            return Optional.of(Departments.Kind.CLINICAL);
        }
        return Optional.empty();
    }

    private Optional<UUID> activeOfKind(Departments.Kind kind) {
        if (kind == Departments.Kind.RECEPTION) {
            return Optional.empty();
        }
        return departments.list().stream().filter(d -> d.active() && d.kind() == kind)
                .map(Departments.Department::id).findFirst();
    }

    private static Optional<String> serviceOf(String ref) {
        if (ref == null || !ref.startsWith("services.")) {
            return Optional.empty();
        }
        String rest = ref.substring("services.".length());
        int dot = rest.indexOf('.');
        return Optional.of(dot < 0 ? rest : rest.substring(0, dot)).filter(s -> !s.isBlank());
    }

    private static Optional<Departments.Kind> kind(String value) {
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Departments.Kind.valueOf(value.strip().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static boolean containsWord(String text, String phrase) {
        return (" " + text + " ").contains(" " + phrase + " ");
    }

    static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String plain = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return plain.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").strip();
    }
}
