package com.clinicavet.assistant.ai;

import com.clinicavet.assistant.clinicknowledge.domain.ClinicKnowledge;
import com.clinicavet.assistant.clinicknowledge.domain.KnowledgeItem;
import com.clinicavet.assistant.clinicknowledge.domain.OpeningHours;
import com.clinicavet.assistant.conversation.application.AssistantRequest;
import com.clinicavet.assistant.conversation.domain.ConversationStatus;
import com.clinicavet.assistant.servicerequest.domain.RequestStatus;
import com.clinicavet.assistant.servicerequest.domain.RequestType;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Templates de {@code src/main/resources/prompts}, lidos em UTF-8 na inicialização (DM-05): se faltar
 * um, a aplicação não sobe.
 * <p>
 * Linhas que começam com {@code ##} são notas para quem mantém o arquivo (pendências, origem) e
 * <b>não</b> vão para o modelo.
 */
@Component
class PromptTemplates {

    private static final DateTimeFormatter LOCAL_DATE_TIME =
            DateTimeFormatter.ofPattern("EEEE, dd/MM/yyyy, HH:mm", Locale.of("pt", "BR"));
    private static final DateTimeFormatter DAY_MONTH_YEAR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm");

    private final String systemMessage;
    private final String operationalContext;
    private final String clinicTemplate;
    private final ClinicKnowledge knowledge;
    private final String tutorContext;

    PromptTemplates(@Value("classpath:prompts/system-message.st") Resource systemMessage,
                    @Value("classpath:prompts/operational-context.st") Resource operationalContext,
                    @Value("classpath:prompts/clinic-context.st") Resource clinicTemplate,
                    @Value("classpath:prompts/tutor-context.st") Resource tutorContext,
                    ClinicKnowledge knowledge) {
        this.systemMessage = read(systemMessage);
        this.operationalContext = read(operationalContext);
        this.tutorContext = read(tutorContext);
        this.clinicTemplate = read(clinicTemplate);
        this.knowledge = knowledge;
    }

    /**
     * Camada 2: uma linha por item, `[chave] valor`, com DESCONHECIDO explícito (ADR-0003). Montada a cada turno:
     * uma seção vencida passa a DESCONHECIDO sem reiniciar a aplicação.
     */
    String clinicContext() {
        return renderClinic(clinicTemplate, knowledge);
    }

    private static String renderClinic(String template, ClinicKnowledge knowledge) {
        List<String> lines = new ArrayList<>();
        for (KnowledgeItem item : knowledge.promptItems()) {
            lines.add("[" + item.key() + "] " + item.display());
        }
        for (String collection : ClinicKnowledge.COLLECTIONS) {
            if (knowledge.unknownCollections().contains(collection)) {
                lines.add("[" + collection + "] " + KnowledgeItem.UNKNOWN_TEXT);
            }
        }
        if (lines.isEmpty()) {
            lines.add("(nenhuma informação cadastrada)");
        }
        return render(template, Map.of("itens", String.join("\n", lines)));
    }

    /** Camada 1: identidade e comportamento. Texto fixo, sem dados da clínica. */
    String systemMessage() {
        return systemMessage;
    }

    /** Camada 5: status, data/hora local e solicitações, no bloco {@code <estado_do_atendimento>}. */
    String operationalContext(AssistantRequest.OperationalContext context) {
        return render(operationalContext, Map.of(
                "status", describe(context.status()),
                "agora", LOCAL_DATE_TIME.format(context.localNow()),
                "funcionamento", opening(context.opening()),
                "solicitacoes", requests(context.requests()),
                "anteriores", previous(context.previousRequests())));
    }

    /**
     * Camada 3: tutor e pets com o status de proveniência de cada campo, no bloco {@code <cliente>} (ADR-0010).
     * Os valores já foram normalizados pelo backend (sem os caracteres que delimitam blocos).
     */
    String tutorContext(AssistantRequest.CustomerContext customer) {
        List<String> lines = new ArrayList<>();
        if (customer.isEmpty()) {
            lines.add("(nenhum dado do tutor registrado)");
        } else {
            lines.add("Tutor: " + (customer.tutorName() == null ? "nome não informado" : field(customer.tutorName())));
            if (customer.pets().isEmpty()) {
                lines.add("Pets: nenhum registrado");
            } else {
                lines.add("Pets:");
                for (AssistantRequest.CustomerContext.PetView pet : customer.pets()) {
                    List<String> parts = new ArrayList<>();
                    parts.add(field(pet.name()));
                    parts.add("espécie: " + (pet.species() == null ? "não informada" : field(pet.species())));
                    if (pet.breed() != null) {
                        parts.add("raça: " + field(pet.breed()));
                    }
                    if (pet.age() != null) {
                        parts.add("idade: " + field(pet.age()));
                    }
                    lines.add("- " + String.join("; ", parts));
                }
            }
        }
        return render(tutorContext, Map.of("itens", String.join("\n", lines)));
    }

    private static String field(AssistantRequest.CustomerContext.FieldView field) {
        return field.value() + " [" + switch (field.status()) {
            case MENTIONED -> "MENCIONADA";
            case CONFIRMED -> "CONFIRMADA";
            case REGISTERED -> "CADASTRADA";
            case TO_RECONFIRM -> "A_RECONFIRMAR";
        } + "]";
    }

    /** "Aberta agora até 18:00" / "Fechada agora; abre quinta-feira, 19/11, às 08:00" / horário não cadastrado. */
    static String opening(OpeningHours.Status status) {
        String text = switch (status.state()) {
            case OPEN -> status.twentyFourHours() ? "aberta agora (atendimento 24 horas)"
                    : "aberta agora" + status.closesAt().map(t -> ", até " + HOUR.format(t)).orElse("");
            case CLOSED -> "fechada agora" + status.nextOpening()
                    .map(n -> "; abre " + OpeningHours.dayName(n.getDayOfWeek()) + ", " + DAY_MONTH.format(n)
                            + ", às " + HOUR.format(n))
                    .orElse("; próxima abertura não cadastrada");
            case UNKNOWN -> "horário de hoje não cadastrado: não diga se está aberta ou fechada";
        };
        return status.holidaysUnknown() && status.state() != OpeningHours.State.UNKNOWN
                ? text + " (feriados não cadastrados: em feriado, a recepção confirma)" : text;
    }

    private static String requests(List<AssistantRequest.RequestView> requests) {
        if (requests.isEmpty()) {
            return "(nenhuma)";
        }
        return requests.stream().map(PromptTemplates::request).collect(Collectors.joining("\n"));
    }

    private static String request(AssistantRequest.RequestView r) {
        if (r.type() != RequestType.APPOINTMENT) {
            String what = switch (r.type()) {
                case INFORMATION -> "Pedido de informação para a equipe";
                case HUMAN_CONTACT -> "Conversa registrada para atendimento da equipe";
                case FOLLOW_UP -> "Pedido de acompanhamento";
                case APPOINTMENT -> "";
            };
            return "- " + what + " (" + requestStatus(r.status()) + ")";
        }
        if (r.status().isFinal()) {
            return "- Pedido de agendamento encerrado pela equipe. Você não sabe o desfecho.";
        }
        String details = "Pet: " + orNot(r.petName()) + "; motivo: " + orNot(r.reason()) + "; preferência: "
                + orNot(r.preferredPeriod()) + ".";
        if (r.status() == RequestStatus.COLLECTING) {
            String missing = r.missing().isEmpty() ? "nada" : String.join("; ", r.missing());
            return "- Pedido de agendamento em coleta, ainda NÃO registrado. " + details + " Falta: " + missing + ".";
        }
        return "- Pedido de agendamento registrado para a equipe (" + requestStatus(r.status()) + "). " + details
                + " Dia e horário ainda não confirmados pela equipe.";
    }

    private static String requestStatus(RequestStatus status) {
        return switch (status) {
            case COLLECTING -> "em coleta";
            case PENDING -> "aguardando a equipe";
            case IN_PROGRESS -> "em andamento com a equipe";
            case RESOLVED, CANCELLED -> "encerrado pela equipe";
        };
    }

    private static String orNot(String value) {
        return value == null ? "não informado" : value;
    }

    private static String previous(List<AssistantRequest.PreviousRequest> previous) {
        if (previous.isEmpty()) {
            return "(nenhum)";
        }
        return previous.stream()
                .map(p -> "- " + switch (p.type()) {
                    case APPOINTMENT -> "Pedido de agendamento";
                    case INFORMATION -> "Pedido de informação";
                    case HUMAN_CONTACT -> "Atendimento pela equipe";
                    case FOLLOW_UP -> "Pedido de acompanhamento";
                } + " aberto em " + DAY_MONTH_YEAR.format(p.openedOn()) + " (" + requestStatus(p.status())
                        + ") — atendimento anterior, não reutilizar dados")
                .collect(Collectors.joining("\n"));
    }

    private static String describe(ConversationStatus status) {
        return switch (status) {
            case OPEN -> "em atendimento pelo assistente";
            case WAITING_HUMAN -> "sinalizada para revisão da equipe";
            case CLOSED -> "encerrada";
        };
    }

    /** O StringTemplate escreve a quebra de linha do sistema (CRLF no Windows); aqui fica sempre LF. */
    private static String render(String template, Map<String, String> values) {
        return new PromptTemplate(template).render(new java.util.HashMap<String, Object>(values)).replace("\r\n", "\n");
    }

    private static String read(Resource resource) {
        try {
            String raw = resource.getContentAsString(StandardCharsets.UTF_8);
            return raw.lines()
                    .filter(line -> !line.startsWith("##"))
                    .collect(Collectors.joining("\n"))
                    .strip();
        } catch (IOException e) {
            throw new UncheckedIOException("template de prompt ausente ou ilegível: " + resource.getDescription(), e);
        }
    }
}
