package com.clinicavet.assistant.conversation.application;

import com.clinicavet.assistant.clinicknowledge.domain.ClinicKnowledge;
import com.clinicavet.assistant.clinicknowledge.domain.KnowledgeItem;
import com.clinicavet.assistant.clinicknowledge.domain.OpeningHours;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * O próximo passo dado ao tutor quando o atendimento precisa da equipe (DA-40): <b>ligar para a recepção</b>, conforme
 * a clínica esteja aberta ou fechada agora. O assistente nunca promete contato: só indica o caminho.
 * <ul>
 *   <li>texto aprovado pela clínica ({@code approved-texts.direct-contact}, P-02), se houver;</li>
 *   <li>senão, com o telefone da recepção ({@code identification.phone}): aberta → "ligue agora"; fechada → quando
 *       abre e "a partir desse horário, ligue"; horário de hoje desconhecido → "no horário de funcionamento";</li>
 *   <li>sem telefone cadastrado: nenhum texto (nunca um número inventado).</li>
 * </ul>
 * Tudo vem da base, que tem validade (DA-39): telefone ou horário vencidos deixam de ser usados.
 */
@Component
class NextStep {

    private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm");

    private final ClinicKnowledge knowledge;
    private final HandoffSettings.Replies texts;
    private final Clock clock;

    NextStep(ClinicKnowledge knowledge, HandoffSettings settings, Clock clock) {
        this.knowledge = knowledge;
        this.texts = settings.replies();
        this.clock = clock;
    }

    /** O próximo passo agora, ou vazio se a base não tem como dizer a quem recorrer. */
    Optional<String> text() {
        Optional<String> approved = known("approved-texts.direct-contact");
        if (approved.isPresent()) {
            return approved;
        }
        Optional<String> phone = known("identification.phone");
        if (phone.isEmpty()) {
            return Optional.empty();
        }
        OpeningHours.Status status = status();
        String template = switch (status.state()) {
            case OPEN -> texts.nextStepOpen();
            case CLOSED -> status.nextOpening().isPresent() ? texts.nextStepClosed() : texts.nextStepUnknown();
            case UNKNOWN -> texts.nextStepUnknown();
        };
        String when = status.nextOpening().map(NextStep::when).orElse("");
        return Optional.of(template.strip().replace("{phone}", phone.get()).replace("{when}", when));
    }

    /**
     * Emergência com a clínica fechada: avisa que está fechada e, se cadastrado, onde procurar fora do horário (P-01).
     * Vazio com a clínica aberta ou horário desconhecido.
     */
    Optional<String> emergencyWhenClosed() {
        if (status().state() != OpeningHours.State.CLOSED) {
            return Optional.empty();
        }
        String closed = texts.emergencyClosed().strip();
        return Optional.of(known("emergency.after-hours-referral")
                .map(referral -> closed + " " + texts.emergencyReferral().strip().replace("{referral}", referral))
                .orElse(closed));
    }

    OpeningHours.Status status() {
        return OpeningHours.at(knowledge, ZonedDateTime.now(clock));
    }

    private Optional<String> known(String key) {
        return knowledge.item(key).filter(KnowledgeItem::isKnown).map(i -> i.value().toString().strip());
    }

    private static String when(ZonedDateTime next) {
        return OpeningHours.dayName(next.getDayOfWeek()) + ", " + DAY_MONTH.format(next) + ", às " + HOUR.format(next);
    }
}
