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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.totschnig.myexpenses.domain.commitment.BillingCycle
import org.totschnig.myexpenses.domain.commitment.CommitmentItem
import org.totschnig.myexpenses.domain.commitment.CommitmentStatus
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

    val activeUtilities = utilityItems.filter { it.subscription.status == CommitmentStatus.ACTIVE }
    val discontinuedUtilities = utilityItems.filter { it.subscription.status != CommitmentStatus.ACTIVE }

    var payingMeterTarget by remember { mutableStateOf<Pair<UtilityMeter, Subscription>?>(null) }
    var payingSubTarget by remember { mutableStateOf<CommitmentItem.SubscriptionItem?>(null) }
    var documentTarget by remember { mutableStateOf<Pair<Long, String>?>(null) }

    var showAddUtilityDialog by remember { mutableStateOf(false) }
    var editingUtilityTarget by remember { mutableStateOf<Subscription?>(null) }
    var addingMeterToSubId by remember { mutableStateOf<Long?>(null) }
    var editingMeterTarget by remember { mutableStateOf<UtilityMeter?>(null) }
    var isDiscontinuedExpanded by remember { mutableStateOf(false) }

    val totalUtilityEstimated = activeUtilities.sumOf { it.amount }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
            BillsSummaryHeader(
                totalEstimated = totalUtilityEstimated,
                utilitiesCount = activeUtilities.size,
                onAddUtility = { showAddUtilityDialog = true }
            )
        }

        if (activeUtilities.isEmpty() && discontinuedUtilities.isEmpty()) {
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
                            Icons.Default.Receipt,
                            contentDescription = null,
                            tint = Color(0xFFE65100),
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No utilities tracked", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Track electricity, broadband, cooking gas, milk, water, etc.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = { showAddUtilityDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add Utility Bill")
                        }
                    }
                }
            }
        } else {
            // Render Active Utilities
            items(activeUtilities) { item ->
                val sub = item.subscription
                val isMultiMeter = item.meters.size > 1 || sub.serviceName.contains("Electricity", ignoreCase = true) || sub.serviceName.contains("TNEB", ignoreCase = true)
                val isDaily = item.meters.any { it.isDailyConsumption } || sub.serviceName.contains("Milk", ignoreCase = true)

                when {
                    isMultiMeter -> {
                        MultiMeterUtilityCard(
                            item = item,
                            onCopyConsumerNumber = { num, label ->
                                copyToClipboard(context, num, "$label Consumer No. copied!")
                            },
                            onPayMeter = { meter ->
                                payingMeterTarget = Pair(meter, sub)
                            },
                            onOpenDocuments = {
                                documentTarget = Pair(sub.id, "${sub.serviceName} Bills & Receipts")
                            },
                            onAddMeter = {
                                addingMeterToSubId = sub.id
                            },
                            onEditUtility = {
                                editingUtilityTarget = sub
                            },
                            onDiscontinueUtility = {
                                viewModel.cancelSubscription(sub.id)
                            },
                            onDeleteUtility = {
                                viewModel.deleteSubscription(sub.id)
                            },
                            onEditMeter = { meter ->
                                editingMeterTarget = meter
                            },
                            onToggleMeterStatus = { meter, active ->
                                viewModel.toggleMeterActiveStatus(meter.id, active)
                            },
                            onDeleteMeter = { meter ->
                                viewModel.deleteUtilityMeter(meter.id)
                            }
                        )
                    }
                    isDaily -> {
                        DailyConsumptionUtilityCard(
                            item = item,
                            onCopyCardNumber = { num ->
                                copyToClipboard(context, num, "Card No. copied!")
                            },
                            onPayMeter = { meter ->
                                payingMeterTarget = Pair(meter, sub)
                            },
                            onOpenDocuments = {
                                documentTarget = Pair(sub.id, "${sub.serviceName} Documents")
                            },
                            onEditUtility = {
                                editingUtilityTarget = sub
                            },
                            onDiscontinueUtility = {
                                viewModel.cancelSubscription(sub.id)
                            },
                            onDeleteUtility = {
                                viewModel.deleteSubscription(sub.id)
                            },
                            onEditMeter = { meter ->
                                editingMeterTarget = meter
                            }
                        )
                    }
                    else -> {
                        val isPaid = item.isCurrentCyclePaid || item.cycleMeterStatus.firstOrNull()?.isPaid == true
                        val paidAmount = item.cycleMeterStatus.firstOrNull()?.paidAmount ?: item.lastPaidPayment?.amount
                        val displayAmount = if (isPaid && paidAmount != null) paidAmount else item.amount
                        val primaryMeter = item.meters.firstOrNull()
                        val consumerNo = primaryMeter?.meterNumber ?: sub.notes.takeIf { it.isNotBlank() } ?: "—"
                        val (icon, tint, bg) = getUtilityStyle(sub.serviceName)

                        SingleUtilityCard(
                            title = sub.serviceName,
                            subtitle = "${sub.billingCycle.name.lowercase().replaceFirstChar { it.uppercase() }} Bill",
                            amount = displayAmount,
                            icon = icon,
                            iconTint = tint,
                            iconBg = bg,
                            dueDate = item.nextDueDate,
                            isPaid = isPaid,
                            consumerNumber = consumerNo,
                            onCopy = {
                                if (consumerNo != "—") {
                                    copyToClipboard(context, consumerNo, "${sub.serviceName} Account No. copied!")
                                }
                            },
                            onPay = {
                                if (primaryMeter != null) {
                                    payingMeterTarget = Pair(primaryMeter, sub)
                                } else {
                                    payingSubTarget = item
                                }
                            },
                            onOpenDocuments = {
                                documentTarget = Pair(sub.id, "${sub.serviceName} Documents")
                            },
                            onEdit = {
                                editingUtilityTarget = sub
                            },
                            onAddMeter = {
                                addingMeterToSubId = sub.id
                            },
                            onDiscontinue = {
                                viewModel.cancelSubscription(sub.id)
                            },
                            onDelete = {
                                viewModel.deleteSubscription(sub.id)
                            }
                        )
                    }
                }
            }

            // Collapsible Inactive / Discontinued Utilities
            if (discontinuedUtilities.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isDiscontinuedExpanded = !isDiscontinuedExpanded },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Inactive & Discontinued Utilities (${discontinuedUtilities.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Icon(
                                imageVector = if (isDiscontinuedExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (isDiscontinuedExpanded) {
                    items(discontinuedUtilities) { item ->
                        val sub = item.subscription
                        val (icon, tint, bg) = getUtilityStyle(sub.serviceName)
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier.size(36.dp).background(bg.copy(alpha = 0.5f), shape = CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(icon, contentDescription = null, tint = tint.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(text = sub.serviceName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                            Text(text = "DISCONTINUED", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(
                                            onClick = { viewModel.reactivateSubscription(sub.id) },
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            modifier = Modifier.height(32.dp)
                                        ) {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Reactivate", fontSize = 11.sp, color = Color(0xFF2E7D32))
                                        }

                                        IconButton(
                                            onClick = { viewModel.deleteSubscription(sub.id) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
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

    // Documents Dialog
    documentTarget?.let { (subId, docTitle) ->
        CommitmentDocumentsDialog(
            type = "SUBSCRIPTION",
            commitmentId = subId,
            title = docTitle,
            viewModel = viewModel,
            onDismiss = { documentTarget = null }
        )
    }

    // Add / Edit Utility Subscription Dialog
    if (showAddUtilityDialog || editingUtilityTarget != null) {
        AddEditUtilitySubscriptionDialog(
            initialSub = editingUtilityTarget,
            accounts = accounts,
            onDismiss = {
                showAddUtilityDialog = false
                editingUtilityTarget = null
            },
            onConfirm = { newSub, initialMeter ->
                if (editingUtilityTarget != null) {
                    viewModel.updateSubscription(newSub) {
                        showAddUtilityDialog = false
                        editingUtilityTarget = null
                        viewModel.resyncAllCommitments()
                    }
                } else {
                    viewModel.createSubscription(newSub) { subId ->
                        if (initialMeter != null && subId > 0) {
                            viewModel.addUtilityMeter(subId, initialMeter.copy(subscriptionId = subId))
                        }
                        showAddUtilityDialog = false
                        editingUtilityTarget = null
                        viewModel.resyncAllCommitments()
                    }
                }
            }
        )
    }

    // Add Meter Dialog
    addingMeterToSubId?.let { subId ->
        AddEditUtilityMeterDialog(
            title = "Add Connection / Meter",
            initialMeter = null,
            onDismiss = { addingMeterToSubId = null },
            onConfirm = { meter ->
                viewModel.addUtilityMeter(subId, meter.copy(subscriptionId = subId)) {
                    addingMeterToSubId = null
                    viewModel.resyncAllCommitments()
                }
            }
        )
    }

    // Edit Meter Dialog
    editingMeterTarget?.let { meter ->
        AddEditUtilityMeterDialog(
            title = "Edit Connection / Meter",
            initialMeter = meter,
            onDismiss = { editingMeterTarget = null },
            onConfirm = { updatedMeter ->
                viewModel.updateUtilityMeter(updatedMeter) {
                    editingMeterTarget = null
                    viewModel.resyncAllCommitments()
                }
            }
        )
    }
}

@Composable
fun BillsSummaryHeader(
    totalEstimated: Long,
    utilitiesCount: Int,
    onAddUtility: () -> Unit = {}
) {
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Bills & Recurring Utilities",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                IconButton(
                    onClick = onAddUtility,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Add Utility",
                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
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
fun MultiMeterUtilityCard(
    item: CommitmentItem.SubscriptionItem,
    onCopyConsumerNumber: (String, String) -> Unit,
    onPayMeter: (UtilityMeter) -> Unit,
    onOpenDocuments: () -> Unit,
    onAddMeter: () -> Unit,
    onEditUtility: () -> Unit,
    onDiscontinueUtility: () -> Unit,
    onDeleteUtility: () -> Unit,
    onEditMeter: (UtilityMeter) -> Unit,
    onToggleMeterStatus: (UtilityMeter, Boolean) -> Unit,
    onDeleteMeter: (UtilityMeter) -> Unit
) {
    val sub = item.subscription
    var showMenu by remember { mutableStateOf(false) }

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
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
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
                            text = sub.serviceName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${sub.billingCycle.name.lowercase().replaceFirstChar { it.uppercase() }} • Next: ${formatCommitmentDate(item.nextDueDate)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onOpenDocuments) {
                        Icon(Icons.Default.Description, contentDescription = "Bills", tint = MaterialTheme.colorScheme.primary)
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Options")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Add Connection / Meter") },
                                onClick = {
                                    showMenu = false
                                    onAddMeter()
                                },
                                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            )
                            DropdownMenuItem(
                                text = { Text("Edit Utility") },
                                onClick = {
                                    showMenu = false
                                    onEditUtility()
                                },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            )
                            DropdownMenuItem(
                                text = { Text("Pause / Discontinue") },
                                onClick = {
                                    showMenu = false
                                    onDiscontinueUtility()
                                },
                                leadingIcon = { Icon(Icons.Default.Close, contentDescription = null, tint = Color(0xFFE65100), modifier = Modifier.size(18.dp)) }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete Utility", color = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showMenu = false
                                    onDeleteUtility()
                                },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp)) }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Meter Tiles
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item.cycleMeterStatus.forEach { meterStatus ->
                    val meter = meterStatus.meter
                    val isPaid = meterStatus.isPaid
                    val isPaused = !meter.isActive
                    var showMeterMenu by remember { mutableStateOf(false) }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = when {
                            isPaused -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                            isPaid -> Color(0xFFF1F8E9)
                            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        },
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
                                        fontWeight = FontWeight.Bold,
                                        color = if (isPaused) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    when {
                                        isPaused -> {
                                            Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFFFFEBEE)) {
                                                Text("PAUSED", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color(0xFFC62828), modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                            }
                                        }
                                        isPaid -> StatusBadge(isPaid = true, labelOverride = "✓ Paid")
                                        else -> StatusBadge(isPaid = false, dueDateMillis = item.nextDueDate)
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
                                        color = if (isPaused) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        Icons.Default.ContentCopy,
                                        contentDescription = "Copy",
                                        tint = if (isPaused) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (!isPaused) {
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

                                Box {
                                    IconButton(
                                        onClick = { showMeterMenu = true },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.MoreVert, contentDescription = "Meter Options", modifier = Modifier.size(16.dp))
                                    }
                                    DropdownMenu(
                                        expanded = showMeterMenu,
                                        onDismissRequest = { showMeterMenu = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Edit Meter") },
                                            onClick = {
                                                showMeterMenu = false
                                                onEditMeter(meter)
                                            },
                                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                        )
                                        if (meter.isActive) {
                                            DropdownMenuItem(
                                                text = { Text("Pause / Discontinue") },
                                                onClick = {
                                                    showMeterMenu = false
                                                    onToggleMeterStatus(meter, false)
                                                },
                                                leadingIcon = { Icon(Icons.Default.Close, contentDescription = null, tint = Color(0xFFE65100), modifier = Modifier.size(16.dp)) }
                                            )
                                        } else {
                                            DropdownMenuItem(
                                                text = { Text("Reactivate Connection") },
                                                onClick = {
                                                    showMeterMenu = false
                                                    onToggleMeterStatus(meter, true)
                                                },
                                                leadingIcon = { Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(16.dp)) }
                                            )
                                        }
                                        DropdownMenuItem(
                                            text = { Text("Delete Meter", color = MaterialTheme.colorScheme.error) },
                                            onClick = {
                                                showMeterMenu = false
                                                onDeleteMeter(meter)
                                            },
                                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp)) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Add meter button tile
                OutlinedButton(
                    onClick = onAddMeter,
                    modifier = Modifier.fillMaxWidth().height(36.dp),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Another Meter / Consumer No.", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun DailyConsumptionUtilityCard(
    item: CommitmentItem.SubscriptionItem,
    onCopyCardNumber: (String) -> Unit,
    onPayMeter: (UtilityMeter) -> Unit,
    onOpenDocuments: () -> Unit,
    onEditUtility: () -> Unit,
    onDiscontinueUtility: () -> Unit,
    onDeleteUtility: () -> Unit,
    onEditMeter: (UtilityMeter) -> Unit
) {
    val sub = item.subscription
    val meterStatus = item.cycleMeterStatus.firstOrNull()
    val meter = meterStatus?.meter ?: return
    val isPaid = meterStatus.isPaid

    val now = remember { LocalDate.now() }
    val daysInMonth = now.lengthOfMonth()
    val calculatedAmount = meter.calculateAmountForDays(daysInMonth)
    var showMenu by remember { mutableStateOf(false) }

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
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
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
                            text = sub.serviceName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Daily Consumption • Monthly Billing",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onOpenDocuments) {
                        Icon(Icons.Default.Description, contentDescription = "Documents", tint = MaterialTheme.colorScheme.primary)
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Options")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Edit Rates & Card No.") },
                                onClick = {
                                    showMenu = false
                                    onEditMeter(meter)
                                },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            )
                            DropdownMenuItem(
                                text = { Text("Edit Utility") },
                                onClick = {
                                    showMenu = false
                                    onEditUtility()
                                },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            )
                            DropdownMenuItem(
                                text = { Text("Pause / Discontinue") },
                                onClick = {
                                    showMenu = false
                                    onDiscontinueUtility()
                                },
                                leadingIcon = { Icon(Icons.Default.Close, contentDescription = null, tint = Color(0xFFE65100), modifier = Modifier.size(18.dp)) }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete Utility", color = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showMenu = false
                                    onDeleteUtility()
                                },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp)) }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Card / Consumer # Row
            if (meter.meterNumber.isNotBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onCopyCardNumber(meter.meterNumber) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Card / Acct #: ",
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
            }

            // Formula Card
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFFF0F4F8),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    val unitPriceFormatted = (meter.unitPrice / 100.0).let { if (it % 1.0 == 0.0) "₹%.0f".format(it) else "₹%.2f".format(it) }
                    Text(
                        text = "Rate: $unitPriceFormatted / unit × ${meter.unitsPerDay} units / day",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF102A43)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Current Month: $daysInMonth days = ${calculatedAmount.toRupeeString()}",
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
                        Text("Pay Bill", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    dueDate: Long,
    isPaid: Boolean,
    consumerNumber: String,
    onCopy: () -> Unit,
    onPay: () -> Unit,
    onOpenDocuments: () -> Unit,
    onEdit: () -> Unit,
    onAddMeter: () -> Unit,
    onDiscontinue: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

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

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onOpenDocuments) {
                        Icon(Icons.Default.Description, contentDescription = "Documents", tint = MaterialTheme.colorScheme.primary)
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Options")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Edit Utility") },
                                onClick = {
                                    showMenu = false
                                    onEdit()
                                },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            )
                            DropdownMenuItem(
                                text = { Text("Add Meter Connection") },
                                onClick = {
                                    showMenu = false
                                    onAddMeter()
                                },
                                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            )
                            DropdownMenuItem(
                                text = { Text("Pause / Discontinue") },
                                onClick = {
                                    showMenu = false
                                    onDiscontinue()
                                },
                                leadingIcon = { Icon(Icons.Default.Close, contentDescription = null, tint = Color(0xFFE65100), modifier = Modifier.size(18.dp)) }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete Utility", color = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showMenu = false
                                    onDelete()
                                },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp)) }
                            )
                        }
                    }
                }
            }

            if (consumerNumber.isNotBlank() && consumerNumber != "—") {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onCopy() },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Consumer / Acct: ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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

