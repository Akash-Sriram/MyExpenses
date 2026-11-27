package org.totschnig.myexpenses.compose.reconcile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.model.CurrencyUnit
import org.totschnig.myexpenses.reconcile.BalanceCheckpoint
import org.totschnig.myexpenses.reconcile.BalanceVerificationResult
import org.totschnig.myexpenses.reconcile.BankStatementParser
import org.totschnig.myexpenses.reconcile.MatchStatus
import org.totschnig.myexpenses.reconcile.ReconcileResult
import org.totschnig.myexpenses.reconcile.ReconciliationReport
import org.totschnig.myexpenses.reconcile.StatementReconciler
import org.totschnig.myexpenses.reconcile.StatementTransaction
import org.totschnig.myexpenses.viewmodel.data.Transaction2
import java.math.BigDecimal
import java.math.RoundingMode

enum class ReconcileTab {
    QUICK_BALANCE,
    STATEMENT_RECONCILE
}

enum class ItemFilter {
    ALL,
    UNMATCHED_ONLY,
    MATCHED_ONLY
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatementReconciliationDialog(
    accountLabel: String,
    currentBalance: Long, // in minor units / paise
    currencyUnit: CurrencyUnit,
    appTransactions: List<Transaction2>,
    onDismiss: () -> Unit,
    onAddMissingTransaction: (StatementTransaction) -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    var selectedTab by rememberSaveable { mutableStateOf(ReconcileTab.QUICK_BALANCE) }

    // Quick Balance State
    var smsText by rememberSaveable { mutableStateOf("") }
    val currentBalanceMajor = remember(currentBalance, currencyUnit) {
        BigDecimal(currentBalance).movePointLeft(currencyUnit.fractionDigits)
    }

    val checkpoint = remember(smsText) {
        if (smsText.isNotBlank()) BankStatementParser.parseSmsBalance(smsText) else null
    }

    val verificationResult = remember(checkpoint, currentBalanceMajor) {
        checkpoint?.let { BankStatementParser.verifyBalance(currentBalanceMajor, it) }
    }

    // Statement Reconcile State
    var statementInputText by rememberSaveable { mutableStateOf("") }
    var reconciliationReport by remember { mutableStateOf<ReconciliationReport?>(null) }
    var itemFilter by rememberSaveable { mutableStateOf(ItemFilter.UNMATCHED_ONLY) }

    fun runReconciliation() {
        if (statementInputText.isNotBlank()) {
            val parsed = BankStatementParser.parse(statementInputText)
            reconciliationReport = StatementReconciler.reconcile(parsed, appTransactions)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "Reconcile $accountLabel",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "App Balance: ${currencyUnit.symbol} ${currentBalanceMajor.toPlainString()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close")
                        }
                    }
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Tab Selection
                PrimaryTabRow(
                    selectedTabIndex = selectedTab.ordinal
                ) {
                    Tab(
                        selected = selectedTab == ReconcileTab.QUICK_BALANCE,
                        onClick = { selectedTab = ReconcileTab.QUICK_BALANCE },
                        text = { Text("Quick Balance Check") }
                    )
                    Tab(
                        selected = selectedTab == ReconcileTab.STATEMENT_RECONCILE,
                        onClick = { selectedTab = ReconcileTab.STATEMENT_RECONCILE },
                        text = { Text("Bank Statement Matcher") }
                    )
                }

