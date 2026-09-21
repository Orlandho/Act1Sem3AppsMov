-- ====================================================================
-- SCRIPT DDL: Base de Datos MySQL / MariaDB para SmartPayroll Pro
-- Arquitectura Resiliente a Fallos (Offline-First)
-- ====================================================================

CREATE DATABASE IF NOT EXISTS smart_payroll CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE smart_payroll;

-- Tabla principal de registros de nómina y liquidaciones
CREATE TABLE IF NOT EXISTS payroll_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    local_id BIGINT NULL,
    voucher_folio VARCHAR(64) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    employee_code VARCHAR(50) NOT NULL,
    hourly_rate DOUBLE NOT NULL,
    hours_worked DOUBLE NOT NULL,
    bonus_percentage DOUBLE NOT NULL DEFAULT 10.0,
    issue_date VARCHAR(64) NOT NULL,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    sync_status INT NOT NULL DEFAULT 1 COMMENT '0=PENDIENTE, 1=SINCRONIZADO, 2=ERROR',
    sync_error TEXT NULL,
    INDEX idx_employee_code (employee_code),
    INDEX idx_voucher_folio (voucher_folio),
    INDEX idx_local_id (local_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
