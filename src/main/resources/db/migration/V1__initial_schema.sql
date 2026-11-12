-- V1 — schema inicial (Etapa 1). ADR-0005: migrations nunca são editadas depois de aplicadas.
-- UUID em BINARY(16); instantes em DATETIME(6), sempre UTC (DM-06).
-- Colunas de encaminhamento e prioridade (E4) e de proveniência e agendamento (E5) entram
-- em migrations futuras.

CREATE TABLE tutor (
    id                  BINARY(16)   NOT NULL,
    channel             VARCHAR(20)  NOT NULL,
    external_contact_id VARCHAR(128) NOT NULL,
    name                VARCHAR(120) NULL,
    phone               VARCHAR(30)  NULL,
    first_seen_at       DATETIME(6)  NOT NULL,
    last_seen_at        DATETIME(6)  NOT NULL,
    version             BIGINT       NOT NULL,
    CONSTRAINT pk_tutor PRIMARY KEY (id),
    CONSTRAINT uk_tutor_contact UNIQUE (channel, external_contact_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE pet (
    id                  BINARY(16)  NOT NULL,
    tutor_id            BINARY(16)  NOT NULL,
    name                VARCHAR(80) NOT NULL,
    species             VARCHAR(10) NOT NULL,
    species_description VARCHAR(80) NULL,
    breed               VARCHAR(80) NULL,
    age_description     VARCHAR(80) NULL,
    created_at          DATETIME(6) NOT NULL,
    version             BIGINT      NOT NULL,
    CONSTRAINT pk_pet PRIMARY KEY (id),
    CONSTRAINT fk_pet_tutor FOREIGN KEY (tutor_id) REFERENCES tutor (id),
    CONSTRAINT ck_pet_species CHECK (species IN ('DOG', 'CAT', 'OTHER'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX ix_pet_tutor ON pet (tutor_id, created_at);

CREATE TABLE conversation (
    id                  BINARY(16)   NOT NULL,
    channel             VARCHAR(20)  NOT NULL,
    external_contact_id VARCHAR(128) NOT NULL,
    tutor_id            BINARY(16)   NULL,
    status              VARCHAR(20)  NOT NULL,
    current_intent      VARCHAR(40)  NULL,
    close_reason        VARCHAR(30)  NULL,
    started_at          DATETIME(6)  NOT NULL,
    last_activity_at    DATETIME(6)  NOT NULL,
    closed_at           DATETIME(6)  NULL,
    version             BIGINT       NOT NULL,
    CONSTRAINT pk_conversation PRIMARY KEY (id),
    CONSTRAINT fk_conversation_tutor FOREIGN KEY (tutor_id) REFERENCES tutor (id),
    CONSTRAINT ck_conversation_status CHECK (status IN ('OPEN', 'WAITING_HUMAN', 'CLOSED')),
    CONSTRAINT ck_conversation_closed CHECK (
        (status = 'CLOSED' AND close_reason IS NOT NULL AND closed_at IS NOT NULL)
        OR (status <> 'CLOSED' AND close_reason IS NULL AND closed_at IS NULL))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Busca da conversa ativa de um contato (ADR-0009). A garantia de unicidade da conversa
-- ativa no banco é verificada na Etapa 2.
CREATE INDEX ix_conversation_contact_status ON conversation (channel, external_contact_id, status);
CREATE INDEX ix_conversation_status ON conversation (status);

CREATE TABLE message (
    id              BINARY(16)  NOT NULL,
    conversation_id BINARY(16)  NOT NULL,
    author          VARCHAR(20) NOT NULL,
    content         TEXT        NOT NULL,
    intent          VARCHAR(40) NULL,
    created_at      DATETIME(6) NOT NULL,
    CONSTRAINT pk_message PRIMARY KEY (id),
    CONSTRAINT fk_message_conversation FOREIGN KEY (conversation_id) REFERENCES conversation (id),
    CONSTRAINT ck_message_author CHECK (author IN ('CUSTOMER', 'ASSISTANT')),
    CONSTRAINT ck_message_intent CHECK (author = 'ASSISTANT' OR intent IS NULL)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX ix_message_conversation_created ON message (conversation_id, created_at);

CREATE TABLE service_request (
    id              BINARY(16)   NOT NULL,
    conversation_id BINARY(16)   NOT NULL,
    tutor_id        BINARY(16)   NULL,
    pet_id          BINARY(16)   NULL,
    type            VARCHAR(20)  NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    summary         VARCHAR(500) NULL,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    version         BIGINT       NOT NULL,
    CONSTRAINT pk_service_request PRIMARY KEY (id),
    CONSTRAINT fk_service_request_conversation FOREIGN KEY (conversation_id) REFERENCES conversation (id),
    CONSTRAINT fk_service_request_tutor FOREIGN KEY (tutor_id) REFERENCES tutor (id),
    CONSTRAINT fk_service_request_pet FOREIGN KEY (pet_id) REFERENCES pet (id),
    CONSTRAINT ck_service_request_type CHECK (type IN ('APPOINTMENT', 'INFORMATION', 'FOLLOW_UP', 'HUMAN_CONTACT')),
    CONSTRAINT ck_service_request_status CHECK (status IN ('COLLECTING', 'PENDING', 'IN_PROGRESS', 'RESOLVED', 'CANCELLED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX ix_service_request_status ON service_request (status, created_at);
CREATE INDEX ix_service_request_conversation ON service_request (conversation_id);
