package com.example.act1sem3appsmov

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Encapsula las estadísticas globales de nómina para el Dashboard.
 */
data class DashboardMetrics(
    val totalCount: Int,
    val totalPayroll: Double,
    val avgPayroll: Double,
    val totalOvertimeHours: Double,
    val totalOvertimePay: Double
)

/**
 * Gestor de base de datos SQLite nativo para el historial de nóminas y métricas de nómina.
 * Implementa el ciclo de vida CRUD completo (Create, Read, Update, Delete) sin dependencias externas.
 */
class PayrollDbHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        val createTableSql = """
            CREATE TABLE $TABLE_PAYROLL (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_VOUCHER_FOLIO TEXT,
                $COL_FIRST_NAME TEXT NOT NULL,
                $COL_LAST_NAME TEXT NOT NULL,
                $COL_EMPLOYEE_CODE TEXT NOT NULL,
                $COL_HOURLY_RATE REAL NOT NULL,
                $COL_HOURS_WORKED REAL NOT NULL,
                $COL_BONUS_PERCENTAGE REAL NOT NULL,
                $COL_ISSUE_DATE TEXT NOT NULL,
                $COL_CREATED_AT INTEGER NOT NULL,
                $COL_SYNC_STATUS INTEGER NOT NULL DEFAULT 0,
                $COL_REMOTE_ID INTEGER,
                $COL_SYNC_ERROR TEXT,
                $COL_UPDATED_AT INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent()
        db.execSQL(createTableSql)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            try {
                db.execSQL("ALTER TABLE $TABLE_PAYROLL ADD COLUMN $COL_SYNC_STATUS INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE $TABLE_PAYROLL ADD COLUMN $COL_REMOTE_ID INTEGER")
                db.execSQL("ALTER TABLE $TABLE_PAYROLL ADD COLUMN $COL_SYNC_ERROR TEXT")
                db.execSQL("ALTER TABLE $TABLE_PAYROLL ADD COLUMN $COL_UPDATED_AT INTEGER NOT NULL DEFAULT 0")
            } catch (e: Exception) {
                db.execSQL("DROP TABLE IF EXISTS $TABLE_PAYROLL")
                onCreate(db)
            }
        } else {
            db.execSQL("DROP TABLE IF EXISTS $TABLE_PAYROLL")
            onCreate(db)
        }
    }

    /**
     * CREATE: Inserta una nueva liquidación de nómina.
     */
    fun insert(payroll: EmployeePayrollData): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_VOUCHER_FOLIO, payroll.voucherFolio)
            put(COL_FIRST_NAME, payroll.firstName)
            put(COL_LAST_NAME, payroll.lastName)
            put(COL_EMPLOYEE_CODE, payroll.employeeCode)
            put(COL_HOURLY_RATE, payroll.hourlyRate)
            put(COL_HOURS_WORKED, payroll.hoursWorked)
            put(COL_BONUS_PERCENTAGE, payroll.bonusPercentage)
            put(COL_ISSUE_DATE, payroll.issueDate)
            put(COL_CREATED_AT, payroll.createdAt)
            put(COL_SYNC_STATUS, payroll.syncStatus)
            put(COL_REMOTE_ID, payroll.remoteId)
            put(COL_SYNC_ERROR, payroll.syncError)
            put(COL_UPDATED_AT, payroll.updatedAt)
        }
        return db.insert(TABLE_PAYROLL, null, values)
    }

    /**
     * READ: Obtiene todas las liquidaciones ordenadas de la más reciente a la más antigua.
     * Soporta búsqueda reactiva por nombre, apellido o código de colaborador.
     */
    fun getAll(searchQuery: String? = null): List<EmployeePayrollData> {
        val list = mutableListOf<EmployeePayrollData>()
        val db = readableDatabase

        val selection: String?
        val selectionArgs: Array<String>?

        if (!searchQuery.isNullOrBlank()) {
            val queryParam = "%${searchQuery.trim()}%"
            selection = "$COL_FIRST_NAME LIKE ? OR $COL_LAST_NAME LIKE ? OR $COL_EMPLOYEE_CODE LIKE ? OR $COL_VOUCHER_FOLIO LIKE ?"
            selectionArgs = arrayOf(queryParam, queryParam, queryParam, queryParam)
        } else {
            selection = null
            selectionArgs = null
        }

        val cursor: Cursor = db.query(
            TABLE_PAYROLL,
            null,
            selection,
            selectionArgs,
            null,
            null,
            "$COL_CREATED_AT DESC"
        )

        cursor.use {
            while (it.moveToNext()) {
                list.add(cursorToPayroll(it))
            }
        }
        return list
    }

    /**
     * READ BY ID: Recupera una liquidación específica.
     */
    fun getById(id: Long): EmployeePayrollData? {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_PAYROLL,
            null,
            "$COL_ID = ?",
            arrayOf(id.toString()),
            null,
            null,
            null
        )
        return cursor.use {
            if (it.moveToFirst()) cursorToPayroll(it) else null
        }
    }

    /**
     * UPDATE: Actualiza los parámetros modificables de una liquidación.
     */
    fun update(payroll: EmployeePayrollData): Boolean {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_FIRST_NAME, payroll.firstName)
            put(COL_LAST_NAME, payroll.lastName)
            put(COL_EMPLOYEE_CODE, payroll.employeeCode)
            put(COL_HOURLY_RATE, payroll.hourlyRate)
            put(COL_HOURS_WORKED, payroll.hoursWorked)
            put(COL_BONUS_PERCENTAGE, payroll.bonusPercentage)
            put(COL_SYNC_STATUS, payroll.syncStatus)
            put(COL_REMOTE_ID, payroll.remoteId)
            put(COL_SYNC_ERROR, payroll.syncError)
            put(COL_UPDATED_AT, payroll.updatedAt)
        }
        val rowsAffected = db.update(
            TABLE_PAYROLL,
            values,
            "$COL_ID = ?",
            arrayOf(payroll.id.toString())
        )
        return rowsAffected > 0
    }

    /**
     * Actualiza únicamente los metadatos de sincronización de un registro.
     */
    fun updateSyncMetadata(id: Long, syncStatus: Int, remoteId: Long? = null, syncError: String? = null): Boolean {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_SYNC_STATUS, syncStatus)
            if (remoteId != null) put(COL_REMOTE_ID, remoteId)
            put(COL_SYNC_ERROR, syncError)
            put(COL_UPDATED_AT, System.currentTimeMillis())
        }
        val rowsAffected = db.update(
            TABLE_PAYROLL,
            values,
            "$COL_ID = ?",
            arrayOf(id.toString())
        )
        return rowsAffected > 0
    }

    /**
     * DELETE: Elimina una liquidación por su ID.
     */
    fun delete(id: Long): Boolean {
        val db = writableDatabase
        val rowsDeleted = db.delete(
            TABLE_PAYROLL,
            "$COL_ID = ?",
            arrayOf(id.toString())
        )
        return rowsDeleted > 0
    }

    /**
     * DASHBOARD: Calcula las métricas financieras agregadas en tiempo real.
     */
    fun getDashboardMetrics(): DashboardMetrics {
        val allPayrolls = getAll()
        if (allPayrolls.isEmpty()) {
            return DashboardMetrics(
                totalCount = 0,
                totalPayroll = 0.0,
                avgPayroll = 0.0,
                totalOvertimeHours = 0.0,
                totalOvertimePay = 0.0
            )
        }

        val count = allPayrolls.size
        val totalNet = allPayrolls.sumOf { it.netPay }
        val avgNet = totalNet / count
        val totalOtHours = allPayrolls.sumOf { it.overtimeHours }
        val totalOtPay = allPayrolls.sumOf { it.overtimePay }

        return DashboardMetrics(
            totalCount = count,
            totalPayroll = totalNet,
            avgPayroll = avgNet,
            totalOvertimeHours = totalOtHours,
            totalOvertimePay = totalOtPay
        )
    }

    private fun cursorToPayroll(cursor: Cursor): EmployeePayrollData {
        val id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID))
        val folio = cursor.getString(cursor.getColumnIndexOrThrow(COL_VOUCHER_FOLIO)).orEmpty()
        val firstName = cursor.getString(cursor.getColumnIndexOrThrow(COL_FIRST_NAME)).orEmpty()
        val lastName = cursor.getString(cursor.getColumnIndexOrThrow(COL_LAST_NAME)).orEmpty()
        val employeeCode = cursor.getString(cursor.getColumnIndexOrThrow(COL_EMPLOYEE_CODE)).orEmpty()
        val hourlyRate = cursor.getDouble(cursor.getColumnIndexOrThrow(COL_HOURLY_RATE))
        val hoursWorked = cursor.getDouble(cursor.getColumnIndexOrThrow(COL_HOURS_WORKED))
        val bonusPercentage = cursor.getDouble(cursor.getColumnIndexOrThrow(COL_BONUS_PERCENTAGE))
        val issueDate = cursor.getString(cursor.getColumnIndexOrThrow(COL_ISSUE_DATE)).orEmpty()
        val createdAt = cursor.getLong(cursor.getColumnIndexOrThrow(COL_CREATED_AT))

        val syncStatus = cursor.getInt(cursor.getColumnIndexOrThrow(COL_SYNC_STATUS))
        val remoteIdIndex = cursor.getColumnIndexOrThrow(COL_REMOTE_ID)
        val remoteId = if (cursor.isNull(remoteIdIndex)) null else cursor.getLong(remoteIdIndex)
        val syncErrorIndex = cursor.getColumnIndexOrThrow(COL_SYNC_ERROR)
        val syncError = if (cursor.isNull(syncErrorIndex)) null else cursor.getString(syncErrorIndex)
        val updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow(COL_UPDATED_AT))

        return EmployeePayrollData(
            id = id,
            firstName = firstName,
            lastName = lastName,
            employeeCode = employeeCode,
            hourlyRate = hourlyRate,
            hoursWorked = hoursWorked,
            bonusPercentage = bonusPercentage,
            voucherFolio = folio,
            issueDate = issueDate,
            createdAt = createdAt,
            syncStatus = syncStatus,
            remoteId = remoteId,
            syncError = syncError,
            updatedAt = updatedAt
        )
    }

    /**
     * Obtiene todos los registros con un determinado estado de sincronización.
     */
    fun getBySyncStatus(status: Int): List<EmployeePayrollData> {
        val list = mutableListOf<EmployeePayrollData>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_PAYROLL,
            null,
            "$COL_SYNC_STATUS = ?",
            arrayOf(status.toString()),
            null,
            null,
            "$COL_CREATED_AT ASC"
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(cursorToPayroll(it))
            }
        }
        return list
    }

    companion object {
        const val DATABASE_NAME = "smart_payroll.db"
        const val DATABASE_VERSION = 2

        const val TABLE_PAYROLL = "payroll_records"
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
        const val COL_SYNC_STATUS = "sync_status"
        const val COL_REMOTE_ID = "remote_id"
        const val COL_SYNC_ERROR = "sync_error"
        const val COL_UPDATED_AT = "updated_at"
    }
}
