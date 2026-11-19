-- V7 — Etapa S2: caixa de entrada e solicitações por setor (DA-42, DA-47; modelo de dados, seção 4).
-- Conversa assumida por funcionário (ASSIGNED), mensagens da equipe (STAFF), setor e responsável da solicitação,
-- notas internas e histórico da solicitação. Instantes em UTC (DM-06).

-- Conversa: estado ASSIGNED, responsável e setor do encaminhamento.
ALTER TABLE conversation
    ADD COLUMN assignee_user_id      BINARY(16) NULL,
    ADD COLUMN waiting_department_id BINARY(16) NULL,
    ADD CONSTRAINT fk_conversation_assignee FOREIGN KEY (assignee_user_id) REFERENCES user_account (id),
    ADD CONSTRAINT fk_conversation_department FOREIGN KEY (waiting_department_id) REFERENCES department (id);
ALTER TABLE conversation DROP CHECK ck_conversation_status;
ALTER TABLE conversation ADD CONSTRAINT ck_conversation_status
    CHECK (status IN ('OPEN', 'WAITING_HUMAN', 'ASSIGNED', 'CLOSED'));
ALTER TABLE conversation ADD CONSTRAINT ck_conversation_assignee
    CHECK ((status = 'ASSIGNED') = (assignee_user_id IS NOT NULL));
CREATE INDEX ix_conversation_assignee ON conversation (clinic_id, assignee_user_id, status);

-- Mensagem: autor STAFF, com o funcionário.
ALTER TABLE message
    ADD COLUMN staff_user_id BINARY(16) NULL,
    ADD CONSTRAINT fk_message_staff FOREIGN KEY (staff_user_id) REFERENCES user_account (id);
ALTER TABLE message DROP CHECK ck_message_author;
ALTER TABLE message ADD CONSTRAINT ck_message_author CHECK (author IN ('CUSTOMER', 'ASSISTANT', 'STAFF'));
ALTER TABLE message ADD CONSTRAINT ck_message_staff CHECK ((author = 'STAFF') = (staff_user_id IS NOT NULL));

-- Solicitação: setor obrigatório (as antigas vão para o setor padrão de cada clínica) e responsável.
ALTER TABLE service_request
    ADD COLUMN department_id    BINARY(16) NULL,
    ADD COLUMN assignee_user_id BINARY(16) NULL;
UPDATE service_request sr
    JOIN department d ON d.clinic_id = sr.clinic_id AND d.is_default
SET sr.department_id = d.id;
-- Clínica sem setores ainda (criados na inicialização, ClinicDefaults): cria a Recepção agora.
INSERT INTO department (id, clinic_id, name, kind, active, is_default, created_at, version)
SELECT UUID_TO_BIN(UUID()), c.id, 'Recepção', 'RECEPTION', TRUE, TRUE, UTC_TIMESTAMP(6), 0
FROM clinic c
WHERE NOT EXISTS (SELECT 1 FROM department d WHERE d.clinic_id = c.id);
UPDATE service_request sr
    JOIN department d ON d.clinic_id = sr.clinic_id AND d.is_default
SET sr.department_id = d.id
WHERE sr.department_id IS NULL;
ALTER TABLE service_request
    MODIFY department_id BINARY(16) NOT NULL,
    ADD CONSTRAINT fk_service_request_department FOREIGN KEY (department_id) REFERENCES department (id),
    ADD CONSTRAINT fk_service_request_assignee FOREIGN KEY (assignee_user_id) REFERENCES user_account (id),
    ADD CONSTRAINT ck_service_request_in_progress CHECK (status <> 'IN_PROGRESS' OR assignee_user_id IS NOT NULL);
CREATE INDEX ix_service_request_queue ON service_request (clinic_id, department_id, status, created_at);

-- Histórico da solicitação: toda mudança de setor, responsável ou estado.
CREATE TABLE service_request_event (
    id               BINARY(16)   NOT NULL,
    clinic_id        BINARY(16)   NOT NULL,
    request_id       BINARY(16)   NOT NULL,
    occurred_at      DATETIME(6)  NOT NULL,
    type             VARCHAR(20)  NOT NULL,
    actor_user_id    BINARY(16)   NULL,
    from_value       VARCHAR(64)  NULL,
    to_value         VARCHAR(64)  NULL,
    reason           VARCHAR(300) NULL,
    CONSTRAINT pk_service_request_event PRIMARY KEY (id),
    CONSTRAINT fk_sr_event_clinic FOREIGN KEY (clinic_id) REFERENCES clinic (id),
    CONSTRAINT fk_sr_event_request FOREIGN KEY (request_id) REFERENCES service_request (id),
    CONSTRAINT ck_sr_event_type CHECK (type IN ('ASSIGNED', 'UNASSIGNED', 'TRANSFERRED', 'STATUS_CHANGED')),
    CONSTRAINT ck_sr_event_transfer_reason CHECK (type <> 'TRANSFERRED' OR reason IS NOT NULL)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
CREATE INDEX ix_sr_event_request ON service_request_event (request_id, occurred_at);

-- Notas internas: nunca vão ao tutor nem ao modelo (RN-S10). Só acrescentadas, nunca editadas.
CREATE TABLE internal_note (
    id              BINARY(16)    NOT NULL,
    clinic_id       BINARY(16)    NOT NULL,
    conversation_id BINARY(16)    NULL,
    request_id      BINARY(16)    NULL,
    author_user_id  BINARY(16)    NULL,
    text            VARCHAR(2000) NOT NULL,
    created_at      DATETIME(6)   NOT NULL,
    CONSTRAINT pk_internal_note PRIMARY KEY (id),
    CONSTRAINT fk_internal_note_clinic FOREIGN KEY (clinic_id) REFERENCES clinic (id),
    CONSTRAINT fk_internal_note_conversation FOREIGN KEY (conversation_id) REFERENCES conversation (id),
    CONSTRAINT fk_internal_note_request FOREIGN KEY (request_id) REFERENCES service_request (id),
    CONSTRAINT fk_internal_note_author FOREIGN KEY (author_user_id) REFERENCES user_account (id),
    CONSTRAINT ck_internal_note_target CHECK ((conversation_id IS NULL) <> (request_id IS NULL))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
CREATE INDEX ix_internal_note_conversation ON internal_note (conversation_id, created_at);
CREATE INDEX ix_internal_note_request ON internal_note (request_id, created_at);
