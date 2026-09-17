package com.example.act1sem3appsmov

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.act1sem3appsmov.databinding.ActivityVoucherBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VoucherActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVoucherBinding
    private var payrollData: EmployeePayrollData? = null
    private var voucherFolio: String = ""
    private var issueDate: String = ""

    // Contrato SAF nativo para guardar y descargar el archivo de texto en el dispositivo
    private val createDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        if (uri != null) {
            saveReceiptToFile(uri)
        } else {
            Toast.makeText(this, "Descarga cancelada", Toast.LENGTH_SHORT).show()
        }
    }

    // Launcher para abrir la vista dedicada de edición y refrescar la boleta in-situ sin regresar al Dashboard
    private val editPayrollLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val updated = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                result.data?.getSerializableExtra(
                    EmployeePayrollData.EXTRA_PAYROLL_DATA,
                    EmployeePayrollData::class.java
                )
            } else {
                @Suppress("DEPRECATION")
                result.data?.getSerializableExtra(EmployeePayrollData.EXTRA_PAYROLL_DATA) as? EmployeePayrollData
            }
            if (updated != null) {
                payrollData = updated
                setupViews()
                Toast.makeText(this, "✅ Boleta actualizada en tiempo real", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVoucherBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Recuperación de datos desde Vista 2
        payrollData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra(
                EmployeePayrollData.EXTRA_PAYROLL_DATA,
                EmployeePayrollData::class.java
            )
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra(EmployeePayrollData.EXTRA_PAYROLL_DATA) as? EmployeePayrollData
        }

        if (payrollData == null) {
            Toast.makeText(this, "Error: No se recibió la liquidación", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        generateMetadata()
        setupViews()
        setupListeners()
    }

    private fun generateMetadata() {
        val data = payrollData ?: return

        if (data.voucherFolio.isNotEmpty()) {
            // Viene del historial del Dashboard
            voucherFolio = data.voucherFolio
            issueDate = data.issueDate
        } else {
            // Nueva liquidación emitida desde el flujo de 3 vistas
            val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
            issueDate = dateFormat.format(Date())

            val yearFormat = SimpleDateFormat("yyyy", Locale.getDefault())
            val randomDigits = (1000..9999).random()
            voucherFolio = "FOLIO: PAY-${yearFormat.format(Date())}-$randomDigits"

            data.voucherFolio = voucherFolio
            data.issueDate = issueDate

            // Persistir automáticamente en SQLite (CREATE)
            val dbHelper = PayrollDbHelper(this)
            val newId = dbHelper.insert(data)
            if (newId > 0) {
                payrollData = data.copy(id = newId)
                Toast.makeText(this, "✅ Liquidación guardada en el historial", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupViews() {
        val data = payrollData ?: return

        binding.tvVoucherFolio.text = voucherFolio
        binding.tvIssueDate.text = "Fecha: $issueDate"

        // Ficha del colaborador
        binding.tvVoucherFullName.text = data.fullName
        binding.tvVoucherCode.text = "ID: ${data.employeeCode}"
        binding.tvVoucherRank.text = data.employeeRank

        // Conceptos detallados
        binding.tvConceptRegularLabel.text = "Horas Regulares (${"%.1f".format(data.regularHours)}h x ${data.formatMoney(data.hourlyRate)})"
        binding.tvConceptRegularPay.text = data.formatMoney(data.regularPay)

        binding.tvConceptOvertimeLabel.text = "Horas Extras (${"%.1f".format(data.overtimeHours)}h x ${data.formatMoney(data.overtimeRate)})"
        binding.tvConceptOvertimePay.text = data.formatMoney(data.overtimePay)

        binding.tvConceptBonusLabel.text = "Incentivo / Bono (${data.bonusPercentage.toInt()}%)"
        binding.tvConceptBonusPay.text = "+${data.formatMoney(data.bonusAmount)}"

        binding.tvConceptDeductions.text = "-${data.formatMoney(data.totalDeductions)}"

        // Total Neto a pagar
        binding.tvVoucherNetPay.text = data.formatMoney(data.netPay)
    }

    private fun setupListeners() {
        // Descargar comprobante en archivo de texto (.txt) mediante Storage Access Framework
        binding.btnDownloadReceipt.setOnClickListener {
            val data = payrollData ?: return@setOnClickListener
            val safeCode = data.employeeCode.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
            val defaultFileName = "Boleta_${safeCode}_${System.currentTimeMillis()}.txt"
            createDocumentLauncher.launch(defaultFileName)
        }

        // Dinámica de Compartir Comprobante (Intent Implícito nativo de Android)
        binding.btnShareReceipt.setOnClickListener {
            val data = payrollData ?: return@setOnClickListener
            val shareableText = data.buildShareableReceipt(voucherFolio, issueDate)

            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, shareableText)
                putExtra(Intent.EXTRA_SUBJECT, "Boleta de Pago - ${data.fullName}")
                type = "text/plain"
            }

            val chooser = Intent.createChooser(sendIntent, "Compartir boleta mediante...")
            startActivity(chooser)
        }

        // Dinámica de Reinicio: Volver al Paso 1 limpiando el stack de navegación
        binding.btnRestart.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
            finish()
        }

        // Volver al Dashboard Central con historial y métricas
        binding.btnGoToDashboard.setOnClickListener {
            val intent = Intent(this, DashboardActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
            finish()
        }

        // Modificar la liquidación completa abriendo la nueva pantalla EditPayrollActivity
        binding.btnEditPayroll.setOnClickListener {
            val data = payrollData ?: return@setOnClickListener
            val intent = Intent(this, EditPayrollActivity::class.java).apply {
                putExtra(EmployeePayrollData.EXTRA_PAYROLL_DATA, data)
            }
            editPayrollLauncher.launch(intent)
        }
    }

    /**
     * Escribe el comprobante oficial en el URI seleccionado por el usuario con codificación UTF-8.
     */
    private fun saveReceiptToFile(uri: Uri) {
        val data = payrollData ?: return
        val receiptContent = data.buildShareableReceipt(voucherFolio, issueDate)

        try {
            contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { writer ->
                writer.write(receiptContent)
            }
            Toast.makeText(
                this,
                "✅ Boleta guardada exitosamente en el dispositivo",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Error al guardar el archivo: ${e.localizedMessage}",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
