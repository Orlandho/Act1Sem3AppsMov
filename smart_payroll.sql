-- ==========================================================
-- SMARTPAYROLL ENTERPRISE - ESQUEMA DE BASE DE DATOS MYSQL
-- Asignatura: Desarrollo de Aplicaciones Móviles (UPN 2026-II)
-- Autor: Orlando Dorival
-- Motor: MySQL 5.7+ / 8.0+ / MariaDB 10+
-- Codificación: UTF-8 (utf8mb4)
-- ==========================================================

CREATE DATABASE IF NOT EXISTS `smart_payroll`
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE `smart_payroll`;

-- ----------------------------------------------------------
-- Estructura de la Tabla: `payroll_records`
-- Almacena el historial y auditoría de liquidaciones de nómina
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `payroll_records` (
    `id` INT AUTO_INCREMENT PRIMARY KEY,
    `voucher_folio` VARCHAR(100) NULL COMMENT 'Folio oficial de la boleta (ej: FOLIO: PAY-2026-4821)',
    `first_name` VARCHAR(100) NOT NULL COMMENT 'Nombres del colaborador',
    `last_name` VARCHAR(100) NOT NULL COMMENT 'Apellidos del colaborador',
    `employee_code` VARCHAR(50) NOT NULL COMMENT 'Código identificador de empleado',
    `hourly_rate` DOUBLE NOT NULL COMMENT 'Tarifa pactada por hora ordinaria',
    `hours_worked` DOUBLE NOT NULL COMMENT 'Total de horas laboradas en el periodo',
    `bonus_percentage` DOUBLE NOT NULL DEFAULT 10.0 COMMENT 'Porcentaje de bono por productividad (0% a 30%)',
    `issue_date` VARCHAR(100) NOT NULL COMMENT 'Fecha y hora de emisión del comprobante',
    `created_at` BIGINT NOT NULL COMMENT 'Timestamp de creación en milisegundos',
    `regular_hours` DOUBLE NOT NULL COMMENT 'Horas regulares ordinarias (máx 40 hrs)',
    `overtime_hours` DOUBLE NOT NULL COMMENT 'Horas extraordinarias de sobretiempo',
    `regular_pay` DOUBLE NOT NULL COMMENT 'Monto liquidado por jornada ordinaria',
    `overtime_pay` DOUBLE NOT NULL COMMENT 'Monto liquidado por sobretiempo al 150%',
    `subtotal_pay` DOUBLE NOT NULL COMMENT 'Subtotal remunerativo (Regular + Extras)',
    `bonus_amount` DOUBLE NOT NULL COMMENT 'Monto monetario otorgado por bonificación',
    `gross_pay` DOUBLE NOT NULL COMMENT 'Remuneración bruta computable',
    `health_deduction` DOUBLE NOT NULL COMMENT 'Aporte de Salud - EsSalud 4%',
    `pension_deduction` DOUBLE NOT NULL COMMENT 'Aporte al Sistema de Pensiones - AFP/ONP 4%',
    `total_deductions` DOUBLE NOT NULL COMMENT 'Total retenciones de ley (8%)',
    `net_pay` DOUBLE NOT NULL COMMENT 'Monto líquido neto a cobrar',
    `employee_rank` VARCHAR(50) NOT NULL COMMENT 'Categoría profesional asignada',
    `sync_timestamp` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Auditoría de sincronización'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------------------------------------
-- Índices para optimización de consultas
-- ----------------------------------------------------------
CREATE INDEX `idx_payroll_employee_code` ON `payroll_records` (`employee_code`);
CREATE INDEX `idx_payroll_voucher_folio` ON `payroll_records` (`voucher_folio`);
CREATE INDEX `idx_payroll_created_at` ON `payroll_records` (`created_at`);

-- ----------------------------------------------------------
-- Datos Demostrativos para Evaluación Docente
-- ----------------------------------------------------------
INSERT INTO `payroll_records` (
    `voucher_folio`, `first_name`, `last_name`, `employee_code`,
    `hourly_rate`, `hours_worked`, `bonus_percentage`, `issue_date`,
    `created_at`, `regular_hours`, `overtime_hours`, `regular_pay`,
    `overtime_pay`, `subtotal_pay`, `bonus_amount`, `gross_pay`,
    `health_deduction`, `pension_deduction`, `total_deductions`,
    `net_pay`, `employee_rank`
) VALUES 
(
    'FOLIO: PAY-2026-1001', 'Carlos Eduardo', 'Mendoza Ramos', 'EMP-2026-88',
    35.50, 48.0, 15.0, '21 Sep 2026, 09:30 AM',
    1789989000000, 40.0, 8.0, 1420.00,
    426.00, 1846.00, 276.90, 2122.90,
    84.92, 84.92, 169.83,
    1953.07, 'Senior Specialist'
),
(
    'FOLIO: PAY-2026-1002', 'Ana Lucia', 'Perez Gómez', 'EMP-2026-45',
    28.00, 40.0, 10.0, '21 Sep 2026, 10:15 AM',
    1789991700000, 40.0, 0.0, 1120.00,
    0.00, 1120.00, 112.00, 1232.00,
    49.28, 49.28, 98.56,
    1133.44, 'Professional Mid'
),
(
    'FOLIO: PAY-2026-1003', 'Jorge Luis', 'Salazar Rivera', 'EMP-2026-12',
    20.00, 35.0, 5.0, '21 Sep 2026, 11:00 AM',
    1789994400000, 35.0, 0.0, 700.00,
    0.00, 700.00, 35.00, 735.00,
    29.40, 29.40, 58.80,
    676.20, 'Associate Junior'
);
