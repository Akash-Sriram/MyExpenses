package org.totschnig.myexpenses.domain.loan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class EmiCalculatorTest {

    @Test
    fun testReducingBalanceEmi_personalLoan() {
        // Principal = ₹1,00,000 (10,000,000 paise), 12% p.a., 12 months
        // Formula gives ₹8,884.88 -> 888488 paise
        val principal = 100_000_00L
        val rate = 12.0
        val tenure = 12

        val emiPaise = EmiCalculator.calculateReducingBalanceEmi(principal, rate, tenure)
        val emiRupees = emiPaise / 100.0

        // Standard Indian bank calculator gives ₹8,885
        assertEquals(8884.88, emiRupees, 1.0)
    }

    @Test
    fun testReducingBalanceEmi_homeLoan() {
        // Principal = ₹30,00,000 (300,000,000 paise), 8.5% p.a., 240 months (20 years)
        // Standard bank calculator gives ₹26,035
        val principal = 3_000_000_00L
        val rate = 8.5
        val tenure = 240

        val emiPaise = EmiCalculator.calculateReducingBalanceEmi(principal, rate, tenure)
        val emiRupees = emiPaise / 100.0

        assertEquals(26035.0, emiRupees, 1.0)
    }

    @Test
    fun testZeroCostEmi() {
        // Principal = ₹60,000 (6,000,000 paise), 6 months No-Cost EMI
        val principal = 60_000_00L
        val tenure = 6

        val emiPaise = EmiCalculator.calculateZeroCostEmi(principal, tenure)
        assertEquals(10_000_00L, emiPaise)
    }

    @Test
    fun testFlatRateEmi() {
        // Principal = ₹50,000 (5,000,000 paise), 10% flat, 12 months
        // Total interest = ₹5,000, Total = ₹55,000, EMI = 55000 / 12 = 4583.33
        val principal = 50_000_00L
        val rate = 10.0
        val tenure = 12

        val emiPaise = EmiCalculator.calculateFlatRateEmi(principal, rate, tenure)
        val emiRupees = emiPaise / 100.0

        assertEquals(4583.0, emiRupees, 1.0)
    }

    @Test
    fun testAmortizationScheduleSum() {
        val principal = 100_000_00L // ₹1 Lakh
        val rate = 10.5
        val tenure = 24

        val schedule = EmiCalculator.generateAmortizationSchedule(principal, rate, tenure)
        assertEquals(tenure, schedule.size)

        var totalPrincipalPaid = 0L
        var totalInterestPaid = 0L

        schedule.forEach { row ->
            totalPrincipalPaid += row.principalPart
            totalInterestPaid += row.interestPart
            assertTrue(row.principalPart > 0)
            assertTrue(row.interestPart >= 0)
        }

        // Must exactly equal original principal (absorbed rounding in final installment)
        assertEquals(principal, totalPrincipalPaid)
        assertEquals(0L, schedule.last().closingBalance)
    }

    @Test
    fun testGenerateInstallmentsForLoan() {
        val loan = Loan(
            id = 1L,
            label = "HDFC Auto Loan",
            lenderName = "HDFC Bank",
            loanType = LoanType.REDUCING_BALANCE,
            principalAmount = 200_000_00L,
            interestRatePercent = 9.0,
            tenureMonths = 12,
            monthlyEmiAmount = 1749000L, // ~₹17,490
            startDate = 1770000000000L,
            dueDayOfMonth = 5
        )

        val installments = EmiCalculator.generateInstallmentsForLoan(1L, loan, ZoneId.of("Asia/Kolkata"))
        assertEquals(12, installments.size)

        var sumPrincipal = 0L
        installments.forEachIndexed { index, inst ->
            assertEquals(index + 1, inst.installmentNumber)
            assertEquals(1L, inst.loanId)
            assertEquals(InstallmentStatus.PENDING, inst.status)
            sumPrincipal += inst.principalPart
        }

        assertEquals(loan.principalAmount, sumPrincipal)
    }
}
