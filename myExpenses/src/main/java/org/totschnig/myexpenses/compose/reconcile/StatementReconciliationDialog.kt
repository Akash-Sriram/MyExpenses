package org.totschnig.myexpenses.compose.reconcile

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.model.CurrencyUnit
import org.totschnig.myexpenses.reconcile.BalanceCheckpoint
import org.totschnig.myexpenses.reconcile.BalanceVerificationResult
import org.totschnig.myexpenses.reconcile.BankSmsScanner
import org.totschnig.myexpenses.reconcile.BankStatementParser
import org.totschnig.myexpenses.reconcile.DocumentReadResult
import org.totschnig.myexpenses.reconcile.MatchStatus
import org.totschnig.myexpenses.reconcile.ReconcileResult
import org.totschnig.myexpenses.reconcile.ReconciliationReport
import org.totschnig.myexpenses.reconcile.SmsScanResult
import org.totschnig.myexpenses.reconcile.StatementDocumentReader
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
    onAddMissingTransaction: (StatementTransaction) -> Unit,
    onAddAllMissingTransactions: ((List<StatementTransaction>) -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    var selectedTab by rememberSaveable { mutableStateOf(ReconcileTab.QUICK_BALANCE) }

    val currentBalanceMajor = remember(currentBalance, currencyUnit) {
        BigDecimal(currentBalance).movePointLeft(currencyUnit.fractionDigits)
    }

    // --- SMS Inbox Intelligence State ---
    var hasSmsPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_SMS
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasSmsPermission = isGranted
    }

    var isScanningSms by remember { mutableStateOf(false) }
    var smsScanResult by remember { mutableStateOf<SmsScanResult?>(null) }
    var selectedCheckpoint by remember { mutableStateOf<BalanceCheckpoint?>(null) }
    var manualSmsText by rememberSaveable { mutableStateOf("") }

    fun refreshSmsScan() {
        if (hasSmsPermission) {
            isScanningSms = true
            coroutineScope.launch(Dispatchers.IO) {
                val result = BankSmsScanner.scanInbox(context, accountLabel)
                withContext(Dispatchers.Main) {
                    smsScanResult = result
                    selectedCheckpoint = result.latestCheckpoint
                    isScanningSms = false
                }
            }
        }
    }

    LaunchedEffect(hasSmsPermission, accountLabel) {
        refreshSmsScan()
    }

    val activeCheckpoint = remember(selectedCheckpoint, manualSmsText) {
        if (manualSmsText.isNotBlank()) {
            BankStatementParser.parseSmsBalance(manualSmsText) ?: selectedCheckpoint
        } else {
            selectedCheckpoint
        }
    }

    val verificationResult = remember(activeCheckpoint, currentBalanceMajor) {
        activeCheckpoint?.let { BankStatementParser.verifyBalance(currentBalanceMajor, it) }
    }

    // --- Statement Document Import State (PDF & CSV) ---
    var importedFileName by remember { mutableStateOf<String?>(null) }
    var importedPageCount by remember { mutableStateOf<Int?>(null) }
    var isProcessingDoc by remember { mutableStateOf(false) }
    var docErrorMessage by remember { mutableStateOf<String?>(null) }
    var parsedTransactions by remember { mutableStateOf<List<StatementTransaction>?>(null) }
    val reconciliationReport = remember(parsedTransactions, appTransactions) {
        parsedTransactions?.let { StatementReconciler.reconcile(it, appTransactions) }
    }
    var itemFilter by rememberSaveable { mutableStateOf(ItemFilter.UNMATCHED_ONLY) }

    // Password Prompt Dialog State for encrypted PDFs
    var isPasswordDialogOpen by remember { mutableStateOf(false) }
    var pendingPasswordDocUri by remember { mutableStateOf<Uri?>(null) }
    var passwordInput by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf<String?>(null) }

    fun processDocument(uri: Uri, password: String? = null) {
        isProcessingDoc = true
        docErrorMessage = null
        coroutineScope.launch(Dispatchers.IO) {
            val result = StatementDocumentReader.readDocument(context, uri, password)
            withContext(Dispatchers.Main) {
                isProcessingDoc = false
                when (result) {
                    is DocumentReadResult.Success -> {
                        importedFileName = result.fileName
                        importedPageCount = result.pageCount
                        isPasswordDialogOpen = false
                        passwordInput = ""
                        passwordError = null
                        parsedTransactions = result.transactions
                    }
                    is DocumentReadResult.PasswordRequired -> {
                        pendingPasswordDocUri = result.uri
                        importedFileName = result.fileName
                        passwordError = result.errorMessage
                        isPasswordDialogOpen = true
                    }
                    is DocumentReadResult.Error -> {
                        docErrorMessage = result.message
                    }
                }
            }
        }
    }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            processDocument(uri)
        }
    }

    // Password Dialog
    if (isPasswordDialogOpen && pendingPasswordDocUri != null) {
        AlertDialog(
            onDismissRequest = { isPasswordDialogOpen = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Password Protected PDF")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Enter password to decrypt '${importedFileName ?: "Statement PDF"}'.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = {
                            passwordInput = it
                            passwordError = null
                        },
                        label = { Text("PDF Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        isError = passwordError != null,
                        supportingText = passwordError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val uri = pendingPasswordDocUri ?: return@Button
                        processDocument(uri, passwordInput)
                    },
                    enabled = passwordInput.isNotBlank()
                ) {
                    Text("Unlock & Import")
                }
            },
            dismissButton = {
                TextButton(onClick = { isPasswordDialogOpen = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    BackHandler(onBack = onDismiss)

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        color = MaterialTheme.colorScheme.background
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
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
                        text = { Text("SMS Checkpoint") }
                    )
                    Tab(
                        selected = selectedTab == ReconcileTab.STATEMENT_RECONCILE,
                        onClick = { selectedTab = ReconcileTab.STATEMENT_RECONCILE },
                        text = { Text("Document Import") }
                    )
                }

                when (selectedTab) {
                    ReconcileTab.QUICK_BALANCE -> {
                        QuickBalanceCheckContent(
                            hasSmsPermission = hasSmsPermission,
                            onRequestPermission = { smsPermissionLauncher.launch(Manifest.permission.READ_SMS) },
                            isScanningSms = isScanningSms,
                            smsScanResult = smsScanResult,
                            selectedCheckpoint = activeCheckpoint,
                            onSelectCheckpoint = {
                                selectedCheckpoint = it
                                manualSmsText = ""
                            },
                            onRefreshScan = ::refreshSmsScan,
                            manualSmsText = manualSmsText,
                            onManualSmsTextChange = { manualSmsText = it },
                            onPasteClipboard = {
                                clipboardManager.getText()?.text?.let { manualSmsText = it }
                            },
                            currentBalanceMajor = currentBalanceMajor,
                            currencyUnit = currencyUnit,
                            verificationResult = verificationResult,
                            onSwitchToStatementReconcile = { selectedTab = ReconcileTab.STATEMENT_RECONCILE }
                        )
                    }

                    ReconcileTab.STATEMENT_RECONCILE -> {
                        StatementDocumentImportContent(
                            onPickDocument = {
                                documentPickerLauncher.launch(
                                    arrayOf(
                                        "application/pdf",
                                        "text/csv",
                                        "text/comma-separated-values",
                                        "text/plain",
                                        "*/*"
                                    )
                                )
                            },
                            isProcessing = isProcessingDoc,
                            fileName = importedFileName,
                            pageCount = importedPageCount,
                            errorMessage = docErrorMessage,
                            report = reconciliationReport,
                            itemFilter = itemFilter,
                            onFilterChange = { itemFilter = it },
                            currencyUnit = currencyUnit,
                            onAddMissing = onAddMissingTransaction,
                            onAddAllMissing = onAddAllMissingTransactions
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickBalanceCheckContent(
    hasSmsPermission: Boolean,
    onRequestPermission: () -> Unit,
    isScanningSms: Boolean,
    smsScanResult: SmsScanResult?,
    selectedCheckpoint: BalanceCheckpoint?,
    onSelectCheckpoint: (BalanceCheckpoint) -> Unit,
    onRefreshScan: () -> Unit,
    manualSmsText: String,
    onManualSmsTextChange: (String) -> Unit,
    onPasteClipboard: () -> Unit,
    currentBalanceMajor: BigDecimal,
    currencyUnit: CurrencyUnit,
    verificationResult: BalanceVerificationResult?,
    onSwitchToStatementReconcile: () -> Unit
) {
    var showManualInput by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // SMS Inbox Intelligence Status Banner
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Sms, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = "SMS Inbox Intelligence",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (hasSmsPermission) {
                            IconButton(onClick = onRefreshScan, enabled = !isScanningSms) {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh SMS")
                            }
                        }
                    }

                    if (smsScanResult?.detectedBank != null || smsScanResult?.detectedDigits != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            smsScanResult.detectedBank?.let { bank ->
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        text = bank,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            smsScanResult.detectedDigits?.let { digits ->
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer
                                ) {
                                    Text(
                                        text = "A/c: ...$digits",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (!hasSmsPermission) {
                        Text(
                            text = "Grant SMS permission to automatically scan your inbox for bank balance updates without manual copy-pasting.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = onRequestPermission,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Sms, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Enable Bank SMS Scan")
                        }
                    } else if (isScanningSms) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Text("Scanning device SMS inbox...", style = MaterialTheme.typography.bodySmall)
                        }
                    } else if (smsScanResult?.checkpoints.isNullOrEmpty()) {
                        Text(
                            text = "No bank balance SMS found for this account. You can paste an SMS manually below.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Live Balance Verification Card
        if (verificationResult != null && selectedCheckpoint != null) {
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
                                "${currencyUnit.symbol} ${selectedCheckpoint.availableBalance.setScale(2, RoundingMode.HALF_UP).toPlainString()}",
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
                                Text("Reconcile via Statement Document")
                            }
                        }

                        // Checkpoint detail metadata
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.04f), RoundedCornerShape(8.dp))
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            selectedCheckpoint.transactionDate?.let { date ->
                                Text("Checkpoint Date: $date", style = MaterialTheme.typography.labelSmall)
                            }
                            selectedCheckpoint.transactionAmount?.let { amt ->
                                val sign = if (selectedCheckpoint.isDebit) "-" else "+"
                                Text("Txn Amount: $sign ${currencyUnit.symbol} $amt", style = MaterialTheme.typography.labelSmall)
                            }
                            selectedCheckpoint.info?.let { info ->
                                Text("Txn Info: $info", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }

        // Recent SMS Checkpoints Feed (if available)
        val feed = smsScanResult?.checkpoints.orEmpty()
        if (feed.isNotEmpty()) {
            item {
                Text(
                    text = "Historical SMS Checkpoints (${feed.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            items(feed) { cp ->
                val isSelected = cp == selectedCheckpoint
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectCheckpoint(cp) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        } else {
                            MaterialTheme.colorScheme.surface
                        }
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = cp.transactionDate?.toString() ?: "Recent",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                cp.accountDigits?.let {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant
                                    ) {
                                        Text(
                                            text = "...$it",
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = cp.info ?: cp.rawText.take(50),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Avl: ${currencyUnit.symbol} ${cp.availableBalance.setScale(2, RoundingMode.HALF_UP).toPlainString()}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            cp.transactionAmount?.let { amt ->
                                val sign = if (cp.isDebit) "-" else "+"
                                Text(
                                    text = "$sign ${currencyUnit.symbol} $amt",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (cp.isDebit) MaterialTheme.colorScheme.error else Color(0xFF2E7D32)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Manual SMS Paste Fallback Section
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showManualInput = !showManualInput }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Manual SMS Text Entry",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    imageVector = if (showManualInput) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null
                )
            }
        }

        if (showManualInput) {
            item {
                OutlinedTextField(
                    value = manualSmsText,
                    onValueChange = onManualSmsTextChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Paste Bank SMS or Message") },
                    placeholder = { Text("ICICI Bank Acc XX407 debited Rs. 489.00 on 01-Nov-26... Avl Bal Rs. 31,500.23") },
                    trailingIcon = {
                        IconButton(onClick = onPasteClipboard) {
                            Icon(Icons.Default.ContentPaste, contentDescription = "Paste")
                        }
                    },
                    maxLines = 4
                )
            }
        }
    }
}

@Composable
private fun StatementDocumentImportContent(
    onPickDocument: () -> Unit,
    isProcessing: Boolean,
    fileName: String?,
    pageCount: Int?,
    errorMessage: String?,
    report: ReconciliationReport?,
    itemFilter: ItemFilter,
    onFilterChange: (ItemFilter) -> Unit,
    currencyUnit: CurrencyUnit,
    onAddMissing: (StatementTransaction) -> Unit,
    onAddAllMissing: ((List<StatementTransaction>) -> Unit)? = null
) {
    if (report == null) {
        // Document Import Prompt Card
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        modifier = Modifier.size(56.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.UploadFile,
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Text(
                        text = "Document-Only Statement Import",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "Select your bank statement PDF (including password-encrypted statements) or CSV. MyExpenses extracts transactions with 100% precision and reconciles them against your ledger.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (isProcessing) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text("Processing document & extracting tables...", style = MaterialTheme.typography.bodySmall)
                        }
                    } else {
                        Button(
                            onClick = onPickDocument,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Choose Statement File (PDF / CSV)")
                        }
                    }

                    if (errorMessage != null) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                Text(
                                    text = errorMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            // Sleek, compact summary bar (~56dp)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Row 1: File pill + Match Rate badge + Change File action
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f, fill = false),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Default.Description,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = fileName ?: "Statement Document",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            pageCount?.let { pages ->
                                Text(
                                    text = "(${pages}p)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val matchPercent = if (report.totalStatementCount > 0) {
                                (report.matchedCount.toDouble() / report.totalStatementCount.toDouble()) * 100.0
                            } else 0.0

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (report.unmatchedCount == 0) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "${String.format(java.util.Locale.US, "%.1f", matchPercent)}% (${report.matchedCount}/${report.totalStatementCount})",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (report.unmatchedCount == 0) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }

                            Text(
                                text = "Change",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable(onClick = onPickDocument)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    // Row 2: Filter chips + Add All (N) button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = itemFilter == ItemFilter.UNMATCHED_ONLY,
                                onClick = { onFilterChange(ItemFilter.UNMATCHED_ONLY) },
                                label = { Text("Missing (${report.unmatchedCount})", style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.height(28.dp)
                            )
                            FilterChip(
                                selected = itemFilter == ItemFilter.MATCHED_ONLY,
                                onClick = { onFilterChange(ItemFilter.MATCHED_ONLY) },
                                label = { Text("Matched (${report.matchedCount})", style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.height(28.dp)
                            )
                            FilterChip(
                                selected = itemFilter == ItemFilter.ALL,
                                onClick = { onFilterChange(ItemFilter.ALL) },
                                label = { Text("All (${report.totalStatementCount})", style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.height(28.dp)
                            )
                        }

                        if (report.unmatchedCount > 0 && onAddAllMissing != null) {
                            FilledTonalButton(
                                onClick = {
                                    val missing = report.items
                                        .filter { it.status is MatchStatus.UnmatchedInApp }
                                        .map { it.statementTx }
                                    onAddAllMissing(missing)
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(13.dp))
                                Spacer(Modifier.width(2.dp))
                                Text(
                                    text = "Add All (${report.unmatchedCount})",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

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
                    .weight(1f)
            ) {
                items(displayedItems, key = { it.statementTx.sNo }) { item ->
                    StatementItemRow(
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
private fun StatementItemRow(
    item: ReconcileResult,
    currencyUnit: CurrencyUnit,
    onAdd: () -> Unit
) {
    val tx = item.statementTx
    val isWithdrawal = tx.withdrawal > BigDecimal.ZERO || tx.amount < BigDecimal.ZERO
    val status = item.status
    val isUnmatched = status is MatchStatus.UnmatchedInApp

    val initial = tx.payeeOrRemarks.trimStart().firstOrNull()?.uppercaseChar()?.toString() ?: if (isWithdrawal) "W" else "D"
    val avatarBg = if (isWithdrawal) {
        MaterialTheme.colorScheme.surfaceContainerHighest
    } else {
        Color(0xFFE8F5E9)
    }
    val avatarTextColor = if (isWithdrawal) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        Color(0xFF2E7D32)
    }

    val formattedDate = remember(tx.date) {
        try {
            tx.date.format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy"))
        } catch (_: Exception) {
            tx.date.toString()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isUnmatched) {
                    Modifier.background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.06f))
                } else {
                    Modifier
                }
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 40dp Avatar
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = avatarBg
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = initial,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = avatarTextColor
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            // Info Column
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = tx.payeeOrRemarks,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = formattedDate,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (tx.utr != null) {
                        Text(
                            text = "• UTR: ${tx.utr}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                when (status) {
                    is MatchStatus.Matched -> {
                        Text(
                            text = "✓ Matched (App ID: ${status.appTransactionId})",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF2E7D32)
                        )
                    }
                    is MatchStatus.UnmatchedInApp -> {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                        ) {
                            Text(
                                text = "Missing from App",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.width(8.dp))

            // Amount and Action Button
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val amt = if (isWithdrawal) tx.withdrawal else tx.deposit
                Text(
                    text = "${if (isWithdrawal) "-" else "+"} ${currencyUnit.symbol} ${amt.setScale(2, RoundingMode.HALF_UP).toPlainString()}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isWithdrawal) MaterialTheme.colorScheme.error else Color(0xFF2E7D32)
                )

                if (isUnmatched) {
                    FilledTonalButton(
                        onClick = onAdd,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(2.dp))
                        Text("Add", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(start = 68.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )
    }
}
