package com.example.act1sem3appsmov

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.act1sem3appsmov.databinding.ActivityPayrollDetailBinding

class PayrollDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPayrollDetailBinding
    private var payrollData: EmployeePayrollData? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPayrollDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Recuperación segura del objeto serializado enviado desde Vista 1
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
            Toast.makeText(this, "Error: No se recibieron datos de nómina", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupViews()
        setupListeners()
    }

    private fun setupViews() {
        val data = payrollData ?: return

        binding.tvFullName.text = data.fullName
        binding.tvEmployeeCode.text = "Código: ${data.employeeCode}"
        binding.tvEmployeeRank.text = data.employeeRank

        // Jornada ordinaria
        binding.tvRegularHours.text = "${"%.1f".format(data.regularHours)} hrs"
        binding.tvRegularPay.text = data.formatMoney(data.regularPay)

        // Jornada extraordinaria (Horas extras al 150%)
        binding.tvOvertimeHours.text = "${"%.1f".format(data.overtimeHours)} hrs"
        binding.tvOvertimePay.text = data.formatMoney(data.overtimePay)

        if (data.overtimeHours > 0) {
            binding.tvOvertimeAlert.text = "⚡ +${"%.1f".format(data.overtimeHours)} hrs extras liquidadas con recargo del 50%"
            binding.tvOvertimeAlert.visibility = View.VISIBLE
        } else {
            binding.tvOvertimeAlert.text = "✓ Jornada regular sin sobretiempos registrados"
            binding.tvOvertimeAlert.setBackgroundResource(R.drawable.bg_pill_badge)
        }

        // Configuración inicial del Slider
        binding.sliderBonus.value = data.bonusPercentage.toFloat()
        updateCalculations(data.bonusPercentage)
    }

    private fun setupListeners() {
        // Dinámica interactiva: Slider en tiempo real
        binding.sliderBonus.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                updateCalculations(value.toDouble())
            }
        }

        // Botón retroceder a Vista 1
        binding.btnBack.setOnClickListener {
            finish()
        }

        // Botón avanzar a Vista 3 con la información consolidada
        binding.btnGenerateVoucher.setOnClickListener {
            val currentData = payrollData ?: return@setOnClickListener
            val intent = Intent(this, VoucherActivity::class.java).apply {
                putExtra(EmployeePayrollData.EXTRA_PAYROLL_DATA, currentData)
            }
            startActivity(intent)
        }
    }

    private fun updateCalculations(bonusPercent: Double) {
        val data = payrollData ?: return
        data.bonusPercentage = bonusPercent

        val bonusAmount = data.bonusAmount
        val subtotal = data.subtotalPay
        val totalDeductions = data.totalDeductions
        val netPay = data.netPay

        binding.tvBonusPercentage.text = "${bonusPercent.toInt()}% (+ ${data.formatMoney(bonusAmount)})"
        binding.tvBonusLabel.text = "Bono por Desempeño (${bonusPercent.toInt()}%)"
        binding.tvSubtotalPay.text = data.formatMoney(subtotal)
        binding.tvBonusAmount.text = "+${data.formatMoney(bonusAmount)}"
        binding.tvHealthDeduction.text = "-${data.formatMoney(data.healthDeduction)}"
        binding.tvPensionDeduction.text = "-${data.formatMoney(data.pensionDeduction)}"
        binding.tvTotalDeductions.text = "-${data.formatMoney(totalDeductions)}"
        binding.tvNetPay.text = data.formatMoney(netPay)
    }
}
