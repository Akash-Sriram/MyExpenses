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
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.totschnig.myexpenses.domain.commitment.BillingCycle
import org.totschnig.myexpenses.domain.commitment.CommitmentItem
import org.totschnig.myexpenses.domain.commitment.CommitmentStatus
import org.totschnig.myexpenses.domain.commitment.Subscription
import org.totschnig.myexpenses.viewmodel.LoanViewModel
import org.totschnig.myexpenses.viewmodel.data.AccountMinimal

@Composable
fun CommitmentSubscriptionsView(
    viewModel: LoanViewModel,
    modifier: Modifier = Modifier
) {
    val commitmentItems by viewModel.commitmentItems.collectAsState()
    val accounts by viewModel.accounts.collectAsState()

    // Filter to purely digital subscriptions (excluding variable-amount utilities)
    val regularSubscriptions = commitmentItems.filterIsInstance<CommitmentItem.SubscriptionItem>()
        .filter { !it.subscription.isVariableAmount }

    var payingSubTarget by remember { mutableStateOf<CommitmentItem.SubscriptionItem?>(null) }
    var documentTarget by remember { mutableStateOf<Subscription?>(null) }
    var showAddSubDialog by remember { mutableStateOf(false) }
    var editingSubTarget by remember { mutableStateOf<Subscription?>(null) }

    val totalAnnualSpend = regularSubscriptions.sumOf { item ->
        when (item.subscription.billingCycle) {
            BillingCycle.MONTHLY -> item.subscription.amount * 12
            BillingCycle.QUARTERLY -> item.subscription.amount * 4
            BillingCycle.BI_MONTHLY -> item.subscription.amount * 6
            BillingCycle.YEARLY -> item.subscription.amount
            BillingCycle.WEEKLY -> item.subscription.amount * 52
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
            SubscriptionsSummaryHeader(
                totalAnnual = totalAnnualSpend,
                subsCount = regularSubscriptions.size,
                onAddSubscription = { showAddSubDialog = true }
            )
        }

        if (regularSubscriptions.isEmpty()) {
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
                            Icons.Default.Subscriptions,
                            contentDescription = null,
                            tint = Color(0xFF00838F),
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No digital subscriptions", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Track Bitwarden, YouTube Premium, Antigravity Plus, etc.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = { showAddSubDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add Subscription")
                        }
                    }
                }
            }
        } else {
            items(regularSubscriptions) { subItem ->
                SubscriptionCard(
                    item = subItem,
                    onOpenDocuments = { documentTarget = subItem.subscription },
                    onPay = { payingSubTarget = subItem },
                    onEdit = { editingSubTarget = subItem.subscription },
                    onCancel = {
                        viewModel.cancelSubscription(subItem.subscription.id)
                    },
                    onDelete = {
                        viewModel.deleteSubscription(subItem.subscription.id)
                    }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Pay / Renew Dialog
    payingSubTarget?.let { subItem ->
        val p = subItem.nextPayment
        if (p != null) {
            PayCommitmentDialog(
                title = subItem.title,
                initialAmountPaise = p.amount,
                linkedAccountId = subItem.subscription.linkedAccountId,
                accounts = accounts,
                isInsurance = false,
                onDismiss = { payingSubTarget = null },
                onConfirm = { amountPaise, accountId, _, utr, uri ->
                    viewModel.payCommitment(
                        paymentId = p.id,
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
                }
            )
        }
    }

    // Source Documents Dialog
    documentTarget?.let { sub ->
        CommitmentDocumentsDialog(
            type = "SUBSCRIPTION",
            commitmentId = sub.id,
            title = "${sub.serviceName} Invoices & Receipts",
            viewModel = viewModel,
            onDismiss = { documentTarget = null }
        )
    }

    // Add Subscription Dialog
    if (showAddSubDialog) {
        AddEditSubscriptionDialog(
            initialSubscription = null,
            accounts = accounts,
            onDismiss = { showAddSubDialog = false },
            onConfirm = { sub ->
                viewModel.createSubscription(sub) {
                    showAddSubDialog = false
                    viewModel.resyncAllCommitments()
                }
            }
        )
    }

    // Edit Subscription Dialog
    editingSubTarget?.let { sub ->
        AddEditSubscriptionDialog(
            initialSubscription = sub,
            accounts = accounts,
            onDismiss = { editingSubTarget = null },
            onConfirm = { updatedSub ->
                viewModel.updateSubscription(updatedSub) {
                    editingSubTarget = null
                    viewModel.resyncAllCommitments()
                }
            },
            onDelete = {
                viewModel.deleteSubscription(sub.id) {
                    editingSubTarget = null
                    viewModel.resyncAllCommitments()
                }
            }
        )
    }
}

@Composable
fun SubscriptionsSummaryHeader(
    totalAnnual: Long,
    subsCount: Int,
    onAddSubscription: () -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
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
                    text = "Digital Subscriptions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                IconButton(
                    onClick = onAddSubscription,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Add Subscription",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
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
                        text = "Total Annual Subscriptions",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                    )
                    Text(
                        text = totalAnnual.toRupeeString(),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }

                Text(
                    text = "$subsCount active services",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

@Composable
fun SubscriptionCard(
    item: CommitmentItem.SubscriptionItem,
    onOpenDocuments: () -> Unit,
    onPay: () -> Unit,
    onEdit: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit
) {
    val sub = item.subscription
    val nextPayment = item.nextPayment
    val (icon, color) = getSubscriptionStyle(sub.serviceName)
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
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(color.copy(alpha = 0.15f), shape = CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = sub.serviceName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${sub.billingCycle.name.lowercase().replaceFirstChar { it.uppercase() }} Subscription",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = sub.amount.toRupeeString(),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "/ ${sub.billingCycle.name.lowercase()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                                text = { Text("Edit Details") },
                                onClick = {
                                    showMenu = false
                                    onEdit()
                                },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            )
                            if (sub.status == CommitmentStatus.ACTIVE) {
                                DropdownMenuItem(
                                    text = { Text("Cancel Subscription") },
                                    onClick = {
                                        showMenu = false
                                        onCancel()
                                    },
                                    leadingIcon = { Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFE65100), modifier = Modifier.size(18.dp)) }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Delete Subscription") },
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

            // Promotional Warning Callout if cancelBeforeRenewal == true
            if (sub.cancelBeforeRenewal) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFFFF3E0),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFE65100),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Promotional Offer",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFE65100)
                                )
                                Text(
                                    text = "Cancel before renewal on ${formatCommitmentDate(nextPayment?.dueDate ?: sub.nextRenewalDate)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFBF360C)
                                )
                            }
                        }

                        if (sub.status == CommitmentStatus.ACTIVE) {
                            TextButton(
                                onClick = onCancel,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("Cancel Now", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD32F2F))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Due Date & Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (sub.status == CommitmentStatus.CANCELLED) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = "CANCELLED",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Text(
                        text = "No future renewals",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    StatusBadge(isPaid = nextPayment == null, dueDateMillis = nextPayment?.dueDate ?: sub.nextRenewalDate)
                    Text(
                        text = "Renewal: ${formatCommitmentDate(nextPayment?.dueDate ?: sub.nextRenewalDate)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (sub.notes.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = sub.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(10.dp))

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
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Invoices", fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = onEdit,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Edit", fontSize = 11.sp)
                    }
                }

                if (nextPayment != null && sub.status == CommitmentStatus.ACTIVE) {
                    Button(
                        onClick = onPay,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = color),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text("Renew / Pay", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

fun getSubscriptionStyle(serviceName: String): Pair<ImageVector, Color> {
    val name = serviceName.lowercase()
    return when {
        name.contains("bitwarden") -> Pair(Icons.Default.Key, Color(0xFF175DDC))
        name.contains("youtube") -> Pair(Icons.Default.PlayCircle, Color(0xFFFF0000))
        name.contains("antigravity") -> Pair(Icons.Default.RocketLaunch, Color(0xFF673AB7))
        else -> Pair(Icons.Default.Subscriptions, Color(0xFF00838F))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditSubscriptionDialog(
    initialSubscription: Subscription? = null,
    accounts: List<AccountMinimal>,
    onDismiss: () -> Unit,
    onConfirm: (Subscription) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val isEdit = initialSubscription != null

    var serviceName by remember(initialSubscription) {
        mutableStateOf(initialSubscription?.serviceName ?: "")
    }
    var amountText by remember(initialSubscription) {
        mutableStateOf(initialSubscription?.let { (it.amount / 100.0).toString() } ?: "")
    }
    var billingCycle by remember(initialSubscription) {
        mutableStateOf(initialSubscription?.billingCycle ?: BillingCycle.MONTHLY)
    }
    var selectedAccountId by remember(initialSubscription) {
        mutableStateOf(initialSubscription?.linkedAccountId ?: accounts.firstOrNull()?.id)
    }
    var autoDebit by remember(initialSubscription) {
        mutableStateOf(initialSubscription?.autoDebit ?: false)
    }
    var cancelBeforeRenewal by remember(initialSubscription) {
        mutableStateOf(initialSubscription?.cancelBeforeRenewal ?: false)
    }
    var notes by remember(initialSubscription) {
        mutableStateOf(initialSubscription?.notes ?: "")
    }
    var selectedStatus by remember(initialSubscription) {
        mutableStateOf(initialSubscription?.status ?: CommitmentStatus.ACTIVE)
    }

    var renewalDateMillis by remember(initialSubscription) {
        mutableStateOf(initialSubscription?.nextRenewalDate ?: (System.currentTimeMillis() + 30L * 86400000L))
    }

    var showDatePicker by remember { mutableStateOf(false) }
    var expandedCycleDropdown by remember { mutableStateOf(false) }
    var expandedAccountDropdown by remember { mutableStateOf(false) }
    var expandedStatusDropdown by remember { mutableStateOf(false) }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = renewalDateMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        renewalDateMillis = it
                    }
                    showDatePicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (isEdit) "Edit Subscription" else "Add Digital Subscription",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = serviceName,
                    onValueChange = { serviceName = it },
                    label = { Text("Service Name (e.g. Antigravity Plus)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Billing Cycle Dropdown
                ExposedDropdownMenuBox(
                    expanded = expandedCycleDropdown,
                    onExpandedChange = { expandedCycleDropdown = !expandedCycleDropdown }
                ) {
                    OutlinedTextField(
                        readOnly = true,
                        value = billingCycle.name.lowercase().replaceFirstChar { it.uppercase() },
                        onValueChange = {},
                        label = { Text("Billing Cycle") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCycleDropdown) },
                        modifier = Modifier
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expandedCycleDropdown,
                        onDismissRequest = { expandedCycleDropdown = false }
                    ) {
                        BillingCycle.values().forEach { cycle ->
                            DropdownMenuItem(
                                text = { Text(cycle.name.lowercase().replaceFirstChar { it.uppercase() }) },
                                onClick = {
                                    billingCycle = cycle
                                    expandedCycleDropdown = false
                                }
                            )
                        }
                    }
                }

                // Next Renewal Date
                OutlinedTextField(
                    readOnly = true,
                    value = formatCommitmentDate(renewalDateMillis),
                    onValueChange = {},
                    label = { Text("Next Renewal Date") },
                    trailingIcon = {
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(Icons.Default.DateRange, contentDescription = "Pick Date")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDatePicker = true }
                )

                // Linked Account Dropdown
                if (accounts.isNotEmpty()) {
                    ExposedDropdownMenuBox(
                        expanded = expandedAccountDropdown,
                        onExpandedChange = { expandedAccountDropdown = !expandedAccountDropdown }
                    ) {
                        val currentAcc = accounts.find { it.id == selectedAccountId }
                        OutlinedTextField(
                            readOnly = true,
                            value = currentAcc?.label ?: "None",
                            onValueChange = {},
                            label = { Text("Payment Account") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedAccountDropdown) },
                            modifier = Modifier
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
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

                // Promotional / Cancel Before Renewal Toggle
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (cancelBeforeRenewal) Color(0xFFFFF3E0) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
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
                            Text(
                                text = "Cancel Before Renewal",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (cancelBeforeRenewal) Color(0xFFE65100) else Color.Unspecified
                            )
                            Text(
                                text = "Promotional offer / trial. Alerts you to cancel before renewal so you won't be charged.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = cancelBeforeRenewal,
                            onCheckedChange = { cancelBeforeRenewal = it }
                        )
                    }
                }

                // Auto Debit Switch
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto Debit Enabled", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text("App records payment automatically on due date", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = autoDebit,
                        onCheckedChange = { autoDebit = it }
                    )
                }

                // Status if editing
                if (isEdit) {
                    ExposedDropdownMenuBox(
                        expanded = expandedStatusDropdown,
                        onExpandedChange = { expandedStatusDropdown = !expandedStatusDropdown }
                    ) {
                        OutlinedTextField(
                            readOnly = true,
                            value = selectedStatus.name,
                            onValueChange = {},
                            label = { Text("Status") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedStatusDropdown) },
                            modifier = Modifier
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = expandedStatusDropdown,
                            onDismissRequest = { expandedStatusDropdown = false }
                        ) {
                            CommitmentStatus.values().forEach { st ->
                                DropdownMenuItem(
                                    text = { Text(st.name) },
                                    onClick = {
                                        selectedStatus = st
                                        expandedStatusDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes / Promotional Details") },
                    singleLine = false,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                if (isEdit && onDelete != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Delete Subscription")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val a = ((amountText.toDoubleOrNull() ?: 0.0) * 100).toLong()
                    if (serviceName.isNotBlank() && a > 0) {
                        val sub = initialSubscription?.copy(
                            serviceName = serviceName.trim(),
                            billingCycle = billingCycle,
                            amount = a,
                            nextRenewalDate = renewalDateMillis,
                            linkedAccountId = selectedAccountId,
                            autoDebit = autoDebit,
                            cancelBeforeRenewal = cancelBeforeRenewal,
                            status = selectedStatus,
                            notes = notes.trim()
                        ) ?: Subscription(
                            serviceName = serviceName.trim(),
                            billingCycle = billingCycle,
                            amount = a,
                            startDate = System.currentTimeMillis(),
                            nextRenewalDate = renewalDateMillis,
                            linkedAccountId = selectedAccountId,
                            autoDebit = autoDebit,
                            cancelBeforeRenewal = cancelBeforeRenewal,
                            status = selectedStatus,
                            notes = notes.trim(),
                            isVariableAmount = false
                        )
                        onConfirm(sub)
                    }
                }
            ) {
                Text(if (isEdit) "Save Changes" else "Add Subscription")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