                when (selectedTab) {
                    ReconcileTab.QUICK_BALANCE -> {
                        QuickBalanceCheckContent(
                            smsText = smsText,
                            onSmsTextChange = { smsText = it },
                            onPasteClipboard = {
                                clipboardManager.getText()?.text?.let { smsText = it }
                            },
                            currentBalanceMajor = currentBalanceMajor,
                            currencyUnit = currencyUnit,
                            checkpoint = checkpoint,
                            verificationResult = verificationResult,
                            onSwitchToStatementReconcile = { selectedTab = ReconcileTab.STATEMENT_RECONCILE }
                        )
                    }

                    ReconcileTab.STATEMENT_RECONCILE -> {
                        StatementReconcileContent(
                            inputText = statementInputText,
                            onInputTextChange = {
                                statementInputText = it
                                reconciliationReport = null
                            },
                            onPasteClipboard = {
                                clipboardManager.getText()?.text?.let {
                                    statementInputText = it
                                    reconciliationReport = null
                                }
                            },
                            onReconcile = ::runReconciliation,
                            report = reconciliationReport,
                            itemFilter = itemFilter,
                            onFilterChange = { itemFilter = it },
                            currencyUnit = currencyUnit,
                            onAddMissing = onAddMissingTransaction
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickBalanceCheckContent(
    smsText: String,
    onSmsTextChange: (String) -> Unit,
    onPasteClipboard: () -> Unit,
    currentBalanceMajor: BigDecimal,
    currencyUnit: CurrencyUnit,
    checkpoint: BalanceCheckpoint?,
    verificationResult: BalanceVerificationResult?,
    onSwitchToStatementReconcile: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Instant Balance Sanity Check",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Paste any bank SMS or statement snippet containing your available balance (e.g. 'Avl Bal Rs. 31,500.23'). MyExpenses will instantly verify whether your ledger matches your bank down to the paise.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            OutlinedTextField(
                value = smsText,
                onValueChange = onSmsTextChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Paste Bank SMS or Message") },
                placeholder = { Text("ICICI Bank Acc XX407 debited Rs. 489.00 on 01-Nov-26 InfoBIL*INFT*FK15.Avl Bal Rs. 31,500.23...") },
                trailingIcon = {
                    IconButton(onClick = onPasteClipboard) {
                        Icon(Icons.Default.ContentPaste, contentDescription = "Paste")
                    }
                },
                maxLines = 5
            )
        }

        if (verificationResult != null && checkpoint != null) {
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = if (verificationResult.isAligned) {
                            Color(0xFFE8F5E9) // Light Green
                        } else {
                            Color(0xFFFFF3E0) // Light Amber
                        }
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (verificationResult.isAligned) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (verificationResult.isAligned) Color(0xFF2E7D32) else Color(0xFFE65100),
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = if (verificationResult.isAligned) "Balances Perfectly In Sync!" else "Discrepancy Detected",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (verificationResult.isAligned) Color(0xFF2E7D32) else Color(0xFFE65100)
                            )
                        }

                        HorizontalDivider(color = Color.Black.copy(alpha = 0.1f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Bank Available Balance:", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "${currencyUnit.symbol} ${checkpoint.availableBalance.setScale(2, RoundingMode.HALF_UP).toPlainString()}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("App Current Balance:", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "${currencyUnit.symbol} ${currentBalanceMajor.setScale(2, RoundingMode.HALF_UP).toPlainString()}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (!verificationResult.isAligned) {
                            val diff = verificationResult.difference.abs()
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Difference (Unrecorded):", style = MaterialTheme.typography.bodyMedium, color = Color(0xFFC62828))
                                Text(
                                    "${currencyUnit.symbol} ${diff.setScale(2, RoundingMode.HALF_UP).toPlainString()}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFC62828)
                                )
                            }

                            Spacer(Modifier.height(4.dp))

                            Button(
                                onClick = onSwitchToStatementReconcile,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100))
                            ) {
                                Icon(Icons.Default.FindInPage, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Find Missing Entries in Statement")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatementReconcileContent(
    inputText: String,
    onInputTextChange: (String) -> Unit,
    onPasteClipboard: () -> Unit,
    onReconcile: () -> Unit,
    report: ReconciliationReport?,
    itemFilter: ItemFilter,
    onFilterChange: (ItemFilter) -> Unit,
    currencyUnit: CurrencyUnit,
    onAddMissing: (StatementTransaction) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (report == null) {
            OutlinedTextField(
                value = inputText,
                onValueChange = onInputTextChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                label = { Text("Paste Bank Statement (CSV or Text)") },
                placeholder = { Text("Paste ICICI statement lines, CSV text, or transaction dump...") },
                trailingIcon = {
                    IconButton(onClick = onPasteClipboard) {
                        Icon(Icons.Default.ContentPaste, contentDescription = "Paste")
                    }
                }
            )

            Button(
                onClick = onReconcile,
                modifier = Modifier.fillMaxWidth(),
                enabled = inputText.isNotBlank()
            ) {
                Icon(Icons.Default.FindInPage, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Run 3-Tier Reconciliation")
            }
        } else {
            // Reconciliation Summary Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val matchPercent = if (report.totalStatementCount > 0) {
                        (report.matchedCount.toDouble() / report.totalStatementCount.toDouble()) * 100.0
                    } else 0.0

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Matching Accuracy: ${String.format(java.util.Locale.US, "%.1f", matchPercent)}%",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (report.unmatchedCount == 0) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                        ) {
                            Text(
                                text = "${report.matchedCount}/${report.totalStatementCount} Matched",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (report.unmatchedCount == 0) Color(0xFF2E7D32) else Color(0xFFC62828),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    if (report.unmatchedCount > 0) {
                        Text(
                            text = "⚠️ Found ${report.unmatchedCount} statement transactions missing from MyExpenses. Tap '+ Add' to record them immediately.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        Text(
                            text = "🎉 All statement transactions accounted for in MyExpenses!",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF2E7D32)
                        )
                    }
                }
            }

            // Filter Chips
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = itemFilter == ItemFilter.UNMATCHED_ONLY,
                    onClick = { onFilterChange(ItemFilter.UNMATCHED_ONLY) },
                    label = { Text("Missing (${report.unmatchedCount})") }
                )
                FilterChip(
                    selected = itemFilter == ItemFilter.MATCHED_ONLY,
                    onClick = { onFilterChange(ItemFilter.MATCHED_ONLY) },
                    label = { Text("Matched (${report.matchedCount})") }
                )
                FilterChip(
                    selected = itemFilter == ItemFilter.ALL,
                    onClick = { onFilterChange(ItemFilter.ALL) },
                    label = { Text("All (${report.totalStatementCount})") }
                )
            }

