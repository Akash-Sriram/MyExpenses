package org.totschnig.myexpenses.viewmodel

import android.app.Application
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import org.totschnig.myexpenses.db2.CashflowSummary
import org.totschnig.myexpenses.db2.CategorySpendItem
import org.totschnig.myexpenses.db2.InsightAccount
import org.totschnig.myexpenses.db2.MerchantSpendItem
import org.totschnig.myexpenses.db2.MonthlyTrendItem
import org.totschnig.myexpenses.injector
import java.time.YearMonth

@OptIn(ExperimentalCoroutinesApi::class)
class InsightsViewModel(application: Application) : ContentResolvingAndroidViewModel(application) {

    init {
        application.injector.inject(this)
    }

    private val _selectedMonth = MutableStateFlow(YearMonth.now())
    val selectedMonth: StateFlow<YearMonth> = _selectedMonth.asStateFlow()

    private val _selectedAccountId = MutableStateFlow<Long?>(null) // null = Unified Net
    val selectedAccountId: StateFlow<Long?> = _selectedAccountId.asStateFlow()

    val accounts: StateFlow<List<InsightAccount>> = repositoryInsights.getAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribedWithTimeout, emptyList())

    val cashflowSummary: StateFlow<CashflowSummary> = combine(_selectedMonth, _selectedAccountId) { month, accId ->
        month to accId
    }.flatMapLatest { (month, accId) ->
        repositoryInsights.getCashflowSummary(month, accId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribedWithTimeout, CashflowSummary())

    val monthlyTrends: StateFlow<List<MonthlyTrendItem>> = combine(_selectedMonth, _selectedAccountId) { month, accId ->
        month to accId
    }.flatMapLatest { (month, accId) ->
        repositoryInsights.getMonthlySpendingTrends(month, monthsBack = 6, accountId = accId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribedWithTimeout, emptyList())

    val topCategories: StateFlow<List<CategorySpendItem>> = combine(_selectedMonth, _selectedAccountId) { month, accId ->
        month to accId
    }.flatMapLatest { (month, accId) ->
        repositoryInsights.getTopCategories(month, accId, limit = 8)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribedWithTimeout, emptyList())

    val topMerchants: StateFlow<List<MerchantSpendItem>> = combine(_selectedMonth, _selectedAccountId) { month, accId ->
        month to accId
    }.flatMapLatest { (month, accId) ->
        repositoryInsights.getTopMerchants(month, accId, limit = 8)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribedWithTimeout, emptyList())

    private val _budgetCapPaise = MutableStateFlow(
        prefHandler.getLong("monthly_budget_cap_paise", 5000000L)
    )
    val budgetCapPaise: StateFlow<Long> = _budgetCapPaise.asStateFlow()

    fun setBudgetCap(capPaise: Long) {
        prefHandler.putLong("monthly_budget_cap_paise", capPaise)
        _budgetCapPaise.value = capPaise
    }

    val monthlyBudget: StateFlow<org.totschnig.myexpenses.db2.MonthlyBudgetInfo> = combine(
        cashflowSummary,
        _budgetCapPaise,
        _selectedMonth
    ) { summary, cap, month ->
        val spent = summary.outflowPaise
        val remaining = (cap - spent).coerceAtLeast(0L)
        val percent = if (cap > 0) (spent.toFloat() / cap.toFloat()).coerceAtLeast(0f) else 0f
        val today = java.time.LocalDate.now()
        val isCurrentMonth = month == YearMonth.now()
        val daysRemaining = if (isCurrentMonth) {
            (month.lengthOfMonth() - today.dayOfMonth + 1).coerceAtLeast(1)
        } else {
            month.lengthOfMonth()
        }
        val dailySafe = if (daysRemaining > 0) remaining / daysRemaining else 0L
        org.totschnig.myexpenses.db2.MonthlyBudgetInfo(
            budgetCapPaise = cap,
            spentPaise = spent,
            remainingPaise = remaining,
            percentSpent = percent,
            daysRemaining = daysRemaining,
            dailySafeSpendPaise = dailySafe
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribedWithTimeout, org.totschnig.myexpenses.db2.MonthlyBudgetInfo())

    fun selectMonth(yearMonth: YearMonth) {
        _selectedMonth.value = yearMonth
    }

    fun previousMonth() {
        _selectedMonth.update { it.minusMonths(1) }
    }

    fun nextMonth() {
        _selectedMonth.update { it.plusMonths(1) }
    }

    fun selectAccount(accountId: Long?) {
        _selectedAccountId.value = accountId
    }

    fun refresh() {
        repositoryInsights.invalidate()
    }
}
