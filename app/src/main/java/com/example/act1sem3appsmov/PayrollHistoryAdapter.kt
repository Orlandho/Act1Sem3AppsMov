package com.example.act1sem3appsmov

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.act1sem3appsmov.databinding.ItemPayrollHistoryBinding

/**
 * Adaptador de alto rendimiento para el historial de liquidaciones en RecyclerView.
 * Utiliza ListAdapter y DiffUtil para animaciones y recálculos fluidos.
 */
class PayrollHistoryAdapter(
    private val onViewClick: (EmployeePayrollData) -> Unit,
    private val onEditClick: (EmployeePayrollData) -> Unit,
    private val onDeleteClick: (EmployeePayrollData) -> Unit
) : ListAdapter<EmployeePayrollData, PayrollHistoryAdapter.PayrollViewHolder>(PayrollDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PayrollViewHolder {
        val binding = ItemPayrollHistoryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PayrollViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PayrollViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class PayrollViewHolder(
        private val binding: ItemPayrollHistoryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: EmployeePayrollData) {
            binding.tvItemFolio.text = if (item.voucherFolio.isNotEmpty()) item.voucherFolio else "ID #${item.id}"
            binding.tvItemDate.text = item.issueDate
            binding.tvItemFullName.text = item.fullName
            binding.tvItemCode.text = "Código: ${item.employeeCode}"
            binding.tvItemNetPay.text = item.formatMoney(item.netPay)
            binding.tvItemRank.text = item.employeeRank

            // Horas extras condicionales
            if (item.overtimeHours > 0) {
                binding.tvItemOvertime.text = "⚡ +${"%.1f".format(item.overtimeHours)}h extras"
                binding.tvItemOvertime.visibility = View.VISIBLE
            } else {
                binding.tvItemOvertime.visibility = View.GONE
            }

            // Listeners de acción
            binding.btnItemView.setOnClickListener { onViewClick(item) }
            binding.btnItemEdit.setOnClickListener { onEditClick(item) }
            binding.btnItemDelete.setOnClickListener { onDeleteClick(item) }
            binding.root.setOnClickListener { onViewClick(item) }
        }
    }

    class PayrollDiffCallback : DiffUtil.ItemCallback<EmployeePayrollData>() {
        override fun areItemsTheSame(oldItem: EmployeePayrollData, newItem: EmployeePayrollData): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: EmployeePayrollData, newItem: EmployeePayrollData): Boolean {
            return oldItem == newItem
        }
    }
}
