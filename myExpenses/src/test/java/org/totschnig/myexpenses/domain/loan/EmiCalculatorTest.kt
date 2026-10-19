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

    @Test
    fun testIciciBankLoanBenchmark_LPCHE00049864136() {
        // Benchmark Test Case from loan_india_gap_enhancement_note.md:
        // Principal = ₹4,70,000.00 (47,000,000 paise)
        // Rate = 12.5% p.a., Tenure = 60 months
        // Disbursal = 21-May-2024, 1st EMI = 05-Jul-2024 (45 days pre-EMI)
        val principalPaise = 470_000_00L
        val ratePercent = 12.5
        val tenureMonths = 60
        val disbursal = java.time.LocalDate.of(2024, 5, 21)
        val firstEmi = java.time.LocalDate.of(2024, 7, 5)

        val schedule = EmiCalculator.generateAmortizationSchedule(
            principal = principalPaise,
            annualInterestRatePercent = ratePercent,
            tenureMonths = tenureMonths,
            disbursalDate = disbursal,
            firstEmiDate = firstEmi
        )

        assertEquals(60, schedule.size)

        // Month 1 Pre-EMI interest: ₹7,344.00 = 734400 paise
        assertEquals(7344_00L, schedule[0].interestPart)
        // Regular monthly EMI (Months 1 to 59): ₹10,629.00 = 1062900 paise
        assertEquals(10629_00L, schedule[0].emiAmount)
        assertEquals(10629_00L, schedule[27].emiAmount) // Month 28 regular EMI
        assertEquals(10629_00L, schedule[58].emiAmount) // Month 59 regular EMI

        // Month 28 Closing Principal as of Oct 2026: ₹2,87,962.00 = 28796200 paise
        assertEquals(287962_00L, schedule[27].closingBalance)

        // Final Month 60 EMI: ₹10,596.00 = 1059600 paise
        assertEquals(10596_00L, schedule[59].emiAmount)
        // Closing balance after Month 60: exactly ₹0.00
        assertEquals(0L, schedule[59].closingBalance)
    }

    @Test
    fun testCreditCardEmi_PoorvikaWithGst() {
        // Poorvika Mobile Swipe: Principal = ₹22,750 (22,750.00), 18% p.a., 6 months
        val principalPaise = 22750_00L
        val rate = 18.0
        val tenure = 6

        val schedule = EmiCalculator.generateAmortizationSchedule(
            principal = principalPaise,
            annualInterestRatePercent = rate,
            tenureMonths = tenure,
            isCreditCardEmi = true,
            cardGstRatePercent = 18.0
        )

        assertEquals(6, schedule.size)

        // Base EMI: ₹3,993.20 (approx 399320 paise)
        val baseEmi = schedule[0].emiAmount
        assertEquals(3993_20L, baseEmi)

        // Month 1 GST on interest: ₹61.42 (6142 paise)
        assertEquals(61_42L, schedule[0].gstOnInterest)
        // Month 6 GST on interest: ₹10.62 (1062 paise)
        assertEquals(10_62L, schedule[5].gstOnInterest)

        val totalGst = schedule.sumOf { it.gstOnInterest }
        assertEquals(217_65L, totalGst) // ~₹217.65
    }

    @Test
    fun testNoCostEmi_FlipkartDiscount() {
        // Flipkart E-Commerce Cart Price: ₹18,118.00 (1811800 paise), 15.99% p.a., 6 months
        val cartPricePaise = 18118_00L
        val rate = 15.99
        val tenure = 6

        val discount = EmiCalculator.calculateNoCostDiscount(cartPricePaise, rate, tenure)
        val bookedPrincipal = cartPricePaise - discount

        // Upfront discount ≈ ₹815.82 (81582 paise)
        assertEquals(815_83L, discount)
        // Booked principal ≈ ₹17,302.17
        assertEquals(17302_17L, bookedPrincipal)
    }
}
