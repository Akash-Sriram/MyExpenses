package org.totschnig.myexpenses.domain.loan

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * Pure math financial calculator for EMI and Amortization schedules.
 * Zero Android dependencies — fully unit-testable.
 */
object EmiCalculator {

    /**
     * Calculates the monthly EMI for a Reducing Balance Loan (standard Indian home, car, personal loan).
     *
     * Formula:
     *   E = P * r * (1 + r)^n / ((1 + r)^n - 1)
     * where:
     *   P = Principal amount in minor units (paise)
     *   r = Monthly interest rate (annual rate / 12 / 100)
     *   n = Tenure in months
     */
    fun calculateReducingBalanceEmi(
        principal: Long,
        annualInterestRatePercent: Double,
        tenureMonths: Int
    ): Long {
        if (tenureMonths <= 0 || principal <= 0L) return 0L
        if (annualInterestRatePercent <= 0.0) {
            return (principal.toDouble() / tenureMonths).roundToLong()
        }

        val monthlyRate = annualInterestRatePercent / 12.0 / 100.0
        val factor = (1.0 + monthlyRate).pow(tenureMonths.toDouble())
        val emi = principal * monthlyRate * factor / (factor - 1.0)
        return emi.roundToLong()
    }

    /**
     * Calculates the monthly EMI for a Flat Rate Loan.
     * Interest is charged on the initial principal across the entire tenure.
     */
    fun calculateFlatRateEmi(
        principal: Long,
        annualInterestRatePercent: Double,
        tenureMonths: Int
    ): Long {
        if (tenureMonths <= 0 || principal <= 0L) return 0L
        val tenureYears = tenureMonths / 12.0
        val totalInterest = (principal * annualInterestRatePercent * tenureYears / 100.0).roundToLong()
        val totalPayable = principal + totalInterest
        return (totalPayable.toDouble() / tenureMonths).roundToLong()
    }

    /**
     * Calculates the monthly EMI for a Zero-Cost / No-Cost EMI (e.g. Amazon, Flipkart).
     */
    fun calculateZeroCostEmi(
        principal: Long,
        tenureMonths: Int
    ): Long {
        if (tenureMonths <= 0 || principal <= 0L) return 0L
        return (principal.toDouble() / tenureMonths).roundToLong()
    }

    /**
     * Computes the recommended monthly EMI based on the loan type.
     */
    fun calculateEmi(loanType: LoanType, principal: Long, annualRate: Double, tenureMonths: Int): Long {
        return when (loanType) {
            LoanType.REDUCING_BALANCE -> calculateReducingBalanceEmi(principal, annualRate, tenureMonths)
            LoanType.FLAT_RATE -> calculateFlatRateEmi(principal, annualRate, tenureMonths)
            LoanType.NO_COST_EMI -> calculateZeroCostEmi(principal, tenureMonths)
        }
    }

    /**
     * Generates a month-by-month Amortization Schedule for a Reducing Balance Loan.
     * Automatically absorbs rounding difference in the final month to guarantee exact zero balance.
     */
    fun generateAmortizationSchedule(
        principal: Long,
        annualInterestRatePercent: Double,
        tenureMonths: Int,
        customEmi: Long? = null
    ): List<AmortizationRow> {
        if (tenureMonths <= 0 || principal <= 0L) return emptyList()

        val emi = customEmi ?: calculateReducingBalanceEmi(principal, annualInterestRatePercent, tenureMonths)
        val monthlyRate = annualInterestRatePercent / 12.0 / 100.0

        var currentBalance = principal
        val rows = ArrayList<AmortizationRow>(tenureMonths)

        for (month in 1..tenureMonths) {
            val opening = currentBalance

            if (month == tenureMonths) {
                // Final installment absorbs any remaining rounding difference
                val interest = if (annualInterestRatePercent > 0.0) {
                    (opening * monthlyRate).roundToLong()
                } else 0L
                val principalPaid = opening
                val finalEmi = principalPaid + interest
                rows.add(
                    AmortizationRow(
                        installmentNumber = month,
                        openingBalance = opening,
                        emiAmount = finalEmi,
                        principalPart = principalPaid,
                        interestPart = interest,
                        closingBalance = 0L
                    )
                )
                break
            }

            val interest = if (annualInterestRatePercent > 0.0) {
                (opening * monthlyRate).roundToLong()
            } else 0L
            val principalPaid = (emi - interest).coerceAtMost(opening)
            currentBalance = (opening - principalPaid).coerceAtLeast(0L)

            rows.add(
                AmortizationRow(
                    installmentNumber = month,
                    openingBalance = opening,
                    emiAmount = emi,
                    principalPart = principalPaid,
                    interestPart = interest,
                    closingBalance = currentBalance
                )
            )
        }

        return rows
    }

