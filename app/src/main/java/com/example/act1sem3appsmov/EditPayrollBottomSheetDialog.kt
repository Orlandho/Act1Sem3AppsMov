package com.example.act1sem3appsmov

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.example.act1sem3appsmov.databinding.DialogEditPayrollBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * Diálogo modal BottomSheet para editar una liquidación en tiempo real (UPDATE).
 */
class EditPayrollBottomSheetDialog(
    private val payrollData: EmployeePayrollData,
    private val onSaveListener: (EmployeePayrollData) -> Unit
) : BottomSheetDialogFragment() {

    private var _binding: DialogEditPayrollBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogEditPayrollBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tvEditSubtitle.text = "Colaborador: ${payrollData.fullName} (${payrollData.employeeCode})"
        binding.etEditHourlyRate.setText(payrollData.hourlyRate.toString())
        binding.etEditHoursWorked.setText(payrollData.hoursWorked.toString())
        binding.sliderEditBonus.value = payrollData.bonusPercentage.toFloat().coerceIn(0f, 30f)
        binding.tvEditBonusPercentage.text = "${payrollData.bonusPercentage.toInt()}%"

        recalculateLive()
        setupListeners()
    }

    private fun setupListeners() {
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                recalculateLive()
            }
            override fun afterTextChanged(s: Editable?) {}
        }

        binding.etEditHourlyRate.addTextChangedListener(watcher)
        binding.etEditHoursWorked.addTextChangedListener(watcher)

        binding.sliderEditBonus.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                binding.tvEditBonusPercentage.text = "${value.toInt()}%"
                recalculateLive()
            }
        }

        binding.btnCancelEdit.setOnClickListener {
            dismiss()
        }

        binding.btnSaveEdit.setOnClickListener {
            val rate = binding.etEditHourlyRate.text?.toString()?.toDoubleOrNull()
            val hours = binding.etEditHoursWorked.text?.toString()?.toDoubleOrNull()
            val bonus = binding.sliderEditBonus.value.toDouble()

            if (rate == null || rate <= 0.0) {
                binding.tilEditHourlyRate.error = "Tarifa inválida"
                return@setOnClickListener
            } else {
                binding.tilEditHourlyRate.error = null
            }

            if (hours == null || hours <= 0.0) {
                binding.tilEditHoursWorked.error = "Horas inválidas"
                return@setOnClickListener
            } else {
                binding.tilEditHoursWorked.error = null
            }

            val updated = payrollData.copy(
                hourlyRate = rate,
                hoursWorked = hours,
                bonusPercentage = bonus
            )

            onSaveListener(updated)
            Toast.makeText(requireContext(), "✅ Liquidación actualizada exitosamente", Toast.LENGTH_SHORT).show()
            dismiss()
        }
    }

    private fun recalculateLive() {
        val rate = binding.etEditHourlyRate.text?.toString()?.toDoubleOrNull() ?: 0.0
        val hours = binding.etEditHoursWorked.text?.toString()?.toDoubleOrNull() ?: 0.0
        val bonus = binding.sliderEditBonus.value.toDouble()

        if (rate > 0.0 && hours > 0.0) {
            val temp = payrollData.copy(
                hourlyRate = rate,
                hoursWorked = hours,
                bonusPercentage = bonus
            )
            binding.tvEditSubtotal.text = temp.formatMoney(temp.subtotalPay)
            binding.tvEditDeductions.text = "-${temp.formatMoney(temp.totalDeductions)}"
            binding.tvEditNetPay.text = temp.formatMoney(temp.netPay)
        } else {
            binding.tvEditSubtotal.text = "$ 0.00"
            binding.tvEditDeductions.text = "$ 0.00"
            binding.tvEditNetPay.text = "$ 0.00"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "EditPayrollBottomSheetDialog"
    }
}
