package org.totschnig.myexpenses.compose.commitments

import android.net.Uri
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
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.input.KeyboardType
import org.totschnig.myexpenses.db2.CreditCardDueInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.totschnig.myexpenses.domain.commitment.CommitmentItem
import org.totschnig.myexpenses.domain.commitment.CommitmentType
import org.totschnig.myexpenses.domain.commitment.CommitmentsSummary
import org.totschnig.myexpenses.domain.commitment.PaidCommitmentInfo
import org.totschnig.myexpenses.domain.commitment.MeterCycleStatus
import org.totschnig.myexpenses.domain.commitment.MobilePlanTracker
import org.totschnig.myexpenses.domain.commitment.UtilityMeter
import org.totschnig.myexpenses.domain.loan.InstallmentStatus
import org.totschnig.myexpenses.viewmodel.LoanViewModel
import org.totschnig.myexpenses.viewmodel.data.AccountMinimal
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

sealed class PayingTarget {
    data class LoanInstallment(val loanItem: CommitmentItem.LoanItem) : PayingTarget()
    data class Insurance(val insuranceItem: CommitmentItem.InsuranceItem) : PayingTarget()
    data class Subscription(val subItem: CommitmentItem.SubscriptionItem) : PayingTarget()
    data class Meter(val meterStatus: MeterCycleStatus, val parentSub: CommitmentItem.SubscriptionItem) : PayingTarget()
}

