package com.clinicavet.assistant.shared.exception;

import java.util.Map;

/**
 * Textos de erro para pessoas (revisão de linguagem, C3). A mensagem que chega à tela não leva estado interno, nome de
 * campo técnico nem id: o código continua nas propriedades do ProblemDetail e no log, para o suporte.
 */
public final class UserText {

    private static final Map<String, String> STATES = Map.ofEntries(
            Map.entry("OPEN", "com o assistente atendendo"),
            Map.entry("WAITING_HUMAN", "aguardando atendimento"),
            Map.entry("ASSIGNED", "em atendimento"),
            Map.entry("CLOSED", "encerrada"),
            Map.entry("COLLECTING", "aguardando dados do tutor"),
            Map.entry("PENDING", "pendente"),
            Map.entry("IN_PROGRESS", "em andamento"),
            Map.entry("RESOLVED", "concluída"),
            Map.entry("CANCELLED", "cancelada"),
            Map.entry("INVITED", "com convite pendente"),
            Map.entry("ACTIVE", "ativa"),
            Map.entry("DISABLED", "desativada"),
            Map.entry("DRAFT", "em rascunho"),
            Map.entry("PUBLISHED", "publicada"),
            Map.entry("ARCHIVED", "arquivada"));

    private static final Map<String, String> FIELDS = Map.ofEntries(
            Map.entry("text", "Texto"),
            Map.entry("reason", "Motivo"),
            Map.entry("note", "Motivo"),
            Map.entry("departmentId", "Setor"),
            Map.entry("assigneeId", "Responsável"),
            Map.entry("status", "Situação"),
            Map.entry("outcome", "Resultado"),
            Map.entry("outcomeNote", "Observação"),
            Map.entry("risk", "Risco clínico"),
            Map.entry("priority", "Prioridade"),
            Map.entry("demandType", "Tipo da demanda"),
            Map.entry("displayName", "Nome"),
            Map.entry("name", "Nome"),
            Map.entry("email", "E-mail"),
            Map.entry("password", "Senha"),
            Map.entry("roleId", "Perfil"),
            Map.entry("departmentIds", "Setores"),
            Map.entry("jobTitle", "Cargo"),
            Map.entry("professionalRegistration", "Registro profissional"),
            Map.entry("title", "Título"),
            Map.entry("body", "Texto"),
            Map.entry("phoneNumberId", "Identificação do número"),
            Map.entry("accessToken", "Token de acesso"),
            Map.entry("wabaId", "Conta do WhatsApp Business"),
            Map.entry("days", "Período"),
            Map.entry("from", "Início do período"),
            Map.entry("to", "Fim do período"),
            Map.entry("size", "Quantidade por página"),
            Map.entry("permissions", "Permissões"),
            Map.entry("sectorScope", "O que a pessoa vê"),
            Map.entry("kind", "Tipo"));

    private UserText() {
    }

    /** Estado em português; desconhecido = "nesta situação". */
    public static String state(String code) {
        return code == null ? "nesta situação" : STATES.getOrDefault(code, "nesta situação");
    }

    /** Nome do campo como aparece na tela; desconhecido = vazio (a mensagem fica sem o nome). */
    public static String field(String code) {
        if (code == null) {
            return "";
        }
        String base = code.contains(".") ? code.substring(code.lastIndexOf('.') + 1) : code;
        return FIELDS.getOrDefault(base, "");
    }
}
