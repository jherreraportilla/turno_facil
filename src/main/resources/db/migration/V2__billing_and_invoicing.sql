-- =====================================================
-- MIGRACIÓN V2: Sistema de Facturación con Inmutabilidad
-- TurnoFácil - Cumplimiento Legal
-- =====================================================

-- =====================================================
-- 1. TABLA: billing_profiles (Datos fiscales del negocio)
-- =====================================================
CREATE TABLE IF NOT EXISTS `billing_profiles` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,

  -- Datos fiscales
  `tax_id` varchar(20) NOT NULL COMMENT 'NIF/CIF',
  `legal_name` varchar(200) NOT NULL COMMENT 'Razón social',
  `address_line1` varchar(200) NOT NULL,
  `address_line2` varchar(200) DEFAULT NULL,
  `city` varchar(100) NOT NULL,
  `postal_code` varchar(10) NOT NULL,
  `province` varchar(100) NOT NULL,
  `country` varchar(2) NOT NULL DEFAULT 'ES',

  -- Configuración fiscal
  `vat_regime` enum('GENERAL','REDUCED','SUPER_REDUCED','EXEMPT') NOT NULL DEFAULT 'GENERAL',
  `vat_rate` decimal(5,2) NOT NULL DEFAULT 21.00,

  -- Configuración de facturación
  `invoice_series` varchar(10) NOT NULL DEFAULT 'TF',
  `next_invoice_number` bigint NOT NULL DEFAULT 1,
  `invoice_notes` varchar(1000) DEFAULT NULL,
  `payment_terms` varchar(500) DEFAULT NULL,

  -- Datos bancarios
  `bank_name` varchar(100) DEFAULT NULL,
  `iban` varchar(34) DEFAULT NULL,
  `swift_bic` varchar(11) DEFAULT NULL,

  -- Auditoría
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,

  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_billing_user` (`user_id`),
  UNIQUE KEY `uk_billing_tax_id` (`tax_id`),
  CONSTRAINT `fk_billing_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================
