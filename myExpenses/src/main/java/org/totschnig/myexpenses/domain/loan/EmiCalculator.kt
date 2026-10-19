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
     * Calculates Broken Period (Pre-EMI) Interest for Month 1 under Indian banking norms.
     * When the loan disbursal date does not align with the mandate cycle date (e.g. 45 days elapsed),
     * banks charge interest for elapsed days:
     *   Interest_1 = round(P * AnnualRate * (Δd / 360))
     */
    fun calculateBrokenPeriodInterest(
        principal: Long,
        annualRatePercent: Double,
        disbursalDate: LocalDate,
        firstEmiDate: LocalDate
    ): Long {
        if (principal <= 0L || annualRatePercent <= 0.0) return 0L
        val days = java.time.temporal.ChronoUnit.DAYS.between(disbursalDate, firstEmiDate)
        return if (days > 30) {
            (principal.toDouble() * (annualRatePercent / 100.0) * (days.toDouble() / 360.0)).roundToLong()
        } else {
            (principal.toDouble() * (annualRatePercent / 12.0 / 100.0)).roundToLong()
        }
    }

    /**
     * Calculates the Regular Monthly EMI (Months 1 to n-1) amortizing Month 1 broken period interest.
     *
     * Formula:
     *   EMI = round((P + Interest_1) / (1 + (1 - (1 + r)^-(n - 1)) / r))
     */
    fun calculateIndianRegularEmi(
        principal: Long,
        annualRatePercent: Double,
        tenureMonths: Int,
        brokenPeriodInterest: Long = 0L
    ): Long {
        if (tenureMonths <= 0 || principal <= 0L) return 0L
        if (annualRatePercent <= 0.0) return (principal.toDouble() / tenureMonths).roundToLong()
        if (tenureMonths == 1) return principal + brokenPeriodInterest

        val r = annualRatePercent / 12.0 / 100.0
        val remainingInstallments = (tenureMonths - 1).toDouble()
        val pvFactor = (1.0 - (1.0 + r).pow(-remainingInstallments)) / r
        val regularEmi = (principal.toDouble() + brokenPeriodInterest.toDouble()) / (1.0 + pvFactor)
        return regularEmi.roundToLong()
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
     * Calculates the upfront merchant discount for E-commerce "No-Cost EMI" (Flipkart / Amazon).
     */
    fun calculateNoCostDiscount(
        cartPrice: Long,
        annualInterestRatePercent: Double,
        tenureMonths: Int
    ): Long {
        if (tenureMonths <= 0 || cartPrice <= 0L || annualInterestRatePercent <= 0.0) return 0L
        val r = annualInterestRatePercent / 12.0 / 100.0
        val factor = (1.0 + r).pow(tenureMonths.toDouble())
        val factorEmi = r * factor / (factor - 1.0)
        val monthlyPayment = cartPrice.toDouble() / tenureMonths.toDouble()
        val bookedPrincipal = (monthlyPayment / factorEmi).roundToLong()
        return (cartPrice - bookedPrincipal).coerceAtLeast(0L)
    }

    /**
     * Calculates 18% GST on a given banking fee or interest amount.
     */
    fun calculateGst(amount: Long, gstRatePercent: Double = 18.0): Long {
        if (amount <= 0L || gstRatePercent <= 0.0) return 0L
        return (amount.toDouble() * (gstRatePercent / 100.0)).roundToLong()
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
     * Generates a month-by-month Amortization Schedule for an Indian Reducing Balance Loan.
     * Incorporates broken period interest for Month 1 and absorbs cumulative rounding
     * in the final installment, leaving exactly 0 closing balance.
     */
    fun generateAmortizationSchedule(
        principal: Long,
        annualInterestRatePercent: Double,
        tenureMonths: Int,
        customEmi: Long? = null,
        disbursalDate: LocalDate? = null,
        firstEmiDate: LocalDate? = null,
        isCreditCardEmi: Boolean = false,
        cardGstRatePercent: Double = 18.0
    ): List<AmortizationRow> {
        if (tenureMonths <= 0 || principal <= 0L) return emptyList()

        val r = annualInterestRatePercent / 12.0 / 100.0
        val hasBrokenPeriod = disbursalDate != null && firstEmiDate != null &&
                java.time.temporal.ChronoUnit.DAYS.between(disbursalDate, firstEmiDate) > 30

        val month1Interest = if (hasBrokenPeriod) {
            calculateBrokenPeriodInterest(principal, annualInterestRatePercent, disbursalDate, firstEmiDate)
        } else {
            if (annualInterestRatePercent > 0.0) (principal.toDouble() * r).roundToLong() else 0L
        }

        val emi = customEmi ?: if (hasBrokenPeriod) {
            calculateIndianRegularEmi(principal, annualInterestRatePercent, tenureMonths, month1Interest)
        } else {
            calculateReducingBalanceEmi(principal, annualInterestRatePercent, tenureMonths)
        }

        var currentBalance = principal
        val rows = ArrayList<AmortizationRow>(tenureMonths)

        for (month in 1..tenureMonths) {
            val opening = currentBalance

            if (month == 1) {
                val interest = month1Interest
                val gst = if (isCreditCardEmi) (interest.toDouble() * (cardGstRatePercent / 100.0)).roundToLong() else 0L
                val principalPaid = (emi - interest).coerceAtMost(opening)
                currentBalance = (opening - principalPaid).coerceAtLeast(0L)
                rows.add(
                    AmortizationRow(
                        installmentNumber = 1,
                        openingBalance = opening,
                        emiAmount = emi,
                        principalPart = principalPaid,
                        interestPart = interest,
                        closingBalance = currentBalance,
                        isBrokenPeriod = hasBrokenPeriod,
                        gstOnInterest = gst
                    )
                )
                continue
            }

            if (month == tenureMonths) {
                // Final installment absorbs any remaining rounding difference
                val interest = if (annualInterestRatePercent > 0.0) {
                    (opening.toDouble() * r).roundToLong()
                } else 0L
                val gst = if (isCreditCardEmi) (interest.toDouble() * (cardGstRatePercent / 100.0)).roundToLong() else 0L
                val principalPaid = opening
                val finalEmi = principalPaid + interest
                rows.add(
                    AmortizationRow(
                        installmentNumber = month,
                        openingBalance = opening,
                        emiAmount = finalEmi,
                        principalPart = principalPaid,
                        interestPart = interest,
                        closingBalance = 0L,
                        isBrokenPeriod = false,
                        gstOnInterest = gst
                    )
                )
                break
            }

            val interest = if (annualInterestRatePercent > 0.0) {
                (opening.toDouble() * r).roundToLong()
            } else 0L
            val gst = if (isCreditCardEmi) (interest.toDouble() * (cardGstRatePercent / 100.0)).roundToLong() else 0L
            val principalPaid = (emi - interest).coerceAtMost(opening)
            currentBalance = (opening - principalPaid).coerceAtLeast(0L)

            rows.add(
                AmortizationRow(
                    installmentNumber = month,
                    openingBalance = opening,
                    emiAmount = emi,
                    principalPart = principalPaid,
                    interestPart = interest,
                    closingBalance = currentBalance,
                    isBrokenPeriod = false,
                    gstOnInterest = gst
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
        val disbursalLocalDate = Instant.ofEpochMilli(loan.disbursalDate).atZone(zoneId).toLocalDate()
        val firstEmiLocalDate = if (loan.firstEmiDate > 0L) {
            Instant.ofEpochMilli(loan.firstEmiDate).atZone(zoneId).toLocalDate()
        } else {
            calculateDueDate(disbursalLocalDate, 1, loan.dueDayOfMonth)
        }

        val schedule = when (loan.loanType) {
            LoanType.REDUCING_BALANCE -> generateAmortizationSchedule(
                principal = loan.principalAmount,
                annualInterestRatePercent = loan.interestRatePercent,
                tenureMonths = loan.tenureMonths,
                customEmi = loan.actualEmiAmount ?: loan.monthlyEmiAmount.takeIf { it > 0L },
                disbursalDate = disbursalLocalDate,
                firstEmiDate = firstEmiLocalDate
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

        return schedule.map { row ->
            val dueLocalDate = if (row.installmentNumber == 1) {
                firstEmiLocalDate
            } else {
                calculateDueDate(firstEmiLocalDate, row.installmentNumber - 1, loan.dueDayOfMonth)
            }
            val dueEpochMillis = dueLocalDate.atStartOfDay(zoneId).toInstant().toEpochMilli()

            EmiInstallment(
                loanId = loanId,
                installmentNumber = row.installmentNumber,
                dueDate = dueEpochMillis,
                principalPart = row.principalPart,
                interestPart = row.interestPart,
                totalAmount = row.emiAmount + row.gstOnInterest,
                gstPart = row.gstOnInterest,
                status = InstallmentStatus.PENDING
            )
        }
    }

    private fun calculateDueDate(baseDate: LocalDate, monthOffset: Int, targetDay: Int): LocalDate {
        val targetMonth = baseDate.plusMonths(monthOffset.toLong())
        val maxDayInMonth = targetMonth.lengthOfMonth()
        val actualDay = targetDay.coerceIn(1, maxDayInMonth)
        return targetMonth.withDayOfMonth(actualDay)
    }
}
