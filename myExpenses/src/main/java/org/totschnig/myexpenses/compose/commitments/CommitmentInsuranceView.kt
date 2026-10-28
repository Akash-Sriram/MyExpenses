package org.totschnig.myexpenses.compose.commitments

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Security
import android.content.ContentUris
import android.content.Intent
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.DropdownMenu
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.clickable
import org.totschnig.myexpenses.contract.TransactionsContract
import org.totschnig.myexpenses.activity.ExpenseEdit
import org.totschnig.myexpenses.domain.commitment.CommitmentStatus
import timber.log.Timber
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.totschnig.myexpenses.domain.commitment.CommitmentItem
import org.totschnig.myexpenses.domain.commitment.CommitmentPayment
import org.totschnig.myexpenses.domain.commitment.InsurancePolicy
import org.totschnig.myexpenses.domain.commitment.InsuranceType
import org.totschnig.myexpenses.domain.commitment.PremiumFrequency
import org.totschnig.myexpenses.viewmodel.LoanViewModel
import org.totschnig.myexpenses.viewmodel.data.AccountMinimal

@Composable
fun CommitmentInsuranceView(
    viewModel: LoanViewModel,
    modifier: Modifier = Modifier
) {
    val commitmentItems by viewModel.commitmentItems.collectAsState()
    val accounts by viewModel.accounts.collectAsState()

    val insuranceItems = commitmentItems.filterIsInstance<CommitmentItem.InsuranceItem>()
    val activeInsuranceItems = insuranceItems.filter { it.policy.status == CommitmentStatus.ACTIVE }
    val inactiveInsuranceItems = insuranceItems.filter { it.policy.status != CommitmentStatus.ACTIVE }

    var payingInsuranceTarget by remember { mutableStateOf<CommitmentItem.InsuranceItem?>(null) }
    var documentTarget by remember { mutableStateOf<InsurancePolicy?>(null) }
    var expandedHistoryPolicyId by remember { mutableStateOf<Long?>(null) }
    var showAddPolicyDialog by remember { mutableStateOf(false) }
    var editingPolicyTarget by remember { mutableStateOf<InsurancePolicy?>(null) }
    var deletingPolicyTarget by remember { mutableStateOf<InsurancePolicy?>(null) }
    var showInactivePolicies by remember { mutableStateOf(false) }

    val totalAnnualPremium = activeInsuranceItems.sumOf { item ->
        when (item.policy.frequency) {
            PremiumFrequency.MONTHLY -> item.policy.premiumAmount * 12
            PremiumFrequency.QUARTERLY -> item.policy.premiumAmount * 4
            PremiumFrequency.HALF_YEARLY -> item.policy.premiumAmount * 2
            PremiumFrequency.YEARLY -> item.policy.premiumAmount
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
            InsuranceSummaryHeader(
                totalAnnual = totalAnnualPremium,
                policiesCount = activeInsuranceItems.size,
                onAddPolicy = { showAddPolicyDialog = true }
            )
        }

        if (activeInsuranceItems.isEmpty()) {
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
                            Icons.Default.Security,
                            contentDescription = null,
                            tint = Color(0xFF5E35B1),
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No active insurance policies", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Add your LIC, Health, or Vehicle insurance policies",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = { showAddPolicyDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add Policy")
                        }
                    }
                }
            }
        } else {
            items(activeInsuranceItems) { insItem ->
                val isHistoryOpen = expandedHistoryPolicyId == insItem.policy.id
                val paymentHistoryFlow = remember(insItem.policy.id) {
                    viewModel.getPaymentsForCommitment("INSURANCE", insItem.policy.id)
                }
                val pastPayments by paymentHistoryFlow.collectAsState(initial = emptyList())

                InsurancePolicyCard(
                    item = insItem,
                    isHistoryOpen = isHistoryOpen,
                    pastPayments = pastPayments.filter { it.status == org.totschnig.myexpenses.domain.commitment.PaymentRecordStatus.PAID },
                    onToggleHistory = {
                        expandedHistoryPolicyId = if (isHistoryOpen) null else insItem.policy.id
                    },
                    onOpenDocuments = { documentTarget = insItem.policy },
                    onPayPremium = { payingInsuranceTarget = insItem },
                    onEdit = { editingPolicyTarget = insItem.policy },
                    onCancel = {
                        viewModel.cancelInsurancePolicy(insItem.policy.id) {
                            viewModel.resyncAllCommitments()
                        }
                    },
                    onDelete = { deletingPolicyTarget = insItem.policy }
                )
            }
        }

        // Inactive / Discontinued Policies Section
        if (inactiveInsuranceItems.isNotEmpty()) {
            item {
                Surface(
                    onClick = { showInactivePolicies = !showInactivePolicies },
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Discontinued Policies (${inactiveInsuranceItems.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Icon(
                            imageVector = if (showInactivePolicies) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (showInactivePolicies) {
                items(inactiveInsuranceItems) { insItem ->
                    val isHistoryOpen = expandedHistoryPolicyId == insItem.policy.id
                    val paymentHistoryFlow = remember(insItem.policy.id) {
                        viewModel.getPaymentsForCommitment("INSURANCE", insItem.policy.id)
                    }
                    val pastPayments by paymentHistoryFlow.collectAsState(initial = emptyList())

                    InsurancePolicyCard(
                        item = insItem,
                        isHistoryOpen = isHistoryOpen,
                        pastPayments = pastPayments.filter { it.status == org.totschnig.myexpenses.domain.commitment.PaymentRecordStatus.PAID },
                        onToggleHistory = {
                            expandedHistoryPolicyId = if (isHistoryOpen) null else insItem.policy.id
                        },
                        onOpenDocuments = { documentTarget = insItem.policy },
                        onPayPremium = { payingInsuranceTarget = insItem },
                        onEdit = { editingPolicyTarget = insItem.policy },
                        onReactivate = {
                            viewModel.reactivateInsurancePolicy(insItem.policy.id) {
                                viewModel.resyncAllCommitments()
                            }
                        },
                        onDelete = { deletingPolicyTarget = insItem.policy }
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Pay Premium Dialog (Supports Me vs Dad)
    payingInsuranceTarget?.let { insItem ->
        val p = insItem.nextPayment
        if (p != null) {
            PayCommitmentDialog(
                title = insItem.title,
                initialAmountPaise = p.amount,
                linkedAccountId = insItem.policy.linkedAccountId,
                accounts = accounts,
                isInsurance = true,
                onDismiss = { payingInsuranceTarget = null },
                onConfirm = { amountPaise, accountId, payer, utr, uri ->
                    viewModel.payCommitment(
                        paymentId = p.id,
                        paidDate = System.currentTimeMillis(),
                        createTransaction = payer == "SELF" && accountId != null,
                        payer = payer,
                        notes = utr,
                        attachmentUri = uri,
                        onComplete = {
                            payingInsuranceTarget = null
                            viewModel.resyncAllCommitments()
                        }
                    )
                }
            )
        }
    }

    // Source Documents Dialog
    documentTarget?.let { policy ->
        CommitmentDocumentsDialog(
            type = "INSURANCE",
            commitmentId = policy.id,
            title = "${policy.policyName} (${policy.insurerName})",
            viewModel = viewModel,
            onDismiss = { documentTarget = null }
        )
    }

    // Add Policy Dialog
    if (showAddPolicyDialog) {
        AddEditInsurancePolicyDialog(
            initialPolicy = null,
            accounts = accounts,
            onDismiss = { showAddPolicyDialog = false },
            onConfirm = { policy ->
                viewModel.createInsurancePolicy(policy) {
                    showAddPolicyDialog = false
                    viewModel.resyncAllCommitments()
                }
            }
        )
    }

    // Edit Policy Dialog
    editingPolicyTarget?.let { policyToEdit ->
        AddEditInsurancePolicyDialog(
            initialPolicy = policyToEdit,
            accounts = accounts,
            onDismiss = { editingPolicyTarget = null },
            onConfirm = { updated ->
                viewModel.updateInsurancePolicy(updated) {
                    editingPolicyTarget = null
                    viewModel.resyncAllCommitments()
                }
            }
        )
    }

    // Delete Policy Confirmation
    deletingPolicyTarget?.let { policyToDelete ->
        AlertDialog(
            onDismissRequest = { deletingPolicyTarget = null },
            title = { Text("Delete Insurance Policy?") },
            text = { Text("Are you sure you want to delete '${policyToDelete.policyName}'? Past transactions in your ledger will not be affected.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteInsurancePolicy(policyToDelete.id) {
                            deletingPolicyTarget = null
                            viewModel.resyncAllCommitments()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingPolicyTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun InsuranceSummaryHeader(
    totalAnnual: Long,
    policiesCount: Int,
    onAddPolicy: () -> Unit
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
                    text = "Insurance & Policy Cover",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                IconButton(
                    onClick = onAddPolicy,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Add Policy",
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
                        text = "Total Annual Outflow",
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
                    text = "$policiesCount active policies",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

@Composable
fun InsurancePolicyCard(
    item: CommitmentItem.InsuranceItem,
    isHistoryOpen: Boolean,
    pastPayments: List<CommitmentPayment>,
    onToggleHistory: () -> Unit,
    onOpenDocuments: () -> Unit,
    onPayPremium: () -> Unit,
    onEdit: () -> Unit,
    onCancel: (() -> Unit)? = null,
    onReactivate: (() -> Unit)? = null,
    onDelete: () -> Unit
) {
    val policy = item.policy
    val nextPayment = item.nextPayment
    val icon = getInsuranceIcon(policy.insuranceType)
    val context = LocalContext.current
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
                verticalAlignment = Alignment.Top
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
                        Icon(icon, contentDescription = null, tint = Color(0xFF5E35B1), modifier = Modifier.size(22.dp))
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = policy.policyName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${policy.insurerName} • ${policy.insuranceType.name} Insurance",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = policy.premiumAmount.toRupeeString(),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "/ ${policy.frequency.name.lowercase()}",
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
                                text = { Text("Edit Policy") },
                                onClick = {
                                    showMenu = false
                                    onEdit()
                                },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            )
                            if (policy.status == CommitmentStatus.ACTIVE && onCancel != null) {
                                DropdownMenuItem(
                                    text = { Text("Discontinue / Cancel") },
                                    onClick = {
                                        showMenu = false
                                        onCancel()
                                    },
                                    leadingIcon = { Icon(Icons.Default.Close, contentDescription = null, tint = Color(0xFFE65100), modifier = Modifier.size(18.dp)) }
                                )
                            }
                            if (policy.status != CommitmentStatus.ACTIVE && onReactivate != null) {
                                DropdownMenuItem(
                                    text = { Text("Reactivate Policy") },
                                    onClick = {
                                        showMenu = false
                                        onReactivate()
                                    },
                                    leadingIcon = { Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(18.dp)) }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Delete Policy", color = MaterialTheme.colorScheme.error) },
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

            Spacer(modifier = Modifier.height(12.dp))

            val isPaid = nextPayment == null || item.isCurrentCyclePaid

            // Due Date & Payer info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (policy.status != CommitmentStatus.ACTIVE) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = "DISCONTINUED",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    } else {
                        StatusBadge(isPaid = isPaid, dueDateMillis = nextPayment?.dueDate ?: 0L)
                    }
                    if (policy.payerMode.equals("FAMILY_SHARED", ignoreCase = true)) {
                        PayerBadge(payer = "DAD")
                    }
                }

                if (policy.status == CommitmentStatus.ACTIVE && nextPayment != null) {
                    Text(
                        text = "Next: ${formatCommitmentDate(nextPayment.dueDate)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
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
                        Text("Policy Docs", fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = onToggleHistory,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isHistoryOpen) "Hide History" else "History (${pastPayments.size})", fontSize = 11.sp)
                    }
                }

                if (policy.status == CommitmentStatus.ACTIVE && nextPayment != null && !isPaid) {
                    Button(
                        onClick = onPayPremium,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5E35B1)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text("Pay Premium", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Expandable History
            AnimatedVisibility(
                visible = isHistoryOpen,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Payment History (${pastPayments.size} recorded)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (pastPayments.isEmpty()) {
                        Text("No past recorded payments yet", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        pastPayments.forEach { payment ->
                            val txId = payment.transactionId
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFF1F8E9),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (txId != null && txId > 0) {
                                            Modifier.clickable {
                                                try {
                                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                                        data = ContentUris.withAppendedId(TransactionsContract.Transactions.CONTENT_URI, txId)
                                                        setClass(context, ExpenseEdit::class.java)
                                                    }
                                                    context.startActivity(intent)
                                                } catch (e: Exception) {
                                                    Timber.e(e, "Failed to open transaction #$txId")
                                                }
                                            }
                                        } else Modifier
                                    )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(
                                                text = formatCommitmentDate(payment.paidDate ?: payment.dueDate),
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            if (txId != null && txId > 0) {
                                                Icon(
                                                    Icons.Default.OpenInNew,
                                                    contentDescription = "View Transaction",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                            }
                                        }
                                        if (payment.notes.isNotBlank()) {
                                            Text(
                                                text = "Ref: ${payment.notes}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            text = payment.amount.toRupeeString(),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF2E7D32)
                                        )
                                        PayerBadge(payer = payment.payer)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

fun getInsuranceIcon(type: InsuranceType): ImageVector = when (type) {
    InsuranceType.LIFE, InsuranceType.TERM -> Icons.Default.Favorite
    InsuranceType.HEALTH -> Icons.Default.LocalHospital
    InsuranceType.VEHICLE -> Icons.Default.DirectionsCar
    else -> Icons.Default.Security
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditInsurancePolicyDialog(
    initialPolicy: InsurancePolicy? = null,
    accounts: List<AccountMinimal>,
    onDismiss: () -> Unit,
    onConfirm: (InsurancePolicy) -> Unit
) {
    val isEdit = initialPolicy != null
    var policyName by remember { mutableStateOf(initialPolicy?.policyName ?: "") }
    var insurerName by remember { mutableStateOf(initialPolicy?.insurerName ?: "LIC") }
    var insuranceType by remember { mutableStateOf(initialPolicy?.insuranceType ?: InsuranceType.LIFE) }
    var premiumText by remember {
        mutableStateOf(if (initialPolicy != null) (initialPolicy.premiumAmount / 100.0).toString() else "")
    }
    var frequency by remember { mutableStateOf(initialPolicy?.frequency ?: PremiumFrequency.QUARTERLY) }
    var dueDayText by remember { mutableStateOf((initialPolicy?.dueDayOfMonth ?: 5).toString()) }
    var payerMode by remember { mutableStateOf(initialPolicy?.payerMode ?: "SELF") }
    var selectedAccountId by remember { mutableStateOf(initialPolicy?.linkedAccountId ?: accounts.firstOrNull()?.id) }

    var expandedFreq by remember { mutableStateOf(false) }
    var expandedType by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEdit) "Edit Insurance Policy" else "Add Insurance Policy", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = policyName,
                    onValueChange = { policyName = it },
                    label = { Text("Policy Name (e.g. LIC Policy)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = insurerName,
                    onValueChange = { insurerName = it },
                    label = { Text("Insurer (e.g. LIC, ICICI Lombard)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                ExposedDropdownMenuBox(
                    expanded = expandedType,
                    onExpandedChange = { expandedType = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = insuranceType.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Insurance Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedType) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expandedType,
                        onDismissRequest = { expandedType = false }
                    ) {
                        InsuranceType.entries.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.name) },
                                onClick = {
                                    insuranceType = type
                                    expandedType = false
                                }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = premiumText,
                        onValueChange = { premiumText = it },
                        label = { Text("Premium (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    ExposedDropdownMenuBox(
                        expanded = expandedFreq,
                        onExpandedChange = { expandedFreq = it },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = frequency.name,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Frequency") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedFreq) },
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = expandedFreq,
                            onDismissRequest = { expandedFreq = false }
                        ) {
                            PremiumFrequency.entries.forEach { freq ->
                                DropdownMenuItem(
                                    text = { Text(freq.name) },
                                    onClick = {
                                        frequency = freq
                                        expandedFreq = false
                                    }
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = dueDayText,
                    onValueChange = { dueDayText = it },
                    label = { Text("Due Day of Month (1-31)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val p = ((premiumText.toDoubleOrNull() ?: 0.0) * 100).toLong()
                    val d = dueDayText.toIntOrNull()?.coerceIn(1, 31) ?: 5
                    if (policyName.isNotBlank() && p > 0) {
                        onConfirm(
                            InsurancePolicy(
                                id = initialPolicy?.id ?: 0L,
                                policyName = policyName.trim(),
                                insurerName = insurerName.trim(),
                                insuranceType = insuranceType,
                                premiumAmount = p,
                                frequency = frequency,
                                startDate = initialPolicy?.startDate ?: System.currentTimeMillis(),
                                endDate = initialPolicy?.endDate,
                                dueDayOfMonth = d,
                                linkedAccountId = selectedAccountId,
                                payerMode = payerMode,
                                status = initialPolicy?.status ?: CommitmentStatus.ACTIVE
                            )
                        )
                    }
                }
            ) {
                Text(if (isEdit) "Update Policy" else "Add Policy")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