-- 2. TABLA: invoices (Facturas - INMUTABLE tras emisión)
-- =====================================================
CREATE TABLE IF NOT EXISTS `invoices` (
  `id` bigint NOT NULL AUTO_INCREMENT,

  -- Identificación
  `invoice_number` varchar(20) NOT NULL COMMENT 'Ej: TF-2025-00001',
  `invoice_series` varchar(10) NOT NULL,

  -- Referencias (solo navegación)
  `business_id` bigint NOT NULL,
  `appointment_id` bigint DEFAULT NULL,

  -- Snapshot emisor (INMUTABLE)
  `emitter_tax_id` varchar(20) NOT NULL,
  `emitter_legal_name` varchar(200) NOT NULL,
  `emitter_address` varchar(500) NOT NULL,
  `emitter_city` varchar(100) NOT NULL,
  `emitter_postal_code` varchar(10) NOT NULL,
  `emitter_province` varchar(100) DEFAULT NULL,
  `emitter_country` varchar(2) NOT NULL DEFAULT 'ES',

  -- Snapshot receptor (INMUTABLE)
  `client_name` varchar(100) NOT NULL,
  `client_email` varchar(120) DEFAULT NULL,
  `client_phone` varchar(20) DEFAULT NULL,
  `client_tax_id` varchar(20) DEFAULT NULL COMMENT 'NIF del cliente',
  `client_address` varchar(500) DEFAULT NULL,

  -- Totales (INMUTABLE)
  `subtotal` decimal(10,2) NOT NULL,
  `discount_total` decimal(10,2) DEFAULT 0.00,
  `taxable_base` decimal(10,2) NOT NULL COMMENT 'Base imponible',
  `vat_rate` decimal(5,2) NOT NULL,
  `vat_amount` decimal(10,2) NOT NULL,
  `total` decimal(10,2) NOT NULL,

  -- Fechas
  `issue_date` date NOT NULL,
  `due_date` date DEFAULT NULL,
  `service_date` date NOT NULL COMMENT 'Fecha del servicio',

  -- Estado
  `status` enum('DRAFT','ISSUED','PAID','CANCELLED','RECTIFIED') NOT NULL DEFAULT 'DRAFT',
  `paid_at` datetime DEFAULT NULL,
  `cancelled_at` datetime DEFAULT NULL,
  `cancellation_reason` varchar(500) DEFAULT NULL,

  -- Factura rectificativa
  `rectifies_invoice_id` bigint DEFAULT NULL,

  -- Notas
  `notes` varchar(1000) DEFAULT NULL,
  `payment_terms` varchar(500) DEFAULT NULL,

  -- Auditoría
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `issued_at` datetime DEFAULT NULL,

  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_invoice_number` (`invoice_number`),
  KEY `idx_invoice_business` (`business_id`),
  KEY `idx_invoice_appointment` (`appointment_id`),
  KEY `idx_invoice_issue_date` (`issue_date`),
  KEY `idx_invoice_status` (`status`),
  CONSTRAINT `fk_invoice_business` FOREIGN KEY (`business_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_invoice_appointment` FOREIGN KEY (`appointment_id`) REFERENCES `appointments` (`id`),
  CONSTRAINT `fk_invoice_rectifies` FOREIGN KEY (`rectifies_invoice_id`) REFERENCES `invoices` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================
-- 3. TABLA: invoice_lines (Líneas de factura - INMUTABLE)
-- =====================================================
CREATE TABLE IF NOT EXISTS `invoice_lines` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `invoice_id` bigint NOT NULL,

  -- Snapshot del concepto (INMUTABLE)
  `description` varchar(500) NOT NULL,
  `quantity` decimal(10,3) NOT NULL DEFAULT 1.000,
  `unit_price` decimal(10,2) NOT NULL,
  `discount_percent` decimal(5,2) NOT NULL DEFAULT 0.00,
  `discount_amount` decimal(10,2) DEFAULT 0.00,
  `line_total` decimal(10,2) NOT NULL,

  -- Referencia opcional
  `service_id` bigint DEFAULT NULL,

  -- Orden
  `line_order` int NOT NULL DEFAULT 1,

  PRIMARY KEY (`id`),
  KEY `idx_line_invoice` (`invoice_id`),
  CONSTRAINT `fk_line_invoice` FOREIGN KEY (`invoice_id`) REFERENCES `invoices` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================
-- 4. TABLA: invoice_audit_log (Auditoría - APPEND-ONLY)
-- =====================================================
CREATE TABLE IF NOT EXISTS `invoice_audit_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `invoice_id` bigint NOT NULL,
  `action` enum('CREATED','UPDATED','ISSUED','PAID','CANCELLED','RECTIFIED','VIEWED','DOWNLOADED','SENT_EMAIL') NOT NULL,
  `performed_by` bigint DEFAULT NULL,
  `performed_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `ip_address` varchar(45) DEFAULT NULL,
  `user_agent` varchar(500) DEFAULT NULL,
  `details` json DEFAULT NULL,
  `old_status` varchar(20) DEFAULT NULL,
  `new_status` varchar(20) DEFAULT NULL,

  PRIMARY KEY (`id`),
  KEY `idx_audit_invoice` (`invoice_id`),
  KEY `idx_audit_date` (`performed_at`),
  CONSTRAINT `fk_audit_invoice` FOREIGN KEY (`invoice_id`) REFERENCES `invoices` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================
-- 5. MODIFICAR: appointments (Añadir snapshots)
-- =====================================================
ALTER TABLE `appointments`
  ADD COLUMN IF NOT EXISTS `service_name` varchar(100) DEFAULT NULL COMMENT 'Snapshot: nombre del servicio',
  ADD COLUMN IF NOT EXISTS `service_price` decimal(10,2) DEFAULT NULL COMMENT 'Snapshot: precio acordado',
  ADD COLUMN IF NOT EXISTS `service_duration` int DEFAULT NULL COMMENT 'Snapshot: duración acordada',
  ADD COLUMN IF NOT EXISTS `business_name` varchar(120) DEFAULT NULL COMMENT 'Snapshot: nombre del negocio',
  ADD COLUMN IF NOT EXISTS `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  ADD COLUMN IF NOT EXISTS `updated_at` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  ADD COLUMN IF NOT EXISTS `invoiced` bit(1) DEFAULT b'0';

-- Índice para buscar citas facturables
CREATE INDEX IF NOT EXISTS `idx_appointments_invoiced` ON `appointments` (`invoiced`, `status`);

-- =====================================================
-- 6. MIGRAR DATOS EXISTENTES (Rellenar snapshots)
-- =====================================================

-- Actualizar appointments existentes con datos del servicio
UPDATE `appointments` a
INNER JOIN `services` s ON a.service_id = s.id
SET
  a.service_name = s.name,
  a.service_price = s.price,
  a.service_duration = s.duration_minutes
WHERE a.service_name IS NULL AND a.service_id IS NOT NULL;

-- Actualizar appointments existentes con nombre del negocio
UPDATE `appointments` a
INNER JOIN `business_config` bc ON a.user_id = bc.user_id
SET a.business_name = bc.business_name
WHERE a.business_name IS NULL;

-- Marcar created_at en appointments existentes
UPDATE `appointments`
SET created_at = CURRENT_TIMESTAMP
WHERE created_at IS NULL;

-- =====================================================
-- 7. TRIGGERS PARA PROTEGER INMUTABILIDAD (Opcional)
-- =====================================================

-- Trigger para evitar actualizaciones en facturas emitidas
DELIMITER //
CREATE TRIGGER IF NOT EXISTS `trg_invoice_immutability`
BEFORE UPDATE ON `invoices`
FOR EACH ROW
BEGIN
  -- Si la factura no es borrador y se intenta modificar campos inmutables
  IF OLD.status != 'DRAFT' AND NEW.status != 'DRAFT' THEN
    -- Solo permitir cambios en: status, paid_at, cancelled_at, cancellation_reason
    IF OLD.invoice_number != NEW.invoice_number
       OR OLD.emitter_tax_id != NEW.emitter_tax_id
       OR OLD.emitter_legal_name != NEW.emitter_legal_name
       OR OLD.client_name != NEW.client_name
       OR OLD.subtotal != NEW.subtotal
       OR OLD.total != NEW.total
       OR OLD.vat_amount != NEW.vat_amount
    THEN
      SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'No se pueden modificar los datos de una factura emitida';
    END IF;
  END IF;
END//
DELIMITER ;

-- =====================================================
-- 8. VISTA: Resumen de facturación mensual
-- =====================================================
CREATE OR REPLACE VIEW `v_monthly_billing_summary` AS
SELECT
  i.business_id,
  YEAR(i.issue_date) AS year,
  MONTH(i.issue_date) AS month,
  COUNT(*) AS total_invoices,
  SUM(CASE WHEN i.status IN ('ISSUED', 'PAID') THEN 1 ELSE 0 END) AS valid_invoices,
  SUM(CASE WHEN i.status IN ('ISSUED', 'PAID') THEN i.taxable_base ELSE 0 END) AS total_taxable_base,
  SUM(CASE WHEN i.status IN ('ISSUED', 'PAID') THEN i.vat_amount ELSE 0 END) AS total_vat,
  SUM(CASE WHEN i.status IN ('ISSUED', 'PAID') THEN i.total ELSE 0 END) AS total_amount,
  SUM(CASE WHEN i.status = 'PAID' THEN i.total ELSE 0 END) AS total_paid,
  SUM(CASE WHEN i.status = 'ISSUED' THEN i.total ELSE 0 END) AS total_pending
FROM invoices i
GROUP BY i.business_id, YEAR(i.issue_date), MONTH(i.issue_date);

-- =====================================================
-- FIN DE LA MIGRACIÓN
-- =====================================================
