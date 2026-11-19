-- V6 — Etapa S1: setores, perfis e permissões, contas de usuário, funcionários e auditoria
-- (ADR-0020, ADR-0024, DA-42, DA-49). Instantes em UTC (DM-06).

-- Setores (clinicknowledge). Exatamente um padrão por clínica: recebe o que não tem regra.
CREATE TABLE department (
    id             BINARY(16)  NOT NULL,
    clinic_id      BINARY(16)  NOT NULL,
    name           VARCHAR(60) NOT NULL,
    kind           VARCHAR(12) NOT NULL,
    active         BOOLEAN     NOT NULL,
    is_default     BOOLEAN     NOT NULL,
    created_at     DATETIME(6) NOT NULL,
    version        BIGINT      NOT NULL,
    default_marker TINYINT GENERATED ALWAYS AS (CASE WHEN is_default THEN 1 ELSE NULL END) STORED,
    CONSTRAINT pk_department PRIMARY KEY (id),
    CONSTRAINT fk_department_clinic FOREIGN KEY (clinic_id) REFERENCES clinic (id),
    CONSTRAINT uk_department_name UNIQUE (clinic_id, name),
    CONSTRAINT uk_department_default UNIQUE (clinic_id, default_marker),
    CONSTRAINT ck_department_kind CHECK (kind IN ('RECEPTION', 'CLINICAL', 'PETSHOP', 'OTHER')),
    CONSTRAINT ck_department_default_active CHECK (NOT is_default OR active)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Perfis editáveis (platform). As permissões vêm de um catálogo fixo no código (modulo:acao).
CREATE TABLE role (
    id           BINARY(16)  NOT NULL,
    clinic_id    BINARY(16)  NOT NULL,
    name         VARCHAR(60) NOT NULL,
    sector_scope VARCHAR(4)  NOT NULL,
    template     VARCHAR(20) NULL,
    created_at   DATETIME(6) NOT NULL,
    version      BIGINT      NOT NULL,
    CONSTRAINT pk_role PRIMARY KEY (id),
    CONSTRAINT fk_role_clinic FOREIGN KEY (clinic_id) REFERENCES clinic (id),
    CONSTRAINT uk_role_name UNIQUE (clinic_id, name),
    CONSTRAINT ck_role_scope CHECK (sector_scope IN ('OWN', 'ALL'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Tabela de coleção do perfil: a clínica vem pelo perfil (FK com exclusão em cascata).
CREATE TABLE role_permission (
    role_id    BINARY(16)  NOT NULL,
    permission VARCHAR(40) NOT NULL,
    CONSTRAINT pk_role_permission PRIMARY KEY (role_id, permission),
    CONSTRAINT fk_role_permission_role FOREIGN KEY (role_id) REFERENCES role (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Contas (platform). E-mail único na plataforma: o login descobre a clínica pelo e-mail (DA-49).
-- A senha e o convite guardam só hashes.
CREATE TABLE user_account (
    id                BINARY(16)   NOT NULL,
    clinic_id         BINARY(16)   NOT NULL,
    email             VARCHAR(254) NOT NULL,
    password_hash     VARCHAR(100) NULL,
    role_id           BINARY(16)   NOT NULL,
    status            VARCHAR(10)  NOT NULL,
    failed_logins     INT          NOT NULL,
    locked_until      DATETIME(6)  NULL,
    invite_token_hash CHAR(64)     NULL,
    invite_expires_at DATETIME(6)  NULL,
    created_at        DATETIME(6)  NOT NULL,
    disabled_at       DATETIME(6)  NULL,
    version           BIGINT       NOT NULL,
    CONSTRAINT pk_user_account PRIMARY KEY (id),
    CONSTRAINT fk_user_account_clinic FOREIGN KEY (clinic_id) REFERENCES clinic (id),
    CONSTRAINT fk_user_account_role FOREIGN KEY (role_id) REFERENCES role (id),
    CONSTRAINT uk_user_account_email UNIQUE (email),
    CONSTRAINT uk_user_account_invite UNIQUE (invite_token_hash),
    CONSTRAINT ck_user_account_status CHECK (status IN ('INVITED', 'ACTIVE', 'DISABLED')),
    CONSTRAINT ck_user_account_active_password CHECK (status <> 'ACTIVE' OR password_hash IS NOT NULL),
    CONSTRAINT ck_user_account_disabled CHECK ((status = 'DISABLED') = (disabled_at IS NOT NULL)),
    CONSTRAINT ck_user_account_invite CHECK ((invite_token_hash IS NULL) = (invite_expires_at IS NULL))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
CREATE INDEX ix_user_account_clinic ON user_account (clinic_id, status);

-- Funcionários (team): 1 para 1 com a conta.
CREATE TABLE employee (
    user_account_id           BINARY(16)   NOT NULL,
    clinic_id                 BINARY(16)   NOT NULL,
    display_name              VARCHAR(120) NOT NULL,
    job_title                 VARCHAR(80)  NULL,
    professional_registration VARCHAR(40)  NULL,
    availability              VARCHAR(10)  NOT NULL,
    version                   BIGINT       NOT NULL,
    CONSTRAINT pk_employee PRIMARY KEY (user_account_id),
    CONSTRAINT fk_employee_user FOREIGN KEY (user_account_id) REFERENCES user_account (id),
    CONSTRAINT fk_employee_clinic FOREIGN KEY (clinic_id) REFERENCES clinic (id),
    CONSTRAINT ck_employee_availability CHECK (availability IN ('AVAILABLE', 'AWAY'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Tabela de coleção do funcionário: a clínica vem pelo funcionário.
CREATE TABLE employee_department (
    employee_id   BINARY(16) NOT NULL,
    department_id BINARY(16) NOT NULL,
    CONSTRAINT pk_employee_department PRIMARY KEY (employee_id, department_id),
    CONSTRAINT fk_employee_department_employee FOREIGN KEY (employee_id) REFERENCES employee (user_account_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_employee_department_department FOREIGN KEY (department_id) REFERENCES department (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Auditoria (platform): só inserção; resumo sem conteúdo de mensagem nem dado de saúde (RF-S07).
CREATE TABLE audit_event (
    id            BINARY(16)   NOT NULL,
    clinic_id     BINARY(16)   NOT NULL,
    occurred_at   DATETIME(6)  NOT NULL,
    actor_user_id BINARY(16)   NULL,
    action        VARCHAR(40)  NOT NULL,
    target_type   VARCHAR(30)  NULL,
    target_id     VARCHAR(64)  NULL,
    summary       VARCHAR(500) NULL,
    CONSTRAINT pk_audit_event PRIMARY KEY (id),
    CONSTRAINT fk_audit_event_clinic FOREIGN KEY (clinic_id) REFERENCES clinic (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
CREATE INDEX ix_audit_event_clinic ON audit_event (clinic_id, occurred_at);
