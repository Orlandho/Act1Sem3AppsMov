package com.example.act1sem3appsmov

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmployeePayrollDataTest {

    @Test
    fun regularHours_noOvertime_calculatesCorrectly() {
        // 35 horas a $20/h -> $700 regular, 0 extra
        val data = EmployeePayrollData(
            firstName = "Ana",
            lastName = "Perez",
            employeeCode = "EMP-001",
            hourlyRate = 20.0,
            hoursWorked = 35.0,
            bonusPercentage = 10.0
        )

        assertEquals(35.0, data.regularHours, 0.001)
        assertEquals(0.0, data.overtimeHours, 0.001)
        assertEquals(700.0, data.regularPay, 0.001)
        assertEquals(0.0, data.overtimePay, 0.001)
        assertEquals(700.0, data.subtotalPay, 0.001)
        assertEquals(70.0, data.bonusAmount, 0.001) // 10%
        assertEquals(770.0, data.grossPay, 0.001)

        // Deducciones: 4% salud ($30.8) + 4% pensión ($30.8) = $61.6
        assertEquals(30.8, data.healthDeduction, 0.001)
        assertEquals(30.8, data.pensionDeduction, 0.001)
        assertEquals(61.6, data.totalDeductions, 0.001)
        assertEquals(708.4, data.netPay, 0.001)
    }

    @Test
    fun overtimeHours_over40_calculatesWith150PercentMultiplier() {
        // 48 horas a $30/h:
        // 40h reg * 30 = $1200
        // 8h ext * (30 * 1.5 = 45) = $360
        // Subtotal = $1560
        // Bono 0% = $0
        // Total Bruto = $1560
        // Deducciones (8%) = $124.8
        // Neto = $1435.2
        val data = EmployeePayrollData(
            firstName = "Carlos",
            lastName = "Mendoza",
            employeeCode = "EMP-002",
            hourlyRate = 30.0,
            hoursWorked = 48.0,
            bonusPercentage = 0.0
        )

        assertEquals(40.0, data.regularHours, 0.001)
        assertEquals(8.0, data.overtimeHours, 0.001)
        assertEquals(1200.0, data.regularPay, 0.001)
        assertEquals(360.0, data.overtimePay, 0.001)
        assertEquals(1560.0, data.subtotalPay, 0.001)
        assertEquals(0.0, data.bonusAmount, 0.001)
        assertEquals(1560.0, data.grossPay, 0.001)
        assertEquals(124.8, data.totalDeductions, 0.001)
        assertEquals(1435.2, data.netPay, 0.001)
    }

    @Test
    fun employeeRank_determinesCorrectClassification() {
        val senior = EmployeePayrollData("Juan", "Gomez", "EMP-01", 50.0, 40.0)
        assertEquals("Senior Specialist", senior.employeeRank)

        val mid = EmployeePayrollData("Maria", "Lopez", "EMP-02", 30.0, 40.0)
        assertEquals("Professional Mid", mid.employeeRank)

        val junior = EmployeePayrollData("Pedro", "Ruiz", "EMP-03", 15.0, 30.0)
        assertEquals("Associate Junior", junior.employeeRank)
    }

    @Test
    fun shareableReceipt_containsAllRequiredFields() {
        val data = EmployeePayrollData("Carlos", "Mendoza", "EMP-002", 30.0, 48.0, 10.0)
        val text = data.buildShareableReceipt("PAY-2026-9999", "03 Sep 2026")

        assertTrue(text.contains("Carlos Mendoza"))
        assertTrue(text.contains("EMP-002"))
        assertTrue(text.contains("PAY-2026-9999"))
        assertTrue(text.contains("TOTAL NETO A PAGAR"))
    }
}
