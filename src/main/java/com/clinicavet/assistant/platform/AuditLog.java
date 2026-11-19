package com.clinicavet.assistant.platform;

/**
 * Trilha de auditoria da clínica atual (RF-S07). Grava na transação de quem chama; o autor é o funcionário logado
 * (vazio = sistema ou chave de serviço). O resumo nunca leva conteúdo de mensagem, dado de saúde nem senha.
 */
public interface AuditLog {

    void record(String action, String targetType, Object targetId, String summary);
}
