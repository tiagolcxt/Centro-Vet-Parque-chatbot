-- V11 — D9 (revisão pré-piloto, seção 3.2): risco clínico, tipo da demanda e prioridade operacional separados.
-- O nível P1–P6 continua em conversation.clinical_priority como motor das regras de segurança; o risco é derivado dele.

-- Risco avaliado: sem isso, P4–P6 vira "não avaliado" em vez de "sem sinal clínico". Conversas antigas que já têm
-- resposta válida do assistente ou prioridade acima da inicial contam como avaliadas.
ALTER TABLE conversation ADD COLUMN risk_assessed BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE conversation c SET c.risk_assessed = TRUE
WHERE c.clinical_priority <> 'P6_RELATIONSHIP'
   OR EXISTS (SELECT 1 FROM message m WHERE m.conversation_id = c.id AND m.author = 'ASSISTANT' AND m.fallback = FALSE);

-- Prioridade da fila escolhida pela equipe (vazia = regra).
ALTER TABLE conversation ADD COLUMN operational_override VARCHAR(10) NULL;
ALTER TABLE conversation ADD CONSTRAINT ck_conversation_operational_override
    CHECK (operational_override IS NULL OR operational_override IN ('IMMEDIATE', 'HIGH', 'NORMAL', 'LOW'));

-- Tipo da demanda, deduzido do fluxo para as solicitações existentes.
ALTER TABLE service_request ADD COLUMN demand_type VARCHAR(30) NULL;
UPDATE service_request SET demand_type = CASE type
    WHEN 'APPOINTMENT' THEN 'APPOINTMENT'
    WHEN 'INFORMATION' THEN 'ADMINISTRATIVE_INFO'
    WHEN 'FOLLOW_UP' THEN 'CLINICAL_QUESTION'
    ELSE 'HUMAN_CONTACT' END;
ALTER TABLE service_request MODIFY COLUMN demand_type VARCHAR(30) NOT NULL;
ALTER TABLE service_request ADD CONSTRAINT ck_service_request_demand_type CHECK (demand_type IN (
    'APPOINTMENT', 'CLINICAL_QUESTION', 'ADMINISTRATIVE_INFO', 'COMMERCIAL', 'GROOMING', 'COMPLAINT',
    'HUMAN_CONTACT', 'OTHER'));
