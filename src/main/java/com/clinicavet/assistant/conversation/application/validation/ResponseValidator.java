package com.clinicavet.assistant.conversation.application.validation;

import com.clinicavet.assistant.clinicknowledge.domain.ClinicKnowledge;
import com.clinicavet.assistant.clinicknowledge.domain.KnowledgeItem;
import com.clinicavet.assistant.conversation.application.AssistantRequest;
import com.clinicavet.assistant.conversation.application.AssistantTurn;
import com.clinicavet.assistant.conversation.domain.Intent;
import com.clinicavet.assistant.conversation.domain.MessageAuthor;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Validação da resposta por fontes (ADR-0008, verificações V1–V7; lista da DA-29). Qualquer violação
 * torna o turno <b>inconsistente</b>: falha, com no máximo uma nova tentativa e, depois, a resposta
 * segura (ADR-0002).
 * <p>
 * Valores autorizados (V2):
 * <ol type="a">
 *   <li>os extraídos dos itens <b>conhecidos</b> que o modelo citou em {@code knowledgeRefs}, mais os
 *       implícitos na chave ({@code hours.saturday} autoriza "sábado"); dinheiro só se a clínica divulga
 *       preços;</li>
 *   <li>os escritos pelo tutor nesta conversa (eco), nas mensagens que foram ao modelo;</li>
 *   <li>os do contexto operacional: dia da semana e data de hoje e de amanhã, hora atual.</li>
 * </ol>
 * <b>Não há garantia absoluta</b>: os limites conhecidos estão no ADR-0008.
 */
@Component
public class ResponseValidator {

    public record Violation(String check, String detail) {

        @Override
        public String toString() {
            return check + ":" + detail;
        }
    }

    private static final Set<Intent> INSTITUTIONAL = EnumSet.of(
            Intent.CLINIC_INFORMATION, Intent.SERVICE_INFORMATION, Intent.PRICE_INQUIRY);
    private static final Map<String, String> DAY_KEYS = Map.of(
            "hours.monday", "segunda", "hours.tuesday", "terca", "hours.wednesday", "quarta",
            "hours.thursday", "quinta", "hours.friday", "sexta", "hours.saturday", "sabado",
            "hours.sunday", "domingo");
    private static final Map<DayOfWeek, String> DAY_NAMES = Map.of(
            DayOfWeek.MONDAY, "segunda", DayOfWeek.TUESDAY, "terca", DayOfWeek.WEDNESDAY, "quarta",
            DayOfWeek.THURSDAY, "quinta", DayOfWeek.FRIDAY, "sexta", DayOfWeek.SATURDAY, "sabado",
            DayOfWeek.SUNDAY, "domingo");
    private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter HOUR_MINUTE = DateTimeFormatter.ofPattern("HH:mm");

    private final ClinicKnowledge knowledge;
    private final ExpressionRules expressions;

    @org.springframework.beans.factory.annotation.Autowired
    public ResponseValidator(ClinicKnowledge knowledge) {
        this(knowledge, ExpressionRules.fromClasspath());
    }

    ResponseValidator(ClinicKnowledge knowledge, ExpressionRules expressions) {
        this.knowledge = knowledge;
        this.expressions = expressions;
    }

