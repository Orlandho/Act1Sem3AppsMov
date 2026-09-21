package com.example.act1sem3appsmov

import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Statement

/**
 * Gestor de persistencia remota para MySQL mediante JDBC nativo.
 * Ejecuta operaciones DDL y DML directamente en la base de datos MySQL con timeouts defensivos,
 * compatible con servidores MySQL y MariaDB (puerto 3306).
 */
class MySQLDbManager(private val config: MySQLConfig) {

    init {
        // Cargar el driver JDBC de MariaDB/MySQL
        try {
            Class.forName("org.mariadb.jdbc.Driver")
        } catch (_: ClassNotFoundException) {
            try {
                Class.forName("com.mysql.cj.jdbc.Driver")
            } catch (_: ClassNotFoundException) {
                // Se intentará resolver en tiempo de conexión
            }
        }
    }

    /**
     * Establece una nueva conexión JDBC con timeout estricto.
     */
    @Throws(SQLException::class)
    fun getConnection(): Connection {
        DriverManager.setLoginTimeout(config.timeoutSeconds)
        return DriverManager.getConnection(config.buildJdbcUrl(), config.user, config.password)
    }

    /**
     * Prueba la conexión y retorna el banner del motor o un mensaje amigable de error.
     */
    fun testConnection(): Result<String> {
        return runCatching {
            getConnection().use { conn ->
                val meta = conn.metaData
                "Conectado exitosamente a ${meta.databaseProductName} v${meta.databaseProductVersion}"
            }
        }
    }

    /**
     * Asegura la creación de la tabla en MySQL si no existe (Auto-DDL).
     */
    fun ensureSchema(): Result<Unit> {
        return runCatching {
            getConnection().use { conn ->
                val sql = """
                    CREATE TABLE IF NOT EXISTS $TABLE_NAME (
                        $COL_ID INT AUTO_INCREMENT PRIMARY KEY,
                        $COL_VOUCHER_FOLIO VARCHAR(100),
                        $COL_FIRST_NAME VARCHAR(100) NOT NULL,
                        $COL_LAST_NAME VARCHAR(100) NOT NULL,
                        $COL_EMPLOYEE_CODE VARCHAR(50) NOT NULL,
                        $COL_HOURLY_RATE DOUBLE NOT NULL,
                        $COL_HOURS_WORKED DOUBLE NOT NULL,
                        $COL_BONUS_PERCENTAGE DOUBLE NOT NULL,
                        $COL_ISSUE_DATE VARCHAR(100) NOT NULL,
                        $COL_CREATED_AT BIGINT NOT NULL,
                        $COL_REGULAR_HOURS DOUBLE NOT NULL,
                        $COL_OVERTIME_HOURS DOUBLE NOT NULL,
                        $COL_REGULAR_PAY DOUBLE NOT NULL,
                        $COL_OVERTIME_PAY DOUBLE NOT NULL,
                        $COL_SUBTOTAL_PAY DOUBLE NOT NULL,
                        $COL_BONUS_AMOUNT DOUBLE NOT NULL,
                        $COL_GROSS_PAY DOUBLE NOT NULL,
                        $COL_HEALTH_DEDUCTION DOUBLE NOT NULL,
                        $COL_PENSION_DEDUCTION DOUBLE NOT NULL,
                        $COL_TOTAL_DEDUCTIONS DOUBLE NOT NULL,
                        $COL_NET_PAY DOUBLE NOT NULL,
                        $COL_EMPLOYEE_RANK VARCHAR(50) NOT NULL,
                        sync_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                """.trimIndent()
                conn.createStatement().use { stmt ->
                    stmt.execute(sql)
                }
            }
        }
    }

