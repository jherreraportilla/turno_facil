-- Fase 3: WhatsApp reminders, roles y campos adicionales

-- =============================================
-- USUARIOS: Roles y tracking
-- =============================================

-- Campo para rol de usuario (ADMIN = negocio, SUPER_ADMIN = plataforma)
ALTER TABLE USERS
ADD COLUMN ROLE VARCHAR(20) NOT NULL DEFAULT 'ADMIN';

-- Campo para tracking de fecha de creacion
ALTER TABLE USERS
ADD COLUMN CREATED_AT DATETIME DEFAULT CURRENT_TIMESTAMP;

-- Campo para tracking de ultimo login
ALTER TABLE USERS
ADD COLUMN LAST_LOGIN DATETIME NULL;

-- Indice para queries de metricas
CREATE INDEX idx_users_role ON USERS (ROLE);
CREATE INDEX idx_users_created_at ON USERS (CREATED_AT);
CREATE INDEX idx_users_last_login ON USERS (LAST_LOGIN);

-- =============================================
-- CITAS: WhatsApp reminders
-- =============================================

-- Campo para tracking de recordatorios WhatsApp
ALTER TABLE APPOINTMENTS
ADD COLUMN WHATSAPP_REMINDER_SENT BOOLEAN NOT NULL DEFAULT FALSE;

-- Indice para buscar citas que necesitan recordatorio
CREATE INDEX idx_appointments_reminder_pending
ON APPOINTMENTS (DATE, REMINDER_SENT, WHATSAPP_REMINDER_SENT, STATUS);

-- =============================================
-- CONFIGURACION NEGOCIO: WhatsApp
-- =============================================

-- Campo para habilitar recordatorios por WhatsApp en la configuracion del negocio
ALTER TABLE BUSINESS_CONFIG
ADD COLUMN ENABLE_WHATSAPP_REMINDERS BOOLEAN NOT NULL DEFAULT FALSE;
