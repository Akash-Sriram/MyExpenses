package org.totschnig.myexpenses.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.viewmodel.data.BalancePeriod
import org.totschnig.myexpenses.viewmodel.data.PeriodBalanceSummary
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class BalancePeriodTest {

    @Test
    fun testBalancePeriodEnumLabels() {
        assertEquals(R.string.period_salary_cycle, BalancePeriod.SALARY_CYCLE.labelRes)
        assertEquals(R.string.period_this_month, BalancePeriod.THIS_MONTH.labelRes)
        assertEquals(R.string.period_last_month, BalancePeriod.LAST_MONTH.labelRes)
        assertEquals(R.string.period_all_time, BalancePeriod.ALL_TIME.labelRes)
    }

    @Test
    fun testSalaryCycleReconciliationFormula() {
        val opening = 273285L // Rs 2,732.85 (leftover balance before salary)
        val salaryIncome = 4646000L // Rs 46,460.00 (Accenture net salary)
        val otherIncome = 1451200L // Rs 14,512.00 (reimbursements & misc)
        val sumIncome = salaryIncome + otherIncome // Rs 60,972.00
        val expense = -4606601L // -Rs 46,066.01
        val transfer = -1751149L // -Rs 17,511.49
        val closing = opening + sumIncome + expense + transfer // Rs 127.35 (12735L)

        val summary = PeriodBalanceSummary(
            period = BalancePeriod.SALARY_CYCLE,
            openingBalance = opening,
            sumIncome = sumIncome,
            sumExpense = expense,
            sumTransfer = transfer,
            closingBalance = closing,
            startLabel = "Opening balance (29 Sep)",
            endLabel = "Current balance"
        )

        assertEquals(12735L, summary.closingBalance)
        assertEquals(
            summary.closingBalance,
            summary.openingBalance + summary.sumIncome + summary.sumExpense + summary.sumTransfer
        )
    }

    @Test
    fun testPeriodReconciliationFormula() {
        // Indian banking passbook reconciliation:
        // Opening + Credits + Debits (negative) + Transfers = Closing
        val opening = 100000L // Rs 1,000.00
        val income = 500000L  // Rs 5,000.00
        val expense = -350000L // -Rs 3,500.00
        val transfer = -50000L // -Rs 500.00
        val closing = opening + income + expense + transfer // Rs 2,000.00 (200000L)

        val summary = PeriodBalanceSummary(
            period = BalancePeriod.THIS_MONTH,
            openingBalance = opening,
            sumIncome = income,
            sumExpense = expense,
            sumTransfer = transfer,
            closingBalance = closing,
            startLabel = "Opening balance (1 Oct)",
            endLabel = "Current balance"
        )

        assertEquals(200000L, summary.closingBalance)
        assertEquals(
            summary.closingBalance,
            summary.openingBalance + summary.sumIncome + summary.sumExpense + summary.sumTransfer
        )
    }

    @Test
    fun testLastMonthBoundariesAcrossYearTransition() {
        val januaryDate = LocalDate.of(2027, 1, 15)
        val prevMonth = januaryDate.minusMonths(1)

        val firstOfPrev = prevMonth.withDayOfMonth(1)
        val lastOfPrev = prevMonth.withDayOfMonth(prevMonth.lengthOfMonth())

        assertEquals(LocalDate.of(2026, 12, 1), firstOfPrev)
        assertEquals(LocalDate.of(2026, 12, 31), lastOfPrev)
    }

    @Test
    fun testLastMonthBoundariesLeapYear() {
        // 2024 is a leap year (Feb 29)
        val marchDate = LocalDate.of(2024, 3, 10)
        val prevMonth = marchDate.minusMonths(1)

        val firstOfPrev = prevMonth.withDayOfMonth(1)
        val lastOfPrev = prevMonth.withDayOfMonth(prevMonth.lengthOfMonth())

        assertEquals(LocalDate.of(2024, 2, 1), firstOfPrev)
        assertEquals(LocalDate.of(2024, 2, 29), lastOfPrev)
    }

    @Test
    fun testLastMonthBoundariesRegularYear() {
        // 2025 is not a leap year (Feb 28)
        val marchDate = LocalDate.of(2025, 3, 10)
        val prevMonth = marchDate.minusMonths(1)

        val firstOfPrev = prevMonth.withDayOfMonth(1)
        val lastOfPrev = prevMonth.withDayOfMonth(prevMonth.lengthOfMonth())

        assertEquals(LocalDate.of(2025, 2, 1), firstOfPrev)
        assertEquals(LocalDate.of(2025, 2, 28), lastOfPrev)
    }
}
