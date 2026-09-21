package com.example.act1sem3appsmov

import java.io.Serializable
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * Modelo de datos de nómina que encapsula la información del colaborador,
 * horas trabajadas, tarifa y las reglas de negocio para liquidación de nómina.
 * Implementa [Serializable] para transferencia limpia y desacoplada entre Activities.
 */
data class EmployeePayrollData(
    val firstName: String,
    val lastName: String,
    val employeeCode: String,
    val hourlyRate: Double,
    val hoursWorked: Double,
    var bonusPercentage: Double = 10.0,
    val id: Long = 0L,
    var voucherFolio: String = "",
    var issueDate: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    var syncStatus: Int = SYNC_STATUS_PENDING,
    var remoteId: Long = 0L,
    var syncMessage: String = ""
) : Serializable {

    val fullName: String
        get() = "${firstName.trim()} ${lastName.trim()}".trim()

    val regularHours: Double
        get() = min(hoursWorked, REGULAR_HOURS_LIMIT)

    val overtimeHours: Double
        get() = max(0.0, hoursWorked - REGULAR_HOURS_LIMIT)

    val regularPay: Double
        get() = regularHours * hourlyRate

    val overtimeRate: Double
        get() = hourlyRate * OVERTIME_MULTIPLIER

    val overtimePay: Double
        get() = overtimeHours * overtimeRate

    val subtotalPay: Double
        get() = regularPay + overtimePay

    val bonusAmount: Double
        get() = subtotalPay * (bonusPercentage / 100.0)

    val grossPay: Double
        get() = subtotalPay + bonusAmount

    val healthDeduction: Double
        get() = grossPay * HEALTH_RATE

    val pensionDeduction: Double
        get() = grossPay * PENSION_RATE

    val totalDeductions: Double
        get() = healthDeduction + pensionDeduction

    val netPay: Double
        get() = grossPay - totalDeductions

    val employeeRank: String
        get() = when {
            hourlyRate >= 45.0 || hoursWorked >= 50.0 -> "Senior Specialist"
            hourlyRate >= 25.0 || hoursWorked >= 40.0 -> "Professional Mid"
            else -> "Associate Junior"
        }

    fun formatMoney(amount: Double): String {
        val format = NumberFormat.getCurrencyInstance(Locale.US)
        return format.format(amount)
    }

    /**
     * Genera el texto formateado para compartir el comprobante por WhatsApp u otras apps.
     */
    fun buildShareableReceipt(voucherCode: String, issueDate: String): String {
        return """
            ====================================
               🏛️ COMPROBANTE DE PAGO OFICIAL
               Sistema SmartPayroll Pro
            ====================================
            📄 Folio: $voucherCode
            📅 Fecha: $issueDate
            👤 Colaborador: $fullName
            🆔 Código: $employeeCode
            🎖️ Rango: $employeeRank
            ------------------------------------
            ⏱️ Horas Regulares: ${"%.1f".format(regularHours)} hrs -> ${formatMoney(regularPay)}
            ⚡ Horas Extras:    ${"%.1f".format(overtimeHours)} hrs -> ${formatMoney(overtimePay)}
            🎁 Bono (${"%.0f".format(bonusPercentage)}%):                 ${formatMoney(bonusAmount)}
            💰 Total Bruto:              ${formatMoney(grossPay)}
            ------------------------------------
            🏥 Salud (4%):              -${formatMoney(healthDeduction)}
            🏦 Pensión (4%):            -${formatMoney(pensionDeduction)}
            ------------------------------------
            💵 TOTAL NETO A PAGAR:      ${formatMoney(netPay)}
            ====================================
            ✅ Estado: APROBADO Y AUDITADO
        """.trimIndent()
    }

    companion object {
        const val EXTRA_PAYROLL_DATA = "extra_payroll_data"
        const val REGULAR_HOURS_LIMIT = 40.0
        const val OVERTIME_MULTIPLIER = 1.5
        const val HEALTH_RATE = 0.04
        const val PENSION_RATE = 0.04

        const val SYNC_STATUS_PENDING = 0
        const val SYNC_STATUS_SYNCED = 1
        const val SYNC_STATUS_ERROR = 2
    }
}