    /**
     * Inserta un registro en MySQL y retorna el ID autoincremental asignado por el servidor.
     */
    fun insertPayroll(payroll: EmployeePayrollData): Result<Long> {
        return runCatching {
            ensureSchema()
            getConnection().use { conn ->
                val sql = """
                    INSERT INTO $TABLE_NAME (
                        $COL_VOUCHER_FOLIO, $COL_FIRST_NAME, $COL_LAST_NAME, $COL_EMPLOYEE_CODE,
                        $COL_HOURLY_RATE, $COL_HOURS_WORKED, $COL_BONUS_PERCENTAGE, $COL_ISSUE_DATE,
                        $COL_CREATED_AT, $COL_REGULAR_HOURS, $COL_OVERTIME_HOURS, $COL_REGULAR_PAY,
                        $COL_OVERTIME_PAY, $COL_SUBTOTAL_PAY, $COL_BONUS_AMOUNT, $COL_GROSS_PAY,
                        $COL_HEALTH_DEDUCTION, $COL_PENSION_DEDUCTION, $COL_TOTAL_DEDUCTIONS,
                        $COL_NET_PAY, $COL_EMPLOYEE_RANK
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent()

                conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS).use { pstmt ->
                    bindPayrollParameters(pstmt, payroll)
                    pstmt.executeUpdate()

                    var generatedId = 0L
                    pstmt.generatedKeys.use { rs ->
                        if (rs.next()) {
                            generatedId = rs.getLong(1)
                        }
                    }
                    generatedId
                }
            }
        }
    }

    /**
     * Actualiza un registro existente en MySQL por su ID remoto o folio.
     */
    fun updatePayroll(remoteId: Long, folio: String, payroll: EmployeePayrollData): Result<Boolean> {
        return runCatching {
            ensureSchema()
            getConnection().use { conn ->
                val sql = """
                    UPDATE $TABLE_NAME SET
                        $COL_FIRST_NAME = ?, $COL_LAST_NAME = ?, $COL_EMPLOYEE_CODE = ?,
                        $COL_HOURLY_RATE = ?, $COL_HOURS_WORKED = ?, $COL_BONUS_PERCENTAGE = ?,
                        $COL_REGULAR_HOURS = ?, $COL_OVERTIME_HOURS = ?, $COL_REGULAR_PAY = ?,
                        $COL_OVERTIME_PAY = ?, $COL_SUBTOTAL_PAY = ?, $COL_BONUS_AMOUNT = ?,
                        $COL_GROSS_PAY = ?, $COL_HEALTH_DEDUCTION = ?, $COL_PENSION_DEDUCTION = ?,
                        $COL_TOTAL_DEDUCTIONS = ?, $COL_NET_PAY = ?, $COL_EMPLOYEE_RANK = ?
                    WHERE $COL_ID = ? OR ($COL_VOUCHER_FOLIO IS NOT NULL AND $COL_VOUCHER_FOLIO = ?)
                """.trimIndent()

                conn.prepareStatement(sql).use { pstmt ->
                    pstmt.setString(1, payroll.firstName)
                    pstmt.setString(2, payroll.lastName)
                    pstmt.setString(3, payroll.employeeCode)
                    pstmt.setDouble(4, payroll.hourlyRate)
                    pstmt.setDouble(5, payroll.hoursWorked)
                    pstmt.setDouble(6, payroll.bonusPercentage)
                    pstmt.setDouble(7, payroll.regularHours)
                    pstmt.setDouble(8, payroll.overtimeHours)
                    pstmt.setDouble(9, payroll.regularPay)
                    pstmt.setDouble(10, payroll.overtimePay)
                    pstmt.setDouble(11, payroll.subtotalPay)
                    pstmt.setDouble(12, payroll.bonusAmount)
                    pstmt.setDouble(13, payroll.grossPay)
                    pstmt.setDouble(14, payroll.healthDeduction)
                    pstmt.setDouble(15, payroll.pensionDeduction)
                    pstmt.setDouble(16, payroll.totalDeductions)
                    pstmt.setDouble(17, payroll.netPay)
                    pstmt.setString(18, payroll.employeeRank)
                    pstmt.setLong(19, remoteId)
                    pstmt.setString(20, folio)

                    val updatedRows = pstmt.executeUpdate()
                    updatedRows > 0
                }
            }
        }
    }

    /**
     * Elimina un registro en MySQL por su ID remoto o folio.
     */
    fun deletePayroll(remoteId: Long, folio: String): Result<Boolean> {
        return runCatching {
            getConnection().use { conn ->
                val sql = "DELETE FROM $TABLE_NAME WHERE $COL_ID = ? OR ($COL_VOUCHER_FOLIO IS NOT NULL AND $COL_VOUCHER_FOLIO = ?)"
                conn.prepareStatement(sql).use { pstmt ->
                    pstmt.setLong(1, remoteId)
                    pstmt.setString(2, folio)
                    pstmt.executeUpdate() > 0
                }
            }
        }
    }

    /**
     * Lee todos los registros directamente desde MySQL.
     */
    fun getAllPayrolls(): Result<List<EmployeePayrollData>> {
        return runCatching {
            ensureSchema()
            getConnection().use { conn ->
                val sql = "SELECT * FROM $TABLE_NAME ORDER BY $COL_CREATED_AT DESC"
                conn.createStatement().use { stmt ->
                    val rs = stmt.executeQuery(sql)
                    val list = mutableListOf<EmployeePayrollData>()
                    while (rs.next()) {
                        list.add(resultSetToPayroll(rs))
                    }
                    list
                }
            }
        }
    }

    private fun bindPayrollParameters(pstmt: PreparedStatement, payroll: EmployeePayrollData) {
        pstmt.setString(1, payroll.voucherFolio)
        pstmt.setString(2, payroll.firstName)
        pstmt.setString(3, payroll.lastName)
        pstmt.setString(4, payroll.employeeCode)
        pstmt.setDouble(5, payroll.hourlyRate)
        pstmt.setDouble(6, payroll.hoursWorked)
        pstmt.setDouble(7, payroll.bonusPercentage)
        pstmt.setString(8, payroll.issueDate)
        pstmt.setLong(9, payroll.createdAt)
        pstmt.setDouble(10, payroll.regularHours)
        pstmt.setDouble(11, payroll.overtimeHours)
        pstmt.setDouble(12, payroll.regularPay)
        pstmt.setDouble(13, payroll.overtimePay)
        pstmt.setDouble(14, payroll.subtotalPay)
        pstmt.setDouble(15, payroll.bonusAmount)
        pstmt.setDouble(16, payroll.grossPay)
        pstmt.setDouble(17, payroll.healthDeduction)
        pstmt.setDouble(18, payroll.pensionDeduction)
        pstmt.setDouble(19, payroll.totalDeductions)
        pstmt.setDouble(20, payroll.netPay)
        pstmt.setString(21, payroll.employeeRank)
    }

    private fun resultSetToPayroll(rs: ResultSet): EmployeePayrollData {
        val remoteId = rs.getLong(COL_ID)
        val folio = rs.getString(COL_VOUCHER_FOLIO).orEmpty()
        val firstName = rs.getString(COL_FIRST_NAME).orEmpty()
        val lastName = rs.getString(COL_LAST_NAME).orEmpty()
        val employeeCode = rs.getString(COL_EMPLOYEE_CODE).orEmpty()
        val hourlyRate = rs.getDouble(COL_HOURLY_RATE)
        val hoursWorked = rs.getDouble(COL_HOURS_WORKED)
        val bonus = rs.getDouble(COL_BONUS_PERCENTAGE)
        val issueDate = rs.getString(COL_ISSUE_DATE).orEmpty()
        val createdAt = rs.getLong(COL_CREATED_AT)

        return EmployeePayrollData(
            id = 0L,
            firstName = firstName,
            lastName = lastName,
            employeeCode = employeeCode,
            hourlyRate = hourlyRate,
            hoursWorked = hoursWorked,
            bonusPercentage = bonus,
            voucherFolio = folio,
            issueDate = issueDate,
            createdAt = createdAt,
            syncStatus = EmployeePayrollData.SYNC_STATUS_SYNCED,
            remoteId = remoteId,
            syncMessage = "Sincronizado con MySQL"
        )
    }

    companion object {
        const val TABLE_NAME = "payroll_records"

        const val COL_ID = "id"
        const val COL_VOUCHER_FOLIO = "voucher_folio"
        const val COL_FIRST_NAME = "first_name"
        const val COL_LAST_NAME = "last_name"
        const val COL_EMPLOYEE_CODE = "employee_code"
        const val COL_HOURLY_RATE = "hourly_rate"
        const val COL_HOURS_WORKED = "hours_worked"
        const val COL_BONUS_PERCENTAGE = "bonus_percentage"
        const val COL_ISSUE_DATE = "issue_date"
        const val COL_CREATED_AT = "created_at"
        const val COL_REGULAR_HOURS = "regular_hours"
        const val COL_OVERTIME_HOURS = "overtime_hours"
        const val COL_REGULAR_PAY = "regular_pay"
        const val COL_OVERTIME_PAY = "overtime_pay"
        const val COL_SUBTOTAL_PAY = "subtotal_pay"
        const val COL_BONUS_AMOUNT = "bonus_amount"
        const val COL_GROSS_PAY = "gross_pay"
        const val COL_HEALTH_DEDUCTION = "health_deduction"
        const val COL_PENSION_DEDUCTION = "pension_deduction"
        const val COL_TOTAL_DEDUCTIONS = "total_deductions"
        const val COL_NET_PAY = "net_pay"
        const val COL_EMPLOYEE_RANK = "employee_rank"
    }
}
