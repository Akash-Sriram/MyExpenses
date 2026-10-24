package org.totschnig.myexpenses.viewmodel.data

import androidx.annotation.StringRes
import org.totschnig.myexpenses.R

enum class BalancePeriod(@param:StringRes val labelRes: Int) {
    SALARY_CYCLE(R.string.period_salary_cycle),
    THIS_MONTH(R.string.period_this_month),
    LAST_MONTH(R.string.period_last_month),
    ALL_TIME(R.string.period_all_time);
}

data class PeriodBalanceSummary(
    val period: BalancePeriod,
    val openingBalance: Long,
    val sumIncome: Long,
    val sumExpense: Long,
    val sumTransfer: Long,
    val closingBalance: Long,
    val startLabel: String,
    val endLabel: String,
    val total: Long? = null,
)
