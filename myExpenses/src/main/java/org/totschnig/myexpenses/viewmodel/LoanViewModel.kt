package org.totschnig.myexpenses.viewmodel

import android.app.Application
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import app.cash.copper.flow.mapToList
import app.cash.copper.flow.observeQuery
import org.totschnig.myexpenses.db2.deleteTemplate
import org.totschnig.myexpenses.db2.entities.Template
import org.totschnig.myexpenses.db2.instantiateTemplate
import org.totschnig.myexpenses.domain.loan.CreditCardDetail
import org.totschnig.myexpenses.domain.loan.EmiCalculator
import org.totschnig.myexpenses.domain.loan.EmiInstallment
import org.totschnig.myexpenses.domain.loan.Loan
import org.totschnig.myexpenses.domain.loan.LoanProgress
import org.totschnig.myexpenses.domain.loan.LoanStatus
import org.totschnig.myexpenses.domain.loan.LoanType
import org.totschnig.myexpenses.injector
import org.totschnig.myexpenses.model.CurrencyUnit
import org.totschnig.myexpenses.provider.KEY_PARENTID
import org.totschnig.myexpenses.provider.KEY_PLANID
import org.totschnig.myexpenses.provider.KEY_TITLE
import org.totschnig.myexpenses.provider.TransactionProvider
import org.totschnig.myexpenses.util.ExchangeRateHandler
import org.totschnig.myexpenses.viewmodel.data.AccountMinimal
import javax.inject.Inject

class LoanViewModel(application: Application) : ContentResolvingAndroidViewModel(application) {

    @Inject
    lateinit var exchangeRateHandler: ExchangeRateHandler

    init {
        application.injector.inject(this)
    }

    val templates: StateFlow<List<Template>> by lazy {
        contentResolver.observeQuery(
            TransactionProvider.TEMPLATES_URI,
            null,
            "$KEY_PARENTID IS NULL AND $KEY_PLANID IS NULL",
            null,
            "$KEY_TITLE COLLATE LOCALIZED ASC"
        ).mapToList { Template.fromCursor(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribedWithTimeout, emptyList())
    }

    val recurringPlans: StateFlow<List<Template>> by lazy {
        contentResolver.observeQuery(
            TransactionProvider.TEMPLATES_URI,
            null,
            "$KEY_PARENTID IS NULL AND $KEY_PLANID IS NOT NULL",
            null,
            "$KEY_TITLE COLLATE LOCALIZED ASC"
        ).mapToList { Template.fromCursor(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribedWithTimeout, emptyList())
    }

    fun instantiateTemplate(
        templateId: Long,
        onComplete: (Boolean) -> Unit = {}
    ) {
        viewModelScope.launch {
            val result = repository.instantiateTemplate(
                exchangeRateHandler,
                PlanInstanceInfo(templateId),
                currencyContext
            )
            onComplete(result.isSuccess)
        }
    }

    fun deleteTemplate(
        templateId: Long,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            repository.deleteTemplate(templateId)
            onComplete()
        }
    }

    val activeLoans: StateFlow<List<Loan>> by lazy {
        repositoryLoan.getActiveLoans()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribedWithTimeout, emptyList())
    }

