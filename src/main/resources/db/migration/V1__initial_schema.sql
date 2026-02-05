-- =====================================================
-- V1: Schema inicial - Baseline
-- Captura el schema creado por Hibernate ddl-auto
-- =====================================================

-- 1. USERS
CREATE TABLE IF NOT EXISTS `users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL,
  `email` varchar(120) NOT NULL,
  `phone` varchar(20) DEFAULT NULL,
  `password` varchar(255) NOT NULL,
  `enabled` bit(1) NOT NULL DEFAULT b'1',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_users_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. BUSINESS_CONFIG
CREATE TABLE IF NOT EXISTS `business_config` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `business_name` varchar(120) NOT NULL DEFAULT 'Mi Negocio',
  `slot_duration_minutes` int NOT NULL DEFAULT 30,
  `opening_time` varchar(5) NOT NULL DEFAULT '09:00',
  `closing_time` varchar(5) NOT NULL DEFAULT '20:00',
  `working_days` varchar(30) NOT NULL DEFAULT '1,2,3,4,5',
  `timezone` varchar(50) NOT NULL DEFAULT 'America/Argentina/Buenos_Aires',
  `enable_reminders` bit(1) NOT NULL DEFAULT b'1',
  `reminder_hours_before` int NOT NULL DEFAULT 24,
  `slug` varchar(255) NOT NULL,
  `logo_url` varchar(255) DEFAULT NULL,
  `receive_email_notifications` bit(1) NOT NULL DEFAULT b'1',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_business_config_user` (`user_id`),
  UNIQUE KEY `uk_business_config_slug` (`slug`),
  CONSTRAINT `fk_business_config_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. SERVICES
CREATE TABLE IF NOT EXISTS `services` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `business_id` bigint NOT NULL,
  `name` varchar(100) NOT NULL,
  `description` varchar(500) DEFAULT NULL,
  `duration_minutes` int NOT NULL DEFAULT 30,
  `price` decimal(10,2) DEFAULT NULL,
  `color` varchar(7) DEFAULT '#6366F1',
  `icon` varchar(50) DEFAULT 'bi-calendar-check',
  `active` bit(1) NOT NULL DEFAULT b'1',
  `display_order` int NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_services_business` (`business_id`),
  CONSTRAINT `fk_services_business` FOREIGN KEY (`business_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. APPOINTMENTS
CREATE TABLE IF NOT EXISTS `appointments` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `date` date NOT NULL,
  `time` time NOT NULL,
  `duration` int NOT NULL DEFAULT 30,
  `client_name` varchar(100) DEFAULT NULL,
  `client_phone` varchar(20) DEFAULT NULL,
  `client_email` varchar(120) DEFAULT NULL,
  `notes` text DEFAULT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'PENDING',
  `user_id` bigint NOT NULL,
  `service_id` bigint DEFAULT NULL,
  `reminder_sent` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  KEY `idx_appointments_user` (`user_id`),
  KEY `idx_appointments_service` (`service_id`),
  KEY `idx_appointments_date` (`date`),
  CONSTRAINT `fk_appointments_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_appointments_service` FOREIGN KEY (`service_id`) REFERENCES `services` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. BLOCKED_SLOTS
CREATE TABLE IF NOT EXISTS `blocked_slots` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `business_id` bigint NOT NULL,
  `title` varchar(100) NOT NULL,
  `start_date` date NOT NULL,
  `end_date` date NOT NULL,
  `start_time` time DEFAULT NULL,
  `end_time` time DEFAULT NULL,
  `all_day` bit(1) NOT NULL DEFAULT b'0',
  `type` varchar(20) NOT NULL DEFAULT 'CUSTOM',
  `notes` text DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_blocked_slots_business` (`business_id`),
  CONSTRAINT `fk_blocked_slots_business` FOREIGN KEY (`business_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 6. NOTIFICATIONS
CREATE TABLE IF NOT EXISTS `notifications` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `business_id` bigint NOT NULL,
  `type` varchar(30) NOT NULL,
  `title` varchar(150) NOT NULL,
  `message` varchar(500) NOT NULL,
  `link` varchar(255) DEFAULT NULL,
  `is_read` bit(1) NOT NULL DEFAULT b'0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `read_at` datetime DEFAULT NULL,
  `appointment_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_notifications_business` (`business_id`),
  KEY `idx_notifications_appointment` (`appointment_id`),
  CONSTRAINT `fk_notifications_business` FOREIGN KEY (`business_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_notifications_appointment` FOREIGN KEY (`appointment_id`) REFERENCES `appointments` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
