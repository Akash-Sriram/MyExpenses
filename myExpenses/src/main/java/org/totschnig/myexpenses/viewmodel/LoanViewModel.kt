package org.totschnig.myexpenses.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.viewModelScope
import timber.log.Timber
import kotlinx.coroutines.flow.Flow
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
import org.totschnig.myexpenses.db2.PastTransactionInfo
import org.totschnig.myexpenses.db2.CreditCardDueInfo
import org.totschnig.myexpenses.db2.deleteTemplate
import org.totschnig.myexpenses.db2.entities.Template
import org.totschnig.myexpenses.db2.instantiateTemplate
import org.totschnig.myexpenses.domain.commitment.BillingCycle
import org.totschnig.myexpenses.domain.commitment.CommitmentItem
import org.totschnig.myexpenses.domain.commitment.CommitmentPayment
import org.totschnig.myexpenses.domain.commitment.CommitmentAttachment
import org.totschnig.myexpenses.domain.commitment.CommitmentsSummary
import org.totschnig.myexpenses.domain.commitment.PaidCommitmentInfo
import org.totschnig.myexpenses.domain.commitment.InsurancePolicy
import org.totschnig.myexpenses.domain.commitment.InsuranceType
import org.totschnig.myexpenses.domain.commitment.PremiumFrequency
import org.totschnig.myexpenses.domain.commitment.Subscription
import org.totschnig.myexpenses.domain.commitment.MobilePlanTracker
import org.totschnig.myexpenses.domain.commitment.UtilityMeter
import org.totschnig.myexpenses.domain.loan.CreditCardDetail
import org.totschnig.myexpenses.domain.loan.EmiCalculator
import org.totschnig.myexpenses.domain.loan.EmiInstallment
import org.totschnig.myexpenses.domain.loan.Loan
import org.totschnig.myexpenses.domain.loan.LoanProgress
import org.totschnig.myexpenses.domain.loan.LoanStatus
import org.totschnig.myexpenses.domain.loan.LoanType
import org.totschnig.myexpenses.injector
import org.totschnig.myexpenses.model.CurrencyUnit
import org.totschnig.myexpenses.model2.Party
import org.totschnig.myexpenses.provider.KEY_LABEL
import org.totschnig.myexpenses.provider.KEY_PARENTID
import org.totschnig.myexpenses.provider.KEY_PAYEE_NAME
import org.totschnig.myexpenses.provider.KEY_PLANID
import org.totschnig.myexpenses.provider.KEY_ROWID
import org.totschnig.myexpenses.provider.KEY_TITLE
import org.totschnig.myexpenses.provider.TransactionProvider
import org.totschnig.myexpenses.util.ExchangeRateHandler
import org.totschnig.myexpenses.viewmodel.data.AccountMinimal
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.map

data class CategoryDisplay(
    val id: Long,
    val label: String,
    val fullPath: String
)

