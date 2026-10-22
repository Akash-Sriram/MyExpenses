package org.totschnig.myexpenses.compose.loans

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Surface
import androidx.compose.ui.platform.LocalContext
import org.totschnig.myexpenses.activity.ExpenseEdit
import org.totschnig.myexpenses.contract.TransactionsContract.Transactions
import org.totschnig.myexpenses.db2.entities.Template
import org.totschnig.myexpenses.model2.Party
import org.totschnig.myexpenses.provider.KEY_ROWID
import org.totschnig.myexpenses.provider.KEY_TEMPLATEID
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import org.totschnig.myexpenses.db2.PastTransactionInfo
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import org.totschnig.myexpenses.viewmodel.CategoryDisplay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.compose.AmountText
import org.totschnig.myexpenses.compose.LocalDateFormatter
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.roundToLong
import org.totschnig.myexpenses.domain.loan.CreditCardDetail
import org.totschnig.myexpenses.domain.loan.EmiCalculator
import org.totschnig.myexpenses.domain.loan.EmiInstallment
import org.totschnig.myexpenses.domain.loan.InstallmentStatus
import org.totschnig.myexpenses.domain.loan.Loan
import org.totschnig.myexpenses.domain.loan.LoanStatus
import org.totschnig.myexpenses.domain.loan.LoanType
import org.totschnig.myexpenses.model.PREDEFINED_NAME_CCARD
import org.totschnig.myexpenses.service.LoanDueWorker
import org.totschnig.myexpenses.util.epochMillis2LocalDate
import org.totschnig.myexpenses.domain.commitment.BillingCycle
import org.totschnig.myexpenses.domain.commitment.CommitmentItem
import org.totschnig.myexpenses.domain.commitment.CommitmentPayment
import org.totschnig.myexpenses.domain.commitment.PaymentRecordStatus
import org.totschnig.myexpenses.domain.commitment.CommitmentStatus
import org.totschnig.myexpenses.domain.commitment.CommitmentType
import org.totschnig.myexpenses.domain.commitment.CommitmentsSummary
import org.totschnig.myexpenses.domain.commitment.InsurancePolicy
import org.totschnig.myexpenses.domain.commitment.InsuranceType
import org.totschnig.myexpenses.domain.commitment.PremiumFrequency
import org.totschnig.myexpenses.domain.commitment.Subscription
import org.totschnig.myexpenses.domain.commitment.UtilityMeter
import org.totschnig.myexpenses.domain.commitment.CommitmentAttachment
import org.totschnig.myexpenses.model.CurrencyUnit
import org.totschnig.myexpenses.viewmodel.LoanViewModel
import org.totschnig.myexpenses.viewmodel.data.AccountMinimal