            val displayedItems = remember(report, itemFilter) {
                when (itemFilter) {
                    ItemFilter.UNMATCHED_ONLY -> report.items.filter { it.status is MatchStatus.UnmatchedInApp }
                    ItemFilter.MATCHED_ONLY -> report.items.filter { it.status is MatchStatus.Matched }
                    ItemFilter.ALL -> report.items
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(displayedItems, key = { it.statementTx.sNo }) { item ->
                    StatementItemCard(
                        item = item,
                        currencyUnit = currencyUnit,
                        onAdd = { onAddMissing(item.statementTx) }
                    )
                }
            }
        }
    }
}

@Composable
private fun StatementItemCard(
    item: ReconcileResult,
    currencyUnit: CurrencyUnit,
    onAdd: () -> Unit
) {
    val tx = item.statementTx
    val isWithdrawal = tx.withdrawal > BigDecimal.ZERO || tx.amount < BigDecimal.ZERO
    val status = item.status

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (status is MatchStatus.UnmatchedInApp) {
                Color(0xFFFFF8E1) // Soft amber for missing
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = tx.date.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (tx.utr != null) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest
                        ) {
                            Text(
                                text = "UTR: ${tx.utr}",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Text(
                    text = tx.payeeOrRemarks,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                when (status) {
                    is MatchStatus.Matched -> {
                        Text(
                            text = "✓ ${status.reason} (App ID: ${status.appTransactionId})",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF2E7D32)
                        )
                    }

                    is MatchStatus.UnmatchedInApp -> {
                        Text(
                            text = "❌ Missing from MyExpenses",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                val amt = if (isWithdrawal) tx.withdrawal else tx.deposit
                Text(
                    text = "${if (isWithdrawal) "-" else "+"} ${currencyUnit.symbol} ${amt.setScale(2, RoundingMode.HALF_UP).toPlainString()}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isWithdrawal) MaterialTheme.colorScheme.error else Color(0xFF2E7D32)
                )

                if (status is MatchStatus.UnmatchedInApp) {
                    Spacer(Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = onAdd,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("+ Add to App", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