@OptIn(ExperimentalCoroutinesApi::class)
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

    val payees: StateFlow<List<Party>> by lazy {
        contentResolver.observeQuery(
            TransactionProvider.PAYEES_URI,
            arrayOf(KEY_ROWID, KEY_PAYEE_NAME),
            "$KEY_PARENTID IS NULL",
            null,
            "$KEY_PAYEE_NAME COLLATE LOCALIZED ASC"
        ).mapToList { Party(id = it.getLong(0), name = it.getString(1)) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribedWithTimeout, emptyList())
    }

    val categories: StateFlow<List<CategoryDisplay>> by lazy {
        contentResolver.observeQuery(
            TransactionProvider.CATEGORIES_URI,
            arrayOf(KEY_ROWID, KEY_LABEL, KEY_PARENTID),
            null,
            null,
            "$KEY_LABEL COLLATE LOCALIZED ASC"
        ).mapToList {
            Triple(it.getLong(0), it.getString(1), if (it.isNull(2)) null else it.getLong(2))
        }.map { rawList ->
            val labelMap = rawList.associate { it.first to it.second }
            rawList.map { (id, label, parentId) ->
                val fullPath = if (parentId != null && labelMap.containsKey(parentId)) {
                    "${labelMap[parentId]} > $label"
                } else {
                    label
                }
                CategoryDisplay(id = id, label = label, fullPath = fullPath)
            }.sortedBy { it.fullPath.lowercase() }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribedWithTimeout, emptyList())
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
        if (id != null) {
            viewModelScope.launch {
                repositoryLoan.resyncAndMatchTransactions(id)
            }
        }
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
        autoDebit: Boolean = false,
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
                status = LoanStatus.ACTIVE,
                autoDebit = autoDebit
            )
            val loanId = repositoryLoan.createLoanWithSchedule(
                loan = loan,
                customEmi = actualEmiAmount,
                emisAlreadyPaid = emisAlreadyPaid
            )
            onSuccess(loanId)
        }
    }

    fun markInstallmentPaid(
        installmentId: Long,
        createTransaction: Boolean,
        paidDate: Long = System.currentTimeMillis(),
        attachmentUri: Uri? = null,
        accountId: Long? = null,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            repositoryLoan.markInstallmentPaid(
                installmentId = installmentId,
                paidDate = paidDate,
                createTransaction = createTransaction,
                attachmentUri = attachmentUri,
                accountId = accountId
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

    fun updateLoan(
        loan: Loan,
        customEmi: Long? = null,
        recalculateRemaining: Boolean = false,
        onComplete: (Boolean) -> Unit = {}
    ) {
        viewModelScope.launch {
            val result = repositoryLoan.updateLoan(loan, customEmi, recalculateRemaining)
            onComplete(result.isSuccess)
        }
    }

    suspend fun getHistoricalTransactionsForPlan(
        templateId: Long,
        accountId: Long,
        emiAmount: Long
    ): List<PastTransactionInfo> {
        return repositoryLoan.getHistoricalTransactionsForPlan(templateId, accountId, emiAmount)
    }

    fun convertPlanToLoan(
        loan: Loan,
        templateId: Long,
        customEmi: Long? = null,
        emisAlreadyPaid: Int = 0,
        linkedTransactions: List<Pair<Long, Long>> = emptyList(),
        deletePlan: Boolean = true,
        onSuccess: (Long) -> Unit = {}
    ) {
        viewModelScope.launch {
            val loanId = repositoryLoan.createLoanWithSchedule(
                loan = loan,
                customEmi = customEmi,
                emisAlreadyPaid = emisAlreadyPaid,
                linkedTransactions = linkedTransactions
            )
            if (deletePlan) {
                repository.deleteTemplate(templateId)
            }
            onSuccess(loanId)
        }
    }

    fun linkTransactionToInstallment(
        installmentId: Long,
        transactionId: Long,
        paidDate: Long,
        onComplete: (Boolean) -> Unit = {}
    ) {
        viewModelScope.launch {
            val res = repositoryLoan.linkTransactionToInstallment(installmentId, transactionId, paidDate)
            onComplete(res.isSuccess)
        }
    }

    fun resyncAndMatchTransactions(
        loanId: Long,
        onComplete: (Int) -> Unit = {}
    ) {
        viewModelScope.launch {
            val res = repositoryLoan.resyncAndMatchTransactions(loanId)
            onComplete(res.getOrDefault(0))
        }
    }

    fun unlinkTransactionFromInstallment(
        installmentId: Long,
        onComplete: (Boolean) -> Unit = {}
    ) {
        viewModelScope.launch {
            val res = repositoryLoan.unlinkTransactionFromInstallment(installmentId)
            onComplete(res.isSuccess)
        }
    }

    suspend fun getCandidateTransactions(
        accountId: Long,
        limit: Int = 100
    ): List<PastTransactionInfo> {
        return repositoryLoan.getCandidateTransactions(accountId, limit)
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
            if (result.isSuccess) {
                resyncAllCommitments()
                repositoryCreditCard.refresh()
            }
            onComplete(result.isSuccess)
        }
    }

    val creditCardDues: StateFlow<List<CreditCardDueInfo>> by lazy {
        repositoryCreditCard.getCreditCardDues()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribedWithTimeout, emptyList())
    }

    val commitmentItems: StateFlow<List<CommitmentItem>> by lazy {
        repositoryCommitment.getAllCommitmentItems()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribedWithTimeout, emptyList())
    }

    val commitmentsSummary: StateFlow<CommitmentsSummary> by lazy {
        repositoryCommitment.getCommitmentsSummary()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribedWithTimeout, CommitmentsSummary())
    }

    val paidPaymentsThisMonth: StateFlow<List<PaidCommitmentInfo>> by lazy {
        repositoryCommitment.getPaidPaymentsThisMonth()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribedWithTimeout, emptyList())
    }

    val insurancePolicies: StateFlow<List<InsurancePolicy>> by lazy {
        repositoryCommitment.getAllInsurancePolicies()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribedWithTimeout, emptyList())
    }

    val subscriptions: StateFlow<List<Subscription>> by lazy {
        repositoryCommitment.getAllSubscriptions()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribedWithTimeout, emptyList())
    }

    val mobilePlanTracker: StateFlow<MobilePlanTracker?> by lazy {
        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.DAY_OF_MONTH, 1)
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        val startOfMonth = calendar.timeInMillis
        calendar.add(java.util.Calendar.MONTH, 1)
        val endOfMonth = calendar.timeInMillis - 1

        repositoryCommitment.getMobilePlanTracker(startOfMonth, endOfMonth)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribedWithTimeout, null)
    }

    fun createInsurancePolicy(
        policy: InsurancePolicy,
        onComplete: (Long) -> Unit = {}
    ) {
        viewModelScope.launch {
            val id = repositoryCommitment.createInsurancePolicy(policy)
            onComplete(id)
        }
    }

    fun updateInsurancePolicy(
        policy: InsurancePolicy,
        onComplete: (Boolean) -> Unit = {}
    ) {
        viewModelScope.launch {
            val ok = repositoryCommitment.updateInsurancePolicy(policy)
            onComplete(ok)
        }
    }

    fun deleteInsurancePolicy(
        id: Long,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            repositoryCommitment.deleteInsurancePolicy(id)
            onComplete()
        }
    }

    fun getCommitmentAttachments(type: String, id: Long): Flow<List<CommitmentAttachment>> =
        repositoryCommitment.getCommitmentAttachments(type, id)

    fun saveCommitmentAttachments(type: String, id: Long, uris: List<Uri>, label: String? = null, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repositoryCommitment.saveCommitmentAttachments(type, id, uris, label)
            onComplete()
        }
    }

    fun deleteCommitmentAttachment(id: Long, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repositoryCommitment.deleteCommitmentAttachment(id)
            onComplete()
        }
    }

    fun createSubscription(
        subscription: Subscription,
        onComplete: (Long) -> Unit = {}
    ) {
        viewModelScope.launch {
            val id = repositoryCommitment.createSubscription(subscription)
            onComplete(id)
        }
    }

    fun updateSubscription(
        subscription: Subscription,
        onComplete: (Boolean) -> Unit = {}
    ) {
        viewModelScope.launch {
            val ok = repositoryCommitment.updateSubscription(subscription)
            resyncAllCommitments()
            onComplete(ok)
        }
    }

    fun cancelSubscription(
        id: Long,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            repositoryCommitment.cancelSubscription(id)
            resyncAllCommitments()
            onComplete()
        }
    }

    fun deleteSubscription(
        id: Long,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            repositoryCommitment.deleteSubscription(id)
            resyncAllCommitments()
            onComplete()
        }
    }

    fun getMetersForSubscription(subscriptionId: Long): Flow<List<UtilityMeter>> =
        repositoryCommitment.getMetersForSubscription(subscriptionId)

    fun saveUtilityMeters(
        subscriptionId: Long,
        meters: List<UtilityMeter>,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            repositoryCommitment.saveUtilityMeters(subscriptionId, meters)
            onComplete()
        }
    }

    fun deleteUtilityMeter(
        meterId: Long,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            repositoryCommitment.deleteUtilityMeter(meterId)
            onComplete()
        }
    }

    fun payMeter(
        meter: UtilityMeter,
        subscription: Subscription,
        fromAccountId: Long?,
        amount: Long = meter.estimatedAmount,
        paidDate: Long = System.currentTimeMillis(),
        attachmentUri: Uri? = null,
        onComplete: (Boolean) -> Unit = {}
    ) {
        viewModelScope.launch {
            val res = repositoryCommitment.payMeter(
                meter = meter,
                subscription = subscription,
                fromAccountId = fromAccountId,
                amount = amount,
                paidDate = paidDate,
                attachmentUri = attachmentUri
            )
            onComplete(res.isSuccess)
        }
    }

    fun payCommitment(
        paymentId: Long,
        paidDate: Long = System.currentTimeMillis(),
        createTransaction: Boolean = true,
        payer: String = "SELF",
        notes: String = "",
        attachmentUri: Uri? = null,
        onComplete: (Boolean) -> Unit = {}
    ) {
        viewModelScope.launch {
            val res = repositoryCommitment.markCommitmentPaid(
                paymentId,
                paidDate = paidDate,
                createTransaction = createTransaction,
                payer = payer,
                notes = notes,
                attachmentUri = attachmentUri
            )
            onComplete(res.isSuccess)
        }
    }

    fun getPaymentsForCommitment(type: String, id: Long): Flow<List<CommitmentPayment>> =
        repositoryCommitment.getPaymentsForCommitmentFlow(type, id)

    fun resyncCommitment(
        type: String,
        id: Long,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            repositoryCommitment.resyncCommitment(type, id)
            onComplete()
        }
    }

    fun resyncAllCommitments(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repositoryCommitment.resyncAllCommitments()
            onComplete()
        }
    }

    fun convertTemplateToInsurance(
        template: Template,
        insurerName: String = "LIC",
        frequency: PremiumFrequency = PremiumFrequency.MONTHLY,
        insuranceType: InsuranceType = InsuranceType.LIFE,
        onComplete: (Long) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val policy = InsurancePolicy(
                    policyName = template.title,
                    insurerName = insurerName,
                    insuranceType = insuranceType,
                    premiumAmount = kotlin.math.abs(template.amount),
                    frequency = frequency,
                    startDate = System.currentTimeMillis(),
                    dueDayOfMonth = 5,
                    linkedAccountId = if (template.accountId > 0) template.accountId else null,
                    templateId = template.id,
                    autoDebit = false
                )
                val policyId = repositoryCommitment.createInsurancePolicy(policy, autoMatch = true)
                if (policyId > 0) {
                    repository.deleteTemplate(template.id, deletePlan = true)
                }
                onComplete(policyId)
            } catch (e: Exception) {
                Timber.e(e, "Failed to convert template to insurance policy")
            }
        }
    }

    fun convertTemplateToSubscription(
        template: Template,
        billingCycle: BillingCycle = BillingCycle.MONTHLY,
        onComplete: (Long) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val cleanName = template.title
                    .replace("subscription", "", ignoreCase = true)
                    .replace("sub", "", ignoreCase = true)
                    .trim()
                    .ifEmpty { template.title }

                val sub = Subscription(
                    serviceName = cleanName,
                    billingCycle = billingCycle,
                    amount = kotlin.math.abs(template.amount),
                    categoryId = template.categoryId,
                    startDate = System.currentTimeMillis(),
                    nextRenewalDate = System.currentTimeMillis(),
                    linkedAccountId = if (template.accountId > 0) template.accountId else null,
                    templateId = template.id,
                    autoDebit = false
                )
                val subId = repositoryCommitment.createSubscription(sub, autoMatch = true)
                if (subId > 0) {
                    repository.deleteTemplate(template.id, deletePlan = true)
                }
                onComplete(subId)
            } catch (e: Exception) {
                Timber.e(e, "Failed to convert template to subscription")
            }
        }
    }

    fun getCurrencyUnit(currencyCode: String): CurrencyUnit = currencyContext[currencyCode]

    val defaultCurrency: CurrencyUnit
        get() = currencyContext.homeCurrencyUnit
}
