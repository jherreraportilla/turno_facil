-- Tabla para tokens de reseteo de contraseña
CREATE TABLE IF NOT EXISTS `password_reset_tokens` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `token` varchar(36) NOT NULL,
    `user_id` bigint NOT NULL,
    `expiry_date` datetime NOT NULL,
    `used` bit(1) NOT NULL DEFAULT b'0',
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_password_reset_token` (`token`),
    KEY `idx_password_reset_user` (`user_id`),
    KEY `idx_password_reset_expiry` (`expiry_date`),
    CONSTRAINT `fk_password_reset_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