    public List<Violation> validate(AssistantTurn turn, AssistantRequest request) {
        List<Violation> violations = new ArrayList<>();

        // V1: referências válidas e conhecidas
        List<Fact> fromKnowledge = new ArrayList<>();
        for (String ref : turn.knowledgeRefs()) {
            List<KnowledgeItem> items = knowledge.resolve(ref);
            List<KnowledgeItem> known = items.stream().filter(KnowledgeItem::isKnown).toList();
            if (items.isEmpty()) {
                violations.add(new Violation("V1", "referencia-inexistente"));
            } else if (known.isEmpty()) {
                violations.add(new Violation("V1", "referencia-desconhecida"));
            }
            known.forEach(item -> fromKnowledge.addAll(authorizedBy(item)));
        }

        List<Fact> echo = new ArrayList<>(FactExtractor.extract(request.customerMessage()));
        request.history().stream()
                .filter(entry -> entry.author() == MessageAuthor.CUSTOMER)
                .forEach(entry -> echo.addAll(FactExtractor.extract(entry.content())));
        // Etapa 5: detalhes de solicitações desta conversa e dados do cadastro vieram do tutor (eco).
        request.operational().requests().forEach(r -> {
            if (r.reason() != null) {
                echo.addAll(FactExtractor.extract(r.reason()));
            }
            if (r.preferredPeriod() != null) {
                echo.addAll(FactExtractor.extract(r.preferredPeriod()));
            }
        });
        request.customer().pets().forEach(p -> {
            if (p.age() != null) {
                echo.addAll(FactExtractor.extract(p.age().value()));
            }
        });
        List<Fact> operational = new ArrayList<>(operationalFacts(request.operational().localNow()));
        // DA-37: a data de abertura de uma solicitação anterior pode ser citada.
        request.operational().previousRequests()
                .forEach(r -> operational.add(new Fact(Fact.Type.DATE, DAY_MONTH.format(r.openedOn()))));

        for (Fact fact : FactExtractor.extract(turn.reply())) {
            boolean echoed = contains(echo, fact);
            if (fact.type() == Fact.Type.DOSE) {
                if (!echoed) {
                    violations.add(new Violation("V7", "dose"));   // V7: dose, salvo eco do tutor
                }
                continue;
            }
            boolean nonInstitutional = echoed || contains(operational, fact);
            if (!nonInstitutional && !contains(fromKnowledge, fact)) {
                violations.add(new Violation("V2", fact.type().name()));   // V2: valor não autorizado
            } else if (turn.knowledgeGap() && !nonInstitutional) {
                violations.add(new Violation("V3", fact.type().name()));   // V3: lacuna com valor da base
            }
        }

        // V4: resposta institucional sem fonte e sem lacuna
        if (INSTITUTIONAL.contains(turn.intent()) && turn.knowledgeRefs().isEmpty() && !turn.knowledgeGap()) {
            violations.add(new Violation("V4", turn.intent().name()));
        }

        // V5 e V6: expressões reprovadas
        for (ExpressionRules.Hit hit : expressions.find(turn.reply())) {
            violations.add(new Violation(hit.check(), hit.id()));
        }
        return List.copyOf(violations);
    }

    private List<Fact> authorizedBy(KnowledgeItem item) {
        List<Fact> facts = new ArrayList<>();
        for (Fact fact : FactExtractor.extract(item.display())) {
            if (fact.type() != Fact.Type.MONEY || knowledge.pricesDisclosed()) {
                facts.add(fact);
            }
        }
        String day = DAY_KEYS.get(item.key());
        if (day != null) {
            facts.add(new Fact(Fact.Type.WEEKDAY, day));
            if (day.equals("sabado") || day.equals("domingo")) {
                facts.add(new Fact(Fact.Type.WEEKEND, "fim-de-semana"));
            }
        }
        if (item.key().equals("hours.holidays")) {
            facts.add(new Fact(Fact.Type.HOLIDAY, "feriado"));
        }
        if (item.key().equals("hours.twenty-four-hours")) {
            facts.add(new Fact(Fact.Type.ALL_DAY, "24h"));
        }
        return facts;
    }

    private static List<Fact> operationalFacts(ZonedDateTime now) {
        ZonedDateTime tomorrow = now.plusDays(1);
        return List.of(
                new Fact(Fact.Type.WEEKDAY, DAY_NAMES.get(now.getDayOfWeek())),
                new Fact(Fact.Type.WEEKDAY, DAY_NAMES.get(tomorrow.getDayOfWeek())),
                new Fact(Fact.Type.DATE, DAY_MONTH.format(now)),
                new Fact(Fact.Type.DATE, DAY_MONTH.format(tomorrow)),
                new Fact(Fact.Type.TIME, HOUR_MINUTE.format(now)));
    }

    private static boolean contains(List<Fact> facts, Fact wanted) {
        return facts.stream().anyMatch(fact -> fact.sameAs(wanted));
    }
}
