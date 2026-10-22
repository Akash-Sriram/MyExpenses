package org.totschnig.myexpenses.compose.commitments

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.totschnig.myexpenses.domain.commitment.CommitmentItem
import org.totschnig.myexpenses.domain.loan.EmiInstallment
import org.totschnig.myexpenses.domain.loan.InstallmentStatus
import org.totschnig.myexpenses.domain.loan.Loan
import org.totschnig.myexpenses.domain.loan.LoanProgress
import org.totschnig.myexpenses.domain.loan.LoanStatus
import org.totschnig.myexpenses.domain.loan.LoanType
import org.totschnig.myexpenses.viewmodel.LoanViewModel
import org.totschnig.myexpenses.viewmodel.data.AccountMinimal

@Composable
fun CommitmentEmisView(
    viewModel: LoanViewModel,
    modifier: Modifier = Modifier
) {
    val commitmentItems by viewModel.commitmentItems.collectAsState()
    val allLoans by viewModel.allLoans.collectAsState()
    val accounts by viewModel.accounts.collectAsState()

    val loanItems = commitmentItems.filterIsInstance<CommitmentItem.LoanItem>()
    val activeLoans = loanItems.filter { it.loan.status == LoanStatus.ACTIVE }
    val closedLoans = allLoans.filter { it.status == LoanStatus.CLOSED }

    var expandedLoanIdForSchedule by remember { mutableStateOf<Long?>(null) }
    var documentLoanTarget by remember { mutableStateOf<Loan?>(null) }
    var payingInstallmentTarget by remember { mutableStateOf<Pair<Loan, EmiInstallment>?>(null) }
    var showAddLoanDialog by remember { mutableStateOf(false) }

    val selectedLoanWithInstallments by viewModel.selectedLoanWithInstallments.collectAsState()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
            ActiveLoansSummaryHeader(activeLoans = activeLoans)
        }

        // Active Loans
        if (activeLoans.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.AccountBalance,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No active loans or EMIs", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Tap 'Add Loan' to create a new loan with auto amortization",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(activeLoans) { loanItem ->
                val isScheduleOpen = expandedLoanIdForSchedule == loanItem.loan.id
                val installments = if (isScheduleOpen && selectedLoanWithInstallments?.first?.id == loanItem.loan.id) {
                    selectedLoanWithInstallments?.second ?: emptyList()
                } else emptyList()

                ActiveLoanCard(
                    loanItem = loanItem,
                    accounts = accounts,
                    isScheduleOpen = isScheduleOpen,
                    installments = installments,
                    onToggleSchedule = {
                        if (isScheduleOpen) {
                            expandedLoanIdForSchedule = null
                            viewModel.selectLoan(null)
                        } else {
                            expandedLoanIdForSchedule = loanItem.loan.id
                            viewModel.selectLoan(loanItem.loan.id)
                        }
                    },
                    onOpenDocuments = { documentLoanTarget = loanItem.loan },
                    onPayNextEmi = {
                        loanItem.nextDueInstallment?.let { inst ->
                            payingInstallmentTarget = Pair(loanItem.loan, inst)
                        }
                    },
                    onPaySpecificInstallment = { inst ->
                        payingInstallmentTarget = Pair(loanItem.loan, inst)
                    }
                )
            }
        }

        // Closed Loans Section
        if (closedLoans.isNotEmpty()) {
            item {
                Text(
                    text = "Completed & Closed Loans (${closedLoans.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }

            items(closedLoans) { closedLoan ->
                ClosedLoanCard(loan = closedLoan)
            }
        }

        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Source Documents Dialog
    documentLoanTarget?.let { loan ->
        CommitmentDocumentsDialog(
            type = "LOAN",
            commitmentId = loan.id,
            title = "${loan.label} (${loan.lenderName})",
            viewModel = viewModel,
            onDismiss = { documentLoanTarget = null }
        )
    }

    // Pay EMI Installment Dialog
    payingInstallmentTarget?.let { (loan, inst) ->
        PayCommitmentDialog(
            title = "${loan.label} • EMI #${inst.installmentNumber}",
            initialAmountPaise = inst.totalAmount,
            linkedAccountId = loan.linkedAccountId,
            accounts = accounts,
            isInsurance = false,
            onDismiss = { payingInstallmentTarget = null },
            onConfirm = { amountPaise, accountId, _, _, uri ->
                viewModel.markInstallmentPaid(
                    installmentId = inst.id,
                    createTransaction = accountId != null,
                    paidDate = System.currentTimeMillis(),
                    attachmentUri = uri,
                    accountId = accountId,
                    onComplete = {
                        payingInstallmentTarget = null
                        viewModel.resyncAllCommitments()
                    }
                )
            }
        )
    }

    // Add Loan Dialog
    if (showAddLoanDialog) {
        AddLoanDialog(
            accounts = accounts,
            onDismiss = { showAddLoanDialog = false },
            onConfirm = { label, lender, type, principal, rate, tenure, startDate, dueDay, accId ->
                viewModel.createLoan(
                    label = label,
                    lenderName = lender,
                    loanType = type,
                    principalAmount = principal,
                    interestRatePercent = rate,
                    tenureMonths = tenure,
                    startDate = startDate,
                    dueDayOfMonth = dueDay,
                    linkedAccountId = accId,
                    onSuccess = {
                        showAddLoanDialog = false
                        viewModel.resyncAllCommitments()
                    }
                )
            }
        )
    }
}

@Composable
fun ActiveLoansSummaryHeader(activeLoans: List<CommitmentItem.LoanItem>) {
    val totalMonthlyEmi = activeLoans.sumOf { it.loan.monthlyEmiAmount }
    val totalRemainingPrincipal = activeLoans.sumOf { it.progress.remainingPrincipal }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Text(
                text = "Loans & Active EMIs",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = "Total Monthly EMI",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    Text(
                        text = totalMonthlyEmi.toRupeeString(),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Remaining Outstanding",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    Text(
                        text = totalRemainingPrincipal.toRupeeString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

@Composable
fun ActiveLoanCard(
    loanItem: CommitmentItem.LoanItem,
    accounts: List<AccountMinimal>,
    isScheduleOpen: Boolean,
    installments: List<EmiInstallment>,
    onToggleSchedule: () -> Unit,
    onOpenDocuments: () -> Unit,
    onPayNextEmi: () -> Unit,
    onPaySpecificInstallment: (EmiInstallment) -> Unit
) {
    val loan = loanItem.loan
    val progress = loanItem.progress
    val nextInst = loanItem.nextDueInstallment
    val linkedAccount = accounts.find { it.id == loan.linkedAccountId }

    val progressFraction = if (progress.totalInstallmentsCount > 0) {
        progress.paidInstallmentsCount.toFloat() / progress.totalInstallmentsCount.toFloat()
    } else 0f
    val percentText = "%.1f%%".format(progressFraction * 100f)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = loan.label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${loan.lenderName} • ${loan.loanType.name.replace('_', ' ')}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (linkedAccount != null) {
                        Text(
                            text = "Debited from: ${linkedAccount.label}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = loan.monthlyEmiAmount.toRupeeString(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "/ month",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Progress Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${progress.paidInstallmentsCount} of ${progress.totalInstallmentsCount} EMIs paid ($percentText)",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
                val remainingCount = (progress.totalInstallmentsCount - progress.paidInstallmentsCount).coerceAtLeast(0)
                Text(
                    text = "$remainingCount remaining",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            LinearProgressIndicator(
                progress = { progressFraction.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = Color(0xFF2E7D32),
                trackColor = Color(0xFFE0E0E0)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Amount summary
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Principal", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(loan.principalAmount.toRupeeString(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Paid So Far", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(progress.totalPrincipalPaid.toRupeeString(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Remaining Balance", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(progress.remainingPrincipal.toRupeeString(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(12.dp))

            // Actions Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onOpenDocuments,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Documents", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = onToggleSchedule,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(
                            if (isScheduleOpen) Icons.Default.ExpandLess else Icons.Default.Schedule,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isScheduleOpen) "Hide Schedule" else "Schedule", fontSize = 12.sp)
                    }
                }

                if (nextInst != null) {
                    Button(
                        onClick = onPayNextEmi,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text("Pay Next EMI", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Expandable Amortization Schedule
            AnimatedVisibility(
                visible = isScheduleOpen,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                ) {
                    Text(
                        text = "Amortization Schedule (60 Installments)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    if (installments.isEmpty()) {
                        Text(
                            text = "Loading installments...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            installments.forEach { inst ->
                                InstallmentRow(
                                    installment = inst,
                                    onPay = { onPaySpecificInstallment(inst) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InstallmentRow(
    installment: EmiInstallment,
    onPay: () -> Unit
) {
    val isPaid = installment.status == InstallmentStatus.PAID
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isPaid) Color(0xFFF1F8E9) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(if (isPaid) Color(0xFF2E7D32) else Color.LightGray, shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (isPaid) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                    } else {
                        Text(
                            text = "#${installment.installmentNumber}",
                            color = Color.DarkGray,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column {
                    Text(
                        text = "EMI #${installment.installmentNumber} • ${formatCommitmentDate(installment.dueDate, short = true)}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "P: ${installment.principalPart.toRupeeString()} + I: ${installment.interestPart.toRupeeString()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = installment.totalAmount.toRupeeString(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isPaid) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurface
                )
                if (!isPaid) {
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(
                        onClick = onPay,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text("Pay", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun ClosedLoanCard(loan: Loan) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = loan.label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${loan.lenderName} • ${loan.tenureMonths} Months Completed",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box(
                modifier = Modifier
                    .background(Color(0xFFE8F5E9), shape = RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "✓ Closed",
                    color = Color(0xFF2E7D32),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddLoanDialog(
    accounts: List<AccountMinimal>,
    onDismiss: () -> Unit,
    onConfirm: (
        label: String,
        lender: String,
        type: LoanType,
        principal: Long,
        rate: Double,
        tenure: Int,
        startDate: Long,
        dueDay: Int,
        accountId: Long?
    ) -> Unit
) {
    var label by remember { mutableStateOf("") }
    var lender by remember { mutableStateOf("") }
    var loanType by remember { mutableStateOf(LoanType.REDUCING_BALANCE) }
    var principalText by remember { mutableStateOf("") }
    var rateText by remember { mutableStateOf("10.5") }
    var tenureText by remember { mutableStateOf("60") }
    var dueDayText by remember { mutableStateOf("5") }
    var selectedAccountId by remember { mutableStateOf(accounts.firstOrNull()?.id) }
    var expandedAccountDropdown by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add New Loan / EMI", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Loan Name (e.g. Narmada Marriage Loan)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = lender,
                    onValueChange = { lender = it },
                    label = { Text("Lender / Loan A/C #") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = principalText,
                    onValueChange = { principalText = it },
                    label = { Text("Principal Amount (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = rateText,
                        onValueChange = { rateText = it },
                        label = { Text("Interest Rate (%)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = tenureText,
                        onValueChange = { tenureText = it },
                        label = { Text("Tenure (Months)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = dueDayText,
                    onValueChange = { dueDayText = it },
                    label = { Text("Due Day of Month (1-31)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Debit Account dropdown
                val currentAcc = accounts.find { it.id == selectedAccountId }
                ExposedDropdownMenuBox(
                    expanded = expandedAccountDropdown,
                    onExpandedChange = { expandedAccountDropdown = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = currentAcc?.label ?: "Select Debit Account",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Debit Account") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedAccountDropdown) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expandedAccountDropdown,
                        onDismissRequest = { expandedAccountDropdown = false }
                    ) {
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
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val p = ((principalText.toDoubleOrNull() ?: 0.0) * 100).toLong()
                    val r = rateText.toDoubleOrNull() ?: 0.0
                    val t = tenureText.toIntOrNull() ?: 12
                    val d = dueDayText.toIntOrNull() ?: 5
                    if (label.isNotBlank() && p > 0) {
                        onConfirm(label, lender, loanType, p, r, t, System.currentTimeMillis(), d, selectedAccountId)
                    }
                }
            ) {
                Text("Create Loan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
