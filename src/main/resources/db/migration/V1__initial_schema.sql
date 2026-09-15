-- ============================================================
-- V1 - Esquema inicial: Identidad, Personal Clínico, Pacientes
-- ============================================================

-- Extensión para gen_random_uuid()
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ============================================================
-- 1. MÓDULO DE IDENTIDAD Y SEGURIDAD (RBAC)
-- ============================================================

CREATE TABLE users (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    username      VARCHAR(60)  NOT NULL UNIQUE,
    email         VARCHAR(150) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    last_login    TIMESTAMP,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    deleted_at    TIMESTAMP                              -- Soft delete
);

CREATE TABLE roles (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    code        VARCHAR(50)  NOT NULL UNIQUE,            -- ADMIN, ODONTOLOGO, SECRETARIA
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    is_system   BOOLEAN      NOT NULL DEFAULT FALSE,     -- Protege roles base
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE permissions (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    code        VARCHAR(80)  NOT NULL UNIQUE,            -- PATIENT_CREATE, BILLING_VIEW
    module      VARCHAR(50)  NOT NULL,                   -- PACIENTES, CITAS, HISTORIA_CLINICA
    description VARCHAR(255)
);

-- Pivote N:M Roles ↔ Permisos
CREATE TABLE role_permissions (
    role_id       UUID NOT NULL REFERENCES roles(id)       ON DELETE CASCADE,
    permission_id UUID NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

-- Pivote N:M Usuarios ↔ Roles
CREATE TABLE user_roles (
    user_id     UUID      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id     UUID      NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    assigned_at TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, role_id)
);

-- ============================================================
-- 2. MÓDULO DE PERSONAL CLÍNICO Y ADMINISTRATIVO
-- ============================================================

CREATE TABLE professionals (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID         UNIQUE REFERENCES users(id) ON DELETE SET NULL, -- 1:1 opcional
    document_type   VARCHAR(20)  NOT NULL,               -- CI, RUC, PASAPORTE
    document_number VARCHAR(30)  NOT NULL UNIQUE,
    first_name      VARCHAR(100) NOT NULL,
    last_name       VARCHAR(100) NOT NULL,
    specialty       VARCHAR(100) NOT NULL,               -- Odontología General, Ortodoncia, Cirugía
    license_number  VARCHAR(50)  NOT NULL UNIQUE,        -- Registro profesional/sanitario
    phone           VARCHAR(30),
    email           VARCHAR(150),
    color_tag       VARCHAR(7),                          -- Color hex para calendario (#RRGGBB)
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMP
);

-- ============================================================
-- 3. MÓDULO DE PACIENTES
-- ============================================================

CREATE TABLE patients (
    id                      UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                 UUID         UNIQUE REFERENCES users(id) ON DELETE SET NULL, -- opcional
    document_type           VARCHAR(20)  NOT NULL,
    document_number         VARCHAR(30)  NOT NULL UNIQUE,
    first_name              VARCHAR(100) NOT NULL,
    last_name               VARCHAR(100) NOT NULL,
    birth_date              DATE         NOT NULL,
    gender                  VARCHAR(20),
    blood_type              VARCHAR(10),                 -- O_POS, AB_POS, etc.
    phone                   VARCHAR(30),
    email                   VARCHAR(150),
    address                 TEXT,
    emergency_contact_name  VARCHAR(150),
    emergency_contact_phone VARCHAR(30),
    active                  BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP    NOT NULL DEFAULT NOW(),
    deleted_at              TIMESTAMP
);

-- ============================================================
-- ÍNDICES ADICIONALES DE PERFORMANCE
-- ============================================================

CREATE INDEX idx_users_email      ON users(email)      WHERE deleted_at IS NULL;
CREATE INDEX idx_users_username   ON users(username)   WHERE deleted_at IS NULL;
CREATE INDEX idx_professionals_dn ON professionals(document_number) WHERE deleted_at IS NULL;
CREATE INDEX idx_patients_dn      ON patients(document_number)      WHERE deleted_at IS NULL;

-- ============================================================
-- DATOS SEMILLA — Roles base del sistema
-- ============================================================

INSERT INTO roles (code, name, description, is_system) VALUES
    ('ADMIN',       'Administrador',  'Acceso total al sistema',                        TRUE),
    ('ODONTOLOGO',  'Odontólogo',     'Acceso a gestión clínica y citas propias',       TRUE),
    ('SECRETARIA',  'Secretaria',     'Acceso a agenda, pacientes y facturación',       TRUE);

-- ============================================================
-- DATOS SEMILLA — Permisos por módulo
-- ============================================================

INSERT INTO permissions (code, module, description) VALUES
    -- Pacientes
    ('PATIENT_CREATE', 'PACIENTES', 'Crear pacientes'),
    ('PATIENT_READ',   'PACIENTES', 'Consultar pacientes'),
    ('PATIENT_UPDATE', 'PACIENTES', 'Editar pacientes'),
    ('PATIENT_DELETE', 'PACIENTES', 'Eliminar pacientes'),
    -- Profesionales
    ('PROFESSIONAL_CREATE', 'PROFESIONALES', 'Crear profesionales'),
    ('PROFESSIONAL_READ',   'PROFESIONALES', 'Consultar profesionales'),
    ('PROFESSIONAL_UPDATE', 'PROFESIONALES', 'Editar profesionales'),
    ('PROFESSIONAL_DELETE', 'PROFESIONALES', 'Eliminar profesionales'),
    -- Citas
    ('APPOINTMENT_CREATE', 'CITAS', 'Crear citas'),
    ('APPOINTMENT_READ',   'CITAS', 'Consultar citas'),
    ('APPOINTMENT_UPDATE', 'CITAS', 'Editar citas'),
    ('APPOINTMENT_DELETE', 'CITAS', 'Cancelar citas'),
    -- Facturación
    ('BILLING_CREATE', 'FACTURACION', 'Registrar abonos'),
    ('BILLING_READ',   'FACTURACION', 'Ver facturación'),
    -- Historia clínica
    ('RECORD_CREATE', 'HISTORIA_CLINICA', 'Crear historia clínica'),
    ('RECORD_READ',   'HISTORIA_CLINICA', 'Consultar historia clínica'),
    ('RECORD_UPDATE', 'HISTORIA_CLINICA', 'Editar historia clínica'),
    -- Usuarios y roles (solo Admin)
    ('USER_MANAGE',       'ADMINISTRACION', 'Gestionar usuarios del sistema'),
    ('ROLE_MANAGE',       'ADMINISTRACION', 'Gestionar roles y permisos'),
    ('DASHBOARD_VIEW',    'DASHBOARD',      'Ver métricas del dashboard');
