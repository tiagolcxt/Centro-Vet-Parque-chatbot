-- V5 — Etapa S1: clínica (tenant) e clinic_id em todas as tabelas de dados da clínica (ADR-0021, DA-43).
-- As linhas existentes (só dados fictícios, DA-17) passam a pertencer à clínica fictícia de id fixo.

CREATE TABLE clinic (
    id               BINARY(16)   NOT NULL,
    name             VARCHAR(120) NOT NULL,
    time_zone        VARCHAR(40)  NOT NULL,
    status           VARCHAR(12)  NOT NULL,
    activation_level TINYINT      NOT NULL,
    created_at       DATETIME(6)  NOT NULL,
    CONSTRAINT pk_clinic PRIMARY KEY (id),
    CONSTRAINT ck_clinic_status CHECK (status IN ('ACTIVE', 'SUSPENDED')),
    CONSTRAINT ck_clinic_activation_level CHECK (activation_level BETWEEN 1 AND 3)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Clínica fictícia (desenvolvimento e testes). O nível 3 mantém o comportamento atual do assistente (ADR-0025).
INSERT INTO clinic (id, name, time_zone, status, activation_level, created_at)
VALUES (UUID_TO_BIN('00000000-0000-4000-8000-000000000001'), 'Clínica Fictícia', 'America/Sao_Paulo', 'ACTIVE', 3,
        UTC_TIMESTAMP(6));

-- Cada tabela: coluna anulável, preenchimento com a clínica fictícia, NOT NULL e FK.
ALTER TABLE tutor ADD COLUMN clinic_id BINARY(16) NULL AFTER id;
ALTER TABLE pet ADD COLUMN clinic_id BINARY(16) NULL AFTER id;
ALTER TABLE conversation ADD COLUMN clinic_id BINARY(16) NULL AFTER id;
ALTER TABLE message ADD COLUMN clinic_id BINARY(16) NULL AFTER id;
ALTER TABLE service_request ADD COLUMN clinic_id BINARY(16) NULL AFTER id;
ALTER TABLE priority_change ADD COLUMN clinic_id BINARY(16) NULL AFTER id;
ALTER TABLE data_provenance ADD COLUMN clinic_id BINARY(16) NULL AFTER id;

UPDATE tutor SET clinic_id = UUID_TO_BIN('00000000-0000-4000-8000-000000000001');
UPDATE pet SET clinic_id = UUID_TO_BIN('00000000-0000-4000-8000-000000000001');
UPDATE conversation SET clinic_id = UUID_TO_BIN('00000000-0000-4000-8000-000000000001');
UPDATE message SET clinic_id = UUID_TO_BIN('00000000-0000-4000-8000-000000000001');
UPDATE service_request SET clinic_id = UUID_TO_BIN('00000000-0000-4000-8000-000000000001');
UPDATE priority_change SET clinic_id = UUID_TO_BIN('00000000-0000-4000-8000-000000000001');
UPDATE data_provenance SET clinic_id = UUID_TO_BIN('00000000-0000-4000-8000-000000000001');

ALTER TABLE tutor MODIFY clinic_id BINARY(16) NOT NULL,
    ADD CONSTRAINT fk_tutor_clinic FOREIGN KEY (clinic_id) REFERENCES clinic (id);
ALTER TABLE pet MODIFY clinic_id BINARY(16) NOT NULL,
    ADD CONSTRAINT fk_pet_clinic FOREIGN KEY (clinic_id) REFERENCES clinic (id);
ALTER TABLE conversation MODIFY clinic_id BINARY(16) NOT NULL,
    ADD CONSTRAINT fk_conversation_clinic FOREIGN KEY (clinic_id) REFERENCES clinic (id);
ALTER TABLE message MODIFY clinic_id BINARY(16) NOT NULL,
    ADD CONSTRAINT fk_message_clinic FOREIGN KEY (clinic_id) REFERENCES clinic (id);
ALTER TABLE service_request MODIFY clinic_id BINARY(16) NOT NULL,
    ADD CONSTRAINT fk_service_request_clinic FOREIGN KEY (clinic_id) REFERENCES clinic (id);
ALTER TABLE priority_change MODIFY clinic_id BINARY(16) NOT NULL,
    ADD CONSTRAINT fk_priority_change_clinic FOREIGN KEY (clinic_id) REFERENCES clinic (id);
ALTER TABLE data_provenance MODIFY clinic_id BINARY(16) NOT NULL,
    ADD CONSTRAINT fk_data_provenance_clinic FOREIGN KEY (clinic_id) REFERENCES clinic (id);

-- Chaves únicas de negócio por clínica: o mesmo telefone pode ser contato de duas clínicas.
ALTER TABLE tutor DROP INDEX uk_tutor_contact,
    ADD CONSTRAINT uk_tutor_contact UNIQUE (clinic_id, channel, external_contact_id);
DROP INDEX uk_conversation_active_contact ON conversation;
CREATE UNIQUE INDEX uk_conversation_active_contact
    ON conversation (clinic_id, channel, external_contact_id, active_marker);

-- Listagens começam por clinic_id.
DROP INDEX ix_conversation_contact_status ON conversation;
CREATE INDEX ix_conversation_contact_status ON conversation (clinic_id, channel, external_contact_id, status);
DROP INDEX ix_conversation_status ON conversation;
CREATE INDEX ix_conversation_status ON conversation (clinic_id, status);
DROP INDEX ix_conversation_queue ON conversation;
CREATE INDEX ix_conversation_queue ON conversation (clinic_id, status, clinical_priority, handoff_at);
DROP INDEX ix_service_request_status ON service_request;
CREATE INDEX ix_service_request_status ON service_request (clinic_id, status, created_at);
