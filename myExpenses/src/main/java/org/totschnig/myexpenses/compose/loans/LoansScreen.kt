package org.totschnig.myexpenses.compose.loans

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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
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
    onClose: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val allLoans by viewModel.allLoans.collectAsState()
    val activeLoans by viewModel.activeLoans.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val creditCardDetails by viewModel.creditCardDetails.collectAsState()

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
                title = { Text(stringResource(R.string.loans_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                FloatingActionButton(
                    onClick = { showAddLoanSheet = true }
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_loan))
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
    var selectedAccountId by remember { mutableStateOf<Long?>(null) }
    var expandedAccountDropdown by remember { mutableStateOf(false) }

    val principal = principalText.toLongOrNull()?.times(100L) ?: 0L
    val interestRate = interestRateText.toDoubleOrNull() ?: 0.0
    val tenureMonths = tenureMonthsText.toIntOrNull() ?: 0
    val dueDay = dueDayOfMonthText.toIntOrNull() ?: 5

    val previewEmi = remember(loanType, principal, interestRate, tenureMonths) {
        if (principal > 0 && tenureMonths > 0) {
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
                    }
                }
            }

            // Submit Button
            val canSave = label.isNotBlank() && principal > 0 && tenureMonths > 0
            Button(
                onClick = {
                    viewModel.createLoan(
                        label = label,
                        lenderName = lenderName,
                        loanType = loanType,
                        principalAmount = principal,
                        interestRatePercent = interestRate,
                        tenureMonths = tenureMonths,
                        startDate = System.currentTimeMillis(),
                        dueDayOfMonth = dueDay,
                        linkedAccountId = selectedAccountId
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