    val allLoans: StateFlow<List<Loan>> by lazy {
        repositoryLoan.getAllLoans()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribedWithTimeout, emptyList())
    }

    val accounts: StateFlow<List<AccountMinimal>> by lazy {
        accountsMinimal(withAggregates = false)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribedWithTimeout, emptyList())
    }

    private val _selectedLoanId = MutableStateFlow<Long?>(null)
    val selectedLoanId: StateFlow<Long?> = _selectedLoanId.asStateFlow()

    val selectedLoanWithInstallments: StateFlow<Pair<Loan, List<EmiInstallment>>?> by lazy {
        _selectedLoanId.flatMapLatest { id ->
            if (id == null) flowOf(null)
            else repositoryLoan.getLoanWithInstallments(id)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribedWithTimeout, null)
    }

    val selectedLoanProgress: StateFlow<LoanProgress?> by lazy {
        _selectedLoanId.flatMapLatest { id ->
            if (id == null) flowOf(null)
            else repositoryLoan.getLoanProgress(id)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribedWithTimeout, null)
    }

    fun selectLoan(id: Long?) {
        _selectedLoanId.update { id }
    }

    fun createLoan(
        label: String,
        lenderName: String,
        loanType: LoanType,
        principalAmount: Long,
        interestRatePercent: Double,
        tenureMonths: Int,
        startDate: Long,
        dueDayOfMonth: Int,
        linkedAccountId: Long?,
        disbursalDate: Long = startDate,
        firstEmiDate: Long = 0L,
        actualEmiAmount: Long? = null,
        emisAlreadyPaid: Int = 0,
        onSuccess: (Long) -> Unit = {}
    ) {
        viewModelScope.launch {
            val emi = actualEmiAmount ?: EmiCalculator.calculateEmi(loanType, principalAmount, interestRatePercent, tenureMonths)
            val loan = Loan(
                label = label.trim(),
                lenderName = lenderName.trim(),
                loanType = loanType,
                principalAmount = principalAmount,
                interestRatePercent = if (loanType == LoanType.NO_COST_EMI) 0.0 else interestRatePercent,
                tenureMonths = tenureMonths,
                monthlyEmiAmount = emi,
                startDate = startDate,
                disbursalDate = disbursalDate,
                firstEmiDate = firstEmiDate,
                actualEmiAmount = actualEmiAmount,
                dueDayOfMonth = dueDayOfMonth.coerceIn(1, 31),
                linkedAccountId = linkedAccountId?.takeIf { it > 0 },
                status = LoanStatus.ACTIVE
            )
            val loanId = repositoryLoan.createLoanWithSchedule(
                loan = loan,
                customEmi = actualEmiAmount,
                emisAlreadyPaid = emisAlreadyPaid
            )
            onSuccess(loanId)
        }
    }

    fun markInstallmentPaid(installmentId: Long, createTransaction: Boolean, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repositoryLoan.markInstallmentPaid(
                installmentId = installmentId,
                paidDate = System.currentTimeMillis(),
                createTransaction = createTransaction
            )
            onComplete()
        }
    }

    fun closeLoan(loanId: Long, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repositoryLoan.closeLoan(loanId)
            onComplete()
        }
    }

    fun deleteLoan(loanId: Long, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repositoryLoan.deleteLoan(loanId)
            if (_selectedLoanId.value == loanId) {
                _selectedLoanId.update { null }
            }
            onComplete()
        }
    }

    val creditCardDetails: StateFlow<List<CreditCardDetail>> by lazy {
        repositoryCreditCard.getAllCreditCardDetails()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribedWithTimeout, emptyList())
    }

    fun saveCreditCardDetail(detail: CreditCardDetail, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repositoryCreditCard.saveCreditCardDetail(detail)
            onComplete()
        }
    }

    fun updateCreditCardStatement(
        accountId: Long,
        totalDue: Long,
        minDue: Long,
        unbilled: Long,
        statementDate: Long? = null,
        dueDate: Long? = null,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            repositoryCreditCard.updateCreditCardStatement(accountId, totalDue, minDue, unbilled, statementDate, dueDate)
            onComplete()
        }
    }

    fun settleCreditCardDue(
        creditCardAccountId: Long,
        fromAccountId: Long,
        amount: Long,
        onComplete: (Boolean) -> Unit = {}
    ) {
        viewModelScope.launch {
            val result = repositoryCreditCard.settleCreditCardDue(creditCardAccountId, fromAccountId, amount)
            onComplete(result.isSuccess)
        }
    }

    fun getCurrencyUnit(currencyCode: String): CurrencyUnit = currencyContext[currencyCode]

    val defaultCurrency: CurrencyUnit
        get() = currencyContext.homeCurrencyUnit
}
