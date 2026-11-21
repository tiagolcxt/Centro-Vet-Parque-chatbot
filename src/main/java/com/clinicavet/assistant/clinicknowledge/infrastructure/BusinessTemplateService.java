package com.clinicavet.assistant.clinicknowledge.infrastructure;

import com.clinicavet.assistant.clinicknowledge.domain.BusinessTemplates;
import com.clinicavet.assistant.clinicknowledge.domain.ClinicConfig;
import com.clinicavet.assistant.clinicknowledge.domain.Departments;
import com.clinicavet.assistant.clinicknowledge.domain.Departments.Kind;
import com.clinicavet.assistant.platform.AuditLog;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Os quatro modelos (S3-13). Só nomes e perguntas: nenhum dado institucional é inventado. */
@Service
class BusinessTemplateService implements BusinessTemplates {

    private static final List<String> COMMON_QUESTIONS = List.of(
            "Qual o horário de funcionamento?",
            "Onde vocês ficam?",
            "Quais formas de pagamento vocês aceitam?",
            "Precisa agendar?");

    private static final Map<Type, Template> TEMPLATES = new LinkedHashMap<>();

    static {
        put(new Template(Type.SMALL_CLINIC, "Clínica pequena",
                "Só atendimento clínico: recepção e veterinário.",
                List.of(Kind.RECEPTION, Kind.CLINICAL),
                List.of(s("Consulta clínica", Kind.CLINICAL), s("Vacinação", Kind.CLINICAL), s("Retorno", Kind.CLINICAL),
                        s("Exames laboratoriais", Kind.CLINICAL)),
                plus(COMMON_QUESTIONS, "Quanto custa a consulta?", "Vocês atendem emergência?",
                        "O que levar na consulta?")));
        put(new Template(Type.CLINIC_WITH_PETSHOP, "Clínica com pet shop e banho e tosa",
                "Atendimento clínico, loja e banho e tosa, cada um com a sua fila.",
                List.of(Kind.RECEPTION, Kind.CLINICAL, Kind.PETSHOP),
                List.of(s("Consulta clínica", Kind.CLINICAL), s("Vacinação", Kind.CLINICAL), s("Retorno", Kind.CLINICAL),
                        s("Banho", Kind.PETSHOP), s("Tosa", Kind.PETSHOP), s("Tosa higiênica", Kind.PETSHOP)),
                plus(COMMON_QUESTIONS, "Quanto custa a consulta?", "Quanto custa o banho?",
                        "Vocês buscam e levam o animal?", "Vocês atendem emergência?")));
        put(new Template(Type.PETSHOP_ONLY, "Só pet shop ou banho e tosa",
                "Loja e banho e tosa, sem atendimento clínico.",
                List.of(Kind.RECEPTION, Kind.PETSHOP),
                List.of(s("Banho", Kind.PETSHOP), s("Tosa", Kind.PETSHOP), s("Tosa higiênica", Kind.PETSHOP), s("Hidratação", Kind.PETSHOP)),
                plus(COMMON_QUESTIONS, "Quanto custa o banho?", "Vocês buscam e levam o animal?",
                        "Quais vacinas o animal precisa ter para o banho?")));
        put(new Template(Type.HOSPITAL_24H, "Hospital 24 horas",
                "Pronto atendimento, internação e exames, com plantão.",
                List.of(Kind.RECEPTION, Kind.CLINICAL),
                List.of(s("Pronto atendimento", Kind.CLINICAL), s("Consulta clínica", Kind.CLINICAL), s("Internação", Kind.CLINICAL),
                        s("Exames laboratoriais", Kind.CLINICAL), s("Exames de imagem", Kind.CLINICAL), s("Cirurgia", Kind.CLINICAL),
                        s("Vacinação", Kind.CLINICAL)),
                plus(COMMON_QUESTIONS, "Vocês atendem de madrugada?", "Como funciona a visita ao animal internado?",
                        "Quanto custa a consulta de emergência?")));
    }

    private final ClinicConfig config;
    private final Departments departments;
    private final AuditLog audit;

    BusinessTemplateService(ClinicConfig config, Departments departments, AuditLog audit) {
        this.config = config;
        this.departments = departments;
        this.audit = audit;
    }

    @Override
    public List<Template> list() {
        return List.copyOf(TEMPLATES.values());
    }

    @Override
    @Transactional
    public ClinicConfig.Version apply(Type type) {
        Template template = TEMPLATES.get(type);
        departments.ensureDefaults();
        for (Departments.Department department : departments.list()) {
            boolean wanted = department.isDefault() || template.departments().contains(department.kind());
            if (department.active() != wanted && department.kind() != Kind.OTHER) {
                departments.setActive(department.id(), wanted);
            }
        }
        ClinicConfig.Version draft = config.draft();
        Map<String, Object> document = draft.document();
        Map<String, Object> services = collection(document, "services");
        for (ServiceSuggestion service : template.services()) {
            Map<String, Object> entry = emptyEntry("services");
            entry.put("name", service.name());
            entry.put("department", service.department().name());
            if (!hasEntry(services, "name", service.name())) {
                services.putIfAbsent(slug(service.name()), entry);
            }
        }
        Map<String, Object> faq = collection(document, "faq");
        for (String question : template.questions()) {
            Map<String, Object> entry = emptyEntry("faq");
            entry.put("question", question);
            if (!hasEntry(faq, "question", question)) {
                faq.putIfAbsent(slug(question), entry);
            }
        }
        ClinicConfig.Version saved = config.saveDraft(document, draft.summary().revision());
        audit.record("config.template-applied", "config_version", saved.summary().id(), "Modelo aplicado: " + template.name());
        return saved;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> collection(Map<String, Object> document, String name) {
        Object current = document.get(name);
        Map<String, Object> result = current instanceof Map<?, ?> map
                ? new LinkedHashMap<>((Map<String, Object>) map) : new LinkedHashMap<>();
        document.put(name, result);
        return result;
    }

    /** Já existe uma entrada com o mesmo texto (sem acento e caixa), qualquer que seja o id? */
    static boolean hasEntry(Map<String, Object> collection, String field, String text) {
        String wanted = slug(text);
        return collection.values().stream().anyMatch(e -> e instanceof Map<?, ?> m && m.get(field) instanceof String v
                && slug(v).equals(wanted));
    }

    static Map<String, Object> emptyEntry(String collection) {
        Map<String, Object> entry = new LinkedHashMap<>();
        KnowledgeSchema.COLLECTION_ENTRIES.get(collection).keySet().forEach(f -> entry.put(f, null));
        KnowledgeSchema.OPTIONAL_IN_ENTRY.getOrDefault(collection, Map.of()).keySet().forEach(f -> entry.put(f, null));
        return entry;
    }

    /** Id de entrada a partir do texto: minúsculas, sem acento, hífen entre palavras, até 40 caracteres. */
    static String slug(String text) {
        String ascii = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String slug = ascii.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        if (slug.length() > 40) {
            slug = slug.substring(0, 40).replaceAll("-$", "");
        }
        return slug.isEmpty() ? "item" : slug;
    }

    private static ServiceSuggestion s(String name, Kind department) {
        return new ServiceSuggestion(name, department);
    }

    private static void put(Template template) {
        TEMPLATES.put(template.type(), template);
    }

    private static List<String> plus(List<String> base, String... more) {
        List<String> all = new java.util.ArrayList<>(base);
        all.addAll(List.of(more));
        return List.copyOf(all);
    }
}
