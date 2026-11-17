-- V3 — Etapa 4: encaminhamento humano (ADR-0007), prioridade clínica (ADR-0018), metadados do turno
-- (DA-31, DA-33) e desfecho da solicitação (DA-34). Instantes em UTC (DM-06).

-- Conversa: prioridade independente do status (H9); conversa nova começa em P6 (H4).
ALTER TABLE conversation
    ADD COLUMN clinical_priority VARCHAR(30) NOT NULL DEFAULT 'P6_RELATIONSHIP',
    ADD COLUMN handoff_reason    VARCHAR(40) NULL,
    ADD COLUMN handoff_at        DATETIME(6) NULL,
    ADD COLUMN human_offer_made  BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD CONSTRAINT ck_conversation_priority CHECK (clinical_priority IN (
        'P1_POSSIBLE_EMERGENCY', 'P2_URGENT', 'P3_CLINICAL_FOLLOW_UP', 'P4_SERVICE_REQUEST',
        'P5_ADMINISTRATIVE', 'P6_RELATIONSHIP')),
    ADD CONSTRAINT ck_conversation_handoff CHECK (
        status <> 'WAITING_HUMAN' OR (handoff_reason IS NOT NULL AND handoff_at IS NOT NULL)),
    ADD CONSTRAINT ck_conversation_handoff_reason CHECK (handoff_reason IS NULL OR handoff_reason IN (
        'POSSIBLE_EMERGENCY', 'CLINICAL_PRIORITY', 'EXPLICIT_REQUEST', 'COMPLAINT', 'CLINICAL',
        'DISSATISFACTION', 'STAFF_DECISION', 'MODEL_SIGNAL', 'EMOTIONAL_DISTRESS',
        'UNPRODUCTIVE_CONVERSATION', 'TURN_FAILURE', 'CLINIC_RULE'));

-- Fila da equipe: status + prioridade + tempo de espera (H10).
CREATE INDEX ix_conversation_queue ON conversation (status, clinical_priority, handoff_at);

-- Mensagem do assistente: resposta segura (DA-31), prioridade efetiva, origem e sinais (ADR-0018, item 8),
-- intenções secundárias (DA-33). Mensagens do tutor ficam com os campos nulos / FALSE.
ALTER TABLE message
    ADD COLUMN fallback          BOOLEAN      NOT NULL DEFAULT FALSE,
    ADD COLUMN priority          VARCHAR(30)  NULL,
    ADD COLUMN priority_source   VARCHAR(20)  NULL,
    ADD COLUMN signals           VARCHAR(300) NULL,
    ADD COLUMN secondary_intents VARCHAR(100) NULL,
    ADD CONSTRAINT ck_message_assistant_fields CHECK (
        author = 'ASSISTANT' OR (fallback = FALSE AND priority IS NULL AND priority_source IS NULL
                                 AND signals IS NULL AND secondary_intents IS NULL)),
    ADD CONSTRAINT ck_message_priority_source CHECK (
        priority_source IS NULL OR priority_source IN ('MODEL', 'BACKEND_FLOOR', 'CURRENT_LEVEL'));

-- Mudanças de prioridade feitas pela equipe: o único caminho de rebaixamento (H4).
-- Autor individual só a partir da Etapa 7 (ADR-0012); até lá, o registro diz que foi pela API Key.
CREATE TABLE priority_change (
    id              BINARY(16)   NOT NULL,
    conversation_id BINARY(16)   NOT NULL,
    from_priority   VARCHAR(30)  NOT NULL,
    to_priority     VARCHAR(30)  NOT NULL,
    changed_by      VARCHAR(60)  NOT NULL,
    note            VARCHAR(500) NULL,
    changed_at      DATETIME(6)  NOT NULL,
    CONSTRAINT pk_priority_change PRIMARY KEY (id),
    CONSTRAINT fk_priority_change_conversation FOREIGN KEY (conversation_id) REFERENCES conversation (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX ix_priority_change_conversation ON priority_change (conversation_id, changed_at);

-- Solicitação: desfecho obrigatório ao resolver ou cancelar (DA-34).
ALTER TABLE service_request
    ADD COLUMN outcome      VARCHAR(30)  NULL,
    ADD COLUMN outcome_note VARCHAR(500) NULL,
    ADD CONSTRAINT ck_service_request_outcome CHECK (
        (status IN ('RESOLVED', 'CANCELLED')) = (outcome IS NOT NULL)),
    ADD CONSTRAINT ck_service_request_outcome_value CHECK (outcome IS NULL OR outcome IN (
        'SCHEDULED_BY_STAFF', 'INFORMATION_PROVIDED', 'HANDLED_OUTSIDE', 'NO_AVAILABILITY',
        'CUSTOMER_GAVE_UP', 'NO_RESPONSE', 'DUPLICATE', 'OTHER'));
