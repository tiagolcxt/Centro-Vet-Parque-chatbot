-- V9 — Etapa S4: canal WhatsApp pela Cloud API (ADR-0023, DA-46, DA-53). Instantes em UTC (DM-06).

-- Conta do WhatsApp da clínica. phone_number_id é único NA PLATAFORMA: é por ele que o webhook descobre a clínica,
-- antes de existir sessão. O token fica cifrado (AES-GCM, chave em variável de ambiente) e nunca vai para log.
CREATE TABLE channel_account (
    id                     BINARY(16)    NOT NULL,
    clinic_id              BINARY(16)    NOT NULL,
    provider               VARCHAR(20)   NOT NULL,
    phone_number_id        VARCHAR(40)   NOT NULL,
    display_phone          VARCHAR(30)   NULL,
    waba_id                VARCHAR(40)   NULL,
    access_token_encrypted VARCHAR(2000) NOT NULL,
    status                 VARCHAR(10)   NOT NULL,
    created_at             DATETIME(6)   NOT NULL,
    updated_at             DATETIME(6)   NOT NULL,
    version                BIGINT        NOT NULL,
    CONSTRAINT pk_channel_account PRIMARY KEY (id),
    CONSTRAINT fk_channel_account_clinic FOREIGN KEY (clinic_id) REFERENCES clinic (id),
    CONSTRAINT uk_channel_account_phone UNIQUE (phone_number_id),
    CONSTRAINT uk_channel_account_clinic UNIQUE (clinic_id, provider),
    CONSTRAINT ck_channel_account_provider CHECK (provider IN ('WHATSAPP_CLOUD')),
    CONSTRAINT ck_channel_account_status CHECK (status IN ('ACTIVE', 'DISABLED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Mensagens recebidas pelo webhook (RF-S50, RF-S51). A deduplicação é pelo wamid: a Meta reenvia o mesmo evento
-- quando não recebe 200 a tempo. O texto não fica aqui: vai para message. ECHO = enviada pelo app do celular
-- (coexistência, smb_message_echoes).
CREATE TABLE inbound_message (
    id           BINARY(16)   NOT NULL,
    clinic_id    BINARY(16)   NOT NULL,
    wamid        VARCHAR(255) NOT NULL,
    contact      VARCHAR(128) NOT NULL,
    kind         VARCHAR(10)  NOT NULL,
    media_id     VARCHAR(255) NULL,
    media_mime   VARCHAR(100) NULL,
    media_name   VARCHAR(255) NULL,
    sent_at      DATETIME(6)  NOT NULL,
    received_at  DATETIME(6)  NOT NULL,
    processed_at DATETIME(6)  NULL,
    attempts     INT          NOT NULL DEFAULT 0,
    message_id   BINARY(16)   NULL,
    payload_text TEXT         NULL,
    version      BIGINT       NOT NULL,
    CONSTRAINT pk_inbound_message PRIMARY KEY (id),
    CONSTRAINT fk_inbound_message_clinic FOREIGN KEY (clinic_id) REFERENCES clinic (id),
    CONSTRAINT uk_inbound_message_wamid UNIQUE (clinic_id, wamid),
    CONSTRAINT ck_inbound_message_kind CHECK (kind IN ('TEXT', 'IMAGE', 'DOCUMENT', 'AUDIO', 'VIDEO', 'OTHER', 'ECHO'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
CREATE INDEX ix_inbound_message_pending ON inbound_message (processed_at, received_at);
CREATE INDEX ix_inbound_message_message ON inbound_message (message_id);

-- Outbox (RF-S52, RF-S55): gravada na mesma transação da resposta; o despachante envia e tenta de novo com espera
-- crescente. O status vem do webhook (enviada, entregue, lida, falhou).
CREATE TABLE outbound_message (
    id              BINARY(16)   NOT NULL,
    clinic_id       BINARY(16)   NOT NULL,
    conversation_id BINARY(16)   NOT NULL,
    message_id      BINARY(16)   NOT NULL,
    recipient       VARCHAR(128) NOT NULL,
    status          VARCHAR(10)  NOT NULL,
    attempts        INT          NOT NULL,
    next_attempt_at DATETIME(6)  NOT NULL,
    wamid           VARCHAR(255) NULL,
    last_error_code VARCHAR(40)  NULL,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    version         BIGINT       NOT NULL,
    CONSTRAINT pk_outbound_message PRIMARY KEY (id),
    CONSTRAINT fk_outbound_message_clinic FOREIGN KEY (clinic_id) REFERENCES clinic (id),
    CONSTRAINT uk_outbound_message_message UNIQUE (message_id),
    CONSTRAINT ck_outbound_message_status CHECK (status IN ('PENDING', 'SENT', 'DELIVERED', 'READ', 'FAILED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
CREATE INDEX ix_outbound_message_due ON outbound_message (status, next_attempt_at);
CREATE INDEX ix_outbound_message_wamid ON outbound_message (wamid);

-- Resposta dada pelo app do WhatsApp Business no celular (coexistência): aparece na conversa, não é reenviada.
ALTER TABLE message DROP CHECK ck_message_author;
ALTER TABLE message ADD CONSTRAINT ck_message_author CHECK (author IN ('CUSTOMER', 'ASSISTANT', 'STAFF', 'BUSINESS_APP'));
