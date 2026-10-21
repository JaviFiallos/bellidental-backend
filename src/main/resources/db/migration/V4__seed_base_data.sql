-- 1. ROLES
INSERT INTO roles (role_id, code, name, description) VALUES
(1, 'ADMIN', 'Administrador', 'Acceso total al sistema'),
(2, 'ODONTOLOGO', 'Odontólogo', 'Personal médico de la clínica'),
(3, 'SECRETARIA', 'Secretaria', 'Personal administrativo y recepción');

SELECT setval('roles_role_id_seq', (SELECT MAX(role_id) FROM roles));

-- 2. PERMISOS
INSERT INTO permissions (permission_id, code, module, description) VALUES
(1, 'PATIENT_CREATE', 'patients', 'Crear pacientes'),
(2, 'PATIENT_READ', 'patients', 'Ver pacientes'),
(3, 'PATIENT_UPDATE', 'patients', 'Actualizar pacientes'),
(4, 'PROFESSIONAL_READ', 'professionals', 'Ver profesionales'),
(5, 'PROFESSIONAL_MANAGE', 'professionals', 'Gestionar profesionales'),
(6, 'APPOINTMENT_CREATE', 'appointments', 'Crear citas'),
(7, 'APPOINTMENT_READ', 'appointments', 'Ver citas'),
(8, 'APPOINTMENT_UPDATE', 'appointments', 'Actualizar citas'),
(9, 'APPOINTMENT_CANCEL', 'appointments', 'Cancelar citas'),
(10, 'BILLING_CREATE', 'billing', 'Crear y anular pagos'),
(11, 'BILLING_READ', 'billing', 'Ver pagos y finanzas'),
(12, 'RECORD_CREATE', 'clinical', 'Abrir y escribir en historias clínicas'),
(13, 'RECORD_READ', 'clinical', 'Ver historias clínicas'),
(14, 'RECORD_UPDATE', 'clinical', 'Actualizar historias clínicas'),
(15, 'DASHBOARD_VIEW', 'dashboard', 'Ver métricas del panel principal'),
(16, 'SCHEDULE_MANAGE', 'professionals', 'Gestionar horarios de atención');

SELECT setval('permissions_permission_id_seq', (SELECT MAX(permission_id) FROM permissions));

-- 3. ROLE_PERMISSIONS
-- ADMIN: Todos los permisos (1 al 16)
INSERT INTO role_permissions (role_id, permission_id)
SELECT 1, permission_id FROM permissions;

-- ODONTOLOGO: PATIENT_READ, PROFESSIONAL_READ, APPOINTMENT_*, RECORD_*, DASHBOARD_VIEW
INSERT INTO role_permissions (role_id, permission_id) VALUES
(2, 2), -- PATIENT_READ
(2, 4), -- PROFESSIONAL_READ
(2, 6), (2, 7), (2, 8), (2, 9), -- APPOINTMENT_*
(2, 12), (2, 13), (2, 14), -- RECORD_*
(2, 15); -- DASHBOARD_VIEW

-- SECRETARIA: PATIENT_CREATE/READ/UPDATE, PROFESSIONAL_READ, APPOINTMENT_*, BILLING_*, SCHEDULE_MANAGE
INSERT INTO role_permissions (role_id, permission_id) VALUES
(3, 1), (3, 2), (3, 3), -- PATIENT_CREATE/READ/UPDATE
(3, 4), -- PROFESSIONAL_READ
(3, 6), (3, 7), (3, 8), (3, 9), -- APPOINTMENT_*
(3, 10), (3, 11), -- BILLING_*
(3, 16); -- SCHEDULE_MANAGE

-- 4. ESPECIALIDADES
INSERT INTO specialties (specialty_id, name, default_duration_minutes, active) VALUES
(1, 'Odontología general', 30, true),
(2, 'Endodoncia', 60, true);

SELECT setval('specialties_specialty_id_seq', (SELECT MAX(specialty_id) FROM specialties));

-- 5. USUARIOS
-- Hash BCrypt para 'Cambiar.2026!' generado por Python passlib/bcrypt
INSERT INTO users (user_id, username, email, password_hash, is_system, active) VALUES
('11111111-1111-1111-1111-111111111111', 'director', 'director@bellidental.local', '$2b$12$7GJX/I3cXGvQAK6yUTKLcOVazmu3MwnnmRmZsa2sN8pftmCDkmflS', false, true),
('22222222-2222-2222-2222-222222222222', 'odontologa', 'odontologa@bellidental.local', '$2b$12$7GJX/I3cXGvQAK6yUTKLcOVazmu3MwnnmRmZsa2sN8pftmCDkmflS', false, true),
('33333333-3333-3333-3333-333333333333', 'secretaria', 'secretaria@bellidental.local', '$2b$12$7GJX/I3cXGvQAK6yUTKLcOVazmu3MwnnmRmZsa2sN8pftmCDkmflS', false, true),
('44444444-4444-4444-4444-444444444444', 'bot_whatsapp', 'bot@bellidental.local', '!', true, true);

-- 6. USER_ROLES
INSERT INTO user_roles (user_id, role_id) VALUES
('11111111-1111-1111-1111-111111111111', 1), -- director -> ADMIN
('11111111-1111-1111-1111-111111111111', 2), -- director -> ODONTOLOGO
('22222222-2222-2222-2222-222222222222', 2), -- odontologa -> ODONTOLOGO
('33333333-3333-3333-3333-333333333333', 3); -- secretaria -> SECRETARIA

-- 7. PROFESSIONALS (director y odontologa)
INSERT INTO professionals (professional_id, user_id, document_type, document_number, first_name, last_name, license_number, color_tag, active) VALUES
('55555555-5555-5555-5555-555555555555', '11111111-1111-1111-1111-111111111111', 'CI', 'PENDIENTE-1', 'Director', 'Médico', 'REG-PENDIENTE-1', '#1E88E5', true),
('66666666-6666-6666-6666-666666666666', '22222222-2222-2222-2222-222222222222', 'CI', 'PENDIENTE-2', 'Odontóloga', 'Principal', 'REG-PENDIENTE-2', '#43A047', true);

-- 8. PROFESSIONAL_SPECIALTIES
INSERT INTO professional_specialties (professional_id, specialty_id, active) VALUES
('55555555-5555-5555-5555-555555555555', 1, true),
('66666666-6666-6666-6666-666666666666', 1, true);
