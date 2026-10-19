package org.totschnig.myexpenses.compose.loans

import android.content.Intent
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import org.totschnig.myexpenses.activity.ExpenseEdit
import org.totschnig.myexpenses.contract.TransactionsContract.Transactions
import org.totschnig.myexpenses.db2.entities.Template
import org.totschnig.myexpenses.provider.KEY_ROWID
import org.totschnig.myexpenses.provider.KEY_TEMPLATEID
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import org.totschnig.myexpenses.domain.loan.CreditCardDetail
import org.totschnig.myexpenses.domain.loan.EmiCalculator
import org.totschnig.myexpenses.domain.loan.EmiInstallment
import org.totschnig.myexpenses.domain.loan.InstallmentStatus
import org.totschnig.myexpenses.domain.loan.Loan
import org.totschnig.myexpenses.domain.loan.LoanStatus
import org.totschnig.myexpenses.domain.loan.LoanType
import org.totschnig.myexpenses.model.PREDEFINED_NAME_CCARD
import org.totschnig.myexpenses.util.epoch2LocalDate
import org.totschnig.myexpenses.viewmodel.LoanViewModel
import org.totschnig.myexpenses.viewmodel.data.AccountMinimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoansScreen(
    viewModel: LoanViewModel,
    initialTab: Int = 0,
    onClose: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(initialTab.coerceIn(0, 3)) }
    val allLoans by viewModel.allLoans.collectAsState()
    val activeLoans by viewModel.activeLoans.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val creditCardDetails by viewModel.creditCardDetails.collectAsState()
    val templates by viewModel.templates.collectAsState()
    val recurringPlans by viewModel.recurringPlans.collectAsState()

    var showAddLoanSheet by remember { mutableStateOf(false) }
    var loanFilterActiveOnly by remember { mutableStateOf(true) }

    // Dialog state for installment payment confirmation
    var pendingPaymentInstallment by remember { mutableStateOf<EmiInstallment?>(null) }
    var pendingPaymentLinkedAccountId by remember { mutableStateOf<Long?>(null) }

    // Dialog state for credit card statement update
    var editingCcDetail by remember { mutableStateOf<Pair<AccountMinimal, CreditCardDetail?>?>(null) }
    // Dialog state for credit card bill payment
    var settlingCcAccount by remember { mutableStateOf<AccountMinimal?>(null) }

    val displayedLoans = if (loanFilterActiveOnly) activeLoans else allLoans

    val selectedLoanWithInstallments by viewModel.selectedLoanWithInstallments.collectAsState()
    val selectedLoanProgress by viewModel.selectedLoanProgress.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.commitments_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            val context = LocalContext.current
            when (selectedTab) {
                0 -> {
                    FloatingActionButton(
                        onClick = { showAddLoanSheet = true }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_loan))
                    }
                }
                2 -> {
                    FloatingActionButton(
                        onClick = {
                            context.startActivity(Intent(context, ExpenseEdit::class.java).apply {
                                putExtra(Transactions.OPERATION_TYPE, Transactions.TYPE_TRANSACTION)
                                putExtra(ExpenseEdit.KEY_NEW_TEMPLATE, true)
                            })
                        }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_plan))
                    }
                }
                3 -> {
                    FloatingActionButton(
                        onClick = {
                            context.startActivity(Intent(context, ExpenseEdit::class.java).apply {
                                putExtra(Transactions.OPERATION_TYPE, Transactions.TYPE_TRANSACTION)
                                putExtra(ExpenseEdit.KEY_NEW_TEMPLATE, true)
                            })
                        }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_template))
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("${stringResource(R.string.tab_loans)} (${activeLoans.size})") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text(stringResource(R.string.tab_credit_cards)) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("${stringResource(R.string.tab_plans)} (${recurringPlans.size})") }
                )
                Tab(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    text = { Text("${stringResource(R.string.tab_templates)} (${templates.size})") }
                )
            }

            when (selectedTab) {
                0 -> {
                    // Filter Chips Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
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

                1 -> {
                    // Credit Cards Tab
                    val ccAccounts = accounts.filter {
                        it.type?.name == PREDEFINED_NAME_CCARD ||
                                creditCardDetails.any { cd -> cd.accountId == it.id }
                    }

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

                2 -> {
                    val context = LocalContext.current
                    var planToDelete by remember { mutableStateOf<Template?>(null) }

                    if (recurringPlans.isEmpty()) {
                        PlansEmptyState(onAddPlan = {
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
                            items(recurringPlans, key = { it.id }) { plan ->
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
                                    onDeletePlan = {
                                        planToDelete = plan
                                    }
                                )
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
                }

                3 -> {
                    val context = LocalContext.current
                    var templateToDelete by remember { mutableStateOf<Template?>(null) }

                    if (templates.isEmpty()) {
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
                            items(templates, key = { it.id }) { template ->
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
                                    onDelete = {
                                        templateToDelete = template
                                    }
                                )
                            }
                            item {
                                Spacer(modifier = Modifier.height(72.dp))
                            }
                        }
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

    // Modal Sheet: Add Loan
    if (showAddLoanSheet) {
        AddLoanBottomSheet(
            accounts = accounts,
            viewModel = viewModel,
            onDismiss = { showAddLoanSheet = false }
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
            }
        )
    }

    // Dialog: Mark Installment as Paid confirmation
    val installmentToPay = pendingPaymentInstallment
    if (installmentToPay != null) {
        var recordTransaction by remember { mutableStateOf(pendingPaymentLinkedAccountId != null) }

        AlertDialog(
            onDismissRequest = { pendingPaymentInstallment = null },
            title = {
                Text(stringResource(R.string.mark_paid_confirmation, installmentToPay.installmentNumber))
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val currency = viewModel.defaultCurrency
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("EMI Amount:")
                        AmountText(amount = installmentToPay.totalAmount, currency = currency, fontWeight = FontWeight.Bold)
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
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.markInstallmentPaid(
                            installmentId = installmentToPay.id,
                            createTransaction = recordTransaction
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
                    Text(
                        text = "Auto-pay: ${linkedAccount.label}",
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
private fun LoanDetailBottomSheet(
    loan: Loan,
    installments: List<EmiInstallment>,
    progress: org.totschnig.myexpenses.domain.loan.LoanProgress?,
    accounts: List<AccountMinimal>,
    viewModel: LoanViewModel,
    onDismiss: () -> Unit,
    onPayInstallment: (EmiInstallment) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val currency = viewModel.defaultCurrency
    val dateFormatter = LocalDateFormatter.current
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
                Column {
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
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
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
                            val dueDateStr = dateFormatter.format(epoch2LocalDate(installment.dueDate))
                            Text(
                                text = "Due: $dueDateStr",
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
                            } else {
                                Button(
                                    onClick = { onPayInstallment(installment) },
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text("Pay", style = MaterialTheme.typography.labelSmall)
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
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var label by remember { mutableStateOf("") }
    var lenderName by remember { mutableStateOf("") }
    var loanType by remember { mutableStateOf(LoanType.REDUCING_BALANCE) }
    var principalText by remember { mutableStateOf("") }
    var interestRateText by remember { mutableStateOf("10.5") }
    var tenureMonthsText by remember { mutableStateOf("12") }
    var dueDayOfMonthText by remember { mutableStateOf("5") }
    var actualEmiText by remember { mutableStateOf("") }
    var emisAlreadyPaidText by remember { mutableStateOf("0") }
    var selectedAccountId by remember { mutableStateOf<Long?>(null) }
    var expandedAccountDropdown by remember { mutableStateOf(false) }

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
                text = stringResource(R.string.add_loan),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

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

            // Mid-Tenure Onboarding: EMIs Already Paid
            OutlinedTextField(
                value = emisAlreadyPaidText,
                onValueChange = { emisAlreadyPaidText = it.filter { ch -> ch.isDigit() } },
                label = { Text("Number of EMIs already paid (0 for new loan)") },
                placeholder = { Text("e.g. 28 if migrating existing loan") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

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
                        .menuAnchor()
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
                        if (alreadyPaid > 0) {
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

            // Submit Button
            val canSave = label.isNotBlank() && principal > 0 && tenureMonths > 0
            Button(
                onClick = {
                    val actualEmiPaise = actualEmiText.toLongOrNull()?.times(100L)
                    val alreadyPaidCount = emisAlreadyPaidText.toIntOrNull() ?: 0
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
                        emisAlreadyPaid = alreadyPaidCount
                    ) {
                        onDismiss()
                    }
                },
                enabled = canSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
            ) {
                Text(stringResource(R.string.save_loan))
            }
        }
    }
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
                            .menuAnchor()
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
    onDeletePlan: () -> Unit
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
                    Button(onClick = onRunPlan) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Run", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Record")
                    }
                    IconButton(onClick = onEditPlan) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }
                    IconButton(onClick = onDeletePlan) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
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
    onDelete: () -> Unit
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
