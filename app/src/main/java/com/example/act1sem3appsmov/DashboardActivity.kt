package com.example.act1sem3appsmov

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.act1sem3appsmov.databinding.ActivityDashboardBinding
import com.example.act1sem3appsmov.databinding.DialogMysqlConfigBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

/**
 * Pantalla Principal de la Aplicación: Dashboard Ejecutivo, Gestión CRUD de Historial
 * y Control de Sincronización Resiliente con MySQL (Offline-First).
 */
class DashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardBinding
    private lateinit var repository: PayrollRepository
    private lateinit var historyAdapter: PayrollHistoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = PayrollRepository.getInstance(this)
        setupRecyclerView()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        // Auto-refresco al retornar de cualquier Activity o modal
        loadDashboardData(binding.etSearch.text?.toString())
    }

    private fun setupRecyclerView() {
        historyAdapter = PayrollHistoryAdapter(
            onViewClick = { item ->
                // Abrir la boleta oficial para visualizar o descargar
                val intent = Intent(this, VoucherActivity::class.java).apply {
                    putExtra(EmployeePayrollData.EXTRA_PAYROLL_DATA, item)
                }
                startActivity(intent)
            },
            onEditClick = { item ->
                // Abrir la pantalla completa dedicada de edición (UPDATE)
                val intent = Intent(this, EditPayrollActivity::class.java).apply {
                    putExtra(EmployeePayrollData.EXTRA_PAYROLL_DATA, item)
                }
                startActivity(intent)
            },
            onDeleteClick = { item ->
                // Diálogo de confirmación para eliminar (DELETE)
                confirmDelete(item)
            }
        )

        binding.rvPayrollHistory.apply {
            layoutManager = LinearLayoutManager(this@DashboardActivity)
            adapter = historyAdapter
        }
    }

    private fun setupListeners() {
        // Iniciar el flujo docente oficial de 3 vistas (CREATE)
        binding.fabNewPayroll.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }

        // Búsqueda reactiva por nombre, código o folio
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                loadDashboardData(s?.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Sincronizar en lote registros pendientes con MySQL
        binding.btnSyncMySQL.setOnClickListener {
            performBatchSync()
        }

        // Configuración de Servidor MySQL
        binding.btnConfigMySQL.setOnClickListener {
            showMySQLConfigDialog()
        }
    }

    private fun loadDashboardData(query: String? = null) {
        // Cargar métricas agregadas del Dashboard
        val metrics = repository.getDashboardMetrics()
        binding.tvStatTotalPayroll.text = formatCurrency(metrics.totalPayroll)
        binding.tvStatCount.text = "${metrics.totalCount} ${if (metrics.totalCount == 1) "boleta" else "boletas"}"
        binding.tvStatOvertimeHours.text = "${"%.1f".format(metrics.totalOvertimeHours)} hrs"
        binding.tvStatOvertimePay.text = "Recargos: ${formatCurrency(metrics.totalOvertimePay)}"
        binding.tvStatAvgPayroll.text = formatCurrency(metrics.avgPayroll)

        // Actualizar barra de estado MySQL
        if (metrics.totalCount == 0) {
            binding.tvMySQLStatusIcon.text = "🌐"
            binding.tvMySQLStatusTitle.text = "MySQL: Listo para operar"
            binding.tvMySQLStatusSubtitle.text = "Host: ${repository.mysqlConfig.host}:${repository.mysqlConfig.port}/${repository.mysqlConfig.database}"
        } else if (metrics.pendingCount == 0) {
            binding.tvMySQLStatusIcon.text = "🟢"
            binding.tvMySQLStatusTitle.text = "MySQL: 100% Sincronizado"
            binding.tvMySQLStatusSubtitle.text = "Todas las ${metrics.syncedCount} boletas están respaldadas en MySQL."
        } else {
            binding.tvMySQLStatusIcon.text = "🟡"
            binding.tvMySQLStatusTitle.text = "MySQL: ${metrics.pendingCount} pendientes"
            binding.tvMySQLStatusSubtitle.text = "${metrics.syncedCount} sincronizadas, ${metrics.pendingCount} guardadas en caché local."
        }

        // Cargar lista filtrada del historial
        val payrolls = repository.getAll(query)
        historyAdapter.submitList(payrolls)

        binding.tvHistoryBadgeCount.text = "${payrolls.size} ${if (payrolls.size == 1) "registro" else "registros"}"

        if (payrolls.isEmpty()) {
            binding.llEmptyState.visibility = View.VISIBLE
            binding.rvPayrollHistory.visibility = View.GONE
        } else {
            binding.llEmptyState.visibility = View.GONE
            binding.rvPayrollHistory.visibility = View.VISIBLE
        }
    }

    private fun performBatchSync() {
        binding.btnSyncMySQL.isEnabled = false
        binding.btnSyncMySQL.text = "⏳ Sincronizando..."

        lifecycleScope.launch {
            try {
                val (synced, failed) = repository.syncPendingRecords()
                loadDashboardData(binding.etSearch.text?.toString())

                if (failed == 0 && synced > 0) {
                    Snackbar.make(
                        binding.root,
                        "✅ ¡Éxito! Se sincronizaron $synced registros en MySQL",
                        Snackbar.LENGTH_LONG
                    ).show()
                } else if (failed > 0 && synced > 0) {
                    Snackbar.make(
                        binding.root,
                        "⚠️ Parcial: $synced sincronizados, $failed no alcanzaron el servidor",
                        Snackbar.LENGTH_LONG
                    ).show()
                } else if (failed > 0 && synced == 0) {
                    Snackbar.make(
                        binding.root,
                        "🔴 MySQL inalcanzable. Datos preservados localmente (Offline-First)",
                        Snackbar.LENGTH_LONG
                    ).setAction("Ajustes") { showMySQLConfigDialog() }.show()
                } else {
                    Snackbar.make(
                        binding.root,
                        "ℹ️ Todo está al día con el servidor MySQL",
                        Snackbar.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Snackbar.make(
                    binding.root,
                    "Error de sincronización: ${e.localizedMessage}",
                    Snackbar.LENGTH_LONG
                ).show()
            } finally {
                binding.btnSyncMySQL.isEnabled = true
                binding.btnSyncMySQL.text = "🔄 Sincronizar"
            }
        }
    }

    private fun showMySQLConfigDialog() {
        val dialogBinding = DialogMysqlConfigBinding.inflate(layoutInflater)
        val config = repository.mysqlConfig

        // Poblar valores actuales
        dialogBinding.etHost.setText(config.host)
        dialogBinding.etPort.setText(config.port.toString())
        dialogBinding.etDatabase.setText(config.database)
        dialogBinding.etUser.setText(config.user)
        dialogBinding.etPassword.setText(config.password)

        // Acción: Probar Conexión en vivo
        dialogBinding.btnTestConnection.setOnClickListener {
            dialogBinding.btnTestConnection.isEnabled = false
            dialogBinding.tvTestResult.text = "⏳ Probando conexión con ${dialogBinding.etHost.text}:${dialogBinding.etPort.text}..."
            dialogBinding.tvTestResult.setTextColor(getColor(R.color.text_primary))

            // Aplicar temporalmente para prueba
            val testHost = dialogBinding.etHost.text?.toString()?.trim().orEmpty().ifEmpty { MySQLConfig.DEFAULT_HOST }
            val testPort = dialogBinding.etPort.text?.toString()?.toIntOrNull() ?: MySQLConfig.DEFAULT_PORT
            val testDb = dialogBinding.etDatabase.text?.toString()?.trim().orEmpty().ifEmpty { MySQLConfig.DEFAULT_DATABASE }
            val testUser = dialogBinding.etUser.text?.toString()?.trim().orEmpty().ifEmpty { MySQLConfig.DEFAULT_USER }
            val testPass = dialogBinding.etPassword.text?.toString().orEmpty()

            config.host = testHost
            config.port = testPort
            config.database = testDb
            config.user = testUser
            config.password = testPass

            lifecycleScope.launch {
                val result = repository.testMySQLConnection()
                dialogBinding.btnTestConnection.isEnabled = true
                if (result.isSuccess) {
                    dialogBinding.tvTestResult.text = "✅ ${result.getOrNull()}"
                    dialogBinding.tvTestResult.setTextColor(getColor(R.color.secondary_dark))
                } else {
                    val ex = result.exceptionOrNull()
                    dialogBinding.tvTestResult.text = "❌ Error: ${ex?.message ?: "Servidor inalcanzable"}"
                    dialogBinding.tvTestResult.setTextColor(getColor(R.color.danger))
                }
            }
        }

        // Acción: Restablecer valores de fábrica
        dialogBinding.btnResetDefaults.setOnClickListener {
            config.resetToDefaults()
            dialogBinding.etHost.setText(MySQLConfig.DEFAULT_HOST)
            dialogBinding.etPort.setText(MySQLConfig.DEFAULT_PORT.toString())
            dialogBinding.etDatabase.setText(MySQLConfig.DEFAULT_DATABASE)
            dialogBinding.etUser.setText(MySQLConfig.DEFAULT_USER)
            dialogBinding.etPassword.setText(MySQLConfig.DEFAULT_PASSWORD)
            dialogBinding.tvTestResult.text = "ℹ️ Valores restaurados a los predeterminados (10.0.2.2:3306)."
            dialogBinding.tvTestResult.setTextColor(getColor(R.color.text_secondary))
        }

        MaterialAlertDialogBuilder(this)
            .setView(dialogBinding.root)
            .setPositiveButton("Guardar Ajustes") { _, _ ->
                val newHost = dialogBinding.etHost.text?.toString()?.trim().orEmpty().ifEmpty { MySQLConfig.DEFAULT_HOST }
                val newPort = dialogBinding.etPort.text?.toString()?.toIntOrNull() ?: MySQLConfig.DEFAULT_PORT
                val newDb = dialogBinding.etDatabase.text?.toString()?.trim().orEmpty().ifEmpty { MySQLConfig.DEFAULT_DATABASE }
                val newUser = dialogBinding.etUser.text?.toString()?.trim().orEmpty().ifEmpty { MySQLConfig.DEFAULT_USER }
                val newPass = dialogBinding.etPassword.text?.toString().orEmpty()

                config.host = newHost
                config.port = newPort
                config.database = newDb
                config.user = newUser
                config.password = newPass

                Toast.makeText(this, "💾 Configuración MySQL guardada", Toast.LENGTH_SHORT).show()
                loadDashboardData(binding.etSearch.text?.toString())
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun confirmDelete(item: EmployeePayrollData) {
        MaterialAlertDialogBuilder(this)
            .setTitle("🗑️ Eliminar Liquidación")
            .setMessage("¿Estás seguro de que deseas eliminar la liquidación de ${item.fullName} (${item.voucherFolio.ifEmpty { "ID #${item.id}" }})?\n\nEsta acción eliminará el registro localmente y en MySQL.")
            .setPositiveButton("Eliminar") { _, _ ->
                repository.deletePayroll(item) { deleted ->
                    if (deleted) {
                        loadDashboardData(binding.etSearch.text?.toString())
                        Snackbar.make(
                            binding.root,
                            "Liquidación eliminada del historial",
                            Snackbar.LENGTH_LONG
                        ).setAction("Deshacer") {
                            // Opción de restaurar
                            repository.savePayroll(item)
                            loadDashboardData(binding.etSearch.text?.toString())
                        }.show()
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun formatCurrency(amount: Double): String {
        val format = NumberFormat.getCurrencyInstance(Locale.US)
        return format.format(amount)
    }
}