enum class CommitmentFilter {
    ALL,
    LOANS,
    INSURANCE,
    SUBSCRIPTIONS,
    CREDIT_CARDS,
    TEMPLATES
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoansScreen(
    viewModel: LoanViewModel,
    initialTab: Int = 0,
    onClose: () -> Unit
) {
    val initialFilter = when (initialTab) {
        1 -> CommitmentFilter.CREDIT_CARDS
        2, 3 -> CommitmentFilter.TEMPLATES
        else -> CommitmentFilter.ALL
    }
    var selectedFilter by remember { mutableStateOf(initialFilter) }
    val dateFormatter = LocalDateFormatter.current

    val allLoans by viewModel.allLoans.collectAsState()
    val activeLoans by viewModel.activeLoans.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val creditCardDetails by viewModel.creditCardDetails.collectAsState()
    val templates by viewModel.templates.collectAsState()
    val recurringPlans by viewModel.recurringPlans.collectAsState()

    val commitmentItems by viewModel.commitmentItems.collectAsState()
    val commitmentsSummary by viewModel.commitmentsSummary.collectAsState()
    val insurancePolicies by viewModel.insurancePolicies.collectAsState()
    val subscriptions by viewModel.subscriptions.collectAsState()

    var showAddLoanSheet by remember { mutableStateOf(false) }
    var editingLoan by remember { mutableStateOf<Loan?>(null) }
    var showAddInsuranceSheet by remember { mutableStateOf(false) }
    var editingInsurance by remember { mutableStateOf<InsurancePolicy?>(null) }
    var selectedInsuranceForDetails by remember { mutableStateOf<InsurancePolicy?>(null) }
    var showAddSubscriptionSheet by remember { mutableStateOf(false) }
    var editingSubscription by remember { mutableStateOf<Subscription?>(null) }
    var selectedSubscriptionForDetails by remember { mutableStateOf<Subscription?>(null) }
    var showFabMenu by remember { mutableStateOf(false) }

    var convertingPlan by remember { mutableStateOf<Template?>(null) }
    var linkingInstallment by remember { mutableStateOf<Pair<EmiInstallment, Long?>?>(null) }
    var loanFilterActiveOnly by remember { mutableStateOf(true) }

    var policyToDelete by remember { mutableStateOf<InsurancePolicy?>(null) }
    var subscriptionToDelete by remember { mutableStateOf<Subscription?>(null) }

    // Dialog state for installment payment confirmation
    var pendingPaymentInstallment by remember { mutableStateOf<EmiInstallment?>(null) }
    var pendingPaymentLinkedAccountId by remember { mutableStateOf<Long?>(null) }

    // Dialog state for credit card statement update
    var editingCcDetail by remember { mutableStateOf<Pair<AccountMinimal, CreditCardDetail?>?>(null) }
    // Dialog state for credit card bill payment
    var settlingCcAccount by remember { mutableStateOf<AccountMinimal?>(null) }

    LaunchedEffect(Unit) {
        viewModel.resyncAllCommitments()
    }

    val displayedLoans = if (loanFilterActiveOnly) activeLoans else allLoans

    val selectedLoanWithInstallments by viewModel.selectedLoanWithInstallments.collectAsState()
    val selectedLoanProgress by viewModel.selectedLoanProgress.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.commitments_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            val context = LocalContext.current
            Box {
                FloatingActionButton(
                    onClick = { showFabMenu = true }
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Commitment")
                }

                DropdownMenu(
                    expanded = showFabMenu,
                    onDismissRequest = { showFabMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.add_loan)) },
                        onClick = {
                            showFabMenu = false
                            showAddLoanSheet = true
                        },
                        leadingIcon = {
                            Icon(Icons.Default.AccountBalance, contentDescription = null)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.add_insurance)) },
                        onClick = {
                            showFabMenu = false
                            showAddInsuranceSheet = true
                        },
                        leadingIcon = {
                            Icon(Icons.Default.DateRange, contentDescription = null)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.add_subscription)) },
                        onClick = {
                            showFabMenu = false
                            showAddSubscriptionSheet = true
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                        }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.add_plan)) },
                        onClick = {
                            showFabMenu = false
                            context.startActivity(Intent(context, ExpenseEdit::class.java).apply {
                                putExtra(Transactions.OPERATION_TYPE, Transactions.TYPE_TRANSACTION)
                                putExtra(ExpenseEdit.KEY_NEW_TEMPLATE, true)
                            })
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Bolt, contentDescription = null)
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 1. Obligations Summary Card
            MonthlyObligationsSummaryCard(
                summary = commitmentsSummary,
                defaultCurrency = viewModel.defaultCurrency
            )

            // 2. Filter Chips Row
            val ccAccounts = accounts.filter {
                it.type?.name == PREDEFINED_NAME_CCARD ||
                        creditCardDetails.any { cd -> cd.accountId == it.id }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedFilter == CommitmentFilter.ALL,
                    onClick = { selectedFilter = CommitmentFilter.ALL },
                    label = { Text("${stringResource(R.string.tab_all)} (${commitmentItems.size})") }
                )
                FilterChip(
                    selected = selectedFilter == CommitmentFilter.LOANS,
                    onClick = { selectedFilter = CommitmentFilter.LOANS },
                    label = { Text("${stringResource(R.string.tab_loans)} (${activeLoans.size})") }
                )
                FilterChip(
                    selected = selectedFilter == CommitmentFilter.INSURANCE,
                    onClick = { selectedFilter = CommitmentFilter.INSURANCE },
                    label = { Text("${stringResource(R.string.tab_insurance)} (${insurancePolicies.size})") }
                )
                FilterChip(
                    selected = selectedFilter == CommitmentFilter.SUBSCRIPTIONS,
                    onClick = { selectedFilter = CommitmentFilter.SUBSCRIPTIONS },
                    label = { Text("${stringResource(R.string.tab_subscriptions)} (${subscriptions.size})") }
                )
                FilterChip(
                    selected = selectedFilter == CommitmentFilter.CREDIT_CARDS,
                    onClick = { selectedFilter = CommitmentFilter.CREDIT_CARDS },
                    label = { Text("${stringResource(R.string.tab_credit_cards)} (${ccAccounts.size})") }
                )
                FilterChip(
                    selected = selectedFilter == CommitmentFilter.TEMPLATES,
                    onClick = { selectedFilter = CommitmentFilter.TEMPLATES },
                    label = { Text("${stringResource(R.string.tab_templates)} (${templates.size + recurringPlans.size})") }
                )
            }

            when (selectedFilter) {
                CommitmentFilter.ALL -> {
                    if (commitmentItems.isEmpty()) {
                        CommitmentsEmptyState(
                            onAddLoan = { showAddLoanSheet = true },
                            onAddInsurance = { showAddInsuranceSheet = true },
                            onAddSubscription = { showAddSubscriptionSheet = true }
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(commitmentItems, key = { "${it.type.name}_${it.id}" }) { item ->
                                UnifiedCommitmentCard(
                                    item = item,
                                    accounts = accounts,
                                    viewModel = viewModel,
                                    onSelect = {
                                        when (item) {
                                            is CommitmentItem.LoanItem -> viewModel.selectLoan(item.loan.id)
                                            is CommitmentItem.InsuranceItem -> selectedInsuranceForDetails = item.policy
                                            is CommitmentItem.SubscriptionItem -> selectedSubscriptionForDetails = item.subscription
                                        }
                                    },
                                    onPayDue = {
                                        when (item) {
                                            is CommitmentItem.LoanItem -> {
                                                item.nextDueInstallment?.let { inst ->
                                                    pendingPaymentInstallment = inst
                                                    pendingPaymentLinkedAccountId = item.loan.linkedAccountId
                                                }
                                            }
                                            is CommitmentItem.InsuranceItem -> {
                                                selectedInsuranceForDetails = item.policy
                                            }
                                            is CommitmentItem.SubscriptionItem -> {
                                                selectedSubscriptionForDetails = item.subscription
                                            }
                                        }
                                    }
                                )
                            }
                            item {
                                Spacer(modifier = Modifier.height(72.dp))
                            }
                        }
                    }
                }

                CommitmentFilter.LOANS -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = loanFilterActiveOnly,
                            onClick = { loanFilterActiveOnly = true },
                            label = { Text("Active (${activeLoans.size})") }
                        )
                        FilterChip(
                            selected = !loanFilterActiveOnly,
                            onClick = { loanFilterActiveOnly = false },
                            label = { Text("All (${allLoans.size})") }
                        )
                    }

                    if (displayedLoans.isEmpty()) {
                        LoansEmptyState(
                            onAddLoan = { showAddLoanSheet = true }
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(displayedLoans, key = { it.id }) { loan ->
                                LoanCard(
                                    loan = loan,
                                    accounts = accounts,
                                    viewModel = viewModel,
                                    onClick = {
                                        viewModel.selectLoan(loan.id)
                                    }
                                )
                            }
                            item {
                                Spacer(modifier = Modifier.height(72.dp))
                            }
                        }
                    }
                }

                CommitmentFilter.INSURANCE -> {
                    if (insurancePolicies.isEmpty()) {
                        InsuranceEmptyState(
                            onAddInsurance = { showAddInsuranceSheet = true }
                        )
                    } else {
                        val context = LocalContext.current
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(insurancePolicies, key = { it.id }) { policy ->
                                InsuranceCard(
                                    policy = policy,
                                    accounts = accounts,
                                    viewModel = viewModel,
                                    onClick = { selectedInsuranceForDetails = policy },
                                    onEdit = { editingInsurance = policy },
                                    onDelete = { policyToDelete = policy },
                                    onPay = {
                                        if (policy.payerMode == "FAMILY_SHARED") {
                                            selectedInsuranceForDetails = policy
                                        } else {
                                            val nextPayment = commitmentItems.filterIsInstance<CommitmentItem.InsuranceItem>()
                                                .find { it.policy.id == policy.id }?.nextPayment
                                            if (nextPayment != null) {
                                                viewModel.payCommitment(nextPayment.id) {
                                                    Toast.makeText(context, "Premium paid successfully", Toast.LENGTH_SHORT).show()
                                                }
                                            } else {
                                                Toast.makeText(context, "No pending premium due", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    onResync = {
                                        viewModel.resyncCommitment("INSURANCE", policy.id) {
                                            Toast.makeText(context, "Transactions scanned and mapped", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                            }
                            item {
                                Spacer(modifier = Modifier.height(72.dp))
                            }
                        }
                    }
                }

                CommitmentFilter.SUBSCRIPTIONS -> {
                    if (subscriptions.isEmpty()) {
                        SubscriptionsEmptyState(
                            onAddSubscription = { showAddSubscriptionSheet = true }
                        )
                    } else {
                        val context = LocalContext.current
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(subscriptions, key = { it.id }) { subscription ->
                                SubscriptionCard(
                                    subscription = subscription,
                                    accounts = accounts,
                                    viewModel = viewModel,
                                    onClick = { selectedSubscriptionForDetails = subscription },
                                    onEdit = { editingSubscription = subscription },
                                    onDelete = { subscriptionToDelete = subscription },
                                    onRenew = {
                                        selectedSubscriptionForDetails = subscription
                                    },
                                    onResync = {
                                        viewModel.resyncCommitment("SUBSCRIPTION", subscription.id) {
                                            Toast.makeText(context, "Transactions scanned and mapped", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                            }
                            item {
                                Spacer(modifier = Modifier.height(72.dp))
                            }
                        }
                    }
                }

                CommitmentFilter.CREDIT_CARDS -> {
                    if (ccAccounts.isEmpty()) {
                        CreditCardsEmptyState()
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(ccAccounts, key = { it.id }) { account ->
                                val detail = creditCardDetails.find { it.accountId == account.id }
                                CreditCardItem(
                                    account = account,
                                    detail = detail,
                                    viewModel = viewModel,
                                    onUpdateStatement = { editingCcDetail = account to detail },
                                    onPayBill = { settlingCcAccount = account }
                                )
                            }
                            item {
                                Spacer(modifier = Modifier.height(72.dp))
                            }
                        }
                    }
                }

                CommitmentFilter.TEMPLATES -> {
                    val context = LocalContext.current
                    var planToDelete by remember { mutableStateOf<Template?>(null) }
                    var templateToDelete by remember { mutableStateOf<Template?>(null) }

                    if (templates.isEmpty() && recurringPlans.isEmpty()) {
                        TemplatesEmptyState(onAddTemplate = {
                            context.startActivity(Intent(context, ExpenseEdit::class.java).apply {
                                putExtra(Transactions.OPERATION_TYPE, Transactions.TYPE_TRANSACTION)
                                putExtra(ExpenseEdit.KEY_NEW_TEMPLATE, true)
                            })
                        })
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (recurringPlans.isNotEmpty()) {
                                item {
                                    Text(
                                        text = "${stringResource(R.string.tab_plans)} (${recurringPlans.size})",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                items(recurringPlans, key = { "plan_${it.id}" }) { plan ->
                                    val linkedAccount = accounts.find { it.id == plan.accountId }
                                    RecurringPlanItem(
                                        plan = plan,
                                        account = linkedAccount,
                                        viewModel = viewModel,
                                        onRunPlan = {
                                            viewModel.instantiateTemplate(plan.id) { success ->
                                                val msg = if (success) context.resources.getString(R.string.template_applied_success)
                                                else "Failed to execute plan"
                                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        onEditPlan = {
                                            context.startActivity(Intent(context, ExpenseEdit::class.java).apply {
                                                putExtra(Transactions.OPERATION_TYPE, Transactions.TYPE_TRANSACTION)
                                                putExtra(KEY_ROWID, plan.id)
                                                putExtra(KEY_TEMPLATEID, plan.id)
                                            })
                                        },
                                        onDeletePlan = { planToDelete = plan },
                                        onConvertToLoan = { convertingPlan = plan },
                                        onConvertToInsurance = {
                                            viewModel.convertTemplateToInsurance(plan) {
                                                Toast.makeText(context, "Converted to Insurance Policy! Past payments mapped.", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        onConvertToSubscription = {
                                            viewModel.convertTemplateToSubscription(plan) {
                                                Toast.makeText(context, "Converted to Subscription! Past payments mapped.", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    )
                                }
                            }

                            if (templates.isNotEmpty()) {
                                item {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "${stringResource(R.string.tab_templates)} (${templates.size})",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                items(templates, key = { "template_${it.id}" }) { template ->
                                    val linkedAccount = accounts.find { it.id == template.accountId }
                                    QuickTemplateItem(
                                        template = template,
                                        account = linkedAccount,
                                        viewModel = viewModel,
                                        onApply = {
                                            viewModel.instantiateTemplate(template.id) { success ->
                                                val msg = if (success) context.resources.getString(R.string.template_applied_success)
                                                else "Failed to instantiate template"
                                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        onEdit = {
                                            context.startActivity(Intent(context, ExpenseEdit::class.java).apply {
                                                putExtra(Transactions.OPERATION_TYPE, Transactions.TYPE_TRANSACTION)
                                                putExtra(KEY_ROWID, template.id)
                                                putExtra(KEY_TEMPLATEID, template.id)
                                            })
                                        },
                                        onDelete = { templateToDelete = template },
                                        onConvertToLoan = { convertingPlan = template },
                                        onConvertToInsurance = {
                                            viewModel.convertTemplateToInsurance(template) {
                                                Toast.makeText(context, "Converted to Insurance Policy! Past payments mapped.", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        onConvertToSubscription = {
                                            viewModel.convertTemplateToSubscription(template) {
                                                Toast.makeText(context, "Converted to Subscription! Past payments mapped.", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    )
                                }
                            }

                            item {
                                Spacer(modifier = Modifier.height(72.dp))
                            }
                        }
                    }

                    if (planToDelete != null) {
                        AlertDialog(
                            onDismissRequest = { planToDelete = null },
                            title = { Text(stringResource(R.string.menu_delete)) },
                            text = { Text(stringResource(R.string.delete_plan_confirm, planToDelete!!.title)) },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        val id = planToDelete!!.id
                                        planToDelete = null
                                        viewModel.deleteTemplate(id)
                                    }
                                ) {
                                    Text(stringResource(R.string.menu_delete))
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { planToDelete = null }) {
                                    Text(stringResource(android.R.string.cancel))
                                }
                            }
                        )
                    }

                    if (templateToDelete != null) {
                        AlertDialog(
                            onDismissRequest = { templateToDelete = null },
                            title = { Text(stringResource(R.string.menu_delete)) },
                            text = { Text(stringResource(R.string.delete_template_confirm, templateToDelete!!.title)) },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        val id = templateToDelete!!.id
                                        templateToDelete = null
                                        viewModel.deleteTemplate(id)
                                    }
                                ) {
                                    Text(stringResource(R.string.menu_delete))
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { templateToDelete = null }) {
                                    Text(stringResource(android.R.string.cancel))
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // Modal Sheet: Add / Edit / Convert Loan
    if (showAddLoanSheet || editingLoan != null || convertingPlan != null) {
        AddLoanBottomSheet(
            accounts = accounts,
            viewModel = viewModel,
            initialLoan = editingLoan,
            initialPlan = convertingPlan,
            onDismiss = {
                showAddLoanSheet = false
                editingLoan = null
                convertingPlan = null
            }
        )
    }

    // Modal Sheet: Add / Edit Insurance Policy
    if (showAddInsuranceSheet || editingInsurance != null) {
        AddInsuranceBottomSheet(
            accounts = accounts,
            viewModel = viewModel,
            initialPolicy = editingInsurance,
            onDismiss = {
                showAddInsuranceSheet = false
                editingInsurance = null
            }
        )
    }

    // Modal Sheet: Add / Edit Subscription
    if (showAddSubscriptionSheet || editingSubscription != null) {
        AddSubscriptionBottomSheet(
            accounts = accounts,
            viewModel = viewModel,
            initialSubscription = editingSubscription,
            onDismiss = {
                showAddSubscriptionSheet = false
                editingSubscription = null
            }
        )
    }

    // Modal Sheet: Loan Details & Amortization Schedule
    val selectedLoan = selectedLoanWithInstallments
    if (selectedLoan != null) {
        val (loan, installments) = selectedLoan
        LoanDetailBottomSheet(
            loan = loan,
            installments = installments,
            progress = selectedLoanProgress,
            accounts = accounts,
            viewModel = viewModel,
            onDismiss = { viewModel.selectLoan(null) },
            onPayInstallment = { installment ->
                pendingPaymentInstallment = installment
                pendingPaymentLinkedAccountId = loan.linkedAccountId
            },
            onEditLoan = {
                editingLoan = loan
                viewModel.selectLoan(null)
            },
            onLinkTransaction = { installment ->
                linkingInstallment = installment to loan.linkedAccountId
            },
            onUnlinkTransaction = { installment ->
                viewModel.unlinkTransactionFromInstallment(installment.id)
            }
        )
    }

    // Modal Sheet: Insurance Policy Details & Payment History
    val insuranceDetails = selectedInsuranceForDetails
    if (insuranceDetails != null) {
        InsuranceDetailBottomSheet(
            policy = insuranceDetails,
            accounts = accounts,
            viewModel = viewModel,
            onDismiss = { selectedInsuranceForDetails = null },
            onEditPolicy = {
                editingInsurance = insuranceDetails
                selectedInsuranceForDetails = null
            },
            onDeletePolicy = {
                policyToDelete = insuranceDetails
                selectedInsuranceForDetails = null
            }
        )
    }

    // Modal Sheet: Subscription Details & Renewal History
    val subscriptionDetails = selectedSubscriptionForDetails
    if (subscriptionDetails != null) {
        SubscriptionDetailBottomSheet(
            subscription = subscriptionDetails,
            accounts = accounts,
            viewModel = viewModel,
            onDismiss = { selectedSubscriptionForDetails = null },
            onEditSubscription = {
                editingSubscription = subscriptionDetails
                selectedSubscriptionForDetails = null
            },
            onDeleteSubscription = {
                subscriptionToDelete = subscriptionDetails
                selectedSubscriptionForDetails = null
            }
        )
    }

    // Dialog: Link Transaction to Installment
    val linking = linkingInstallment
    if (linking != null) {
        val (inst, accId) = linking
        LinkTransactionDialog(
            installment = inst,
            accountId = accId,
            accounts = accounts,
            viewModel = viewModel,
            onDismiss = { linkingInstallment = null }
        )
    }

    // Dialog: Mark Installment as Paid confirmation
    val installmentToPay = pendingPaymentInstallment
    if (installmentToPay != null) {
        val context = LocalContext.current
        var recordTransaction by remember { mutableStateOf(pendingPaymentLinkedAccountId != null) }
        var payOnToday by remember { mutableStateOf(true) }
        val todayStr = dateFormatter.format(LocalDate.now())
        val dueStr = dateFormatter.format(epochMillis2LocalDate(installmentToPay.dueDate))
        var selectedAttachmentUri by remember { mutableStateOf<Uri?>(null) }
        val attachmentPicker = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            if (uri != null) {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: Exception) {
                    // Ignore non-persistable uri
                }
                selectedAttachmentUri = uri
            }
        }

        AlertDialog(
            onDismissRequest = { pendingPaymentInstallment = null },
            title = {
                Text(stringResource(R.string.mark_paid_confirmation, installmentToPay.installmentNumber))
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val currency = viewModel.defaultCurrency
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("EMI Amount:")
                        AmountText(amount = installmentToPay.totalAmount, currency = currency, fontWeight = FontWeight.Bold)
                    }

                    Text("Payment Date:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = payOnToday,
                            onClick = { payOnToday = true },
                            label = { Text("Today ($todayStr)") }
                        )
                        FilterChip(
                            selected = !payOnToday,
                            onClick = { payOnToday = false },
                            label = { Text("Due Date ($dueStr)") }
                        )
                    }

                    if (pendingPaymentLinkedAccountId != null) {
                        val linkedAcc = accounts.find { it.id == pendingPaymentLinkedAccountId }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = recordTransaction,
                                onCheckedChange = { recordTransaction = it }
                            )
                            Text(
                                text = "Debit ₹${installmentToPay.totalAmount / 100.0} from ${linkedAcc?.label ?: "linked account"}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    if (recordTransaction) {
                        if (selectedAttachmentUri == null) {
                            OutlinedButton(
                                onClick = { attachmentPicker.launch("*/*") },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Attach Receipt / Bill")
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = selectedAttachmentUri?.lastPathSegment ?: "Attached Document",
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    IconButton(
                                        onClick = { selectedAttachmentUri = null },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val paidDate = if (payOnToday) System.currentTimeMillis() else installmentToPay.dueDate
                        viewModel.markInstallmentPaid(
                            installmentId = installmentToPay.id,
                            createTransaction = recordTransaction,
                            paidDate = paidDate,
                            attachmentUri = selectedAttachmentUri
                        )
                        pendingPaymentInstallment = null
                    }
                ) {
                    Text(stringResource(R.string.mark_paid))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingPaymentInstallment = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    // Dialog: Update Credit Card Statement
    val editingCc = editingCcDetail
    if (editingCc != null) {
        val (account, detail) = editingCc
        UpdateCreditCardDialog(
            account = account,
            detail = detail,
            viewModel = viewModel,
            onDismiss = { editingCcDetail = null }
        )
    }

    // Dialog: Settle Credit Card Due
    val settlingCc = settlingCcAccount
    if (settlingCc != null) {
        val detail = creditCardDetails.find { it.accountId == settlingCc.id }
        SettleCreditCardDialog(
            creditCardAccount = settlingCc,
            detail = detail,
            accounts = accounts,
            viewModel = viewModel,
            onDismiss = { settlingCcAccount = null }
        )
    }

    // Dialog: Delete Insurance Policy
    val policyDel = policyToDelete
    if (policyDel != null) {
        AlertDialog(
            onDismissRequest = { policyToDelete = null },
            title = { Text(stringResource(R.string.menu_delete)) },
            text = { Text(stringResource(R.string.delete_commitment_confirm, policyDel.policyName)) },
            confirmButton = {
                Button(
                    onClick = {
                        val id = policyDel.id
                        policyToDelete = null
                        viewModel.deleteInsurancePolicy(id)
                    }
                ) {
                    Text(stringResource(R.string.menu_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { policyToDelete = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    // Dialog: Delete Subscription
    val subDel = subscriptionToDelete
    if (subDel != null) {
        AlertDialog(
            onDismissRequest = { subscriptionToDelete = null },
            title = { Text(stringResource(R.string.menu_delete)) },
            text = { Text(stringResource(R.string.delete_commitment_confirm, subDel.serviceName)) },
            confirmButton = {
                Button(
                    onClick = {
                        val id = subDel.id
                        subscriptionToDelete = null
                        viewModel.deleteSubscription(id)
                    }
                ) {
                    Text(stringResource(R.string.menu_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { subscriptionToDelete = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun LoansEmptyState(onAddLoan: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_bank),
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.no_loans_message),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onAddLoan) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.add_loan))
        }
    }
}

@Composable
private fun CreditCardsEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = painterResource(id = R.drawable.card_travel),
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.secondary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.no_credit_cards_message),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

enum class CommitmentsSummaryViewMode {
    DUE_THIS_MONTH,
    BY_BILLING_CYCLE
}

@Composable
fun MonthlyObligationsSummaryCard(
    summary: CommitmentsSummary,
    defaultCurrency: CurrencyUnit
) {
    var viewMode by remember { mutableStateOf(CommitmentsSummaryViewMode.DUE_THIS_MONTH) }
    val currentMonthName = remember {
        java.time.format.DateTimeFormatter.ofPattern("MMMM").format(java.time.LocalDate.now())
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header with View Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountBalance,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = if (viewMode == CommitmentsSummaryViewMode.DUE_THIS_MONTH)
                            "Dues in $currentMonthName"
                        else
                            "Commitments by Period",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                AssistChip(
                    onClick = {
                        viewMode = if (viewMode == CommitmentsSummaryViewMode.DUE_THIS_MONTH)
                            CommitmentsSummaryViewMode.BY_BILLING_CYCLE
                        else
                            CommitmentsSummaryViewMode.DUE_THIS_MONTH
                    },
                    label = {
                        Text(
                            if (viewMode == CommitmentsSummaryViewMode.DUE_THIS_MONTH) "View All Cycles" else "View This Month",
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    modifier = Modifier.height(28.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (viewMode == CommitmentsSummaryViewMode.DUE_THIS_MONTH) {
                // DUE THIS MONTH MODE
                if (summary.upcomingCountThisMonth > 0) {
                    AmountText(
                        amount = summary.upcomingTotalThisMonth,
                        currency = defaultCurrency,
                        fontSize = MaterialTheme.typography.headlineMedium.fontSize,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${summary.upcomingCountThisMonth} pending due(s) scheduled for $currentMonthName",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "All Clear for $currentMonthName!",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (summary.paidThisMonthTotal > 0L)
                            "₹${summary.paidThisMonthTotal / 100.0} already settled for this month • Next dues in upcoming months"
                        else
                            "No commitments due in $currentMonthName • All accounts up to date",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Next Month Minimum Outflow Banner / Card
                val nextMonthName = remember {
                    java.time.format.DateTimeFormatter.ofPattern("MMMM").format(java.time.LocalDate.now().plusMonths(1))
                }
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DateRange,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    "Next Month Need ($nextMonthName)",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                if (summary.nextMonthNeedCount > 0)
                                    "${summary.nextMonthNeedCount} commitment(s) projected"
                                else
                                    "No commitments scheduled",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        AmountText(
                            amount = summary.nextMonthNeedTotal,
                            currency = defaultCurrency,
                            fontSize = MaterialTheme.typography.titleMedium.fontSize,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            } else {
                // BY BILLING CYCLE MODE (Shows distinct cycles separately without conflating!)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (summary.monthlyTotal > 0L) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Monthly (Every Month):", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            AmountText(amount = summary.monthlyTotal, currency = defaultCurrency, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (summary.quarterlyTotal > 0L) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Quarterly (Every 3 Mo):", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            AmountText(amount = summary.quarterlyTotal, currency = defaultCurrency, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (summary.yearlyTotal > 0L) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Yearly (Annual):", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            AmountText(amount = summary.yearlyTotal, currency = defaultCurrency, fontWeight = FontWeight.Bold)
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Amortized Monthly Eqv:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        AmountText(
                            amount = summary.totalMonthlyObligation,
                            currency = defaultCurrency,
                            fontSize = MaterialTheme.typography.bodySmall.fontSize,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Frequency Quick Pills
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (summary.nextMonthNeedTotal > 0L) {
                    val nextMonthPillName = remember {
                        java.time.format.DateTimeFormatter.ofPattern("MMM").format(java.time.LocalDate.now().plusMonths(1))
                    }
                    AssistChip(
                        onClick = { },
                        label = { Text("Need in $nextMonthPillName: ₹${summary.nextMonthNeedTotal / 100.0}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) }
                    )
                }
                if (summary.monthlyTotal > 0L) {
                    AssistChip(
                        onClick = { viewMode = CommitmentsSummaryViewMode.BY_BILLING_CYCLE },
                        label = { Text("Monthly: ₹${summary.monthlyTotal / 100.0}/mo", style = MaterialTheme.typography.labelSmall) }
                    )
                }
                if (summary.quarterlyTotal > 0L) {
                    AssistChip(
                        onClick = { viewMode = CommitmentsSummaryViewMode.BY_BILLING_CYCLE },
                        label = { Text("Quarterly: ₹${summary.quarterlyTotal / 100.0}/qtr", style = MaterialTheme.typography.labelSmall) }
                    )
                }
                if (summary.yearlyTotal > 0L) {
                    AssistChip(
                        onClick = { viewMode = CommitmentsSummaryViewMode.BY_BILLING_CYCLE },
                        label = { Text("Yearly: ₹${summary.yearlyTotal / 100.0}/yr", style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CommitmentsEmptyState(
    onAddLoan: () -> Unit,
    onAddInsurance: () -> Unit,
    onAddSubscription: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_bank),
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No financial commitments tracked yet",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Track your EMIs, insurance premiums, and recurring subscriptions with automated dues tracking and 1-tap payments.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(onClick = onAddLoan) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.tab_loans))
            }
            OutlinedButton(onClick = onAddInsurance) {
                Text(stringResource(R.string.tab_insurance))
            }
            OutlinedButton(onClick = onAddSubscription) {
                Text(stringResource(R.string.tab_subscriptions))
            }
        }
    }
}

@Composable
fun UnifiedCommitmentCard(
    item: CommitmentItem,
    accounts: List<AccountMinimal>,
    viewModel: LoanViewModel,
    onSelect: () -> Unit,
    onPayDue: () -> Unit
) {
    val dateFormatter = LocalDateFormatter.current
    val linkedAccount = accounts.find { it.id == item.linkedAccountId }
    val currency = viewModel.getCurrencyUnit(linkedAccount?.currency ?: viewModel.defaultCurrency.code)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    val typeLabel = when (item.type) {
                        CommitmentType.LOAN -> "LOAN"
                        CommitmentType.INSURANCE -> "INSURANCE"
                        CommitmentType.SUBSCRIPTION -> "SUBSCRIPTION"
                        CommitmentType.UTILITY -> "UTILITY"
                        else -> "COMMITMENT"
                    }
                    AssistChip(
                        onClick = {},
                        label = { Text(typeLabel, style = MaterialTheme.typography.labelSmall) }
                    )
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (item is CommitmentItem.InsuranceItem && item.policy.payerMode == "FAMILY_SHARED") {
                        AssistChip(
                            onClick = {},
                            label = { Text("👨‍👦 Family (Dad)", style = MaterialTheme.typography.labelSmall) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                labelColor = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        )
                    }
                    if (item.autoDebit) {
                        AssistChip(
                            onClick = {},
                            label = { Text("Auto-Debit", style = MaterialTheme.typography.labelSmall) },
                            leadingIcon = {
                                Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(12.dp))
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val pastCount = when (item) {
                    is CommitmentItem.InsuranceItem -> item.pastPaymentsCount
                    is CommitmentItem.SubscriptionItem -> item.pastPaymentsCount
                    else -> 0
                }
                if (item is CommitmentItem.SubscriptionItem && item.subscription.isVariableAmount && item.meters.isNotEmpty()) {
                    val paidCount = item.cycleMeterStatus.count { it.isPaid }
                    val statusText = if (paidCount == item.meters.size) {
                        "✓ All ${item.meters.size} meters paid this cycle"
                    } else {
                        "⚡ $paidCount / ${item.meters.size} meters paid this cycle"
                    }
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (paidCount == item.meters.size) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                } else if (pastCount > 0) {
                    Text(
                        text = "✓ $pastCount payments linked",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    val periodLabel = when (item) {
                        is CommitmentItem.LoanItem -> "Monthly EMI"
                        is CommitmentItem.InsuranceItem -> "Premium (${item.policy.frequency.name.lowercase().replace('_', ' ')})"
                        is CommitmentItem.SubscriptionItem -> if (item.subscription.isVariableAmount && item.meters.isNotEmpty()) {
                            "Est. Cycle (${item.meters.size} connections)"
                        } else {
                            "Renewal (${item.subscription.billingCycle.name.lowercase()})"
                        }
                    }
                    Text(
                        text = periodLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        AmountText(
                            amount = item.amount,
                            currency = currency,
                            fontWeight = FontWeight.Bold,
                            fontSize = MaterialTheme.typography.titleMedium.fontSize
                        )
                        val periodSuffix = when (item) {
                            is CommitmentItem.LoanItem -> " / mo"
                            is CommitmentItem.InsuranceItem -> when (item.policy.frequency) {
                                PremiumFrequency.MONTHLY -> " / mo"
                                PremiumFrequency.QUARTERLY -> " / qtr"
                                PremiumFrequency.HALF_YEARLY -> " / 6 mo"
                                PremiumFrequency.YEARLY -> " / yr"
                            }
                            is CommitmentItem.SubscriptionItem -> when (item.subscription.billingCycle) {
                                BillingCycle.WEEKLY -> " / wk"
                                BillingCycle.MONTHLY -> " / mo"
                                BillingCycle.BI_MONTHLY -> " / 2 mo"
                                BillingCycle.QUARTERLY -> " / qtr"
                                BillingCycle.YEARLY -> " / yr"
                            }
                        }
                        Text(
                            text = periodSuffix,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 2.dp, start = 2.dp)
                        )
                    }
                }

                val todayLocalDate = LocalDate.now()
                val dueLocalDate = epochMillis2LocalDate(item.nextDueDate)
                val daysLate = java.time.temporal.ChronoUnit.DAYS.between(dueLocalDate, todayLocalDate).toInt()
                val isOverdue = daysLate > 0
                val inGracePeriod = daysLate in 1..30

                val statusLabel = when {
                    inGracePeriod -> "Grace Period (${daysLate}d)"
                    isOverdue -> "Overdue (${daysLate}d)"
                    else -> stringResource(R.string.next_due)
                }
                val statusColor = when {
                    inGracePeriod -> Color(0xFFE65100)
                    isOverdue -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                val dateColor = when {
                    inGracePeriod -> Color(0xFFE65100)
                    isOverdue -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurface
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        fontWeight = if (isOverdue) FontWeight.Bold else FontWeight.Normal
                    )
                    Text(
                        text = dateFormatter.format(dueLocalDate),
                        style = MaterialTheme.typography.bodyMedium,
                        color = dateColor,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            if (item is CommitmentItem.LoanItem) {
                Spacer(modifier = Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { (item.progress.progressPercent / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${item.progress.paidInstallmentsCount}/${item.progress.totalInstallmentsCount} EMIs paid",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${item.progress.progressPercent.toInt()}% completed",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                linkedAccount?.let {
                    Text(
                        text = "Account: ${it.label}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } ?: Spacer(modifier = Modifier.width(1.dp))

                Button(
                    onClick = onPayDue,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = when (item.type) {
                            CommitmentType.LOAN -> stringResource(R.string.pay_emi)
                            CommitmentType.INSURANCE -> "Pay Premium"
                            CommitmentType.SUBSCRIPTION -> "Renew"
                            CommitmentType.UTILITY -> "Pay Bill"
                            else -> stringResource(R.string.pay_now)
                        },
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

@Composable
fun InsuranceEmptyState(onAddInsurance: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.DateRange,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No Insurance Policies Tracked",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Track your LIC, health, vehicle, and term policies. Automatically track premium dues and connect past payments.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onAddInsurance) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.add_insurance))
        }
    }
}

@Composable
fun InsuranceCard(
    policy: InsurancePolicy,
    accounts: List<AccountMinimal>,
    viewModel: LoanViewModel,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onPay: () -> Unit,
    onResync: () -> Unit
) {
    val dateFormatter = LocalDateFormatter.current
    val linkedAccount = accounts.find { it.id == policy.linkedAccountId }
    val currency = viewModel.getCurrencyUnit(linkedAccount?.currency ?: viewModel.defaultCurrency.code)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = policy.policyName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (policy.insurerName.isNotBlank() || policy.policyNumber.isNotBlank()) {
                        Text(
                            text = listOf(policy.insurerName, policy.policyNumber).filter { it.isNotBlank() }.joinToString(" • "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (policy.payerMode == "FAMILY_SHARED") {
                        AssistChip(
                            onClick = {},
                            label = { Text("👨‍👦 Family (Dad)", style = MaterialTheme.typography.labelSmall) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                labelColor = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        )
                    }
                    AssistChip(
                        onClick = {},
                        label = { Text(policy.frequency.name, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.premium_amount),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        AmountText(
                            amount = policy.premiumAmount,
                            currency = currency,
                            fontWeight = FontWeight.Bold,
                            fontSize = MaterialTheme.typography.titleMedium.fontSize
                        )
                        val periodSuffix = when (policy.frequency) {
                            PremiumFrequency.MONTHLY -> " / mo"
                            PremiumFrequency.QUARTERLY -> " / qtr"
                            PremiumFrequency.HALF_YEARLY -> " / 6 mo"
                            PremiumFrequency.YEARLY -> " / yr"
                        }
                        Text(
                            text = periodSuffix,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                        )
                    }
                }

                if (policy.sumAssured > 0L) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = stringResource(R.string.sum_assured),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        AmountText(
                            amount = policy.sumAssured,
                            currency = currency,
                            fontWeight = FontWeight.Medium,
                            fontSize = MaterialTheme.typography.bodyMedium.fontSize
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                linkedAccount?.let {
                    Text(
                        text = "Account: ${it.label}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } ?: Spacer(modifier = Modifier.width(1.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Button(
                        onClick = onPay,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Pay", style = MaterialTheme.typography.labelSmall)
                    }
                    IconButton(onClick = onResync, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Refresh, contentDescription = "Resync", modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun SubscriptionsEmptyState(onAddSubscription: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Refresh,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No Subscriptions Tracked",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Track your Bitwarden, YouTube Premium, cloud storage, and other services. Keep tabs on renewal dates and auto-debits.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onAddSubscription) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.add_subscription))
        }
    }
}

@Composable
fun SubscriptionCard(
    subscription: Subscription,
    accounts: List<AccountMinimal>,
    viewModel: LoanViewModel,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onRenew: () -> Unit,
    onResync: () -> Unit
) {
    val dateFormatter = LocalDateFormatter.current
    val linkedAccount = accounts.find { it.id == subscription.linkedAccountId }
    val currency = viewModel.getCurrencyUnit(linkedAccount?.currency ?: viewModel.defaultCurrency.code)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = subscription.serviceName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (subscription.notes.isNotBlank()) {
                        Text(
                            text = subscription.notes,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                AssistChip(
                    onClick = {},
                    label = { Text(subscription.billingCycle.name, style = MaterialTheme.typography.labelSmall) }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.subscription_amount),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        AmountText(
                            amount = subscription.amount,
                            currency = currency,
                            fontWeight = FontWeight.Bold,
                            fontSize = MaterialTheme.typography.titleMedium.fontSize
                        )
                        val periodSuffix = when (subscription.billingCycle) {
                            BillingCycle.WEEKLY -> " / wk"
                            BillingCycle.MONTHLY -> " / mo"
                            BillingCycle.BI_MONTHLY -> " / 2 mo"
                            BillingCycle.QUARTERLY -> " / qtr"
                            BillingCycle.YEARLY -> " / yr"
                        }
                        Text(
                            text = periodSuffix,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = stringResource(R.string.next_renewal),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = dateFormatter.format(epochMillis2LocalDate(subscription.nextRenewalDate)),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                linkedAccount?.let {
                    Text(
                        text = "Account: ${it.label}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } ?: Spacer(modifier = Modifier.width(1.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Button(
                        onClick = onRenew,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Renew", style = MaterialTheme.typography.labelSmall)
                    }
                    IconButton(onClick = onResync, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Refresh, contentDescription = "Resync", modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchableItemSelectionDialog(
    title: String,
    items: List<Pair<Long, String>>,
    selectedId: Long?,
    noneLabel: String = "None / Auto-detect",
    onItemSelected: (Long?) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredItems = remember(searchQuery, items) {
        if (searchQuery.isBlank()) items
        else items.filter { it.second.contains(searchQuery, ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    singleLine = true
                )

                LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onItemSelected(null)
                                    onDismiss()
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                noneLabel,
                                color = if (selectedId == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (selectedId == null) FontWeight.Bold else FontWeight.Normal
                            )
                            if (selectedId == null) {
                                Icon(Icons.Default.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        HorizontalDivider()
                    }

                    items(filteredItems, key = { it.first }) { (id, name) ->
                        val isSelected = id == selectedId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onItemSelected(id)
                                    onDismiss()
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                name,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.weight(1f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (isSelected) {
                                Icon(Icons.Default.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }

                    if (filteredItems.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "No matching items found",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CommitmentAttachmentPicker(
    attachedUris: List<Uri>,
    onUrisChanged: (List<Uri>) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Documents / Bills / Screenshots"
) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            uris.forEach { uri ->
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: Exception) {
                    // Ignore non-persistable uri
                }
            }
            onUrisChanged(attachedUris + uris)
        }
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            OutlinedButton(
                onClick = { launcher.launch("*/*") },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Attach Doc / SS", style = MaterialTheme.typography.labelSmall)
            }
        }

        if (attachedUris.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                attachedUris.forEachIndexed { index, uri ->
                    val fileName = uri.lastPathSegment?.substringAfterLast("/") ?: "Doc ${index + 1}"
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = fileName,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 140.dp)
                            )
                            IconButton(
                                onClick = { onUrisChanged(attachedUris.filterIndexed { i, _ -> i != index }) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CommitmentDocumentsSection(
    type: String,
    commitmentId: Long,
    viewModel: LoanViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val attachments by viewModel.getCommitmentAttachments(type, commitmentId).collectAsState(initial = emptyList())
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            uris.forEach { uri ->
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: Exception) {
                    // Ignore non-persistable uri
                }
            }
            viewModel.saveCommitmentAttachments(type, commitmentId, uris)
        }
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Documents & Screenshots (${attachments.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            TextButton(
                onClick = { launcher.launch("*/*") },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add Doc / SS", style = MaterialTheme.typography.labelMedium)
            }
        }

        if (attachments.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { launcher.launch("*/*") }
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Default.AttachFile,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "No documents attached yet",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "Tap to attach policy bond, receipt, passbook, or screenshot",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                attachments.forEach { att ->
                    val uri = Uri.parse(att.uri)
                    val displayName = att.label?.takeIf { it.isNotBlank() }
                        ?: uri.lastPathSegment?.substringAfterLast("/")
                        ?: "Attachment #${att.id}"
                    val isImage = att.uri.endsWith(".png", true) ||
                            att.uri.endsWith(".jpg", true) ||
                            att.uri.endsWith(".jpeg", true) ||
                            att.uri.contains("image", true)

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                try {
                                    val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                                        val mime = context.contentResolver.getType(uri) ?: if (isImage) "image/*" else "*/*"
                                        setDataAndType(uri, mime)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(viewIntent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Cannot open document: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    if (isImage) Icons.Default.AttachFile else Icons.Default.Description,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        text = displayName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date(att.createdAt)),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            IconButton(
                                onClick = { viewModel.deleteCommitmentAttachment(att.id) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddInsuranceBottomSheet(
    accounts: List<AccountMinimal>,
    viewModel: LoanViewModel,
    initialPolicy: InsurancePolicy? = null,
    onDismiss: () -> Unit
) {
    val isEdit = initialPolicy != null
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var policyName by remember(initialPolicy) {
        mutableStateOf(initialPolicy?.policyName ?: "")
    }
    var insurerName by remember(initialPolicy) {
        mutableStateOf(initialPolicy?.insurerName ?: "")
    }
    var policyNumber by remember(initialPolicy) {
        mutableStateOf(initialPolicy?.policyNumber ?: "")
    }
    var premiumAmountText by remember(initialPolicy) {
        mutableStateOf(initialPolicy?.let { (it.premiumAmount / 100.0).toString() } ?: "")
    }
    var frequency by remember(initialPolicy) {
        mutableStateOf(initialPolicy?.frequency ?: PremiumFrequency.MONTHLY)
    }
    var dueDayOfMonthText by remember(initialPolicy) {
        mutableStateOf(initialPolicy?.dueDayOfMonth?.toString() ?: "1")
    }
    var sumAssuredText by remember(initialPolicy) {
        mutableStateOf(initialPolicy?.takeIf { it.sumAssured > 0 }?.let { (it.sumAssured / 100.0).toString() } ?: "")
    }
    var selectedAccountId by remember(initialPolicy) {
        mutableStateOf(initialPolicy?.linkedAccountId)
    }
    var autoDebit by remember(initialPolicy) {
        mutableStateOf(initialPolicy?.autoDebit ?: false)
    }
    var payerMode by remember(initialPolicy) {
        mutableStateOf(initialPolicy?.payerMode ?: "SELF")
    }
    var attachedUris by remember { mutableStateOf<List<Uri>>(emptyList()) }

    val payees by viewModel.payees.collectAsState()
    var selectedPayeeId by remember(initialPolicy) {
        mutableStateOf(initialPolicy?.payeeId)
    }

    var showPayeeSearchDialog by remember { mutableStateOf(false) }
    var expandedFrequencyDropdown by remember { mutableStateOf(false) }
    var expandedAccountDropdown by remember { mutableStateOf(false) }
    var expandedPayerModeDropdown by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = if (isEdit) stringResource(R.string.edit_insurance) else stringResource(R.string.add_insurance),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            OutlinedTextField(
                value = policyName,
                onValueChange = { policyName = it },
                label = { Text(stringResource(R.string.insurance_policy_name)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = insurerName,
                    onValueChange = { insurerName = it },
                    label = { Text(stringResource(R.string.insurer_name)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = policyNumber,
                    onValueChange = { policyNumber = it },
                    label = { Text(stringResource(R.string.policy_number)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = premiumAmountText,
                    onValueChange = { premiumAmountText = it },
                    label = { Text(stringResource(R.string.premium_amount)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )

                ExposedDropdownMenuBox(
                    expanded = expandedFrequencyDropdown,
                    onExpandedChange = { expandedFrequencyDropdown = it },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = frequency.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.premium_frequency)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedFrequencyDropdown) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = expandedFrequencyDropdown,
                        onDismissRequest = { expandedFrequencyDropdown = false }
                    ) {
                        PremiumFrequency.entries.forEach { freq ->
                            DropdownMenuItem(
                                text = { Text(freq.name) },
                                onClick = {
                                    frequency = freq
                                    expandedFrequencyDropdown = false
                                }
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = dueDayOfMonthText,
                    onValueChange = { dueDayOfMonthText = it },
                    label = { Text("Due Day (1-31)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = sumAssuredText,
                    onValueChange = { sumAssuredText = it },
                    label = { Text(stringResource(R.string.sum_assured)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }

            // Searchable Payee Picker Button
            val selectedPayeeName = payees.find { it.id == selectedPayeeId }?.name ?: "Auto-detect by Name / None"
            Surface(
                onClick = { showPayeeSearchDialog = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(4.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                color = Color.Transparent
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Linked Payee (Exact Match)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            selectedPayeeName,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search Payee",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (showPayeeSearchDialog) {
                SearchableItemSelectionDialog(
                    title = "Select Payee",
                    items = payees.map { it.id to it.name },
                    selectedId = selectedPayeeId,
                    noneLabel = "Auto-detect by Name / None",
                    onItemSelected = { selectedPayeeId = it },
                    onDismiss = { showPayeeSearchDialog = false }
                )
            }

            ExposedDropdownMenuBox(
                expanded = expandedAccountDropdown,
                onExpandedChange = { expandedAccountDropdown = it },
                modifier = Modifier.fillMaxWidth()
            ) {
                val selectedAccLabel = accounts.find { it.id == selectedAccountId }?.label ?: "None / Unlinked"
                OutlinedTextField(
                    value = selectedAccLabel,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Debit Account") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedAccountDropdown) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(
                    expanded = expandedAccountDropdown,
                    onDismissRequest = { expandedAccountDropdown = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("None / Unlinked") },
                        onClick = {
                            selectedAccountId = null
                            expandedAccountDropdown = false
                        }
                    )
                    accounts.forEach { acc ->
                        DropdownMenuItem(
                            text = { Text(acc.label) },
                            onClick = {
                                selectedAccountId = acc.id
                                expandedAccountDropdown = false
                            }
                        )
                    }
                }
            }

            ExposedDropdownMenuBox(
                expanded = expandedPayerModeDropdown,
                onExpandedChange = { expandedPayerModeDropdown = it },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = if (payerMode == "FAMILY_SHARED") "Family / Dad (Shared)" else "Self (100% Personal)",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Payer & Expense Responsibility") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedPayerModeDropdown) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(
                    expanded = expandedPayerModeDropdown,
                    onDismissRequest = { expandedPayerModeDropdown = false }
                ) {
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text("Self (100% Personal)", fontWeight = FontWeight.SemiBold)
                                Text("Always paid from my accounts, 100% my expense", style = MaterialTheme.typography.bodySmall)
                            }
                        },
                        onClick = {
                            payerMode = "SELF"
                            expandedPayerModeDropdown = false
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text("Family / Dad (Shared)", fontWeight = FontWeight.SemiBold)
                                Text("Dad sometimes pays directly; skips ledger deduction when marked Paid by Dad", style = MaterialTheme.typography.bodySmall)
                            }
                        },
                        onClick = {
                            payerMode = "FAMILY_SHARED"
                            expandedPayerModeDropdown = false
                        }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = autoDebit,
                    onCheckedChange = { autoDebit = it }
                )
                Text("Enable auto-debit on due date")
            }

            CommitmentAttachmentPicker(
                attachedUris = attachedUris,
                onUrisChanged = { attachedUris = it },
                label = "Attach Policy Document / Screenshots"
            )

            Button(
                onClick = {
                    val amountMinor = (premiumAmountText.toDoubleOrNull()?.times(100))?.toLong() ?: 0L
                    val sumMinor = (sumAssuredText.toDoubleOrNull()?.times(100))?.toLong() ?: 0L
                    val dueDay = dueDayOfMonthText.toIntOrNull()?.coerceIn(1, 31) ?: 1
                    val policy = InsurancePolicy(
                        id = initialPolicy?.id ?: 0L,
                        policyName = policyName.trim(),
                        policyNumber = policyNumber.trim(),
                        insurerName = insurerName.trim(),
                        premiumAmount = amountMinor,
                        frequency = frequency,
                        startDate = initialPolicy?.startDate ?: System.currentTimeMillis(),
                        endDate = initialPolicy?.endDate,
                        dueDayOfMonth = dueDay,
                        sumAssured = sumMinor,
                        linkedAccountId = selectedAccountId,
                        autoDebit = autoDebit,
                        payerMode = payerMode,
                        payeeId = selectedPayeeId
                    )
                    if (isEdit) {
                        viewModel.updateInsurancePolicy(policy)
                        if (attachedUris.isNotEmpty() && initialPolicy != null) {
                            viewModel.saveCommitmentAttachments("INSURANCE", initialPolicy.id, attachedUris)
                        }
                    } else {
                        viewModel.createInsurancePolicy(policy) { newId ->
                            if (attachedUris.isNotEmpty()) {
                                viewModel.saveCommitmentAttachments("INSURANCE", newId, attachedUris)
                            }
                        }
                    }
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = policyName.isNotBlank() && (premiumAmountText.toDoubleOrNull() ?: 0.0) > 0.0
            ) {
                Text(if (isEdit) "Update Policy" else "Save Policy")
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddSubscriptionBottomSheet(
    accounts: List<AccountMinimal>,
    viewModel: LoanViewModel,
    initialSubscription: Subscription? = null,
    onDismiss: () -> Unit
) {
    val isEdit = initialSubscription != null
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var serviceName by remember(initialSubscription) {
        mutableStateOf(initialSubscription?.serviceName ?: "")
    }
    var amountText by remember(initialSubscription) {
        mutableStateOf(initialSubscription?.let { (it.amount / 100.0).toString() } ?: "")
    }
    var billingCycle by remember(initialSubscription) {
        mutableStateOf(initialSubscription?.billingCycle ?: BillingCycle.MONTHLY)
    }
    var dueDayOfMonthText by remember(initialSubscription) {
        mutableStateOf(
            initialSubscription?.let {
                Instant.ofEpochMilli(it.nextRenewalDate).atZone(ZoneId.systemDefault()).dayOfMonth.toString()
            } ?: "8"
        )
    }
    var notes by remember(initialSubscription) {
        mutableStateOf(initialSubscription?.notes ?: "")
    }
    var selectedAccountId by remember(initialSubscription) {
        mutableStateOf(initialSubscription?.linkedAccountId)
    }
    var autoDebit by remember(initialSubscription) {
        mutableStateOf(initialSubscription?.autoDebit ?: false)
    }
    var isVariableAmount by remember(initialSubscription) {
        mutableStateOf(initialSubscription?.isVariableAmount ?: false)
    }
    var attachedUris by remember { mutableStateOf<List<Uri>>(emptyList()) }

    val context = LocalContext.current
    val subscriptionMeters by if (initialSubscription != null) {
        viewModel.getMetersForSubscription(initialSubscription.id).collectAsState(initial = emptyList())
    } else {
        remember { mutableStateOf(emptyList()) }
    }
    var showManageMetersInEdit by remember { mutableStateOf(false) }

    LaunchedEffect(subscriptionMeters) {
        if (isVariableAmount && subscriptionMeters.isNotEmpty() && initialSubscription != null) {
            val dynamicAmount = subscriptionMeters.sumOf { it.calculateAmountForDate(initialSubscription.nextRenewalDate) }
            amountText = (dynamicAmount / 100.0).toString()
        }
    }

    val payees by viewModel.payees.collectAsState()
    var selectedPayeeId by remember(initialSubscription) {
        mutableStateOf(initialSubscription?.payeeId)
    }

    val categories by viewModel.categories.collectAsState()
    var selectedCategoryId by remember(initialSubscription) {
        mutableStateOf(initialSubscription?.categoryId)
    }

    var showPayeeSearchDialog by remember { mutableStateOf(false) }
    var showCategorySearchDialog by remember { mutableStateOf(false) }
    var expandedCycleDropdown by remember { mutableStateOf(false) }
    var expandedAccountDropdown by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = if (isEdit) stringResource(R.string.edit_subscription) else stringResource(R.string.add_subscription),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            OutlinedTextField(
                value = serviceName,
                onValueChange = { serviceName = it },
                label = { Text(stringResource(R.string.service_name)) },
                placeholder = { Text("e.g. Bitwarden, YouTube, TNEB Electricity") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text(if (isVariableAmount) "Est. / Target Amount" else stringResource(R.string.subscription_amount)) },
                    placeholder = if (isVariableAmount) {
                        { Text("e.g. 5000 (Approx)") }
                    } else null,
                    supportingText = if (isVariableAmount && subscriptionMeters.isNotEmpty()) {
                        { Text("Auto-calculated from meters", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall) }
                    } else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )

                ExposedDropdownMenuBox(
                    expanded = expandedCycleDropdown,
                    onExpandedChange = { expandedCycleDropdown = it },
                    modifier = Modifier.weight(1f)
                ) {
                    val cycleLabel = when (billingCycle) {
                        BillingCycle.WEEKLY -> "Weekly"
                        BillingCycle.MONTHLY -> "Monthly"
                        BillingCycle.BI_MONTHLY -> "Bi-Monthly (2 Mo)"
                        BillingCycle.QUARTERLY -> "Quarterly"
                        BillingCycle.YEARLY -> "Yearly"
                    }
                    OutlinedTextField(
                        value = cycleLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.billing_cycle)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCycleDropdown) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = expandedCycleDropdown,
                        onDismissRequest = { expandedCycleDropdown = false }
                    ) {
                        BillingCycle.entries.forEach { cycle ->
                            val label = when (cycle) {
                                BillingCycle.WEEKLY -> "Weekly"
                                BillingCycle.MONTHLY -> "Monthly"
                                BillingCycle.BI_MONTHLY -> "Bi-Monthly (Every 2 Months)"
                                BillingCycle.QUARTERLY -> "Quarterly"
                                BillingCycle.YEARLY -> "Yearly"
                            }
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    billingCycle = cycle
                                    expandedCycleDropdown = false
                                }
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = dueDayOfMonthText,
                onValueChange = { dueDayOfMonthText = it.filter { ch -> ch.isDigit() } },
                label = { Text("Renewal / Due Day of Month (1 - 31)") },
                placeholder = { Text("e.g. 8 for 8th of each cycle (TNEB, Hathway)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Variable Amount Bill / Utility Checkbox
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = isVariableAmount,
                    onCheckedChange = { isVariableAmount = it }
                )
                Column(modifier = Modifier.padding(start = 4.dp)) {
                    Text("Variable Amount Bill / Utility (e.g. Electricity, Water)", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Auto-matches varying payments without rejecting different bill amounts",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Searchable Payee Picker Button
            val selectedPayeeName = payees.find { it.id == selectedPayeeId }?.name ?: "Auto-detect by Name / None"
            Surface(
                onClick = { showPayeeSearchDialog = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(4.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                color = Color.Transparent
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Linked Payee (Exact Match)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            selectedPayeeName,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search Payee",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (showPayeeSearchDialog) {
                SearchableItemSelectionDialog(
                    title = "Select Payee",
                    items = payees.map { it.id to it.name },
                    selectedId = selectedPayeeId,
                    noneLabel = "Auto-detect by Name / None",
                    onItemSelected = { selectedPayeeId = it },
                    onDismiss = { showPayeeSearchDialog = false }
                )
            }

            // Searchable Category Picker Button
            val selectedCategoryName = categories.find { it.id == selectedCategoryId }?.fullPath ?: "None / Uncategorized"
            Surface(
                onClick = { showCategorySearchDialog = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(4.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                color = Color.Transparent
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Expense Category",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            selectedCategoryName,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Select Category",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (showCategorySearchDialog) {
                SearchableItemSelectionDialog(
                    title = "Select Expense Category",
                    items = categories.map { it.id to it.fullPath },
                    selectedId = selectedCategoryId,
                    noneLabel = "None / Uncategorized",
                    onItemSelected = { selectedCategoryId = it },
                    onDismiss = { showCategorySearchDialog = false }
                )
            }

            ExposedDropdownMenuBox(
                expanded = expandedAccountDropdown,
                onExpandedChange = { expandedAccountDropdown = it },
                modifier = Modifier.fillMaxWidth()
            ) {
                val selectedAccLabel = accounts.find { it.id == selectedAccountId }?.label ?: "None / Unlinked"
                OutlinedTextField(
                    value = selectedAccLabel,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Payment Account") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedAccountDropdown) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(
                    expanded = expandedAccountDropdown,
                    onDismissRequest = { expandedAccountDropdown = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("None / Unlinked") },
                        onClick = {
                            selectedAccountId = null
                            expandedAccountDropdown = false
                        }
                    )
                    accounts.forEach { acc ->
                        DropdownMenuItem(
                            text = { Text(acc.label) },
                            onClick = {
                                selectedAccountId = acc.id
                                expandedAccountDropdown = false
                            }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes (Optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = autoDebit,
                    onCheckedChange = { autoDebit = it }
                )
                Text("Enable auto-debit on renewal date")
            }

            if (isVariableAmount) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.Bolt, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                Text(
                                    text = if (subscriptionMeters.isNotEmpty()) "Connected Meters & Rates (${subscriptionMeters.size})" else "Meter / Rate Tracking",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            if (initialSubscription != null) {
                                TextButton(
                                    onClick = { showManageMetersInEdit = true },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Manage Rates", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }

                        if (subscriptionMeters.isNotEmpty()) {
                            subscriptionMeters.forEach { m ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "⚡ ${m.meterLabel}${if (m.meterNumber.isNotBlank()) " (#${m.meterNumber})" else ""}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        if (m.isDailyConsumption) {
                                            val qtyStr = if (m.unitsPerDay % 1.0 == 0.0) m.unitsPerDay.toInt().toString() else m.unitsPerDay.toString()
                                            Text(
                                                text = "$qtyStr pkts/day @ ₹${m.unitPrice / 100.0} (Auto-calculated by calendar days)",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                    val targetRenewal = initialSubscription?.nextRenewalDate ?: System.currentTimeMillis()
                                    val estAmt = if (m.isDailyConsumption) {
                                        m.calculateAmountForDate(targetRenewal)
                                    } else {
                                        m.estimatedAmount
                                    }
                                    Text(
                                        text = "₹${estAmt / 100.0}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            val daysInCycle = epochMillis2LocalDate(initialSubscription?.nextRenewalDate ?: System.currentTimeMillis()).lengthOfMonth()
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                val totalCycleEst = subscriptionMeters.sumOf {
                                    it.calculateAmountForDate(initialSubscription?.nextRenewalDate ?: System.currentTimeMillis())
                                }
                                Text(
                                    text = "📅 Auto-calc for upcoming cycle (${daysInCycle} days): ₹${totalCycleEst / 100.0}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                )
                            }
                        } else {
                            Text(
                                text = "Variable utility enabled. Multi-meter connections and daily packet rates can be managed directly via Manage Rates.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            CommitmentAttachmentPicker(
                attachedUris = attachedUris,
                onUrisChanged = { attachedUris = it },
                label = "Attach Bills / Invoices / Screenshots"
            )

            Button(
                onClick = {
                    val amountMinor = (amountText.toDoubleOrNull()?.times(100))?.toLong() ?: 0L
                    val dueDay = dueDayOfMonthText.toIntOrNull()?.coerceIn(1, 31) ?: 8
                    val today = LocalDate.now()
                    val renewalDate = if (today.dayOfMonth <= dueDay) {
                        today.withDayOfMonth(dueDay.coerceAtMost(today.lengthOfMonth()))
                    } else {
                        today.plusMonths(1).let { it.withDayOfMonth(dueDay.coerceAtMost(it.lengthOfMonth())) }
                    }
                    val renewalMillis = renewalDate.atTime(LocalTime.of(9, 0)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    val intervalMonths = billingCycle.monthsInterval().coerceAtLeast(1)
                    val startMillis = renewalDate.minusMonths(intervalMonths.toLong())
                        .atTime(LocalTime.of(9, 0)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

                    val sub = Subscription(
                        id = initialSubscription?.id ?: 0L,
                        serviceName = serviceName.trim(),
                        billingCycle = billingCycle,
                        amount = amountMinor,
                        categoryId = selectedCategoryId,
                        startDate = initialSubscription?.startDate ?: startMillis,
                        nextRenewalDate = initialSubscription?.nextRenewalDate
                            ?.takeIf {
                                Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).dayOfMonth == dueDay
                            } ?: renewalMillis,
                        linkedAccountId = selectedAccountId,
                        autoDebit = autoDebit,
                        notes = notes.trim(),
                        payeeId = selectedPayeeId,
                        isVariableAmount = isVariableAmount
                    )

                    if (isEdit) {
                        viewModel.updateSubscription(sub)
                        if (attachedUris.isNotEmpty() && initialSubscription != null) {
                            viewModel.saveCommitmentAttachments("SUBSCRIPTION", initialSubscription.id, attachedUris)
                        }
                    } else {
                        viewModel.createSubscription(sub) { newId ->
                            if (attachedUris.isNotEmpty()) {
                                viewModel.saveCommitmentAttachments("SUBSCRIPTION", newId, attachedUris)
                            }
                        }
                    }
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = serviceName.isNotBlank() && (isVariableAmount || (amountText.toDoubleOrNull() ?: 0.0) > 0.0)
            ) {
                Text(if (isEdit) "Update Subscription" else "Save Subscription")
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showManageMetersInEdit && initialSubscription != null) {
        ManageUtilityMetersDialog(
            subscriptionId = initialSubscription.id,
            initialMeters = subscriptionMeters,
            onDismiss = { showManageMetersInEdit = false },
            onSaveMeters = { updatedMeters ->
                viewModel.saveUtilityMeters(initialSubscription.id, updatedMeters) {
                    Toast.makeText(context, "Meters updated!", Toast.LENGTH_SHORT).show()
                }
                showManageMetersInEdit = false
            }
        )
    }
}

@Composable
private fun LoanCard(
    loan: Loan,
    accounts: List<AccountMinimal>,
    viewModel: LoanViewModel,
    onClick: () -> Unit
) {
    val currency = viewModel.defaultCurrency
    val linkedAccount = accounts.find { it.id == loan.linkedAccountId }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row: Label + Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = loan.label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (loan.lenderName.isNotBlank()) {
                        Text(
                            text = loan.lenderName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            text = when (loan.loanType) {
                                LoanType.REDUCING_BALANCE -> "Reducing"
                                LoanType.FLAT_RATE -> "Flat Rate"
                                LoanType.NO_COST_EMI -> "0% EMI"
                            },
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Main Figures Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.monthly_emi),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    AmountText(
                        amount = loan.monthlyEmiAmount,
                        currency = currency,
                        fontSize = MaterialTheme.typography.titleLarge.fontSize,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = stringResource(R.string.principal_amount),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    AmountText(
                        amount = loan.principalAmount,
                        currency = currency,
                        fontSize = MaterialTheme.typography.titleMedium.fontSize,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Meta Info: Due Day & Linked Account
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Due: ${loan.dueDayOfMonth}th of each month",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (linkedAccount != null) {
                    val accText = if (loan.autoDebit) "Auto-debit: ${linkedAccount.label}" else "Account: ${linkedAccount.label}"
                    Text(
                        text = accText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InsuranceDetailBottomSheet(
    policy: InsurancePolicy,
    accounts: List<AccountMinimal>,
    viewModel: LoanViewModel,
    onDismiss: () -> Unit,
    onEditPolicy: () -> Unit,
    onDeletePolicy: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val dateFormatter = LocalDateFormatter.current
    val linkedAccount = accounts.find { it.id == policy.linkedAccountId }
    val currency = viewModel.getCurrencyUnit(linkedAccount?.currency ?: viewModel.defaultCurrency.code)

    val payments: List<CommitmentPayment> by viewModel.getPaymentsForCommitment("INSURANCE", policy.id).collectAsState(initial = emptyList())
    val pendingPayment = payments.filter { it.status == PaymentRecordStatus.PENDING }.minByOrNull { it.dueDate }
    val pastPayments = payments.filter { it.status == PaymentRecordStatus.PAID }.sortedByDescending { it.paidDate ?: it.dueDate }

    var pendingPayAction by remember { mutableStateOf<Pair<CommitmentPayment, String>?>(null) } // (payment, payer)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = policy.policyName,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    if (policy.insurerName.isNotBlank() || policy.policyNumber.isNotBlank()) {
                        Text(
                            text = listOf(policy.insurerName, policy.policyNumber).filter { it.isNotBlank() }.joinToString(" • "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        viewModel.resyncCommitment("INSURANCE", policy.id) {
                            Toast.makeText(context, "Transactions scanned and mapped", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Resync past transactions")
                    }
                    IconButton(onClick = onEditPolicy) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Policy")
                    }
                    IconButton(onClick = onDeletePolicy) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Policy", tint = MaterialTheme.colorScheme.error)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
            }

            // Chips Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AssistChip(
                    onClick = {},
                    label = { Text(policy.frequency.name, style = MaterialTheme.typography.labelSmall) }
                )
                if (policy.payerMode == "FAMILY_SHARED") {
                    AssistChip(
                        onClick = {},
                        label = { Text("👨‍👦 Family / Dad (Shared)", style = MaterialTheme.typography.labelSmall) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            labelColor = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    )
                } else {
                    AssistChip(
                        onClick = {},
                        label = { Text("👤 Self (100% Me)", style = MaterialTheme.typography.labelSmall) }
                    )
                }
                if (policy.sumAssured > 0L) {
                    AssistChip(
                        onClick = {},
                        label = { Text("Cover: ₹${policy.sumAssured / 100.0}", style = MaterialTheme.typography.labelSmall) }
                    )
                }
                if (policy.autoDebit) {
                    AssistChip(
                        onClick = {},
                        label = { Text("⚡ Auto-Debit", style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            HorizontalDivider()

            // Next Due Section
            Text(
                text = "Next Premium Due",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            if (pendingPayment != null) {
                val todayLocalDate = LocalDate.now()
                val dueLocalDate = epochMillis2LocalDate(pendingPayment.dueDate)
                val daysLate = java.time.temporal.ChronoUnit.DAYS.between(dueLocalDate, todayLocalDate).toInt()
                val isOverdue = daysLate > 0
                val inGracePeriod = daysLate in 1..30

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = when {
                            inGracePeriod -> Color(0xFFFFF3E0)
                            isOverdue -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        }
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                val statusTitle = when {
                                    inGracePeriod -> "In Grace Period (${daysLate}d late)"
                                    isOverdue -> "Overdue by ${daysLate} days"
                                    else -> "Due Date"
                                }
                                val statusColor = when {
                                    inGracePeriod -> Color(0xFFE65100)
                                    isOverdue -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                                Text(
                                    text = statusTitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = statusColor,
                                    fontWeight = if (isOverdue) FontWeight.Bold else FontWeight.Normal
                                )
                                Text(
                                    text = dateFormatter.format(dueLocalDate),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "Premium Amount",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                AmountText(
                                    amount = pendingPayment.amount,
                                    currency = currency,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = MaterialTheme.typography.titleLarge.fontSize
                                )
                            }
                        }

                        linkedAccount?.let {
                            Text(
                                text = "Account: ${it.label}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Pay Action Buttons
                        if (policy.payerMode == "FAMILY_SHARED") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { pendingPayAction = pendingPayment to "SELF" },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Pay (Me)")
                                }
                                OutlinedButton(
                                    onClick = { pendingPayAction = pendingPayment to "DAD" },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Paid by Dad")
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { pendingPayAction = pendingPayment to "SELF" },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Pay Premium")
                                }
                                OutlinedButton(
                                    onClick = { pendingPayAction = pendingPayment to "EXTERNAL" },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Paid Externally")
                                }
                            }
                        }
                    }
                }
            } else {
                Text(
                    text = "No pending premium due. Next due will be scheduled automatically.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider()

            CommitmentDocumentsSection(
                type = "INSURANCE",
                commitmentId = policy.id,
                viewModel = viewModel
            )

            HorizontalDivider()

            // Payment History Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Payment History",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${pastPayments.size} recorded",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (pastPayments.isEmpty()) {
                Text(
                    text = "No past payments recorded yet. Tap the sync icon above to match existing transactions from your ledger.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                pastPayments.forEach { payment ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    val dueLd = epochMillis2LocalDate(payment.dueDate)
                                    val paidLd = payment.paidDate?.let { epochMillis2LocalDate(it) } ?: dueLd
                                    val delayDays = java.time.temporal.ChronoUnit.DAYS.between(dueLd, paidLd).toInt()
                                    val dateDisplay = when {
                                        delayDays > 0 -> "Due: ${dateFormatter.format(dueLd)} • Paid: ${dateFormatter.format(paidLd)} (${delayDays}d late)"
                                        delayDays < 0 -> "Due: ${dateFormatter.format(dueLd)} • Paid: ${dateFormatter.format(paidLd)} (${-delayDays}d early)"
                                        else -> "Due: ${dateFormatter.format(dueLd)} • Paid on time"
                                    }
                                    Text(
                                        text = dateDisplay,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        val isDad = payment.payer == "DAD"
                                        AssistChip(
                                            onClick = {},
                                            label = {
                                                Text(
                                                    if (isDad) "Paid by Dad" else "Paid by Me",
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                            },
                                            colors = AssistChipDefaults.assistChipColors(
                                                containerColor = if (isDad)
                                                    MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)
                                                else
                                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                            ),
                                            modifier = Modifier.height(24.dp)
                                        )

                                        val attachmentUriStr = payment.notes.lines().find { it.startsWith("Attachment: ") }?.removePrefix("Attachment: ")?.trim()
                                        if (!attachmentUriStr.isNullOrEmpty()) {
                                            AssistChip(
                                                onClick = {
                                                    try {
                                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(attachmentUriStr)).apply {
                                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                        }
                                                        context.startActivity(intent)
                                                    } catch (e: Exception) {
                                                        Toast.makeText(context, "Cannot open attachment", Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                leadingIcon = {
                                                    Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(14.dp))
                                                },
                                                label = { Text("Receipt", style = MaterialTheme.typography.labelSmall) },
                                                modifier = Modifier.height(24.dp)
                                            )
                                        }
                                    }
                                }

                                if (payment.transactionId != null) {
                                    Text(
                                        text = "Linked to Transaction #${payment.transactionId}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    Text(
                                        text = "External payment (no ledger transaction)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            AmountText(
                                amount = payment.amount,
                                currency = currency,
                                fontWeight = FontWeight.Bold,
                                fontSize = MaterialTheme.typography.bodyMedium.fontSize
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Confirmation dialog for payment action
    val actionToExecute = pendingPayAction
    if (actionToExecute != null) {
        val (payment, payer) = actionToExecute
        val isDad = payer == "DAD"
        var payOnToday by remember { mutableStateOf(true) }
        val todayStr = dateFormatter.format(LocalDate.now())
        val dueStr = dateFormatter.format(epochMillis2LocalDate(payment.dueDate))
        var selectedAttachmentUri by remember { mutableStateOf<Uri?>(null) }
        val attachmentPicker = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            if (uri != null) {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: Exception) {
                    // Ignore non-persistable uri
                }
                selectedAttachmentUri = uri
            }
        }

        AlertDialog(
            onDismissRequest = { pendingPayAction = null },
            title = {
                Text(if (isDad) "Mark as Paid by Dad?" else "Record Premium Payment")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (isDad) {
                        Text("This marks the premium as paid by Dad and advances the next due date forward by a cycle. No money will be deducted from your bank accounts, and no expense transaction will be recorded.")
                    } else {
                        Text("This marks the premium as paid and creates an expense transaction of ₹${payment.amount / 100.0} in ${linkedAccount?.label ?: "your account"}.")
                    }

                    Text("Payment Date:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = payOnToday,
                            onClick = { payOnToday = true },
                            label = { Text("Today ($todayStr)") }
                        )
                        FilterChip(
                            selected = !payOnToday,
                            onClick = { payOnToday = false },
                            label = { Text("Due Date ($dueStr)") }
                        )
                    }

                    if (selectedAttachmentUri == null) {
                        OutlinedButton(
                            onClick = { attachmentPicker.launch("*/*") },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(if (isDad) "Attach Proof / Receipt (Optional)" else "Attach Receipt / Bill")
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = selectedAttachmentUri?.lastPathSegment ?: "Attached Document",
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(
                                    onClick = { selectedAttachmentUri = null },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val paymentId = payment.id
                        val paidDate = if (payOnToday) System.currentTimeMillis() else payment.dueDate
                        val att = selectedAttachmentUri
                        pendingPayAction = null
                        viewModel.payCommitment(
                            paymentId = paymentId,
                            paidDate = paidDate,
                            createTransaction = !isDad && payer != "EXTERNAL",
                            payer = payer,
                            attachmentUri = att
                        ) { success ->
                            if (success) {
                                Toast.makeText(
                                    context,
                                    if (isDad) "Marked as paid by Dad" else "Premium payment recorded",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                ) {
                    Text(if (isDad) "Confirm (Paid by Dad)" else "Confirm & Pay")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingPayAction = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubscriptionDetailBottomSheet(
    subscription: Subscription,
    accounts: List<AccountMinimal>,
    viewModel: LoanViewModel,
    onDismiss: () -> Unit,
    onEditSubscription: () -> Unit,
    onDeleteSubscription: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val dateFormatter = LocalDateFormatter.current
    val linkedAccount = accounts.find { it.id == subscription.linkedAccountId }
    val currency = viewModel.getCurrencyUnit(linkedAccount?.currency ?: viewModel.defaultCurrency.code)

    val payments: List<CommitmentPayment> by viewModel.getPaymentsForCommitment("SUBSCRIPTION", subscription.id).collectAsState(initial = emptyList())
    val meters: List<UtilityMeter> by viewModel.getMetersForSubscription(subscription.id).collectAsState(initial = emptyList())
    val pendingPayment = payments.filter { it.status == PaymentRecordStatus.PENDING }.minByOrNull { it.dueDate }
    val pastPayments = payments.filter { it.status == PaymentRecordStatus.PAID }.sortedByDescending { it.paidDate ?: it.dueDate }

    var confirmingRenewPayment by remember { mutableStateOf<CommitmentPayment?>(null) }
    var showManageMetersDialog by remember { mutableStateOf(false) }
    var meterToPay by remember { mutableStateOf<UtilityMeter?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = subscription.serviceName,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    if (subscription.notes.isNotBlank()) {
                        Text(
                            text = subscription.notes,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        viewModel.resyncCommitment("SUBSCRIPTION", subscription.id) {
                            Toast.makeText(context, "Transactions scanned and mapped", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Resync past transactions")
                    }
                    IconButton(onClick = onEditSubscription) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Subscription")
                    }
                    IconButton(onClick = onDeleteSubscription) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Subscription", tint = MaterialTheme.colorScheme.error)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
            }

            // Chips Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AssistChip(
                    onClick = {},
                    label = { Text(subscription.billingCycle.name, style = MaterialTheme.typography.labelSmall) }
                )
                if (subscription.autoDebit) {
                    AssistChip(
                        onClick = {},
                        label = { Text("⚡ Auto-Debit", style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            HorizontalDivider()

            // Next Renewal Section
            Text(
                text = "Next Renewal",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            val renewalDueDate = pendingPayment?.dueDate ?: subscription.nextRenewalDate
            val todayLocalDate = LocalDate.now()
            val dueLocalDate = epochMillis2LocalDate(renewalDueDate)
            val daysLate = java.time.temporal.ChronoUnit.DAYS.between(dueLocalDate, todayLocalDate).toInt()
            val isOverdue = daysLate > 0
            val inGracePeriod = daysLate in 1..30

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        inGracePeriod -> Color(0xFFFFF3E0)
                        isOverdue -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    }
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            val statusTitle = when {
                                inGracePeriod -> "In Grace Period (${daysLate}d late)"
                                isOverdue -> "Overdue by ${daysLate} days"
                                else -> "Renewal Date"
                            }
                            val statusColor = when {
                                inGracePeriod -> Color(0xFFE65100)
                                isOverdue -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            Text(
                                text = statusTitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = statusColor,
                                fontWeight = if (isOverdue) FontWeight.Bold else FontWeight.Normal
                            )
                            Text(
                                text = dateFormatter.format(dueLocalDate),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Renewal Cost",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            AmountText(
                                amount = subscription.amount,
                                currency = currency,
                                fontWeight = FontWeight.Bold,
                                fontSize = MaterialTheme.typography.titleLarge.fontSize
                            )
                        }
                    }

                    linkedAccount?.let {
                        Text(
                            text = "Account: ${it.label}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (pendingPayment != null) {
                        Button(
                            onClick = { confirmingRenewPayment = pendingPayment },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Renew Now")
                        }
                    }
                }
            }

            if (subscription.isVariableAmount) {
                HorizontalDivider()

                val targetCycleDue = pendingPayment?.dueDate ?: subscription.nextRenewalDate
                val tolerance = if (subscription.billingCycle == BillingCycle.BI_MONTHLY) 45L * 86400000L else 25L * 86400000L
                val cyclePayments = payments.filter { it.status == PaymentRecordStatus.PAID && kotlin.math.abs(it.dueDate - targetCycleDue) <= tolerance }
                val paidMeterIds = cyclePayments.mapNotNull { it.meterId }.toSet()
                val paidCount = meters.count { it.id in paidMeterIds }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Meter Connections (${meters.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (meters.isNotEmpty()) {
                            Text(
                                text = "$paidCount / ${meters.size} Paid for ${dateFormatter.format(dueLocalDate)} Cycle",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (paidCount == meters.size) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    TextButton(onClick = { showManageMetersDialog = true }) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (meters.isEmpty()) "Set Up Meters" else "Manage Meters", style = MaterialTheme.typography.labelSmall)
                    }
                }

                if (meters.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "No Meter Connections Configured",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Track separate household meters (Single Phase, Three Phase, Nandhini, Tenant) with individual consumer numbers and cycle checklists.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = { showManageMetersDialog = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add Meter Connections")
                            }
                        }
                    }
                } else {
                    LinearProgressIndicator(
                        progress = { if (meters.isNotEmpty()) paidCount.toFloat() / meters.size else 0f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp),
                        color = if (paidCount == meters.size) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                    )

                    meters.forEach { meter ->
                        val paymentForMeter = cyclePayments.firstOrNull { it.meterId == meter.id }
                        val isPaid = paymentForMeter != null

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isPaid) Color(0xFFF1F8E9) else MaterialTheme.colorScheme.surface
                            ),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, if (isPaid) Color(0xFFA5D6A7) else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        if (isPaid) {
                                            Icon(Icons.Default.Check, contentDescription = "Paid", tint = Color(0xFF2E7D32), modifier = Modifier.size(18.dp))
                                        }
                                        Text(
                                            text = meter.meterLabel,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    if (meter.meterNumber.isNotBlank()) {
                                        Text(
                                            text = "Consumer #${meter.meterNumber}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (meter.isDailyConsumption) {
                                        val targetDue = pendingPayment?.dueDate ?: subscription.nextRenewalDate
                                        val targetLd = epochMillis2LocalDate(targetDue)
                                        val days = targetLd.lengthOfMonth()
                                        val qtyDisplay = if (meter.unitsPerDay % 1.0 == 0.0) meter.unitsPerDay.toInt().toString() else meter.unitsPerDay.toString()
                                        Text(
                                            text = "$qtyDisplay pkts/day @ ₹${meter.unitPrice / 100.0} (${days}d in ${targetLd.month.name.take(3)})",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    if (paymentForMeter?.paidDate != null) {
                                        Text(
                                            text = "Paid on ${dateFormatter.format(epochMillis2LocalDate(paymentForMeter.paidDate))}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFF2E7D32)
                                        )
                                    } else if (!isPaid) {
                                        Text(
                                            text = "Pending this cycle",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFFE65100)
                                        )
                                    }
                                }

                                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    val cycleAmount = if (meter.isDailyConsumption) {
                                        val targetDue = pendingPayment?.dueDate ?: subscription.nextRenewalDate
                                        meter.calculateAmountForDate(targetDue)
                                    } else {
                                        meter.estimatedAmount
                                    }
                                    AmountText(
                                        amount = paymentForMeter?.amount ?: cycleAmount,
                                        currency = currency,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isPaid) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurface
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        if (meter.meterNumber.isNotBlank()) {
                                            OutlinedButton(
                                                onClick = {
                                                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                                    val clip = android.content.ClipData.newPlainText("Consumer Number", meter.meterNumber)
                                                    clipboard.setPrimaryClip(clip)
                                                    Toast.makeText(context, "Consumer #${meter.meterNumber} copied!", Toast.LENGTH_SHORT).show()
                                                },
                                                modifier = Modifier.height(28.dp),
                                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                            ) {
                                                Text("Copy No.", style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                        if (!isPaid) {
                                            Button(
                                                onClick = { meterToPay = meter },
                                                modifier = Modifier.height(28.dp),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                            ) {
                                                Text("Pay", style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider()

            CommitmentDocumentsSection(
                type = "SUBSCRIPTION",
                commitmentId = subscription.id,
                viewModel = viewModel
            )

            HorizontalDivider()

            // Payment History Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Payment History",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${pastPayments.size} recorded",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (pastPayments.isEmpty()) {
                Text(
                    text = "No past renewals recorded yet. Tap the sync icon above to match existing transactions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                pastPayments.forEach { payment ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                val dueLd = epochMillis2LocalDate(payment.dueDate)
                                val paidLd = payment.paidDate?.let { epochMillis2LocalDate(it) } ?: dueLd
                                val delayDays = java.time.temporal.ChronoUnit.DAYS.between(dueLd, paidLd).toInt()
                                val dateDisplay = when {
                                    delayDays > 0 -> "Due: ${dateFormatter.format(dueLd)} • Paid: ${dateFormatter.format(paidLd)} (${delayDays}d late)"
                                    delayDays < 0 -> "Due: ${dateFormatter.format(dueLd)} • Paid: ${dateFormatter.format(paidLd)} (${-delayDays}d early)"
                                    else -> "Due: ${dateFormatter.format(dueLd)} • Paid on time"
                                }
                                Text(
                                    text = dateDisplay,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )

                                val meterInfo = meters.find { it.id == payment.meterId }
                                if (meterInfo != null) {
                                    Text(
                                        text = "⚡ ${meterInfo.meterLabel} (Consumer #${meterInfo.meterNumber})",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                } else if (payment.notes.isNotBlank() && !payment.notes.startsWith("Attachment: ")) {
                                    Text(
                                        text = payment.notes.lines().firstOrNull() ?: "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                val attachmentUriStr = payment.notes.lines().find { it.startsWith("Attachment: ") }?.removePrefix("Attachment: ")?.trim()
                                if (!attachmentUriStr.isNullOrEmpty()) {
                                    AssistChip(
                                        onClick = {
                                            try {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(attachmentUriStr)).apply {
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Cannot open attachment", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(14.dp))
                                        },
                                        label = { Text("Receipt", style = MaterialTheme.typography.labelSmall) },
                                        modifier = Modifier.height(24.dp)
                                    )
                                }

                                if (payment.transactionId != null) {
                                    Text(
                                        text = "Linked to Transaction #${payment.transactionId}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    Text(
                                        text = "Manual renewal",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            AmountText(
                                amount = payment.amount,
                                currency = currency,
                                fontWeight = FontWeight.Bold,
                                fontSize = MaterialTheme.typography.bodyMedium.fontSize
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Confirmation dialog for renewal
    val renewPayment = confirmingRenewPayment
    if (renewPayment != null) {
        var payOnToday by remember { mutableStateOf(true) }
        val todayStr = dateFormatter.format(LocalDate.now())
        val dueStr = dateFormatter.format(epochMillis2LocalDate(renewPayment.dueDate))
        var selectedAttachmentUri by remember { mutableStateOf<Uri?>(null) }
        val attachmentPicker = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            if (uri != null) {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: Exception) {
                    // Ignore non-persistable uri
                }
                selectedAttachmentUri = uri
            }
        }

        AlertDialog(
            onDismissRequest = { confirmingRenewPayment = null },
            title = { Text("Renew Subscription") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Renew ${subscription.serviceName} for ₹${subscription.amount / 100.0} and record an expense transaction in ${linkedAccount?.label ?: "your account"}?")
                    Text("Payment Date:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = payOnToday,
                            onClick = { payOnToday = true },
                            label = { Text("Today ($todayStr)") }
                        )
                        FilterChip(
                            selected = !payOnToday,
                            onClick = { payOnToday = false },
                            label = { Text("Due Date ($dueStr)") }
                        )
                    }

                    if (selectedAttachmentUri == null) {
                        OutlinedButton(
                            onClick = { attachmentPicker.launch("*/*") },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Attach Bill / Receipt / Image")
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = selectedAttachmentUri?.lastPathSegment ?: "Attached Document",
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(
                                    onClick = { selectedAttachmentUri = null },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val paymentId = renewPayment.id
                        val paidDate = if (payOnToday) System.currentTimeMillis() else renewPayment.dueDate
                        val att = selectedAttachmentUri
                        confirmingRenewPayment = null
                        viewModel.payCommitment(
                            paymentId = paymentId,
                            paidDate = paidDate,
                            createTransaction = true,
                            payer = "SELF",
                            attachmentUri = att
                        ) { success ->
                            if (success) {
                                Toast.makeText(context, "Subscription renewed successfully", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                ) {
                    Text("Confirm Renewal")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingRenewPayment = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    val meterPaying = meterToPay
    if (meterPaying != null) {
        val targetCycleDue = pendingPayment?.dueDate ?: subscription.nextRenewalDate
        val targetLocalDate = epochMillis2LocalDate(targetCycleDue)
        val daysInCycle = targetLocalDate.lengthOfMonth()
        val defaultCycleAmount = if (meterPaying.isDailyConsumption) {
            meterPaying.calculateAmountForDays(daysInCycle)
        } else {
            meterPaying.estimatedAmount
        }

        var payAmountText by remember(meterPaying) {
            mutableStateOf(if (defaultCycleAmount > 0) (defaultCycleAmount / 100.0).toString() else "")
        }
        var selectedPayAccountId by remember(meterPaying) { mutableStateOf(subscription.linkedAccountId ?: accounts.firstOrNull()?.id) }
        var expandedPayAccDropdown by remember { mutableStateOf(false) }
        var selectedAttachmentUri by remember(meterPaying) { mutableStateOf<Uri?>(null) }
        val attachmentPicker = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            if (uri != null) {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: Exception) {
                    // Ignore non-persistable uri
                }
                selectedAttachmentUri = uri
            }
        }

        AlertDialog(
            onDismissRequest = { meterToPay = null },
            title = { Text("Record Payment: ${meterPaying.meterLabel}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (meterPaying.meterNumber.isNotBlank()) {
                        Text(
                            "Consumer #${meterPaying.meterNumber}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (meterPaying.isDailyConsumption) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                val qtyDisplay = if (meterPaying.unitsPerDay % 1.0 == 0.0) meterPaying.unitsPerDay.toInt().toString() else meterPaying.unitsPerDay.toString()
                                Text(
                                    text = "Cycle: ${targetLocalDate.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${targetLocalDate.year} (${daysInCycle} days)",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "${daysInCycle} days × $qtyDisplay pkts/day @ ₹${meterPaying.unitPrice / 100.0} = ₹${defaultCycleAmount / 100.0}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = payAmountText,
                        onValueChange = { payAmountText = it },
                        label = { Text("Actual Paid Amount") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                        singleLine = true
                    )

                    if (meterPaying.isDailyConsumption && defaultCycleAmount > 0) {
                        val baseAmtStr = (defaultCycleAmount / 100.0).toString()
                        val feeAmtStr = ((defaultCycleAmount + 1800L) / 100.0).toString()
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = payAmountText == baseAmtStr,
                                onClick = { payAmountText = baseAmtStr },
                                label = { Text("₹$baseAmtStr", style = MaterialTheme.typography.labelSmall) }
                            )
                            FilterChip(
                                selected = payAmountText == feeAmtStr,
                                onClick = { payAmountText = feeAmtStr },
                                label = { Text("+₹18 fee (₹$feeAmtStr)", style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }

                    ExposedDropdownMenuBox(
                        expanded = expandedPayAccDropdown,
                        onExpandedChange = { expandedPayAccDropdown = it },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val selAcc = accounts.find { it.id == selectedPayAccountId }?.label ?: "Select Account"
                        OutlinedTextField(
                            value = selAcc,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Payment Account") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedPayAccDropdown) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        )
                        ExposedDropdownMenu(
                            expanded = expandedPayAccDropdown,
                            onDismissRequest = { expandedPayAccDropdown = false }
                        ) {
                            accounts.forEach { acc ->
                                DropdownMenuItem(
                                    text = { Text(acc.label) },
                                    onClick = {
                                        selectedPayAccountId = acc.id
                                        expandedPayAccDropdown = false
                                    }
                                )
                            }
                        }
                    }

                    // Attach Receipt / Screenshot / Bill
                    if (selectedAttachmentUri == null) {
                        OutlinedButton(
                            onClick = { attachmentPicker.launch("*/*") },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Attach Screenshot / Bill / PDF")
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = selectedAttachmentUri?.lastPathSegment ?: "Attached File",
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(
                                    onClick = { selectedAttachmentUri = null },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amtPaise = (payAmountText.toDoubleOrNull()?.times(100))?.toLong() ?: defaultCycleAmount
                        viewModel.payMeter(
                            meter = meterPaying,
                            subscription = subscription,
                            fromAccountId = selectedPayAccountId,
                            amount = amtPaise,
                            attachmentUri = selectedAttachmentUri
                        ) {
                            Toast.makeText(context, "Payment recorded for ${meterPaying.meterLabel}!", Toast.LENGTH_SHORT).show()
                        }
                        meterToPay = null
                    }
                ) {
                    Text("Confirm Payment")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { meterToPay = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showManageMetersDialog) {
        ManageUtilityMetersDialog(
            subscriptionId = subscription.id,
            initialMeters = meters,
            onDismiss = { showManageMetersDialog = false },
            onSaveMeters = { updatedMeters ->
                viewModel.saveUtilityMeters(subscription.id, updatedMeters) {
                    Toast.makeText(context, "Meters updated!", Toast.LENGTH_SHORT).show()
                }
                showManageMetersDialog = false
            }
        )
    }
}

@Composable
private fun ManageUtilityMetersDialog(
    subscriptionId: Long,
    initialMeters: List<UtilityMeter>,
    onDismiss: () -> Unit,
    onSaveMeters: (List<UtilityMeter>) -> Unit
) {
    var metersList by remember(initialMeters) {
        mutableStateOf(
            if (initialMeters.isEmpty()) {
                listOf(
                    UtilityMeter(subscriptionId = subscriptionId, meterLabel = "Single Phase", meterNumber = "", estimatedAmount = 50000L, matchingKeywords = "Single phase, Single Phase, Domestic, Commercial"),
                    UtilityMeter(subscriptionId = subscriptionId, meterLabel = "Three Phase", meterNumber = "", estimatedAmount = 400000L, matchingKeywords = "Three phase, Three Phase"),
                    UtilityMeter(subscriptionId = subscriptionId, meterLabel = "Nandhini", meterNumber = "", estimatedAmount = 500000L, matchingKeywords = "Nandhini, Nandhini EB, Nandhini Eb"),
                    UtilityMeter(subscriptionId = subscriptionId, meterLabel = "Tenant", meterNumber = "", estimatedAmount = 250000L, matchingKeywords = "Tenant, Tenant eb, Tenant Eb, For Tenant")
                )
            } else {
                initialMeters
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Manage Meter Connections", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Configure your household connections. Future transactions will automatically link to these meters based on consumer numbers or keywords.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                metersList.forEachIndexed { index, meter ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Meter #${index + 1}: ${meter.meterLabel}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                IconButton(
                                    onClick = {
                                        metersList = metersList.filterIndexed { i, _ -> i != index }
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Remove Meter", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                }
                            }

                            OutlinedTextField(
                                value = meter.meterLabel,
                                onValueChange = { newLabel ->
                                    metersList = metersList.mapIndexed { i, m -> if (i == index) m.copy(meterLabel = newLabel) else m }
                                },
                                label = { Text("Label", style = MaterialTheme.typography.labelSmall) },
                                placeholder = { Text("e.g. Single Phase, Three Phase, Milk Card") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            // Calculation mode selection
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = !meter.isDailyConsumption,
                                    onClick = {
                                        metersList = metersList.mapIndexed { i, m ->
                                            if (i == index) m.copy(unitPrice = 0L, unitsPerDay = 0.0) else m
                                        }
                                    },
                                    label = { Text("Fixed Est. ₹", style = MaterialTheme.typography.labelSmall) }
                                )
                                FilterChip(
                                    selected = meter.isDailyConsumption,
                                    onClick = {
                                        val defPrice = if (meter.unitPrice > 0L) meter.unitPrice else 1850L
                                        val defQty = if (meter.unitsPerDay > 0.0) meter.unitsPerDay else 2.0
                                        val defEst = (30 * defQty * (defPrice / 100.0) * 100.0).roundToLong()
                                        metersList = metersList.mapIndexed { i, m ->
                                            if (i == index) m.copy(unitPrice = defPrice, unitsPerDay = defQty, estimatedAmount = defEst) else m
                                        }
                                    },
                                    label = { Text("Daily Packets / Units", style = MaterialTheme.typography.labelSmall) }
                                )
                            }

                            if (meter.isDailyConsumption) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = if (meter.unitPrice > 0) (meter.unitPrice / 100.0).toString() else "",
                                        onValueChange = { priceStr ->
                                            val pricePaise = (priceStr.toDoubleOrNull()?.times(100))?.roundToLong() ?: 0L
                                            val autoEst = (30 * meter.unitsPerDay * (pricePaise / 100.0) * 100.0).roundToLong()
                                            metersList = metersList.mapIndexed { i, m ->
                                                if (i == index) m.copy(unitPrice = pricePaise, estimatedAmount = autoEst) else m
                                            }
                                        },
                                        label = { Text("₹ / Packet", style = MaterialTheme.typography.labelSmall) },
                                        placeholder = { Text("18.50") },
                                        modifier = Modifier.weight(1f),
                                        keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = if (meter.unitsPerDay > 0) {
                                            if (meter.unitsPerDay % 1.0 == 0.0) meter.unitsPerDay.toInt().toString() else meter.unitsPerDay.toString()
                                        } else "",
                                        onValueChange = { qtyStr ->
                                            val qty = qtyStr.toDoubleOrNull() ?: 0.0
                                            val autoEst = (30 * qty * (meter.unitPrice / 100.0) * 100.0).roundToLong()
                                            metersList = metersList.mapIndexed { i, m ->
                                                if (i == index) m.copy(unitsPerDay = qty, estimatedAmount = autoEst) else m
                                            }
                                        },
                                        label = { Text("Packets / Day", style = MaterialTheme.typography.labelSmall) },
                                        placeholder = { Text("2") },
                                        modifier = Modifier.weight(1f),
                                        keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                                        singleLine = true
                                    )
                                }

                                val preview30 = (30 * meter.unitsPerDay * (meter.unitPrice / 100.0)).roundToLong()
                                val preview31 = (31 * meter.unitsPerDay * (meter.unitPrice / 100.0)).roundToLong()
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "📅 Auto-calc: 30 days = ₹$preview30 • 31 days = ₹$preview31",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                    )
                                }
                            } else {
                                OutlinedTextField(
                                    value = if (meter.estimatedAmount > 0) (meter.estimatedAmount / 100.0).toString() else "",
                                    onValueChange = { newAmtStr ->
                                        val newAmtPaise = (newAmtStr.toDoubleOrNull()?.times(100))?.toLong() ?: 0L
                                        metersList = metersList.mapIndexed { i, m -> if (i == index) m.copy(estimatedAmount = newAmtPaise) else m }
                                    },
                                    label = { Text("Estimated Amount (₹)", style = MaterialTheme.typography.labelSmall) },
                                    placeholder = { Text("e.g. 500") },
                                    modifier = Modifier.fillMaxWidth(),
                                    keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                                    singleLine = true
                                )
                            }

                            OutlinedTextField(
                                value = meter.meterNumber,
                                onValueChange = { newNo ->
                                    metersList = metersList.mapIndexed { i, m -> if (i == index) m.copy(meterNumber = newNo) else m }
                                },
                                label = { Text("Consumer / Service No.", style = MaterialTheme.typography.labelSmall) },
                                placeholder = { Text("e.g. 09-xxx-xxx-xxxx", style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            OutlinedTextField(
                                value = meter.matchingKeywords,
                                onValueChange = { newKw ->
                                    metersList = metersList.mapIndexed { i, m -> if (i == index) m.copy(matchingKeywords = newKw) else m }
                                },
                                label = { Text("Keywords (comma-separated)", style = MaterialTheme.typography.labelSmall) },
                                placeholder = { Text("e.g. Single Phase, Domestic", style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }
                    }
                }

                OutlinedButton(
                    onClick = {
                        metersList = metersList + UtilityMeter(
                            subscriptionId = subscriptionId,
                            meterLabel = "Meter ${metersList.size + 1}",
                            meterNumber = "",
                            estimatedAmount = 200000L
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Another Meter")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSaveMeters(metersList.filter { it.meterLabel.isNotBlank() })
                }
            ) {
                Text("Save Connections")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoanDetailBottomSheet(
    loan: Loan,
    installments: List<EmiInstallment>,
    progress: org.totschnig.myexpenses.domain.loan.LoanProgress?,
    accounts: List<AccountMinimal>,
    viewModel: LoanViewModel,
    onDismiss: () -> Unit,
    onPayInstallment: (EmiInstallment) -> Unit,
    onEditLoan: () -> Unit,
    onLinkTransaction: (EmiInstallment) -> Unit,
    onUnlinkTransaction: (EmiInstallment) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val currency = viewModel.defaultCurrency
    val dateFormatter = LocalDateFormatter.current
    val context = LocalContext.current
    var showDeleteConfirm by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = loan.label,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    if (loan.lenderName.isNotBlank()) {
                        Text(
                            text = loan.lenderName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (loan.linkedAccountId != null) {
                        IconButton(onClick = {
                            viewModel.resyncAndMatchTransactions(loan.id) { count ->
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.matched_emi_transactions, count),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }) {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.resync_transactions))
                        }
                    }
                    IconButton(onClick = onEditLoan) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit_loan))
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            when (loan.loanType) {
                                LoanType.REDUCING_BALANCE -> "Reducing Balance"
                                LoanType.FLAT_RATE -> "Flat Rate"
                                LoanType.NO_COST_EMI -> "0% No-Cost EMI"
                            }
                        )
                    }
                )
                if (loan.autoDebit) {
                    AssistChip(
                        onClick = {},
                        label = { Text("⚡ Auto-debit ON", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Progress Bar
            if (progress != null) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${progress.paidInstallmentsCount} of ${progress.totalInstallmentsCount} EMIs Paid",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "${progress.progressPercent.toInt()}%",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { (progress.progressPercent / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Remaining: ₹${progress.remainingPrincipal / 100.0}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Paid: ₹${progress.totalPrincipalPaid / 100.0}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))

            CommitmentDocumentsSection(
                type = "LOAN",
                commitmentId = loan.id,
                viewModel = viewModel
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))

            // Amortization Schedule
            Text(
                text = stringResource(R.string.amortization_schedule),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            installments.forEach { installment ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (installment.status == InstallmentStatus.PAID)
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        else
                            MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "EMI #${installment.installmentNumber}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            val dueDateStr = dateFormatter.format(epochMillis2LocalDate(installment.dueDate))
                            val dateLabel = if (installment.status == InstallmentStatus.PAID && installment.paidDate != null) {
                                val paidDateStr = dateFormatter.format(epochMillis2LocalDate(installment.paidDate))
                                "Due: $dueDateStr (Paid: $paidDateStr)"
                            } else {
                                "Due: $dueDateStr"
                            }
                            Text(
                                text = dateLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Principal: ₹${installment.principalPart / 100.0} | Interest: ₹${installment.interestPart / 100.0}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            AmountText(
                                amount = installment.totalAmount,
                                currency = currency,
                                fontSize = MaterialTheme.typography.titleSmall.fontSize,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            if (installment.status == InstallmentStatus.PAID) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    AssistChip(
                                        onClick = {},
                                        label = { Text("PAID", color = Color(0xFF2E7D32)) },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = null,
                                                tint = Color(0xFF2E7D32),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    )
                                    if (installment.transactionId != null) {
                                        IconButton(
                                            onClick = { onUnlinkTransaction(installment) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = stringResource(R.string.unlink_transaction),
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                                if (installment.transactionId != null) {
                                    Text(
                                        text = "Tx #${installment.transactionId}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                } else {
                                    TextButton(
                                        onClick = { onLinkTransaction(installment) },
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                        modifier = Modifier.height(24.dp)
                                    ) {
                                        Text(stringResource(R.string.link_transaction), style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { onLinkTransaction(installment) },
                                        modifier = Modifier.height(32.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                    ) {
                                        Text(stringResource(R.string.link_transaction), style = MaterialTheme.typography.labelSmall)
                                    }
                                    Button(
                                        onClick = { onPayInstallment(installment) },
                                        modifier = Modifier.height(32.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                                    ) {
                                        Text("Pay", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (loan.status == LoanStatus.ACTIVE) {
                    OutlinedButton(
                        onClick = {
                            viewModel.closeLoan(loan.id) { onDismiss() }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.close_loan))
                    }
                }
                Button(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.weight(1f),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.delete_loan))
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.delete_loan)) },
            text = { Text(stringResource(R.string.delete_loan_confirmation, loan.label)) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteLoan(loan.id) {
                            showDeleteConfirm = false
                            onDismiss()
                        }
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.delete_loan))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddLoanBottomSheet(
    accounts: List<AccountMinimal>,
    viewModel: LoanViewModel,
    initialLoan: Loan? = null,
    initialPlan: Template? = null,
    onDismiss: () -> Unit
) {
    val isEdit = initialLoan != null
    val isConvert = initialPlan != null
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var label by remember(initialLoan, initialPlan) {
        mutableStateOf(initialLoan?.label ?: initialPlan?.title ?: "")
    }
    var lenderName by remember(initialLoan, initialPlan) {
        mutableStateOf(initialLoan?.lenderName ?: initialPlan?.payeeName ?: "")
    }
    var loanType by remember(initialLoan) {
        mutableStateOf(initialLoan?.loanType ?: LoanType.REDUCING_BALANCE)
    }
    var principalText by remember(initialLoan) {
        mutableStateOf(initialLoan?.let { (it.principalAmount / 100L).toString() } ?: "")
    }
    var interestRateText by remember(initialLoan) {
        mutableStateOf(initialLoan?.interestRatePercent?.toString() ?: "10.5")
    }
    var tenureMonthsText by remember(initialLoan) {
        mutableStateOf(initialLoan?.tenureMonths?.toString() ?: "12")
    }
    var dueDayOfMonthText by remember(initialLoan) {
        mutableStateOf(initialLoan?.dueDayOfMonth?.toString() ?: "5")
    }
    var actualEmiText by remember(initialLoan, initialPlan) {
        mutableStateOf(
            initialLoan?.actualEmiAmount?.let { (it / 100L).toString() }
                ?: initialLoan?.let { (it.monthlyEmiAmount / 100L).toString() }
                ?: initialPlan?.takeIf { it.amount != 0L }?.let { (kotlin.math.abs(it.amount) / 100L).toString() }
                ?: ""
        )
    }
    var emisAlreadyPaidText by remember { mutableStateOf("0") }
    val context = LocalContext.current
    var autoDebit by remember(initialLoan) {
        mutableStateOf(initialLoan?.autoDebit ?: false)
    }
    var selectedAccountId by remember(initialLoan, initialPlan) {
        mutableStateOf(initialLoan?.linkedAccountId ?: initialPlan?.accountId)
    }
    var expandedAccountDropdown by remember { mutableStateOf(false) }
    var attachedUris by remember { mutableStateOf<List<Uri>>(emptyList()) }

    // Conversion specific state
    var detectedTransactions by remember { mutableStateOf<List<PastTransactionInfo>>(emptyList()) }
    var isLoadingDetectedTransactions by remember { mutableStateOf(false) }
    var deletePlanAfterConvert by remember { mutableStateOf(true) }

    // Edit specific state
    var recalculateSchedule by remember { mutableStateOf(true) }

    LaunchedEffect(initialPlan) {
        if (initialPlan != null) {
            isLoadingDetectedTransactions = true
            val emiPaise = kotlin.math.abs(initialPlan.amount)
            val history = viewModel.getHistoricalTransactionsForPlan(
                templateId = initialPlan.id,
                accountId = initialPlan.accountId,
                emiAmount = emiPaise
            )
            detectedTransactions = history
            if (history.isNotEmpty()) {
                emisAlreadyPaidText = history.size.toString()
            }
            isLoadingDetectedTransactions = false
        }
    }

    val principal = principalText.toLongOrNull()?.times(100L) ?: 0L
    val interestRate = interestRateText.toDoubleOrNull() ?: 0.0
    val tenureMonths = tenureMonthsText.toIntOrNull() ?: 0
    val dueDay = dueDayOfMonthText.toIntOrNull() ?: 5
    val overrideEmi = actualEmiText.toLongOrNull()?.times(100L)

    val previewEmi = remember(loanType, principal, interestRate, tenureMonths, overrideEmi) {
        overrideEmi ?: if (principal > 0 && tenureMonths > 0) {
            EmiCalculator.calculateEmi(loanType, principal, interestRate, tenureMonths)
        } else 0L
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = when {
                    isEdit -> stringResource(R.string.edit_loan)
                    isConvert -> stringResource(R.string.convert_plan_title)
                    else -> stringResource(R.string.add_loan)
                },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            if (isConvert) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = stringResource(R.string.convert_to_loan),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        if (isLoadingDetectedTransactions) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Detecting previous transactions...", style = MaterialTheme.typography.bodySmall)
                            }
                        } else {
                            Text(
                                text = "Found ${detectedTransactions.size} previous transactions recorded from this plan. They will be linked as PAID EMIs #1 to #${detectedTransactions.size}.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = deletePlanAfterConvert,
                                onCheckedChange = { deletePlanAfterConvert = it }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Remove recurring plan template after conversion",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            if (isEdit) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = recalculateSchedule,
                            onCheckedChange = { recalculateSchedule = it }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Recalculate pending installments with updated terms (paid EMIs remain intact)",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // Loan Label
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text(stringResource(R.string.loan_label) + " *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Lender Name
            OutlinedTextField(
                value = lenderName,
                onValueChange = { lenderName = it },
                label = { Text(stringResource(R.string.lender_name)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Loan Type Selector
            Text("Loan Type", style = MaterialTheme.typography.labelMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = loanType == LoanType.REDUCING_BALANCE,
                    onClick = { loanType = LoanType.REDUCING_BALANCE },
                    label = { Text("Reducing") }
                )
                FilterChip(
                    selected = loanType == LoanType.FLAT_RATE,
                    onClick = { loanType = LoanType.FLAT_RATE },
                    label = { Text("Flat Rate") }
                )
                FilterChip(
                    selected = loanType == LoanType.NO_COST_EMI,
                    onClick = { loanType = LoanType.NO_COST_EMI },
                    label = { Text("0% No-Cost") }
                )
            }

            // Principal Amount
            OutlinedTextField(
                value = principalText,
                onValueChange = { principalText = it.filter { ch -> ch.isDigit() } },
                label = { Text(stringResource(R.string.principal_amount) + " *") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Interest Rate (if not no-cost EMI)
            if (loanType != LoanType.NO_COST_EMI) {
                OutlinedTextField(
                    value = interestRateText,
                    onValueChange = { interestRateText = it },
                    label = { Text(stringResource(R.string.interest_rate) + " *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            // Tenure Months
            OutlinedTextField(
                value = tenureMonthsText,
                onValueChange = { tenureMonthsText = it.filter { ch -> ch.isDigit() } },
                label = { Text(stringResource(R.string.tenure_months) + " *") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Due Day of Month
            OutlinedTextField(
                value = dueDayOfMonthText,
                onValueChange = { dueDayOfMonthText = it.filter { ch -> ch.isDigit() } },
                label = { Text(stringResource(R.string.due_day_of_month)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Optional Actual EMI Override
            OutlinedTextField(
                value = actualEmiText,
                onValueChange = { actualEmiText = it.filter { ch -> ch.isDigit() } },
                label = { Text("Actual Monthly EMI (₹) (Optional Override)") },
                placeholder = { Text("e.g. from bank statement") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Mid-Tenure Onboarding: EMIs Already Paid (only in Add / Convert mode)
            if (!isEdit) {
                OutlinedTextField(
                    value = emisAlreadyPaidText,
                    onValueChange = { emisAlreadyPaidText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Number of EMIs already paid (0 for new loan)") },
                    placeholder = { Text("e.g. 28 if migrating existing loan") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            // Linked Bank Account
            ExposedDropdownMenuBox(
                expanded = expandedAccountDropdown,
                onExpandedChange = { expandedAccountDropdown = !expandedAccountDropdown }
            ) {
                val selectedAccount = accounts.find { it.id == selectedAccountId }
                OutlinedTextField(
                    readOnly = true,
                    value = selectedAccount?.label ?: stringResource(R.string.none),
                    onValueChange = {},
                    label = { Text(stringResource(R.string.linked_account)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedAccountDropdown) },
                    modifier = Modifier
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = expandedAccountDropdown,
                    onDismissRequest = { expandedAccountDropdown = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.none)) },
                        onClick = {
                            selectedAccountId = null
                            expandedAccountDropdown = false
                        }
                    )
                    accounts.forEach { account ->
                        DropdownMenuItem(
                            text = { Text(account.label) },
                            onClick = {
                                selectedAccountId = account.id
                                expandedAccountDropdown = false
                            }
                        )
                    }
                }
            }

            // Auto-debit Checkbox (when linked account is selected)
            if (selectedAccountId != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { autoDebit = !autoDebit }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = autoDebit,
                        onCheckedChange = { autoDebit = it }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.auto_debit_loan),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = stringResource(R.string.auto_debit_loan_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Live Preview Card
            if (previewEmi > 0) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Live Calculation Preview",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Monthly EMI:")
                            Text("₹${previewEmi / 100.0}", fontWeight = FontWeight.Bold)
                        }
                        val totalPayable = previewEmi * tenureMonths
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Total Repayment:")
                            Text("₹${totalPayable / 100.0}")
                        }
                        if (loanType != LoanType.NO_COST_EMI) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Total Interest:")
                                Text("₹${(totalPayable - principal) / 100.0}")
                            }
                        }
                        val alreadyPaid = emisAlreadyPaidText.toIntOrNull() ?: 0
                        if (alreadyPaid > 0 && !isEdit) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Onboarding: $alreadyPaid of $tenureMonths EMIs marked paid",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            CommitmentAttachmentPicker(
                attachedUris = attachedUris,
                onUrisChanged = { attachedUris = it },
                label = "Attach Loan Documents / Sanction Letter / EMI Schedule"
            )

            // Submit Button
            val canSave = label.isNotBlank() && principal > 0 && tenureMonths > 0
            Button(
                onClick = {
                    val actualEmiPaise = actualEmiText.toLongOrNull()?.times(100L)
                    val alreadyPaidCount = emisAlreadyPaidText.toIntOrNull() ?: 0

                    val isAutoDebitEnabled = selectedAccountId != null && autoDebit

                    if (isEdit && initialLoan != null) {
                        val updatedLoan = initialLoan.copy(
                            label = label,
                            lenderName = lenderName,
                            loanType = loanType,
                            principalAmount = principal,
                            interestRatePercent = interestRate,
                            tenureMonths = tenureMonths,
                            dueDayOfMonth = dueDay,
                            linkedAccountId = selectedAccountId,
                            actualEmiAmount = actualEmiPaise,
                            autoDebit = isAutoDebitEnabled
                        )
                        viewModel.updateLoan(
                            loan = updatedLoan,
                            customEmi = actualEmiPaise,
                            recalculateRemaining = recalculateSchedule
                        ) {
                            if (attachedUris.isNotEmpty()) {
                                viewModel.saveCommitmentAttachments("LOAN", initialLoan.id, attachedUris)
                            }
                            LoanDueWorker.enqueue(context)
                            onDismiss()
                        }
                    } else if (isConvert && initialPlan != null) {
                        val newLoan = Loan(
                            label = label,
                            lenderName = lenderName,
                            loanType = loanType,
                            principalAmount = principal,
                            interestRatePercent = interestRate,
                            tenureMonths = tenureMonths,
                            monthlyEmiAmount = previewEmi,
                            startDate = detectedTransactions.firstOrNull()?.dateMillis ?: System.currentTimeMillis(),
                            firstEmiDate = detectedTransactions.firstOrNull()?.dateMillis ?: 0L,
                            dueDayOfMonth = dueDay,
                            linkedAccountId = selectedAccountId,
                            actualEmiAmount = actualEmiPaise,
                            autoDebit = isAutoDebitEnabled
                        )
                        val txPairs = detectedTransactions.take(alreadyPaidCount).map { it.id to it.dateMillis }
                        viewModel.convertPlanToLoan(
                            loan = newLoan,
                            templateId = initialPlan.id,
                            customEmi = actualEmiPaise,
                            emisAlreadyPaid = alreadyPaidCount,
                            linkedTransactions = txPairs,
                            deletePlan = deletePlanAfterConvert
                        ) { newLoanId ->
                            if (attachedUris.isNotEmpty()) {
                                viewModel.saveCommitmentAttachments("LOAN", newLoanId, attachedUris)
                            }
                            LoanDueWorker.enqueue(context)
                            onDismiss()
                        }
                    } else {
                        viewModel.createLoan(
                            label = label,
                            lenderName = lenderName,
                            loanType = loanType,
                            principalAmount = principal,
                            interestRatePercent = interestRate,
                            tenureMonths = tenureMonths,
                            startDate = System.currentTimeMillis(),
                            dueDayOfMonth = dueDay,
                            linkedAccountId = selectedAccountId,
                            actualEmiAmount = actualEmiPaise,
                            emisAlreadyPaid = alreadyPaidCount,
                            autoDebit = isAutoDebitEnabled
                        ) { newLoanId ->
                            if (attachedUris.isNotEmpty()) {
                                viewModel.saveCommitmentAttachments("LOAN", newLoanId, attachedUris)
                            }
                            LoanDueWorker.enqueue(context)
                            onDismiss()
                        }
                    }
                },
                enabled = canSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
            ) {
                Text(
                    when {
                        isEdit -> stringResource(R.string.update_loan)
                        isConvert -> stringResource(R.string.convert_to_loan)
                        else -> stringResource(R.string.save_loan)
                    }
                )
            }
        }
    }
}

@Composable
private fun LinkTransactionDialog(
    installment: EmiInstallment,
    accountId: Long?,
    accounts: List<AccountMinimal>,
    viewModel: LoanViewModel,
    onDismiss: () -> Unit
) {
    var selectedAccountId by remember { mutableStateOf(accountId ?: accounts.firstOrNull()?.id) }
    var candidates by remember { mutableStateOf<List<PastTransactionInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    val dateFormatter = LocalDateFormatter.current

    LaunchedEffect(selectedAccountId) {
        val accId = selectedAccountId
        if (accId != null) {
            isLoading = true
            candidates = viewModel.getCandidateTransactions(accId)
            isLoading = false
        } else {
            candidates = emptyList()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.select_transaction_to_link))
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
            ) {
                Text(
                    text = "Link to EMI #${installment.installmentNumber} (₹${installment.totalAmount / 100.0})",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (accounts.size > 1) {
                    var expanded by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { expanded = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val accName = accounts.find { it.id == selectedAccountId }?.label ?: "Select Account"
                            Text("Account: $accName", style = MaterialTheme.typography.bodySmall)
                        }
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            accounts.forEach { acc ->
                                DropdownMenuItem(
                                    text = { Text(acc.label) },
                                    onClick = {
                                        selectedAccountId = acc.id
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (candidates.isEmpty()) {
                    Text(
                        text = stringResource(R.string.no_candidate_transactions),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                    ) {
                        items(candidates) { tx ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable {
                                        viewModel.linkTransactionToInstallment(
                                            installmentId = installment.id,
                                            transactionId = tx.id,
                                            paidDate = tx.dateMillis
                                        ) {
                                            onDismiss()
                                        }
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = tx.comment?.takeIf { it.isNotBlank() } ?: "Transaction #${tx.id}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                        val dateStr = dateFormatter.format(epochMillis2LocalDate(tx.dateMillis))
                                        Text(
                                            text = dateStr,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "₹${kotlin.math.abs(tx.amount) / 100.0}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}

@Composable
private fun CreditCardItem(
    account: AccountMinimal,
    detail: CreditCardDetail?,
    viewModel: LoanViewModel,
    onUpdateStatement: () -> Unit,
    onPayBill: () -> Unit
) {
    val currency = viewModel.getCurrencyUnit(account.currency)

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = account.label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Credit Card",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (detail != null && detail.creditLimit > 0) {
                    AssistChip(
                        onClick = {},
                        label = { Text("Limit: ₹${detail.creditLimit / 100.0}") }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (detail != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.total_due),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        AmountText(
                            amount = detail.totalDueAmount,
                            currency = currency,
                            fontSize = MaterialTheme.typography.titleMedium.fontSize,
                            fontWeight = FontWeight.Bold,
                            color = if (detail.totalDueAmount > 0) MaterialTheme.colorScheme.error else Color.Unspecified
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = stringResource(R.string.minimum_due),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        AmountText(
                            amount = detail.minimumDueAmount,
                            currency = currency,
                            fontSize = MaterialTheme.typography.titleSmall.fontSize
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Statement Day: ${detail.statementDayOfMonth}th",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Due Day: ${detail.dueDayOfMonth}th",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Text(
                    text = "No billing cycle configured yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onUpdateStatement,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.update_statement), style = MaterialTheme.typography.labelSmall)
                }
                Button(
                    onClick = onPayBill,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.record_payment), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun UpdateCreditCardDialog(
    account: AccountMinimal,
    detail: CreditCardDetail?,
    viewModel: LoanViewModel,
    onDismiss: () -> Unit
) {
    var creditLimitText by remember { mutableStateOf((detail?.creditLimit?.div(100L) ?: 50000L).toString()) }
    var totalDueText by remember { mutableStateOf((detail?.totalDueAmount?.div(100L) ?: 0L).toString()) }
    var minDueText by remember { mutableStateOf((detail?.minimumDueAmount?.div(100L) ?: 0L).toString()) }
    var statementDayText by remember { mutableStateOf((detail?.statementDayOfMonth ?: 15).toString()) }
    var dueDayText by remember { mutableStateOf((detail?.dueDayOfMonth ?: 5).toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${account.label} - ${stringResource(R.string.update_statement)}") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = creditLimitText,
                    onValueChange = { creditLimitText = it.filter { ch -> ch.isDigit() } },
                    label = { Text(stringResource(R.string.credit_limit)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                OutlinedTextField(
                    value = totalDueText,
                    onValueChange = { totalDueText = it.filter { ch -> ch.isDigit() } },
                    label = { Text(stringResource(R.string.total_due)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                OutlinedTextField(
                    value = minDueText,
                    onValueChange = { minDueText = it.filter { ch -> ch.isDigit() } },
                    label = { Text(stringResource(R.string.minimum_due)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                OutlinedTextField(
                    value = statementDayText,
                    onValueChange = { statementDayText = it.filter { ch -> ch.isDigit() } },
                    label = { Text(stringResource(R.string.billing_day)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                OutlinedTextField(
                    value = dueDayText,
                    onValueChange = { dueDayText = it.filter { ch -> ch.isDigit() } },
                    label = { Text(stringResource(R.string.due_day)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val limit = (creditLimitText.toLongOrNull() ?: 0L) * 100L
                    val totalDue = (totalDueText.toLongOrNull() ?: 0L) * 100L
                    val minDue = (minDueText.toLongOrNull() ?: 0L) * 100L
                    val stmtDay = statementDayText.toIntOrNull()?.coerceIn(1, 31) ?: 15
                    val dueDay = dueDayText.toIntOrNull()?.coerceIn(1, 31) ?: 5

                    val updatedDetail = CreditCardDetail(
                        accountId = account.id,
                        creditLimit = limit,
                        statementDayOfMonth = stmtDay,
                        dueDayOfMonth = dueDay,
                        totalDueAmount = totalDue,
                        minimumDueAmount = minDue,
                        lastStatementDate = System.currentTimeMillis()
                    )
                    viewModel.saveCreditCardDetail(updatedDetail) {
                        onDismiss()
                    }
                }
            ) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettleCreditCardDialog(
    creditCardAccount: AccountMinimal,
    detail: CreditCardDetail?,
    accounts: List<AccountMinimal>,
    viewModel: LoanViewModel,
    onDismiss: () -> Unit
) {
    val payingAccounts = accounts.filter { it.id != creditCardAccount.id }
    var selectedPayingAccountId by remember {
        mutableStateOf(payingAccounts.firstOrNull()?.id ?: 0L)
    }
    var amountText by remember {
        mutableStateOf((detail?.totalDueAmount?.div(100L) ?: 0L).toString())
    }
    var expandedPayingAccountDropdown by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.record_payment)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Settling bill for: ${creditCardAccount.label}", fontWeight = FontWeight.Bold)

                ExposedDropdownMenuBox(
                    expanded = expandedPayingAccountDropdown,
                    onExpandedChange = { expandedPayingAccountDropdown = !expandedPayingAccountDropdown }
                ) {
                    val selectedAcc = payingAccounts.find { it.id == selectedPayingAccountId }
                    OutlinedTextField(
                        readOnly = true,
                        value = selectedAcc?.label ?: "Select paying account",
                        onValueChange = {},
                        label = { Text(stringResource(R.string.select_paying_account)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedPayingAccountDropdown) },
                        modifier = Modifier
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expandedPayingAccountDropdown,
                        onDismissRequest = { expandedPayingAccountDropdown = false }
                    ) {
                        payingAccounts.forEach { acc ->
                            DropdownMenuItem(
                                text = { Text(acc.label) },
                                onClick = {
                                    selectedPayingAccountId = acc.id
                                    expandedPayingAccountDropdown = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Payment Amount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            val amount = (amountText.toLongOrNull() ?: 0L) * 100L
            Button(
                onClick = {
                    if (amount > 0 && selectedPayingAccountId > 0) {
                        viewModel.settleCreditCardDue(
                            creditCardAccountId = creditCardAccount.id,
                            fromAccountId = selectedPayingAccountId,
                            amount = amount
                        ) {
                            onDismiss()
                        }
                    }
                },
                enabled = amount > 0 && selectedPayingAccountId > 0
            ) {
                Text(stringResource(R.string.record_payment))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}

@Composable
fun RecurringPlanItem(
    plan: Template,
    account: AccountMinimal?,
    viewModel: LoanViewModel,
    onRunPlan: () -> Unit,
    onEditPlan: () -> Unit,
    onDeletePlan: () -> Unit,
    onConvertToLoan: () -> Unit,
    onConvertToInsurance: () -> Unit,
    onConvertToSubscription: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = plan.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            if (plan.planExecutionAutomatic) "Auto-Debit" else "Manual Run",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            account?.let {
                Text(
                    text = "Account: ${it.label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!plan.categoryPath.isNullOrBlank()) {
                Text(
                    text = "Category: ${plan.categoryPath}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val currencyUnit = viewModel.getCurrencyUnit(account?.currency ?: viewModel.defaultCurrency.code)
                AmountText(
                    amount = plan.amount,
                    currency = currencyUnit,
                    fontWeight = FontWeight.SemiBold
                )

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Button(onClick = onRunPlan, modifier = Modifier.height(32.dp), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Run", modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("Record", style = MaterialTheme.typography.labelSmall)
                    }
                    IconButton(onClick = onEditPlan, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onDeletePlan, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Convert to commitment:",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(
                    onClick = onConvertToLoan,
                    modifier = Modifier.weight(1f).height(32.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) {
                    Text("Loan", style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(
                    onClick = onConvertToInsurance,
                    modifier = Modifier.weight(1f).height(32.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) {
                    Text("Insurance", style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(
                    onClick = onConvertToSubscription,
                    modifier = Modifier.weight(1f).height(32.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) {
                    Text("Subscription", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
fun QuickTemplateItem(
    template: Template,
    account: AccountMinimal?,
    viewModel: LoanViewModel,
    onApply: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onConvertToLoan: () -> Unit,
    onConvertToInsurance: () -> Unit,
    onConvertToSubscription: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = template.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                account?.let {
                    AssistChip(
                        onClick = {},
                        label = { Text(it.label, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            if (!template.categoryPath.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = template.categoryPath,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val currencyUnit = viewModel.getCurrencyUnit(account?.currency ?: viewModel.defaultCurrency.code)
                if (template.amount != 0L) {
                    AmountText(
                        amount = template.amount,
                        currency = currencyUnit,
                        fontWeight = FontWeight.SemiBold
                    )
                } else {
                    Text(
                        text = "Variable Amount",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Button(onClick = onApply) {
                        Icon(Icons.Default.Bolt, contentDescription = "Apply", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.apply_template))
                    }
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Convert to commitment:",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(
                    onClick = onConvertToLoan,
                    modifier = Modifier.weight(1f).height(32.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) {
                    Text("Loan", style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(
                    onClick = onConvertToInsurance,
                    modifier = Modifier.weight(1f).height(32.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) {
                    Text("Insurance", style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(
                    onClick = onConvertToSubscription,
                    modifier = Modifier.weight(1f).height(32.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) {
                    Text("Subscription", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
fun PlansEmptyState(onAddPlan: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.DateRange,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.no_plans_message),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedButton(onClick = onAddPlan) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.add_plan))
        }
    }
}

@Composable
fun TemplatesEmptyState(onAddTemplate: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Bolt,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.no_templates_message),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedButton(onClick = onAddTemplate) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.add_template))
        }
    }
}
