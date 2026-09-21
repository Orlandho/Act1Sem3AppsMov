package com.example.act1sem3appsmov

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Resultado estructurado del proceso de sincronización en lote.
 */
data class SyncResult(
    val totalPending: Int,
    val syncedCount: Int,
    val failedCount: Int,
    val errors: List<String>
)

/**
 * Repository Pattern: Motor de Sincronización Offline-First.
 * SQLite nativo (`PayrollDbHelper`) es la fuente única de verdad (Single Source of Truth).
 * Las escrituras son atómicas en SQLite y se despachan de forma asíncrona a MySQL en background.
 */
class PayrollRepository(context: Context) {

    private val dbHelper = PayrollDbHelper(context)
    private val mySQLManager = MySQLDatabaseManager(context)
    private val scope = CoroutineScope(Dispatchers.IO)

    fun getAll(searchQuery: String? = null): List<EmployeePayrollData> {
        return dbHelper.getAll(searchQuery)
    }

    fun getById(id: Long): EmployeePayrollData? {
        return dbHelper.getById(id)
    }

    fun getDashboardMetrics(): DashboardMetrics {
        return dbHelper.getDashboardMetrics()
    }

    /**
     * Escritura atómica inmediata en SQLite local (Single Source of Truth).
     * Despacho asíncrono a MySQL en segundo plano.
     */
    fun insert(payroll: EmployeePayrollData, onComplete: ((Long) -> Unit)? = null): Long {
        val newItem = payroll.copy(
            syncStatus = EmployeePayrollData.SYNC_STATUS_PENDING,
            syncError = null,
            updatedAt = System.currentTimeMillis()
        )
        val localId = dbHelper.insert(newItem)
        if (localId > 0) {
            val insertedItem = newItem.copy(id = localId)
            onComplete?.invoke(localId)
            dispatchAsyncSync(insertedItem)
        }
        return localId
    }

    /**
     * Edición atómica inmediata en SQLite local.
     * Despacho asíncrono a MySQL en segundo plano.
     */
    fun update(payroll: EmployeePayrollData): Boolean {
        val updatedItem = payroll.copy(
            syncStatus = EmployeePayrollData.SYNC_STATUS_PENDING,
            syncError = null,
            updatedAt = System.currentTimeMillis()
        )
        val success = dbHelper.update(updatedItem)
        if (success) {
            dispatchAsyncSync(updatedItem)
        }
        return success
    }

    /**
     * Eliminación atómica inmediata en SQLite local.
     * Despacho asíncrono de borrado en MySQL en segundo plano.
     */
    fun delete(item: EmployeePayrollData): Boolean {
        val deleted = dbHelper.delete(item.id)
        if (deleted) {
            scope.launch {
                mySQLManager.deleteRecord(item.remoteId, item.id)
            }
        }
        return deleted
    }

    /**
     * Sincronización en lote de todos los registros pendientes (status PENDING o ERROR).
     */
    suspend fun syncPendingRecords(): SyncResult = withContext(Dispatchers.IO) {
        val pending = dbHelper.getBySyncStatus(EmployeePayrollData.SYNC_STATUS_PENDING) +
                dbHelper.getBySyncStatus(EmployeePayrollData.SYNC_STATUS_ERROR)

        if (pending.isEmpty()) {
            return@withContext SyncResult(0, 0, 0, emptyList())
        }

        var synced = 0
        var failed = 0
        val errors = mutableListOf<String>()

        mySQLManager.ensureSchemaExists()

        for (item in pending) {
            val result = mySQLManager.upsertRecord(item)
            if (result.isSuccess) {
                val remoteId = result.getOrNull()
                dbHelper.updateSyncMetadata(
                    id = item.id,
                    syncStatus = EmployeePayrollData.SYNC_STATUS_SYNCED,
                    remoteId = remoteId,
                    syncError = null
                )
                synced++
            } else {
                val errorMsg = result.exceptionOrNull()?.localizedMessage ?: "Error de conexión MySQL"
                dbHelper.updateSyncMetadata(
                    id = item.id,
                    syncStatus = EmployeePayrollData.SYNC_STATUS_ERROR,
                    remoteId = item.remoteId,
                    syncError = errorMsg
                )
                failed++
                errors.add("${item.fullName}: $errorMsg")
            }
        }

        SyncResult(pending.size, synced, failed, errors)
    }

    private fun dispatchAsyncSync(payroll: EmployeePayrollData) {
        scope.launch {
            val result = mySQLManager.upsertRecord(payroll)
            if (result.isSuccess) {
                val remoteId = result.getOrNull()
                dbHelper.updateSyncMetadata(
                    id = payroll.id,
                    syncStatus = EmployeePayrollData.SYNC_STATUS_SYNCED,
                    remoteId = remoteId,
                    syncError = null
                )
            } else {
                val errorMsg = result.exceptionOrNull()?.localizedMessage ?: "Error de conexión con MySQL"
                dbHelper.updateSyncMetadata(
                    id = payroll.id,
                    syncStatus = EmployeePayrollData.SYNC_STATUS_ERROR,
                    remoteId = payroll.remoteId,
                    syncError = errorMsg
                )
            }
        }
    }
}
