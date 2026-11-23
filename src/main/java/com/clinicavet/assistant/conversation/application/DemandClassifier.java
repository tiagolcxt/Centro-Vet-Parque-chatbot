package com.clinicavet.assistant.conversation.application;

import com.clinicavet.assistant.clinicknowledge.domain.Departments;
import com.clinicavet.assistant.conversation.domain.HandoffReason;
import com.clinicavet.assistant.conversation.domain.Intent;
import com.clinicavet.assistant.servicerequest.domain.DemandType;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Optional;

/**
 * Tipo da demanda de uma solicitação nova (D9, revisão pré-piloto 3.2). Determinístico: intenção do turno, motivo do
 * encaminhamento e o setor do serviço citado (pet shop / banho e tosa). Sem informação, devolve vazio e vale o tipo
 * deduzido do fluxo. A equipe corrige pela tela.
 */
@Component
class DemandClassifier {

    private final DepartmentRouter router;

    DemandClassifier(DepartmentRouter router) {
        this.router = router;
    }

    Optional<DemandType> classify(Intent intent, Collection<String> refs, String text, HandoffReason reason) {
        if (reason == HandoffReason.COMPLAINT || intent == Intent.COMPLAINT) {
            return Optional.of(DemandType.COMPLAINT);
        }
        boolean petshop = router.kindFor(refs, text, Optional.empty())
                .filter(k -> k == Departments.Kind.PETSHOP).isPresent();
        if (intent == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(switch (intent) {
            case APPOINTMENT_REQUEST -> petshop ? DemandType.GROOMING : DemandType.APPOINTMENT;
            case MEDICAL_CONCERN, TREATMENT_FOLLOW_UP -> DemandType.CLINICAL_QUESTION;
            case SERVICE_INFORMATION, PRICE_INQUIRY -> petshop ? DemandType.COMMERCIAL : DemandType.ADMINISTRATIVE_INFO;
            case CLINIC_INFORMATION -> DemandType.ADMINISTRATIVE_INFO;
            case HUMAN_REQUEST -> DemandType.HUMAN_CONTACT;
            case COMPLAINT -> DemandType.COMPLAINT;
            case GREETING, PET_INFORMATION, OTHER -> null;
        });
    }
}
