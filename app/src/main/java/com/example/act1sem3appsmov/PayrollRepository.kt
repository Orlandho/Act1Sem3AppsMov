package com.example.act1sem3appsmov

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Repositorio Central de Nómina (Single Source of Truth) con Arquitectura Offline-First.
 * Coordina la persistencia inmediata en SQLite local para garantizar 0ms de latencia y disponibilidad
 * ininterrumpida sin conexión, junto con un motor de sincronización asíncrono hacia MySQL en segundo plano.
 */
class PayrollRepository(context: Context) {

    val localDb = PayrollDbHelper(context)
    val mysqlConfig = MySQLConfig(context)
    val mysqlManager = MySQLDbManager(mysqlConfig)

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Guarda una liquidación de nómina:
     * 1. Inserta inmediatamente en SQLite local (0ms).
     * 2. Despacha sincronización asíncrona hacia MySQL en segundo plano.
     */
    fun savePayroll(
        payroll: EmployeePayrollData,
        onSyncResult: ((EmployeePayrollData) -> Unit)? = null
    ): Long {
        payroll.syncStatus = EmployeePayrollData.SYNC_STATUS_PENDING
        payroll.syncMessage = "Guardado localmente. Pendiente de sincronización MySQL."

        val localId = localDb.insert(payroll)
        val savedData = payroll.copy(id = localId)

        // Despachar sincronización asíncrona con MySQL
        repositoryScope.launch {
            val result = mysqlManager.insertPayroll(savedData)
            val updatedData = if (result.isSuccess) {
                val remoteId = result.getOrNull() ?: 0L
                localDb.updateSyncStatus(
                    id = localId,
                    status = EmployeePayrollData.SYNC_STATUS_SYNCED,
                    remoteId = remoteId,
                    message = "Sincronizado con MySQL (ID #$remoteId)"
                )
                savedData.copy(
                    syncStatus = EmployeePayrollData.SYNC_STATUS_SYNCED,
                    remoteId = remoteId,
                    syncMessage = "Sincronizado con MySQL (ID #$remoteId)"
                )
            } else {
                val errorMsg = result.exceptionOrNull()?.localizedMessage ?: "Error de conexión MySQL"
                localDb.updateSyncStatus(
                    id = localId,
                    status = EmployeePayrollData.SYNC_STATUS_ERROR,
                    message = "Pendiente (Offline): $errorMsg"
                )
                savedData.copy(
                    syncStatus = EmployeePayrollData.SYNC_STATUS_ERROR,
                    syncMessage = "Pendiente (Offline): $errorMsg"
                )
            }

            if (onSyncResult != null) {
                withContext(Dispatchers.Main) {
                    onSyncResult(updatedData)
                }
            }
        }

        return localId
    }

    /**
     * Actualiza una liquidación:
     * 1. Actualiza inmediatamente en SQLite local.
     * 2. Notifica o actualiza el registro en MySQL en segundo plano.
     */
    fun updatePayroll(
        payroll: EmployeePayrollData,
        onSyncResult: ((Boolean) -> Unit)? = null
    ): Boolean {
        payroll.syncStatus = EmployeePayrollData.SYNC_STATUS_PENDING
        payroll.syncMessage = "Modificado localmente. Pendiente de sincronización MySQL."

        val localSuccess = localDb.update(payroll)

        if (localSuccess) {
            repositoryScope.launch {
                val result = mysqlManager.updatePayroll(payroll.remoteId, payroll.voucherFolio, payroll)
                if (result.isSuccess && result.getOrDefault(false)) {
                    localDb.updateSyncStatus(
                        id = payroll.id,
                        status = EmployeePayrollData.SYNC_STATUS_SYNCED,
                        remoteId = payroll.remoteId,
                        message = "Actualizado en MySQL"
                    )
                } else {
                    val errorMsg = result.exceptionOrNull()?.localizedMessage ?: "No se pudo sincronizar edición con MySQL"
                    localDb.updateSyncStatus(
                        id = payroll.id,
                        status = EmployeePayrollData.SYNC_STATUS_ERROR,
                        message = "Edición pendiente (Offline): $errorMsg"
                    )
                }

                if (onSyncResult != null) {
                    withContext(Dispatchers.Main) {
                        onSyncResult(result.isSuccess)
                    }
                }
            }
        }

        return localSuccess
    }

