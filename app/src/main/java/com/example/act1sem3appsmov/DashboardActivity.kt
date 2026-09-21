package com.example.act1sem3appsmov

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.act1sem3appsmov.databinding.ActivityDashboardBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import java.text.NumberFormat
import java.util.Locale

/**
 * Pantalla Principal de la Aplicación: Dashboard Ejecutivo y Gestión CRUD de Historial.
 * Muestra métricas consolidadas en tiempo real y permite Administrar (Crear, Ver, Editar y Eliminar)
 * todas las liquidaciones generadas.
 */
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class DashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardBinding
    private lateinit var repository: PayrollRepository
    private lateinit var historyAdapter: PayrollHistoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = PayrollRepository(this)
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

        // Abrir la pantalla de Configuración y Probador MySQL
        binding.btnMySQLConfig.setOnClickListener {
            val intent = Intent(this, MySQLConfigActivity::class.java)
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
    }

    private fun loadDashboardData(query: String? = null) {
        // Cargar métricas agregadas del Dashboard
        val metrics = repository.getDashboardMetrics()
        binding.tvStatTotalPayroll.text = formatCurrency(metrics.totalPayroll)
        binding.tvStatCount.text = "${metrics.totalCount} ${if (metrics.totalCount == 1) "boleta" else "boletas"}"
        binding.tvStatOvertimeHours.text = "${"%.1f".format(metrics.totalOvertimeHours)} hrs"
        binding.tvStatOvertimePay.text = "Recargos: ${formatCurrency(metrics.totalOvertimePay)}"
        binding.tvStatAvgPayroll.text = formatCurrency(metrics.avgPayroll)

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

        // Disparar sincronización asíncrona de registros pendientes
        lifecycleScope.launch {
            repository.syncPendingRecords()
            val updatedPayrolls = repository.getAll(binding.etSearch.text?.toString())
            historyAdapter.submitList(updatedPayrolls)
        }
    }

    private fun confirmDelete(item: EmployeePayrollData) {
        MaterialAlertDialogBuilder(this)
            .setTitle("🗑️ Eliminar Liquidación")
            .setMessage("¿Estás seguro de que deseas eliminar la liquidación de ${item.fullName} (${item.voucherFolio.ifEmpty { "ID #${item.id}" }})?\n\nEsta acción actualizará automáticamente los totales del Dashboard y sincronizará con MySQL.")
            .setPositiveButton("Eliminar") { _, _ ->
                val deleted = repository.delete(item)
                if (deleted) {
                    loadDashboardData(binding.etSearch.text?.toString())
                    Snackbar.make(
                        binding.root,
                        "Liquidación eliminada del historial",
                        Snackbar.LENGTH_LONG
                    ).setAction("Deshacer") {
                        // Opción de restaurar
                        repository.insert(item)
                        loadDashboardData(binding.etSearch.text?.toString())
                    }.show()
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