@Composable
fun CommitmentsThisMonthView(
    viewModel: LoanViewModel,
    modifier: Modifier = Modifier,
    onNavigateToTab: (Int) -> Unit = {}
) {
    val summary by viewModel.commitmentsSummary.collectAsState()
    val commitmentItems by viewModel.commitmentItems.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val paidPaymentsThisMonth by viewModel.paidPaymentsThisMonth.collectAsState()
    val creditCardDues by viewModel.creditCardDues.collectAsState()
    val mobilePlanTracker by viewModel.mobilePlanTracker.collectAsState()

    var activePayingTarget by remember { mutableStateOf<PayingTarget?>(null) }
    var settlingCreditCardDue by remember { mutableStateOf<CreditCardDueInfo?>(null) }

    val now = remember { LocalDate.now() }
    val currentMonthName = remember { now.format(DateTimeFormatter.ofPattern("MMMM yyyy")) }
    val zoneId = remember { ZoneId.systemDefault() }
    val endOfMonthMillis = remember {
        YearMonth.from(now).atEndOfMonth().atTime(23, 59, 59).atZone(zoneId).toInstant().toEpochMilli()
    }

    // Filter pending items due on or before end of current month
    val pendingLoans = commitmentItems.filterIsInstance<CommitmentItem.LoanItem>()
        .filter { it.nextDueInstallment != null && it.nextDueInstallment.dueDate <= endOfMonthMillis && it.nextDueInstallment.status == InstallmentStatus.PENDING }

    val pendingInsurance = commitmentItems.filterIsInstance<CommitmentItem.InsuranceItem>()
        .filter { it.nextPayment != null && it.nextPayment.dueDate <= endOfMonthMillis }

    val pendingRegularSubs = commitmentItems.filterIsInstance<CommitmentItem.SubscriptionItem>()
        .filter { !it.subscription.isVariableAmount && it.nextPayment != null && it.nextPayment.dueDate <= endOfMonthMillis }

    val pendingMeterItems = commitmentItems.filterIsInstance<CommitmentItem.SubscriptionItem>()
        .filter { it.subscription.isVariableAmount }
        .flatMap { subItem ->
            subItem.cycleMeterStatus.filter { !it.isPaid }.map { meterStatus ->
                Pair(meterStatus, subItem)
            }
        }

    val totalPendingCount = pendingLoans.size + pendingInsurance.size + pendingRegularSubs.size + pendingMeterItems.size

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
            ThisMonthSummaryCard(
                monthName = currentMonthName,
                summary = summary
            )
        }

        mobilePlanTracker?.let { tracker ->
            item {
                MobilePlanTrackerCard(tracker = tracker)
            }
        }

        // Section: Upcoming / Due This Month
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Due This Month ($totalPendingCount)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (totalPendingCount == 0) {
                    Text(
                        text = "All clear! 🎉",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF2E7D32)
                    )
                }
            }
        }

        if (totalPendingCount == 0) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "All commitments paid for $currentMonthName",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "No pending EMIs, utility bills, or premiums due.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            // Pending Loans
            items(pendingLoans) { loanItem ->
                DueLoanCard(
                    loanItem = loanItem,
                    onPay = { activePayingTarget = PayingTarget.LoanInstallment(loanItem) },
                    onViewDetails = { onNavigateToTab(1) }
                )
            }

            // Pending Insurance
            items(pendingInsurance) { insItem ->
                DueInsuranceCard(
                    insItem = insItem,
                    onPay = { activePayingTarget = PayingTarget.Insurance(insItem) },
                    onViewDetails = { onNavigateToTab(3) }
                )
            }

            // Pending Utility Meters
            items(pendingMeterItems) { (meterStatus, parentSub) ->
                DueMeterCard(
                    meterStatus = meterStatus,
                    parentSub = parentSub,
                    onPay = { activePayingTarget = PayingTarget.Meter(meterStatus, parentSub) },
                    onViewDetails = { onNavigateToTab(2) }
                )
            }

            // Pending Subscriptions
            items(pendingRegularSubs) { subItem ->
                DueSubscriptionCard(
                    subItem = subItem,
                    onPay = { activePayingTarget = PayingTarget.Subscription(subItem) },
                    onViewDetails = { onNavigateToTab(4) },
                    onCancelSub = {
                        viewModel.cancelSubscription(subItem.subscription.id) {
                            viewModel.resyncAllCommitments()
                        }
                    }
                )
            }
        }

        // Section: Credit Card Dues (Dynamic)
        if (creditCardDues.isNotEmpty()) {
            item {
                CreditCardDuesSection(
                    dues = creditCardDues,
                    onSettle = { settlingCreditCardDue = it }
                )
            }
        }

        // Section: Already Paid This Month
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Paid This Month (${paidPaymentsThisMonth.size.coerceAtLeast(summary.paidThisMonthCount)})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = summary.paidThisMonthTotal.toRupeeString(),
                    style = MaterialTheme.typography.titleSmall,
                    color = Color(0xFF2E7D32),
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (paidPaymentsThisMonth.isEmpty()) {
            item {
                Text(
                    text = "No payments recorded yet for $currentMonthName",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        } else {
            items(paidPaymentsThisMonth) { item ->
                PaidItemCard(
                    title = item.title,
                    subtitle = item.subtitle,
                    amount = item.amount,
                    paidDate = item.paidDate,
                    payer = item.payer,
                    icon = getCommitmentPaidIcon(item)
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Active Pay Dialog Handler
    activePayingTarget?.let { target ->
        when (target) {
            is PayingTarget.LoanInstallment -> {
                val inst = target.loanItem.nextDueInstallment
                if (inst != null) {
                    PayCommitmentDialog(
                        title = "${target.loanItem.title} - EMI #${inst.installmentNumber}",
                        initialAmountPaise = inst.totalAmount,
                        linkedAccountId = target.loanItem.linkedAccountId,
                        accounts = accounts,
                        isInsurance = false,
                        onDismiss = { activePayingTarget = null },
                        onConfirm = { amountPaise, accountId, _, _, uri ->
                            viewModel.markInstallmentPaid(
                                installmentId = inst.id,
                                createTransaction = accountId != null,
                                paidDate = System.currentTimeMillis(),
                                attachmentUri = uri,
                                onComplete = {
                                    activePayingTarget = null
                                    viewModel.resyncAllCommitments()
                                }
                            )
                        }
                    )
                }
            }
            is PayingTarget.Insurance -> {
                val p = target.insuranceItem.nextPayment
                if (p != null) {
                    PayCommitmentDialog(
                        title = target.insuranceItem.title,
                        initialAmountPaise = p.amount,
                        linkedAccountId = target.insuranceItem.linkedAccountId,
                        accounts = accounts,
                        isInsurance = true,
                        onDismiss = { activePayingTarget = null },
                        onConfirm = { amountPaise, accountId, payer, utr, uri ->
                            viewModel.payCommitment(
                                paymentId = p.id,
                                paidDate = System.currentTimeMillis(),
                                createTransaction = payer == "SELF" && accountId != null,
                                payer = payer,
                                notes = utr,
                                attachmentUri = uri,
                                onComplete = {
                                    activePayingTarget = null
                                    viewModel.resyncAllCommitments()
                                }
                            )
                        }
                    )
                }
            }
            is PayingTarget.Subscription -> {
                val p = target.subItem.nextPayment
                if (p != null) {
                    PayCommitmentDialog(
                        title = target.subItem.title,
                        initialAmountPaise = p.amount,
                        linkedAccountId = target.subItem.linkedAccountId,
                        accounts = accounts,
                        isInsurance = false,
                        onDismiss = { activePayingTarget = null },
                        onConfirm = { amountPaise, accountId, _, utr, uri ->
                            viewModel.payCommitment(
                                paymentId = p.id,
                                paidDate = System.currentTimeMillis(),
                                createTransaction = accountId != null,
                                payer = "SELF",
                                notes = utr,
                                attachmentUri = uri,
                                onComplete = {
                                    activePayingTarget = null
                                    viewModel.resyncAllCommitments()
                                }
                            )
                        }
                    )
                }
            }
            is PayingTarget.Meter -> {
                val meter = target.meterStatus.meter
                val sub = target.parentSub.subscription
                PayCommitmentDialog(
                    title = "${sub.serviceName} • ${meter.meterLabel}",
                    initialAmountPaise = target.meterStatus.cycleCalculatedAmount,
                    linkedAccountId = sub.linkedAccountId,
                    accounts = accounts,
                    isInsurance = false,
                    onDismiss = { activePayingTarget = null },
                    onConfirm = { amountPaise, accountId, _, _, uri ->
                        viewModel.payMeter(
                            meter = meter,
                            subscription = sub,
                            fromAccountId = accountId,
                            amount = amountPaise,
                            paidDate = System.currentTimeMillis(),
                            attachmentUri = uri,
                            onComplete = {
                                activePayingTarget = null
                                viewModel.resyncAllCommitments()
                            }
                        )
                    }
                )
            }
        }
    }

    settlingCreditCardDue?.let { due ->
        SettleCreditCardDueDialog(
            due = due,
            accounts = accounts,
            onDismiss = { settlingCreditCardDue = null },
            onConfirm = { fromAccountId, amount ->
                viewModel.settleCreditCardDue(
                    creditCardAccountId = due.accountId,
                    fromAccountId = fromAccountId,
                    amount = amount
                ) {
                    settlingCreditCardDue = null
                }
            }
        )
    }
}

@Composable
fun ThisMonthSummaryCard(
    monthName: String,
    summary: CommitmentsSummary
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = monthName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "Total Monthly Outflow",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = summary.totalMonthlyObligation.toRupeeString(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Pending card
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "Remaining Due",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = summary.upcomingTotalThisMonth.toRupeeString(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "${summary.upcomingCountThisMonth} items",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Paid card
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "Already Paid",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF2E7D32)
                        )
                        Text(
                            text = summary.paidThisMonthTotal.toRupeeString(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2E7D32)
                        )
                        Text(
                            text = "${summary.paidThisMonthCount} items",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DueLoanCard(
    loanItem: CommitmentItem.LoanItem,
    onPay: () -> Unit,
    onViewDetails: () -> Unit
) {
    val inst = loanItem.nextDueInstallment ?: return
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onViewDetails() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color(0xFFE3F2FD), shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.AccountBalance,
                        contentDescription = null,
                        tint = Color(0xFF1565C0),
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = loanItem.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "EMI #${inst.installmentNumber} of ${loanItem.progress.totalInstallmentsCount} • ${loanItem.subtitle}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    StatusBadge(isPaid = false, dueDateMillis = inst.dueDate)
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = inst.totalAmount.toRupeeString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Button(
                    onClick = onPay,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    contentPadding = ButtonDefaults.ContentPadding,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Text("Pay EMI", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun DueInsuranceCard(
    insItem: CommitmentItem.InsuranceItem,
    onPay: () -> Unit,
    onViewDetails: () -> Unit
) {
    val payment = insItem.nextPayment ?: return
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onViewDetails() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color(0xFFEDE7F6), shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Security,
                        contentDescription = null,
                        tint = Color(0xFF5E35B1),
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = insItem.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = insItem.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    StatusBadge(isPaid = false, dueDateMillis = payment.dueDate)
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = payment.amount.toRupeeString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Button(
                    onClick = onPay,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5E35B1)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Text("Pay Premium", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun DueMeterCard(
    meterStatus: MeterCycleStatus,
    parentSub: CommitmentItem.SubscriptionItem,
    onPay: () -> Unit,
    onViewDetails: () -> Unit
) {
    val meter = meterStatus.meter
    val icon = getUtilityIcon(parentSub.title, meter.meterLabel)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onViewDetails() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color(0xFFFFF3E0), shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = Color(0xFFE65100),
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = "${parentSub.title} • ${meter.meterLabel}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "No. ${meter.meterNumber}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    StatusBadge(isPaid = false, dueDateMillis = parentSub.nextDueDate)
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = meterStatus.cycleCalculatedAmount.toRupeeString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Button(
                    onClick = onPay,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Text("Pay Bill", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun DueSubscriptionCard(
    subItem: CommitmentItem.SubscriptionItem,
    onPay: () -> Unit,
    onViewDetails: () -> Unit,
    onCancelSub: () -> Unit = {}
) {
    val payment = subItem.nextPayment ?: return
    val isPromotional = subItem.subscription.cancelBeforeRenewal

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onViewDetails() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color(0xFFE0F7FA), shape = CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Subscriptions,
                            contentDescription = null,
                            tint = Color(0xFF00838F),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = subItem.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = subItem.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        StatusBadge(isPaid = false, dueDateMillis = payment.dueDate)
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = payment.amount.toRupeeString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Button(
                        onClick = onPay,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00838F)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text("Pay / Renew", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Promotional Cancellation Banner
            if (isPromotional) {
                Surface(
                    shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp),
                    color = Color(0xFFFFF3E0),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFE65100),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Promotional offer ending! Cancel before ${formatCommitmentDate(payment.dueDate)} to avoid charge.",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE65100)
                            )
                        }
                        TextButton(
                            onClick = onCancelSub,
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("Cancel Sub", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD32F2F))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PaidItemCard(
    title: String,
    subtitle: String,
    amount: Long,
    paidDate: Long?,
    payer: String,
    icon: ImageVector
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = Color(0xFF2E7D32),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "$subtitle • Paid ${formatCommitmentDate(paidDate ?: 0L, short = true)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = amount.toRupeeString(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2E7D32)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    StatusBadge(isPaid = true)
                    if (payer != "SELF") {
                        PayerBadge(payer = payer)
                    }
                }
            }
        }
    }
}

fun getUtilityIcon(serviceName: String, label: String): ImageVector {
    val lower = "$serviceName $label".lowercase()
    return when {
        lower.contains("tneb") || lower.contains("electricity") || lower.contains("phase") -> Icons.Default.Bolt
        lower.contains("milk") || lower.contains("aavin") -> Icons.Default.LocalDrink
        lower.contains("gas") || lower.contains("bpcl") || lower.contains("lpg") -> Icons.Default.LocalGasStation
        lower.contains("hathway") || lower.contains("internet") || lower.contains("wifi") -> Icons.Default.Wifi
        else -> Icons.Default.Payment
    }
}

fun getCommitmentPaidIcon(item: PaidCommitmentInfo): ImageVector {
    if (item.iconKey == "mobile") return Icons.Default.PhoneAndroid
    if (item.iconKey == "grocery") return Icons.Default.ShoppingCart
    return when (item.commitmentType) {
        CommitmentType.LOAN -> Icons.Default.AccountBalance
        CommitmentType.INSURANCE -> Icons.Default.Security
        CommitmentType.UTILITY -> getUtilityIcon(item.title, item.subtitle)
        CommitmentType.SUBSCRIPTION -> {
            val lower = "${item.title} ${item.subtitle}".lowercase()
            if (lower.contains("hathway") || lower.contains("internet") || lower.contains("wifi")) {
                Icons.Default.Wifi
            } else {
                Icons.Default.Subscriptions
            }
        }
        else -> Icons.Default.Payment
    }
}

@Composable
fun MobilePlanTrackerCard(
    tracker: MobilePlanTracker,
    modifier: Modifier = Modifier
) {
    val expiryDateFormatted = remember(tracker.expiryDate) {
        java.time.Instant.ofEpochMilli(tracker.expiryDate)
            .atZone(java.time.ZoneId.systemDefault())
            .format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy"))
    }
    val rechargeDateFormatted = remember(tracker.lastRechargeDate) {
        java.time.Instant.ofEpochMilli(tracker.lastRechargeDate)
            .atZone(java.time.ZoneId.systemDefault())
            .format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy"))
    }

    val (badgeText, badgeBgColor, badgeTextColor) = when {
        tracker.daysRemaining > 7 -> Triple(
            "🟢 ${tracker.daysRemaining} days left",
            Color(0xFFE8F5E9),
            Color(0xFF2E7D32)
        )
        tracker.daysRemaining in 0..7 -> Triple(
            "🟡 ${tracker.daysRemaining} days left",
            Color(0xFFFFF8E1),
            Color(0xFFF57F17)
        )
        else -> Triple(
            "⚪ On-Demand",
            Color(0xFFF5F5F5),
            Color(0xFF757575)
        )
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF0078D7).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhoneAndroid,
                            contentDescription = "Mobile Plan",
                            tint = Color(0xFF0078D7),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Mobile & Data (${tracker.operator})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Base Recharge: ${tracker.lastRechargeAmount.toRupeeString()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = badgeBgColor
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = badgeTextColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

            Text(
                text = "Recharged $rechargeDateFormatted • Valid till $expiryDateFormatted (${tracker.validityDays} days)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (tracker.addonsCountThisMonth > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Data Add-ons (This Month: ${tracker.addonsCountThisMonth} pack${if (tracker.addonsCountThisMonth > 1) "s" else ""})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = tracker.addonsTotalThisMonth.toRupeeString(),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Total Mobile Spend (This Month)",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = tracker.totalSpendThisMonth.toRupeeString(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2E7D32)
                )
            }
        }
    }
}

@Composable
fun CreditCardDuesSection(
    dues: List<CreditCardDueInfo>,
    onSettle: (CreditCardDueInfo) -> Unit
) {
    val totalDue = dues.sumOf { it.outstandingAmountPaise }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0xFFEDE7F6), shape = CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.CreditCard,
                            contentDescription = null,
                            tint = Color(0xFF512DA8),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Credit Card Dues (${dues.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Current unsettled spends to clear",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Text(
                    text = totalDue.toRupeeString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            dues.forEach { due ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = due.accountLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Outstanding: ${due.outstandingAmountPaise.toRupeeString()}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        Button(
                            onClick = { onSettle(due) },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF512DA8)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("Settle Bill", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettleCreditCardDueDialog(
    due: CreditCardDueInfo,
    accounts: List<AccountMinimal>,
    onDismiss: () -> Unit,
    onConfirm: (fromAccountId: Long, amountPaise: Long) -> Unit
) {
    val bankAccounts = accounts.filter { it.id != due.accountId }
    var selectedPayingAccountId by remember {
        mutableStateOf(bankAccounts.firstOrNull()?.id ?: 0L)
    }
    var amountText by remember {
        mutableStateOf((due.outstandingAmountPaise / 100.0).toString())
    }
    var expandedDropdown by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settle Credit Card Bill", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Settling: ${due.accountLabel}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Total Outstanding: ${due.outstandingAmountPaise.toRupeeString()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold
                )

                // Paying Bank Account Dropdown
                ExposedDropdownMenuBox(
                    expanded = expandedDropdown,
                    onExpandedChange = { expandedDropdown = !expandedDropdown }
                ) {
                    val currentAcc = bankAccounts.find { it.id == selectedPayingAccountId }
                    OutlinedTextField(
                        readOnly = true,
                        value = currentAcc?.label ?: "Select paying account",
                        onValueChange = {},
                        label = { Text("Pay From (Bank Account)") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedDropdown) },
                        modifier = Modifier
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expandedDropdown,
                        onDismissRequest = { expandedDropdown = false }
                    ) {
                        bankAccounts.forEach { acc ->
                            DropdownMenuItem(
                                text = { Text(acc.label) },
                                onClick = {
                                    selectedPayingAccountId = acc.id
                                    expandedDropdown = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Payment Amount (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val a = ((amountText.toDoubleOrNull() ?: 0.0) * 100).toLong()
                    if (a > 0 && selectedPayingAccountId > 0) {
                        onConfirm(selectedPayingAccountId, a)
                    }
                },
                enabled = (amountText.toDoubleOrNull() ?: 0.0) > 0.0 && selectedPayingAccountId > 0
            ) {
                Text("Confirm Transfer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

