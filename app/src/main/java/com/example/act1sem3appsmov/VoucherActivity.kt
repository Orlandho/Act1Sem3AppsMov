package com.example.act1sem3appsmov

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
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
        val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        issueDate = dateFormat.format(Date())

        val yearFormat = SimpleDateFormat("yyyy", Locale.getDefault())
        val randomDigits = (1000..9999).random()
        voucherFolio = "FOLIO: PAY-${yearFormat.format(Date())}-$randomDigits"
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

        // Volver a ajustar el bono en Paso 2
        binding.btnBackToStep2.setOnClickListener {
            finish()
        }
    }
}
