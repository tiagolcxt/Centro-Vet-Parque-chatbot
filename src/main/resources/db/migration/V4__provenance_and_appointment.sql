-- V4 — Etapa 5: proveniência por campo (ADR-0010, DA-35), espécie do pet opcional e detalhes da solicitação de
-- agendamento em colunas próprias (DA-19). Instantes em UTC (DM-06).

-- Proveniência por campo. Só MENTIONED e CONFIRMED são gravados, sempre com a conversa em que aconteceram;
-- CADASTRADA / A_RECONFIRMAR são calculados ao montar o contexto de outra conversa (DA-35).
CREATE TABLE data_provenance (
    id              BINARY(16)  NOT NULL,
    subject         VARCHAR(10) NOT NULL,
    subject_id      BINARY(16)  NOT NULL,
    field           VARCHAR(20) NOT NULL,
    status          VARCHAR(12) NOT NULL,
    conversation_id BINARY(16)  NOT NULL,
    confirmed_at    DATETIME(6) NULL,
    updated_at      DATETIME(6) NOT NULL,
    version         BIGINT      NOT NULL,
    CONSTRAINT pk_data_provenance PRIMARY KEY (id),
    CONSTRAINT uk_data_provenance_field UNIQUE (subject, subject_id, field),
    CONSTRAINT fk_data_provenance_conversation FOREIGN KEY (conversation_id) REFERENCES conversation (id),
    CONSTRAINT ck_data_provenance_subject CHECK (subject IN ('TUTOR', 'PET')),
    CONSTRAINT ck_data_provenance_field CHECK (field IN ('TUTOR_NAME', 'PET_NAME', 'PET_SPECIES', 'PET_BREED', 'PET_AGE')),
    CONSTRAINT ck_data_provenance_status CHECK (status IN ('MENTIONED', 'CONFIRMED')),
    CONSTRAINT ck_data_provenance_confirmed CHECK (status <> 'CONFIRMED' OR confirmed_at IS NOT NULL)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- O tutor pode dizer o nome do pet antes da espécie ("é para a Mel"). A CHECK de V1 aceita NULL.
ALTER TABLE pet MODIFY COLUMN species VARCHAR(10) NULL;

-- Solicitação de agendamento (P-13 provisório): motivo nas palavras do tutor e preferência em texto livre.
-- Não existe data/hora confirmada (DA-07).
ALTER TABLE service_request
    ADD COLUMN reason           VARCHAR(300) NULL,
    ADD COLUMN preferred_period VARCHAR(120) NULL,
    ADD CONSTRAINT ck_service_request_appointment_details CHECK (
        type = 'APPOINTMENT' OR (reason IS NULL AND preferred_period IS NULL));
