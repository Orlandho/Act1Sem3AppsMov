package com.example.act1sem3appsmov

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.act1sem3appsmov.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        EdgeToEdgeHelper.applyEdgeToEdge(
            activity = this,
            rootView = binding.root,
            headerView = binding.llHeader,
            scrollContentView = binding.llMainContent
        )

        setupListeners()
    }

    private fun setupListeners() {
        // Botón principal: Validar y avanzar a Vista 2
        binding.btnCalculate.setOnClickListener {
            validateAndProceed()
        }

        // Botón Demo rápido para sustentación ante el docente
        binding.btnQuickDemo.setOnClickListener {
            loadDemoData()
        }

        // Botón Limpiar formulario
        binding.btnClear.setOnClickListener {
            clearForm()
        }

        // Chips interactivos de horas predefinidas
        binding.chip20h.setOnClickListener { binding.etHoursWorked.setText("20") }
        binding.chip40h.setOnClickListener { binding.etHoursWorked.setText("40") }
        binding.chip48h.setOnClickListener { binding.etHoursWorked.setText("48") }
        binding.chip60h.setOnClickListener { binding.etHoursWorked.setText("60") }
    }

    private fun validateAndProceed() {
        var isValid = true

        val firstName = binding.etFirstName.text?.toString()?.trim().orEmpty()
        val lastName = binding.etLastName.text?.toString()?.trim().orEmpty()
        val employeeCode = binding.etEmployeeCode.text?.toString()?.trim().orEmpty()
        val hourlyRateStr = binding.etHourlyRate.text?.toString()?.trim().orEmpty()
        val hoursWorkedStr = binding.etHoursWorked.text?.toString()?.trim().orEmpty()

        if (firstName.isEmpty()) {
            binding.tilFirstName.error = "Ingresa los nombres del colaborador"
            isValid = false
        } else {
            binding.tilFirstName.error = null
        }

        if (lastName.isEmpty()) {
            binding.tilLastName.error = "Ingresa los apellidos del colaborador"
            isValid = false
        } else {
            binding.tilLastName.error = null
        }

        if (employeeCode.isEmpty()) {
            binding.tilEmployeeCode.error = "Ingresa el código (ej: EMP-101)"
            isValid = false
        } else {
            binding.tilEmployeeCode.error = null
        }

        val hourlyRate = hourlyRateStr.toDoubleOrNull()
        if (hourlyRate == null || hourlyRate <= 0.0) {
            binding.tilHourlyRate.error = "Tarifa inválida (debe ser mayor a 0)"
            isValid = false
        } else {
            binding.tilHourlyRate.error = null
        }

        val hoursWorked = hoursWorkedStr.toDoubleOrNull()
        if (hoursWorked == null || hoursWorked <= 0.0) {
            binding.tilHoursWorked.error = "Horas inválidas (debe ser mayor a 0)"
            isValid = false
        } else {
            binding.tilHoursWorked.error = null
        }

        if (!isValid) {
            Toast.makeText(this, "Por favor completa todos los campos requeridos", Toast.LENGTH_SHORT).show()
            return
        }

        // Empaquetar datos en modelo de nómina
        val payrollData = EmployeePayrollData(
            firstName = firstName,
            lastName = lastName,
            employeeCode = employeeCode,
            hourlyRate = hourlyRate!!,
            hoursWorked = hoursWorked!!
        )

        // Comunicación mediante Intent Explícito hacia Vista 2
        val intent = Intent(this, PayrollDetailActivity::class.java).apply {
            putExtra(EmployeePayrollData.EXTRA_PAYROLL_DATA, payrollData)
        }
        startActivity(intent)
    }

    private fun loadDemoData() {
        binding.etFirstName.setText("Carlos Eduardo")
        binding.etLastName.setText("Mendoza Ramos")
        binding.etEmployeeCode.setText("EMP-2026-88")
        binding.etHourlyRate.setText("35.50")
        binding.etHoursWorked.setText("48")

        binding.tilFirstName.error = null
        binding.tilLastName.error = null
        binding.tilEmployeeCode.error = null
        binding.tilHourlyRate.error = null
        binding.tilHoursWorked.error = null

        Toast.makeText(this, "Datos de prueba cargados exitosamente", Toast.LENGTH_SHORT).show()
    }

    private fun clearForm() {
        binding.etFirstName.text?.clear()
        binding.etLastName.text?.clear()
        binding.etEmployeeCode.text?.clear()
        binding.etHourlyRate.text?.clear()
        binding.etHoursWorked.text?.clear()
        binding.chipGroupHours.clearCheck()

        binding.tilFirstName.error = null
        binding.tilLastName.error = null
        binding.tilEmployeeCode.error = null
        binding.tilHourlyRate.error = null
        binding.tilHoursWorked.error = null

        binding.etFirstName.requestFocus()
    }
}