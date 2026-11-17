package com.clinicavet.assistant.conversation.application.rules;

import com.clinicavet.assistant.clinicknowledge.domain.ClinicKnowledge;
import com.clinicavet.assistant.clinicknowledge.domain.KnowledgeItem;
import com.clinicavet.assistant.conversation.domain.ClinicalPriority;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Lista de expressões de alerta aprovada pela equipe veterinária (ADR-0019, DA-27), lida da base
 * institucional ({@code triage.alert-expressions-p1/p2}). Uma correspondência impõe o <b>piso</b> P1 ou P2.
 * <ul>
 *   <li>Só eleva a prioridade, nunca rebaixa, e não diagnostica;</li>
 *   <li>a ausência de correspondência não significa situação leve;</li>
 *   <li>não há regra de negação: "não está respirando" é a própria expressão, e um falso positivo só eleva.</li>
 * </ul>
 * Sem lista na base ({@code null}), o detector nunca dispara. Até a aprovação (P-20), só a lista ilustrativa
 * da base fictícia existe.
 */
@Component
public class AlertExpressionDetector {

    private final List<String> p1;
    private final List<String> p2;

    public AlertExpressionDetector(ClinicKnowledge knowledge) {
        this.p1 = expressions(knowledge, "triage.alert-expressions-p1");
        this.p2 = expressions(knowledge, "triage.alert-expressions-p2");
    }

    /** O piso imposto pela mensagem: P1 se casar com alguma de P1; senão P2 se casar com alguma de P2. */
    public Optional<ClinicalPriority> floor(String customerText) {
        String text = RuleText.normalize(customerText);
        if (p1.stream().anyMatch(e -> RuleText.indexOfWords(text, e) >= 0)) {
            return Optional.of(ClinicalPriority.P1_POSSIBLE_EMERGENCY);
        }
        if (p2.stream().anyMatch(e -> RuleText.indexOfWords(text, e) >= 0)) {
            return Optional.of(ClinicalPriority.P2_URGENT);
        }
        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    private static List<String> expressions(ClinicKnowledge knowledge, String key) {
        return knowledge.item(key)
                .filter(KnowledgeItem::isKnown)
                .map(item -> ((List<String>) item.value()).stream().map(RuleText::normalize).filter(s -> !s.isEmpty()).toList())
                .orElse(List.of());
    }
}
