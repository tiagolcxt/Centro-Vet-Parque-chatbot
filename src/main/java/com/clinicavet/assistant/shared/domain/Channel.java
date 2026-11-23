package com.clinicavet.assistant.shared.domain;

/**
 * Canal pelo qual o contato fala com a clínica: a API de testes (DM-10) ou o WhatsApp pela Cloud API (Etapa S4,
 * ADR-0023). No WhatsApp, o contato é o número do tutor ({@code wa_id}).
 */
public enum Channel {
    API,
    WHATSAPP
}
