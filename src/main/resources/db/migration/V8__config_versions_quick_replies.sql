-- V8 — Etapa S3: configuração da clínica no banco, com rascunho, publicação e histórico (ADR-0026), e respostas
-- prontas da equipe (S3-16, DA-53). Instantes em UTC (DM-06).

-- Uma linha por versão da configuração. O conteúdo é o documento do modelo de informações (schema v2, DA-39),
-- em JSON, validado pelo mesmo KnowledgeSchema do arquivo. No máximo um rascunho e uma publicada por clínica:
-- a coluna gerada fica NULL nas arquivadas, e o índice único só vale para os valores não nulos.
CREATE TABLE config_version (
    id           BINARY(16)   NOT NULL,
    clinic_id    BINARY(16)   NOT NULL,
    number       INT          NOT NULL,
    status       VARCHAR(10)  NOT NULL,
    document     LONGTEXT     NOT NULL,
    note         VARCHAR(200) NULL,
    created_at   DATETIME(6)  NOT NULL,
    created_by   BINARY(16)   NULL,
    updated_at   DATETIME(6)  NOT NULL,
    published_at DATETIME(6)  NULL,
    published_by BINARY(16)   NULL,
    version      BIGINT       NOT NULL,
    live_slot    VARCHAR(10) GENERATED ALWAYS AS (CASE WHEN status IN ('DRAFT', 'PUBLISHED') THEN status END) STORED,
    CONSTRAINT pk_config_version PRIMARY KEY (id),
    CONSTRAINT fk_config_version_clinic FOREIGN KEY (clinic_id) REFERENCES clinic (id),
    CONSTRAINT uk_config_version_number UNIQUE (clinic_id, number),
    CONSTRAINT uk_config_version_live UNIQUE (clinic_id, live_slot),
    CONSTRAINT ck_config_version_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Respostas prontas: textos que a equipe insere ao responder ("como chegar", "preço do banho"). Não passam
-- pela IA e não são versionadas: valem na hora, como os setores.
CREATE TABLE quick_reply (
    id         BINARY(16)    NOT NULL,
    clinic_id  BINARY(16)    NOT NULL,
    title      VARCHAR(60)   NOT NULL,
    body       VARCHAR(2000) NOT NULL,
    created_at DATETIME(6)   NOT NULL,
    updated_at DATETIME(6)   NOT NULL,
    version    BIGINT        NOT NULL,
    CONSTRAINT pk_quick_reply PRIMARY KEY (id),
    CONSTRAINT fk_quick_reply_clinic FOREIGN KEY (clinic_id) REFERENCES clinic (id),
    CONSTRAINT uk_quick_reply_title UNIQUE (clinic_id, title)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
