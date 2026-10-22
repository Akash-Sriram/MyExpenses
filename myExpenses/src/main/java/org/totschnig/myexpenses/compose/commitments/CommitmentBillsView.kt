package org.totschnig.myexpenses.compose.commitments

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.totschnig.myexpenses.domain.commitment.CommitmentItem
import org.totschnig.myexpenses.domain.commitment.MeterCycleStatus
import org.totschnig.myexpenses.domain.commitment.Subscription
import org.totschnig.myexpenses.domain.commitment.UtilityMeter
import org.totschnig.myexpenses.viewmodel.LoanViewModel
import org.totschnig.myexpenses.viewmodel.data.AccountMinimal
import java.time.LocalDate

@Composable
fun CommitmentBillsView(
    viewModel: LoanViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val commitmentItems by viewModel.commitmentItems.collectAsState()
    val accounts by viewModel.accounts.collectAsState()

    val utilityItems = commitmentItems.filterIsInstance<CommitmentItem.SubscriptionItem>()
        .filter { it.subscription.isVariableAmount }

    var payingMeterTarget by remember { mutableStateOf<Pair<UtilityMeter, Subscription>?>(null) }
    var payingSubTarget by remember { mutableStateOf<CommitmentItem.SubscriptionItem?>(null) }
    var documentTarget by remember { mutableStateOf<Pair<Long, String>?>(null) }

    val totalUtilityEstimated = utilityItems.sumOf { it.amount }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
            BillsSummaryHeader(totalEstimated = totalUtilityEstimated, utilitiesCount = utilityItems.size)
        }

        // TNEB Electricity Section
        val tnebItem = utilityItems.find { it.title.contains("Electricity", ignoreCase = true) || it.title.contains("TNEB", ignoreCase = true) }
        if (tnebItem != null) {
            item {
                TnebSection(
                    item = tnebItem,
                    onCopyConsumerNumber = { num, label ->
                        copyToClipboard(context, num, "$label Consumer No. copied!")
                    },
                    onPayMeter = { meter ->
                        payingMeterTarget = Pair(meter, tnebItem.subscription)
                    },
                    onOpenDocuments = {
                        documentTarget = Pair(tnebItem.subscription.id, "TNEB Electricity Bills & Receipts")
                    }
                )
            }
        }

        // Aavin Milk Card Section
        val milkCardItem = utilityItems.find { it.title.contains("Milk", ignoreCase = true) || it.title.contains("Aavin", ignoreCase = true) }
        if (milkCardItem != null) {
            item {
                AavinMilkCardSection(
                    item = milkCardItem,
                    onCopyCardNumber = { num ->
                        copyToClipboard(context, num, "Milk Card No. copied!")
                    },
                    onPayMeter = { meter ->
                        payingMeterTarget = Pair(meter, milkCardItem.subscription)
                    },
                    onOpenDocuments = {
                        documentTarget = Pair(milkCardItem.subscription.id, "Aavin Milk Card Documents")
                    }
                )
            }
        }

        // Broadband Internet (Hathway)
        val broadbandItem = utilityItems.find { it.title.contains("Hathway", ignoreCase = true) || it.title.contains("Internet", ignoreCase = true) }
        if (broadbandItem != null) {
            val isBroadbandPaid = broadbandItem.isCurrentCyclePaid
            val displayAmount = if (isBroadbandPaid) (broadbandItem.lastPaidPayment?.amount ?: broadbandItem.amount) else broadbandItem.amount
            item {
                SingleUtilityCard(
                    title = "Hathway Broadband",
                    subtitle = "Monthly Home Internet",
                    amount = displayAmount,
                    icon = Icons.Default.Wifi,
                    iconTint = Color(0xFF1565C0),
                    iconBg = Color(0xFFE3F2FD),
                    dueDate = broadbandItem.nextDueDate,
                    isPaid = isBroadbandPaid,
                    consumerNumber = "Account: 0921400512",
                    onCopy = { copyToClipboard(context, "0921400512", "Hathway Account No copied!") },
                    onPay = {
                        payingSubTarget = broadbandItem
                    },
                    onOpenDocuments = {
                        documentTarget = Pair(broadbandItem.subscription.id, "Hathway Invoices")
                    }
                )
            }
        }

        // Bharat Gas (BPCL) LPG
        val gasItem = utilityItems.find { it.title.contains("Gas", ignoreCase = true) || it.title.contains("BPCL", ignoreCase = true) || it.title.contains("Bharat", ignoreCase = true) }
        if (gasItem != null) {
            val isGasPaid = gasItem.cycleMeterStatus.firstOrNull()?.isPaid == true || gasItem.isCurrentCyclePaid
            val paidAmount = gasItem.cycleMeterStatus.firstOrNull()?.paidAmount ?: gasItem.lastPaidPayment?.amount
            val displayAmount = if (isGasPaid && paidAmount != null) paidAmount else gasItem.amount
            item {
                SingleUtilityCard(
                    title = "Bharat Gas (BPCL)",
                    subtitle = "LPG Cylinder Refill",
                    amount = displayAmount,
                    icon = Icons.Default.LocalGasStation,
                    iconTint = Color(0xFFE65100),
                    iconBg = Color(0xFFFFF3E0),
                    dueDate = gasItem.nextDueDate,
                    isPaid = isGasPaid,
                    consumerNumber = "Consumer: 23528914",
                    onCopy = { copyToClipboard(context, "23528914", "Bharat Gas Consumer No copied!") },
                    onPay = {
                        gasItem.meters.firstOrNull()?.let { meter ->
                            payingMeterTarget = Pair(meter, gasItem.subscription)
                        } ?: run {
                            payingSubTarget = gasItem
                        }
                    },
                    onOpenDocuments = {
                        documentTarget = Pair(gasItem.subscription.id, "Bharat Gas Receipts")
                    }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Pay Meter Dialog
    payingMeterTarget?.let { (meter, sub) ->
        PayCommitmentDialog(
            title = "${sub.serviceName} • ${meter.meterLabel}",
            initialAmountPaise = meter.estimatedAmount,
            linkedAccountId = sub.linkedAccountId,
            accounts = accounts,
            isInsurance = false,
            onDismiss = { payingMeterTarget = null },
            onConfirm = { amountPaise, accountId, _, _, uri ->
                viewModel.payMeter(
                    meter = meter,
                    subscription = sub,
                    fromAccountId = accountId,
                    amount = amountPaise,
                    paidDate = System.currentTimeMillis(),
                    attachmentUri = uri,
                    onComplete = {
                        payingMeterTarget = null
                        viewModel.resyncAllCommitments()
                    }
                )
            }
        )
    }

    // Pay Subscription Dialog
    payingSubTarget?.let { subItem ->
        val payment = subItem.nextPayment
        PayCommitmentDialog(
            title = subItem.title,
            initialAmountPaise = payment?.amount ?: subItem.amount,
            linkedAccountId = subItem.linkedAccountId,
            accounts = accounts,
            isInsurance = false,
            onDismiss = { payingSubTarget = null },
            onConfirm = { amountPaise, accountId, _, utr, uri ->
                if (payment != null) {
                    viewModel.payCommitment(
                        paymentId = payment.id,
                        paidDate = System.currentTimeMillis(),
                        createTransaction = accountId != null,
                        payer = "SELF",
                        notes = utr,
                        attachmentUri = uri,
                        onComplete = {
                            payingSubTarget = null
                            viewModel.resyncAllCommitments()
                        }
                    )
                } else {
                    payingSubTarget = null
                }
            }
        )
    }

    // Source Documents Dialog
    documentTarget?.let { (id, title) ->
        CommitmentDocumentsDialog(
            type = "SUBSCRIPTION",
            commitmentId = id,
            title = title,
            viewModel = viewModel,
            onDismiss = { documentTarget = null }
        )
    }
}

@Composable
fun BillsSummaryHeader(totalEstimated: Long, utilitiesCount: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Text(
                text = "Bills & Recurring Utilities",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = "Estimated Monthly Utilities",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                    )
                    Text(
                        text = totalEstimated.toRupeeString(),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }

                Text(
                    text = "$utilitiesCount services active",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }
    }
}

@Composable
fun TnebSection(
    item: CommitmentItem.SubscriptionItem,
    onCopyConsumerNumber: (String, String) -> Unit,
    onPayMeter: (UtilityMeter) -> Unit,
    onOpenDocuments: () -> Unit
) {
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
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(Color(0xFFFFF3E0), shape = CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Bolt, contentDescription = null, tint = Color(0xFFE65100), modifier = Modifier.size(22.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Tamil Nadu Electricity Board (TNEB)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Bi-monthly • Next Deadline: ${formatCommitmentDate(item.nextDueDate)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                IconButton(onClick = onOpenDocuments) {
                    Icon(Icons.Default.Description, contentDescription = "Bills", tint = MaterialTheme.colorScheme.primary)
                }
            }

            // Payment window explanation banner
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
            ) {
                Text(
                    text = "Payment Window: Pay between month-end salary credit & ${formatCommitmentDate(item.nextDueDate, short = true)} deadline.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 4 Meter Tiles
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item.cycleMeterStatus.forEach { meterStatus ->
                    val meter = meterStatus.meter
                    val isPaid = meterStatus.isPaid

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isPaid) Color(0xFFF1F8E9) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = meter.meterLabel,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    if (isPaid) {
                                        StatusBadge(isPaid = true, labelOverride = "✓ Paid")
                                    } else {
                                        StatusBadge(isPaid = false, dueDateMillis = item.nextDueDate)
                                    }
                                }

                                Spacer(modifier = Modifier.height(2.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable {
                                        onCopyConsumerNumber(meter.meterNumber, meter.meterLabel)
                                    }
                                ) {
                                    Text(
                                        text = meter.meterNumber,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        Icons.Default.ContentCopy,
                                        contentDescription = "Copy",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }

                            if (!isPaid) {
                                Button(
                                    onClick = { onPayMeter(meter) },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100)),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text("Pay Bill", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Text(
                                    text = (meterStatus.paidAmount ?: meter.estimatedAmount).toRupeeString(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2E7D32)
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
fun AavinMilkCardSection(
    item: CommitmentItem.SubscriptionItem,
    onCopyCardNumber: (String) -> Unit,
    onPayMeter: (UtilityMeter) -> Unit,
    onOpenDocuments: () -> Unit
) {
    val meterStatus = item.cycleMeterStatus.firstOrNull()
    val meter = meterStatus?.meter ?: return
    val isPaid = meterStatus.isPaid

    val now = remember { LocalDate.now() }
    val daysInMonth = now.lengthOfMonth()
    val calculatedAmount = (daysInMonth * 2 * 1850L) // ₹18.50 * 2 * days

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
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(Color(0xFFE1F5FE), shape = CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.LocalDrink, contentDescription = null, tint = Color(0xFF0288D1), modifier = Modifier.size(22.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Aavin Milk Card",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Monthly Cycle (15th - 14th)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(onClick = onOpenDocuments) {
                    Icon(Icons.Default.Description, contentDescription = "Documents", tint = MaterialTheme.colorScheme.primary)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Card / Consumer # Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onCopyCardNumber(meter.meterNumber) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Milk Card #: ",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = meter.meterNumber,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Formula Card
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFFF0F4F8),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Formula: ₹18.50 / packet × 2 packets / day",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF102A43)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Current Month: $daysInMonth days × ₹37.00 = ${calculatedAmount.toRupeeString()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF334E68)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action & Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusBadge(isPaid = isPaid, dueDateMillis = item.nextDueDate)

                if (!isPaid) {
                    Button(
                        onClick = { onPayMeter(meter) },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0288D1))
                    ) {
                        Text("Pay Milk Card", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Text(
                        text = calculatedAmount.toRupeeString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2E7D32)
                    )
                }
            }
        }
    }
}

@Composable
fun SingleUtilityCard(
    title: String,
    subtitle: String,
    amount: Long,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    iconBg: Color,
    dueDate: Long,
    isPaid: Boolean,
    consumerNumber: String,
    onCopy: () -> Unit,
    onPay: () -> Unit,
    onOpenDocuments: () -> Unit
) {
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(iconBg, shape = CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                IconButton(onClick = onOpenDocuments) {
                    Icon(Icons.Default.Description, contentDescription = "Documents", tint = MaterialTheme.colorScheme.primary)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onCopy() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = consumerNumber,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = amount.toRupeeString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isPaid) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusBadge(isPaid = isPaid, dueDateMillis = dueDate)
                        if (isPaid && dueDate > 0L) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Next: Pay by ${formatCommitmentDate(dueDate, short = true)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (!isPaid) {
                    Button(
                        onClick = onPay,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = iconTint)
                    ) {
                        Text("Pay Bill", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
