-- V10: Buffer time entre citas y confirmaciones WhatsApp
-- Fecha: 2026-02-05

-- Agregar buffer time entre citas (0, 5, 10, 15, 30 minutos)
ALTER TABLE BUSINESS_CONFIG
ADD COLUMN BUFFER_TIME_MINUTES INT NOT NULL DEFAULT 0;

-- Agregar flag para confirmaciones por WhatsApp (separado de recordatorios)
ALTER TABLE BUSINESS_CONFIG
ADD COLUMN ENABLE_WHATSAPP_CONFIRMATIONS BOOLEAN NOT NULL DEFAULT FALSE;

-- Índice para mejorar consultas de citas por fecha
CREATE INDEX IF NOT EXISTS idx_appointments_date_business
ON APPOINTMENTS(DATE, USER_ID, STATUS);
