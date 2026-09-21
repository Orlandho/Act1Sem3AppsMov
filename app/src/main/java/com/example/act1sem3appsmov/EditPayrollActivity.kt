package com.example.act1sem3appsmov

import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.act1sem3appsmov.databinding.ActivityEditPayrollBinding

/**
 * Nueva Vista Dedicada para la Modificación Integral de una Liquidación (UPDATE).
 * Permite modificar todos los campos (identificación, tarifa, horas y bono) con recálculo en vivo
 * y actualización directa en la base de datos SQLite.
 */
class EditPayrollActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditPayrollBinding
    private lateinit var dbHelper: PayrollDbHelper
    private var originalData: EmployeePayrollData? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditPayrollBinding.inflate(layoutInflater)
        setContentView(binding.root)

        EdgeToEdgeHelper.applyEdgeToEdge(
            activity = this,
            rootView = binding.root,
            headerView = binding.llHeader,
            scrollContentView = binding.llEditContent
        )

        dbHelper = PayrollDbHelper(this)

        originalData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra(
                EmployeePayrollData.EXTRA_PAYROLL_DATA,
                EmployeePayrollData::class.java
            )
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra(EmployeePayrollData.EXTRA_PAYROLL_DATA) as? EmployeePayrollData
        }

        if (originalData == null) {
            Toast.makeText(this, "Error: No se cargó la liquidación a editar", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        populateInitialData()
        setupListeners()
        recalculateLive()
    }

    private fun populateInitialData() {
        val data = originalData ?: return

        binding.tvEditHeaderFolio.text = if (data.voucherFolio.isNotEmpty()) data.voucherFolio else "REGISTRO ID #${data.id}"
        binding.etEditFirstName.setText(data.firstName)
        binding.etEditLastName.setText(data.lastName)
        binding.etEditEmployeeCode.setText(data.employeeCode)
        binding.etEditHourlyRate.setText(data.hourlyRate.toString())
        binding.etEditHoursWorked.setText(data.hoursWorked.toString())

        val bonusVal = data.bonusPercentage.toFloat().coerceIn(0f, 30f)
        binding.sliderEditBonus.value = bonusVal
        binding.tvEditBonusBadge.text = "${bonusVal.toInt()}%"
    }

    private fun setupListeners() {
        val liveWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                recalculateLive()
            }
            override fun afterTextChanged(s: Editable?) {}
        }

        binding.etEditHourlyRate.addTextChangedListener(liveWatcher)
        binding.etEditHoursWorked.addTextChangedListener(liveWatcher)

        binding.sliderEditBonus.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                binding.tvEditBonusBadge.text = "${value.toInt()}%"
                recalculateLive()
            }
        }

        // Guardar cambios en SQLite
        binding.btnSavePayrollEdit.setOnClickListener {
            saveChanges()
        }

        // Cancelar y volver
        binding.btnCancelPayrollEdit.setOnClickListener {
            finish()
        }
    }

    private fun recalculateLive() {
        val current = originalData ?: return

        val rate = binding.etEditHourlyRate.text?.toString()?.toDoubleOrNull() ?: 0.0
        val hours = binding.etEditHoursWorked.text?.toString()?.toDoubleOrNull() ?: 0.0
        val bonus = binding.sliderEditBonus.value.toDouble()

        if (rate > 0.0 && hours > 0.0) {
            val preview = current.copy(
                hourlyRate = rate,
                hoursWorked = hours,
                bonusPercentage = bonus
            )

            binding.tvLiveRegularLabel.text = "Horas Regulares (${"%.1f".format(preview.regularHours)}h)"
            binding.tvLiveRegularPay.text = preview.formatMoney(preview.regularPay)

            binding.tvLiveOvertimeLabel.text = "Horas Extras (${"%.1f".format(preview.overtimeHours)}h x 150%)"
            binding.tvLiveOvertimePay.text = "+${preview.formatMoney(preview.overtimePay)}"

            binding.tvLiveSubtotal.text = preview.formatMoney(preview.subtotalPay)
            binding.tvLiveBonusLabel.text = "Bono Productividad (${bonus.toInt()}%)"
            binding.tvLiveBonusAmount.text = "+${preview.formatMoney(preview.bonusAmount)}"

            binding.tvLiveHealthDeduction.text = "-${preview.formatMoney(preview.healthDeduction)}"
            binding.tvLivePensionDeduction.text = "-${preview.formatMoney(preview.pensionDeduction)}"

            binding.tvLiveNetPay.text = preview.formatMoney(preview.netPay)
        } else {
            binding.tvLiveRegularPay.text = "$ 0.00"
            binding.tvLiveOvertimePay.text = "$ 0.00"
            binding.tvLiveSubtotal.text = "$ 0.00"
            binding.tvLiveBonusAmount.text = "$ 0.00"
            binding.tvLiveHealthDeduction.text = "$ 0.00"
            binding.tvLivePensionDeduction.text = "$ 0.00"
            binding.tvLiveNetPay.text = "$ 0.00"
        }
    }

    private fun saveChanges() {
        val current = originalData ?: return

        val firstName = binding.etEditFirstName.text?.toString()?.trim().orEmpty()
        val lastName = binding.etEditLastName.text?.toString()?.trim().orEmpty()
        val employeeCode = binding.etEditEmployeeCode.text?.toString()?.trim().orEmpty()
        val rate = binding.etEditHourlyRate.text?.toString()?.toDoubleOrNull()
        val hours = binding.etEditHoursWorked.text?.toString()?.toDoubleOrNull()
        val bonus = binding.sliderEditBonus.value.toDouble()

        var isValid = true

        if (firstName.isEmpty()) {
            binding.tilEditFirstName.error = "Ingresa los nombres"
            isValid = false
        } else {
            binding.tilEditFirstName.error = null
        }

        if (lastName.isEmpty()) {
            binding.tilEditLastName.error = "Ingresa los apellidos"
            isValid = false
        } else {
            binding.tilEditLastName.error = null
        }

        if (employeeCode.isEmpty()) {
            binding.tilEditEmployeeCode.error = "Ingresa el código"
            isValid = false
        } else {
            binding.tilEditEmployeeCode.error = null
        }

        if (rate == null || rate <= 0.0) {
            binding.tilEditHourlyRate.error = "Tarifa inválida (> 0)"
            isValid = false
        } else {
            binding.tilEditHourlyRate.error = null
        }

        if (hours == null || hours <= 0.0) {
            binding.tilEditHoursWorked.error = "Horas inválidas (> 0)"
            isValid = false
        } else {
            binding.tilEditHoursWorked.error = null
        }

        if (!isValid) {
            Toast.makeText(this, "Completa todos los campos correctamente", Toast.LENGTH_SHORT).show()
            return
        }

        val updated = current.copy(
            firstName = firstName,
            lastName = lastName,
            employeeCode = employeeCode,
            hourlyRate = rate!!,
            hoursWorked = hours!!,
            bonusPercentage = bonus
        )

        val repository = PayrollRepository.getInstance(this)
        repository.updatePayroll(updated) { success ->
            if (success) {
                val resultIntent = android.content.Intent().apply {
                    putExtra(EmployeePayrollData.EXTRA_PAYROLL_DATA, updated)
                }
                setResult(RESULT_OK, resultIntent)
                Toast.makeText(this, "✅ Liquidación actualizada (Sincronización a MySQL despachada)", Toast.LENGTH_LONG).show()
                finish()
            } else {
                Toast.makeText(this, "Error al actualizar la base de datos", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