fun getUtilityStyle(name: String): Triple<ImageVector, Color, Color> {
    return when {
        name.contains("Internet", ignoreCase = true) || name.contains("Broadband", ignoreCase = true) || name.contains("Wifi", ignoreCase = true) || name.contains("Hathway", ignoreCase = true) || name.contains("Airtel", ignoreCase = true) || name.contains("Jio", ignoreCase = true) ->
            Triple(Icons.Default.Wifi, Color(0xFF1565C0), Color(0xFFE3F2FD))
        name.contains("Gas", ignoreCase = true) || name.contains("LPG", ignoreCase = true) || name.contains("BPCL", ignoreCase = true) || name.contains("Bharat", ignoreCase = true) || name.contains("Indane", ignoreCase = true) || name.contains("HP", ignoreCase = true) ->
            Triple(Icons.Default.LocalGasStation, Color(0xFFE65100), Color(0xFFFFF3E0))
        name.contains("Milk", ignoreCase = true) || name.contains("Aavin", ignoreCase = true) ->
            Triple(Icons.Default.LocalDrink, Color(0xFF0288D1), Color(0xFFE1F5FE))
        name.contains("Electricity", ignoreCase = true) || name.contains("TNEB", ignoreCase = true) || name.contains("Power", ignoreCase = true) ->
            Triple(Icons.Default.Bolt, Color(0xFFF57C00), Color(0xFFFFF3E0))
        else ->
            Triple(Icons.Default.Receipt, Color(0xFF00838F), Color(0xFFE0F7FA))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditUtilitySubscriptionDialog(
    initialSub: Subscription? = null,
    accounts: List<AccountMinimal>,
    onDismiss: () -> Unit,
    onConfirm: (Subscription, UtilityMeter?) -> Unit
) {
    val isEdit = initialSub != null
    var serviceName by remember { mutableStateOf(initialSub?.serviceName ?: "") }
    var billingCycle by remember { mutableStateOf(initialSub?.billingCycle ?: BillingCycle.MONTHLY) }
    var amountText by remember {
        mutableStateOf(if (initialSub != null && initialSub.amount > 0) (initialSub.amount / 100.0).toString() else "")
    }
    var consumerNumber by remember { mutableStateOf(initialSub?.notes ?: "") }
    var selectedAccountId by remember { mutableStateOf(initialSub?.linkedAccountId ?: accounts.firstOrNull()?.id) }
    var isDailyEssential by remember { mutableStateOf(false) }
    var unitPriceText by remember { mutableStateOf("18.50") }
    var unitsPerDayText by remember { mutableStateOf("2.0") }

    var expandedCycle by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEdit) "Edit Utility Service" else "Add Recurring Utility Bill", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = serviceName,
                    onValueChange = { serviceName = it },
                    label = { Text("Provider Name (e.g. BESCOM, Airtel Fiber, Bharat Gas)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                ExposedDropdownMenuBox(
                    expanded = expandedCycle,
                    onExpandedChange = { expandedCycle = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = billingCycle.name.replace('_', '-'),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Billing Cycle") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCycle) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expandedCycle,
                        onDismissRequest = { expandedCycle = false }
                    ) {
                        BillingCycle.entries.forEach { cycle ->
                            DropdownMenuItem(
                                text = { Text(cycle.name.replace('_', '-')) },
                                onClick = {
                                    billingCycle = cycle
                                    expandedCycle = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = consumerNumber,
                    onValueChange = { consumerNumber = it },
                    label = { Text("Consumer / Account / Meter Number") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (!isDailyEssential) {
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = { Text("Estimated Amount per Cycle (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = unitPriceText,
                            onValueChange = { unitPriceText = it },
                            label = { Text("Rate / Unit (₹)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = unitsPerDayText,
                            onValueChange = { unitsPerDayText = it },
                            label = { Text("Units / Day") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth().clickable { isDailyEssential = !isDailyEssential }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isDailyEssential) "✓ Daily essential rate formula enabled" else "+ Enable daily unit consumption formula (e.g. Milk, News)",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val p = ((amountText.toDoubleOrNull() ?: 0.0) * 100).toLong()
                    if (serviceName.isNotBlank()) {
                        val sub = Subscription(
                            id = initialSub?.id ?: 0L,
                            serviceName = serviceName.trim(),
                            billingCycle = billingCycle,
                            amount = p,
                            categoryId = initialSub?.categoryId,
                            startDate = initialSub?.startDate ?: System.currentTimeMillis(),
                            nextRenewalDate = initialSub?.nextRenewalDate ?: (System.currentTimeMillis() + 30L * 86400000L),
                            linkedAccountId = selectedAccountId,
                            status = initialSub?.status ?: CommitmentStatus.ACTIVE,
                            isVariableAmount = true,
                            notes = consumerNumber.trim()
                        )

                        val initialMeter = if (!isEdit && consumerNumber.isNotBlank()) {
                            UtilityMeter(
                                subscriptionId = 0L,
                                meterNumber = consumerNumber.trim(),
                                meterLabel = serviceName.trim(),
                                estimatedAmount = p,
                                unitPrice = if (isDailyEssential) ((unitPriceText.toDoubleOrNull() ?: 0.0) * 100).toLong() else 0L,
                                unitsPerDay = if (isDailyEssential) (unitsPerDayText.toDoubleOrNull() ?: 0.0) else 0.0,
                                isActive = true
                            )
                        } else null

                        onConfirm(sub, initialMeter)
                    }
                }
            ) {
                Text(if (isEdit) "Update Utility" else "Add Utility")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun AddEditUtilityMeterDialog(
    title: String,
    initialMeter: UtilityMeter? = null,
    onDismiss: () -> Unit,
    onConfirm: (UtilityMeter) -> Unit
) {
    val isEdit = initialMeter != null
    var meterLabel by remember { mutableStateOf(initialMeter?.meterLabel ?: "") }
    var meterNumber by remember { mutableStateOf(initialMeter?.meterNumber ?: "") }
    var estimatedAmountText by remember {
        mutableStateOf(if (initialMeter != null && initialMeter.estimatedAmount > 0) (initialMeter.estimatedAmount / 100.0).toString() else "")
    }
    var unitPriceText by remember {
        mutableStateOf(if (initialMeter != null && initialMeter.unitPrice > 0) (initialMeter.unitPrice / 100.0).toString() else "")
    }
    var unitsPerDayText by remember {
        mutableStateOf(if (initialMeter != null && initialMeter.unitsPerDay > 0.0) initialMeter.unitsPerDay.toString() else "")
    }
    var matchingKeywords by remember { mutableStateOf(initialMeter?.matchingKeywords ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = meterLabel,
                    onValueChange = { meterLabel = it },
                    label = { Text("Connection Label (e.g. Single Phase, Ground Floor)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = meterNumber,
                    onValueChange = { meterNumber = it },
                    label = { Text("Consumer / Meter Number (e.g. 092140051234)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = estimatedAmountText,
                    onValueChange = { estimatedAmountText = it },
                    label = { Text("Estimated Bill Amount (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = unitPriceText,
                        onValueChange = { unitPriceText = it },
                        label = { Text("Unit Price (₹) [Opt]") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = unitsPerDayText,
                        onValueChange = { unitsPerDayText = it },
                        label = { Text("Units / Day [Opt]") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = matchingKeywords,
                    onValueChange = { matchingKeywords = it },
                    label = { Text("Matching SMS Keywords (comma-separated)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val p = ((estimatedAmountText.toDoubleOrNull() ?: 0.0) * 100).toLong()
                    val uPrice = ((unitPriceText.toDoubleOrNull() ?: 0.0) * 100).toLong()
                    val uPerDay = unitsPerDayText.toDoubleOrNull() ?: 0.0

                    if (meterLabel.isNotBlank() && meterNumber.isNotBlank()) {
                        onConfirm(
                            UtilityMeter(
                                id = initialMeter?.id ?: 0L,
                                subscriptionId = initialMeter?.subscriptionId ?: 0L,
                                meterNumber = meterNumber.trim(),
                                meterLabel = meterLabel.trim(),
                                estimatedAmount = p,
                                matchingKeywords = matchingKeywords.trim(),
                                createdAt = initialMeter?.createdAt ?: System.currentTimeMillis(),
                                unitPrice = uPrice,
                                unitsPerDay = uPerDay,
                                isActive = initialMeter?.isActive ?: true
                            )
                        )
                    }
                }
            ) {
                Text(if (isEdit) "Update Meter" else "Add Meter")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
