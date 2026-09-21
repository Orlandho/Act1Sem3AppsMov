package com.example.act1sem3appsmov

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet

/**
 * Conector JDBC MySQL/MariaDB Asíncrono para Android ART.
 * Ejecuta operaciones en background con Dispatchers.IO y timeouts defensivos (3-5s).
 */
class MySQLDatabaseManager(private val context: Context) {

    init {
        try {
            Class.forName("org.mariadb.jdbc.Driver")
        } catch (e: ClassNotFoundException) {
            e.printStackTrace()
        }
    }

    private fun getConfig(): MySQLConfig {
        return MySQLConfig.load(context)
    }

    private fun getConnection(): Connection {
        val config = getConfig()
        DriverManager.setLoginTimeout(4)
        return DriverManager.getConnection(config.jdbcUrl, config.user, config.password)
    }

    /**
     * Prueba la conexión remota con el servidor MySQL.
     */
    suspend fun testConnection(): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            getConnection().use { conn ->
                conn.isValid(3)
            }
        }
    }

    /**
     * Asegura que la tabla remota exista en MySQL.
     */
    suspend fun ensureSchemaExists(): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            getConnection().use { conn ->
                val sql = """
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
                        sync_status INT NOT NULL DEFAULT 1,
                        sync_error TEXT NULL
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                """.trimIndent()
                conn.createStatement().use { stmt ->
                    stmt.execute(sql)
                }
                true
            }
        }
    }

    /**
     * Sincroniza (Insert or Update) un registro de nómina en MySQL.
     * Retorna el ID remoto generado o existente.
     */
    suspend fun upsertRecord(payroll: EmployeePayrollData): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            getConnection().use { conn ->
                if (payroll.remoteId != null && payroll.remoteId!! > 0L) {
                    val updateSql = """
                        UPDATE payroll_records SET
                            first_name = ?, last_name = ?, employee_code = ?,
                            hourly_rate = ?, hours_worked = ?, bonus_percentage = ?,
                            voucher_folio = ?, issue_date = ?, updated_at = ?,
                            sync_status = 1, sync_error = NULL
                        WHERE id = ?
                    """.trimIndent()
                    conn.prepareStatement(updateSql).use { stmt ->
                        stmt.setString(1, payroll.firstName)
                        stmt.setString(2, payroll.lastName)
                        stmt.setString(3, payroll.employeeCode)
                        stmt.setDouble(4, payroll.hourlyRate)
                        stmt.setDouble(5, payroll.hoursWorked)
                        stmt.setDouble(6, payroll.bonusPercentage)
                        stmt.setString(7, payroll.voucherFolio)
                        stmt.setString(8, payroll.issueDate)
                        stmt.setLong(9, System.currentTimeMillis())
                        stmt.setLong(10, payroll.remoteId!!)
                        stmt.executeUpdate()
                    }
                    payroll.remoteId!!
                } else {
                    val insertSql = """
                        INSERT INTO payroll_records (
                            local_id, voucher_folio, first_name, last_name, employee_code,
                            hourly_rate, hours_worked, bonus_percentage, issue_date,
                            created_at, updated_at, sync_status, sync_error
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, NULL)
                    """.trimIndent()
                    conn.prepareStatement(insertSql, PreparedStatement.RETURN_GENERATED_KEYS).use { stmt ->
                        stmt.setLong(1, payroll.id)
                        stmt.setString(2, payroll.voucherFolio)
                        stmt.setString(3, payroll.firstName)
                        stmt.setString(4, payroll.lastName)
                        stmt.setString(5, payroll.employeeCode)
                        stmt.setDouble(6, payroll.hourlyRate)
                        stmt.setDouble(7, payroll.hoursWorked)
                        stmt.setDouble(8, payroll.bonusPercentage)
                        stmt.setString(9, payroll.issueDate)
                        stmt.setLong(10, payroll.createdAt)
                        stmt.setLong(11, System.currentTimeMillis())
                        stmt.executeUpdate()

                        var generatedId = -1L
                        stmt.generatedKeys.use { rs: ResultSet ->
                            if (rs.next()) {
                                generatedId = rs.getLong(1)
                            }
                        }
                        if (generatedId <= 0L) {
                            generatedId = payroll.id
                        }
                        generatedId
                    }
                }
            }
        }
    }

    /**
     * Elimina el registro remoto de MySQL por ID remoto o ID local.
     */
    suspend fun deleteRecord(remoteId: Long?, localId: Long): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            getConnection().use { conn ->
                val sql = if (remoteId != null && remoteId > 0L) {
                    "DELETE FROM payroll_records WHERE id = ?"
                } else {
                    "DELETE FROM payroll_records WHERE local_id = ?"
                }
                val targetId = if (remoteId != null && remoteId > 0L) remoteId else localId
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setLong(1, targetId)
                    stmt.executeUpdate()
                }
                true
            }
        }
    }
}
