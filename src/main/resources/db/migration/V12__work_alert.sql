-- V12 — Alerta de trabalho esquecido (E3, achado A10 da simulação de 1 mês).
-- Um alerta por pendência: conversa aguardando a equipe além do prazo do nível, ou solicitação sem responsável além do
-- prazo. "pending_since" identifica o episódio: se a conversa sai da espera e volta, é outro alerta.

CREATE TABLE work_alert (
    id              BINARY(16)  NOT NULL,
    clinic_id       BINARY(16)  NOT NULL,
    kind            VARCHAR(30) NOT NULL,
    subject_id      BINARY(16)  NOT NULL,
    pending_since   DATETIME(6) NOT NULL,
    raised_at       DATETIME(6) NOT NULL,
    escalated_at    DATETIME(6) NULL,
    acknowledged_by BINARY(16)  NULL,
    acknowledged_at DATETIME(6) NULL,
    closed_at       DATETIME(6) NULL,
    version         BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT pk_work_alert PRIMARY KEY (id),
    CONSTRAINT fk_work_alert_clinic FOREIGN KEY (clinic_id) REFERENCES clinic (id),
    CONSTRAINT fk_work_alert_acknowledged_by FOREIGN KEY (acknowledged_by) REFERENCES user_account (id),
    CONSTRAINT uq_work_alert_episode UNIQUE (clinic_id, kind, subject_id, pending_since),
    CONSTRAINT ck_work_alert_kind CHECK (kind IN ('CONVERSATION_WAITING', 'REQUEST_UNASSIGNED')),
    CONSTRAINT ck_work_alert_ack CHECK ((acknowledged_by IS NULL) = (acknowledged_at IS NULL))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
CREATE INDEX ix_work_alert_open ON work_alert (clinic_id, closed_at);
