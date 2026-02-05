-- V7: Campos de personalización visual para landing pages
ALTER TABLE `business_config` ADD COLUMN `primary_color` VARCHAR(7) DEFAULT '#c9a227';
ALTER TABLE `business_config` ADD COLUMN `background_color` VARCHAR(7) DEFAULT '#1a1a2e';
ALTER TABLE `business_config` ADD COLUMN `text_color` VARCHAR(7) DEFAULT '#e0e0e0';