    /**
     * Generates EmiInstallment entity list ready for database insertion, computing due dates for each month.
     */
    fun generateInstallmentsForLoan(
        loanId: Long,
        loan: Loan,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): List<EmiInstallment> {
        val schedule = when (loan.loanType) {
            LoanType.REDUCING_BALANCE -> generateAmortizationSchedule(
                loan.principalAmount,
                loan.interestRatePercent,
                loan.tenureMonths,
                loan.monthlyEmiAmount
            )
            LoanType.FLAT_RATE -> {
                val totalInterest = (loan.principalAmount * loan.interestRatePercent * (loan.tenureMonths / 12.0) / 100.0).roundToLong()
                val monthlyPrincipal = loan.principalAmount / loan.tenureMonths
                val monthlyInterest = totalInterest / loan.tenureMonths
                (1..loan.tenureMonths).map { month ->
                    val isLast = month == loan.tenureMonths
                    val pPart = if (isLast) loan.principalAmount - (monthlyPrincipal * (loan.tenureMonths - 1)) else monthlyPrincipal
                    val iPart = if (isLast) totalInterest - (monthlyInterest * (loan.tenureMonths - 1)) else monthlyInterest
                    AmortizationRow(
                        installmentNumber = month,
                        openingBalance = 0L,
                        emiAmount = pPart + iPart,
                        principalPart = pPart,
                        interestPart = iPart,
                        closingBalance = 0L
                    )
                }
            }
            LoanType.NO_COST_EMI -> {
                val monthlyPrincipal = loan.principalAmount / loan.tenureMonths
                (1..loan.tenureMonths).map { month ->
                    val isLast = month == loan.tenureMonths
                    val pPart = if (isLast) loan.principalAmount - (monthlyPrincipal * (loan.tenureMonths - 1)) else monthlyPrincipal
                    AmortizationRow(
                        installmentNumber = month,
                        openingBalance = 0L,
                        emiAmount = pPart,
                        principalPart = pPart,
                        interestPart = 0L,
                        closingBalance = 0L
                    )
                }
            }
        }

        val startLocalDate = Instant.ofEpochMilli(loan.startDate).atZone(zoneId).toLocalDate()

        return schedule.map { row ->
            // Compute due date by adding (row.installmentNumber) months, setting day to dueDayOfMonth
            val dueLocalDate = calculateDueDate(startLocalDate, row.installmentNumber, loan.dueDayOfMonth)
            val dueEpochMillis = dueLocalDate.atStartOfDay(zoneId).toInstant().toEpochMilli()

            EmiInstallment(
                loanId = loanId,
                installmentNumber = row.installmentNumber,
                dueDate = dueEpochMillis,
                principalPart = row.principalPart,
                interestPart = row.interestPart,
                totalAmount = row.emiAmount,
                status = InstallmentStatus.PENDING
            )
        }
    }

    private fun calculateDueDate(startDate: LocalDate, monthOffset: Int, targetDay: Int): LocalDate {
        val targetMonth = startDate.plusMonths(monthOffset.toLong())
        val maxDayInMonth = targetMonth.lengthOfMonth()
        val actualDay = targetDay.coerceIn(1, maxDayInMonth)
        return targetMonth.withDayOfMonth(actualDay)
    }
}