    /**
     * Elimina una liquidación en SQLite y sincroniza la eliminación en MySQL.
     */
    fun deletePayroll(
        payroll: EmployeePayrollData,
        onComplete: ((Boolean) -> Unit)? = null
    ): Boolean {
        val deletedLocal = localDb.delete(payroll.id)
        if (deletedLocal) {
            repositoryScope.launch {
                mysqlManager.deletePayroll(payroll.remoteId, payroll.voucherFolio)
                if (onComplete != null) {
                    withContext(Dispatchers.Main) {
                        onComplete(true)
                    }
                }
            }
        }
        return deletedLocal
    }

    /**
     * Sincroniza en lote todas las liquidaciones que quedaron pendientes por falta de red.
     */
    fun syncPendingRecords(
        onFinished: (syncedCount: Int, failedCount: Int, message: String) -> Unit
    ) {
        repositoryScope.launch {
            val (synced, failed) = syncPendingRecords()
            withContext(Dispatchers.Main) {
                val msg = if (failed == 0) {
                    "✅ $synced liquidación(es) sincronizadas exitosamente con MySQL."
                } else {
                    "⚠️ $synced sincronizadas, $failed pendientes por conectividad."
                }
                onFinished(synced, failed, msg)
            }
        }
    }

    /**
     * Versión suspendida para ejecución directa desde corrutinas (ej. lifecycleScope en UI).
     */
    suspend fun syncPendingRecords(): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val pendingList = localDb.getPendingSync()
        if (pendingList.isEmpty()) return@withContext Pair(0, 0)

        var synced = 0
        var failed = 0

        for (item in pendingList) {
            val result = if (item.remoteId > 0L) {
                mysqlManager.updatePayroll(item.remoteId, item.voucherFolio, item)
            } else {
                mysqlManager.insertPayroll(item).map { it > 0 }
            }

            if (result.isSuccess) {
                localDb.updateSyncStatus(
                    id = item.id,
                    status = EmployeePayrollData.SYNC_STATUS_SYNCED,
                    remoteId = item.remoteId,
                    message = "Sincronizado con MySQL exitosamente"
                )
                synced++
            } else {
                val errorMsg = result.exceptionOrNull()?.localizedMessage ?: "Error de red"
                localDb.updateSyncStatus(
                    id = item.id,
                    status = EmployeePayrollData.SYNC_STATUS_ERROR,
                    message = "Fallo de sincronización: $errorMsg"
                )
                failed++
            }
        }
        Pair(synced, failed)
    }

    /**
     * Delegación de lectura al caché local SQLite (Single Source of Truth).
     */
    fun getAll(query: String? = null): List<EmployeePayrollData> {
        return localDb.getAll(query)
    }

    /**
     * Delegación de cálculo de métricas financieras del Dashboard.
     */
    fun getDashboardMetrics(): DashboardMetrics {
        return localDb.getDashboardMetrics()
    }

    /**
     * Prueba la conexión en segundo plano e informa el resultado en el hilo principal.
     */
    fun testConnection(onResult: (success: Boolean, message: String) -> Unit) {
        repositoryScope.launch {
            val testResult = testMySQLConnection()
            withContext(Dispatchers.Main) {
                if (testResult.isSuccess) {
                    onResult(true, testResult.getOrDefault("Conexión exitosa a MySQL"))
                } else {
                    val err = testResult.exceptionOrNull()?.localizedMessage ?: "Error de conexión"
                    onResult(false, err)
                }
            }
        }
    }

    /**
     * Versión suspendida para test de conexión MySQL.
     */
    suspend fun testMySQLConnection(): Result<String> = withContext(Dispatchers.IO) {
        mysqlManager.testConnection()
    }

    companion object {
        @Volatile
        private var INSTANCE: PayrollRepository? = null

        fun getInstance(context: Context): PayrollRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PayrollRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
