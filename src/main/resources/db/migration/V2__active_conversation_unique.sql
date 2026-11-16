-- V2 — garantia no banco de no máximo uma conversa ativa por contato (ADR-0009, Etapa 2).
--
-- O MySQL não tem índice único parcial. A coluna gerada vale 1 quando a conversa está ativa
-- (OPEN ou WAITING_HUMAN) e NULL quando está encerrada. Como um índice único aceita vários NULL,
-- só pode existir uma linha ativa por (channel, external_contact_id); as encerradas não contam.
-- A aplicação não escreve nesta coluna e a entidade JPA não a mapeia.

ALTER TABLE conversation
    ADD COLUMN active_marker TINYINT
        GENERATED ALWAYS AS (CASE WHEN status <> 'CLOSED' THEN 1 ELSE NULL END) STORED;

CREATE UNIQUE INDEX uk_conversation_active_contact
    ON conversation (channel, external_contact_id, active_marker);
