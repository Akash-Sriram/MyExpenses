package org.totschnig.myexpenses.compose.transactions

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.booleanResource
import androidx.compose.ui.window.DialogProperties
import org.totschnig.myexpenses.compose.TEST_TAG_DIALOG
import org.totschnig.myexpenses.compose.conditional

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.FilledTonalButton
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import org.totschnig.myexpenses.MyApplication
import org.totschnig.myexpenses.activity.SystemViewIntentProvider
import org.totschnig.myexpenses.util.PictureDirHelper
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.res.painterResource
import timber.log.Timber
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.activity.Action as ManageAction
import org.totschnig.myexpenses.activity.ManageCategories
import org.totschnig.myexpenses.compose.LocalColors
import org.totschnig.myexpenses.compose.LocalCurrencyFormatter
import org.totschnig.myexpenses.compose.tags.TagsScreen
import org.totschnig.myexpenses.contract.TransactionsContract.Transactions.TYPE_SPLIT
import org.totschnig.myexpenses.contract.TransactionsContract.Transactions.TYPE_TRANSACTION
import org.totschnig.myexpenses.contract.TransactionsContract.Transactions.TYPE_TRANSFER
import org.totschnig.myexpenses.db2.FLAG_EXPENSE
import org.totschnig.myexpenses.db2.FLAG_INCOME
import org.totschnig.myexpenses.db2.FLAG_NEUTRAL
import org.totschnig.myexpenses.db2.FLAG_TRANSFER
import org.totschnig.myexpenses.model.CurrencyUnit
import org.totschnig.myexpenses.model.Money
import org.totschnig.myexpenses.model.generateUuid
import org.totschnig.myexpenses.provider.BaseTransactionProvider
import org.totschnig.myexpenses.provider.KEY_ACCOUNTID
import org.totschnig.myexpenses.provider.KEY_ICON
import org.totschnig.myexpenses.provider.KEY_LABEL
import org.totschnig.myexpenses.provider.KEY_ROWID
import org.totschnig.myexpenses.provider.KEY_TYPE
import org.totschnig.myexpenses.ui.DisplayParty
import org.totschnig.myexpenses.viewmodel.CategoryViewModel
import org.totschnig.myexpenses.viewmodel.data.Account
import org.totschnig.myexpenses.viewmodel.data.Category
import org.totschnig.myexpenses.viewmodel.data.PaymentMethod
import org.totschnig.myexpenses.viewmodel.data.Tag
import org.totschnig.myexpenses.viewmodel.data.TransactionEditData
import org.totschnig.myexpenses.viewmodel.data.TransferEditData
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

data class QuickCategory(
    val id: Long,
    val label: String,
    val path: String = label,
    val icon: String? = null,
    val color: Int? = null,
    val type: Byte = FLAG_NEUTRAL
)

enum class TransactionTypeOption {
    EXPENSE,
    INCOME,
    TRANSFER
}

/**
 * Modern Material 3 Native Jetpack Compose Transaction Editor for Expenses.
 * Eliminates the legacy XML ExpenseEdit activity with inline calculator,
 * smart category carousel, and in-place tag selection.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExpenseEditScreen(
    initialData: TransactionEditData?,
    accounts: List<Account>,
    availableMethods: List<PaymentMethod> = emptyList(),
    quickCategories: List<QuickCategory> = emptyList(),
    allCategories: List<Category> = emptyList(),
    allTags: List<Tag> = emptyList(),
    onSave: (TransactionEditData) -> Unit,
    onClose: () -> Unit,
    onOpenCategoryPicker: () -> Unit = {},
    onAddTag: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val currencyFormatter = LocalCurrencyFormatter.current
    val colors = LocalColors.current
    val context = LocalContext.current

    // Transaction Type
    var selectedType by rememberSaveable {
        mutableStateOf(
            when {
                initialData?.isTransfer == true -> TransactionTypeOption.TRANSFER
                initialData?.amount?.amountMajor?.signum() == 1 -> TransactionTypeOption.INCOME
                else -> TransactionTypeOption.EXPENSE
            }
        )
    }

    // Accounts
    var selectedAccountId by rememberSaveable {
        mutableLongStateOf(initialData?.accountId ?: accounts.firstOrNull()?.id ?: 0L)
    }
    var transferAccountId by rememberSaveable {
        mutableLongStateOf(initialData?.transferEditData?.transferAccountId ?: accounts.getOrNull(1)?.id ?: 0L)
    }

    val selectedAccount = accounts.find { it.id == selectedAccountId } ?: accounts.firstOrNull()
    val transferAccount = accounts.find { it.id == transferAccountId } ?: accounts.getOrNull(1)

    // Amount & Calculator
    var amount by remember {
        mutableStateOf(initialData?.amount?.amountMajor?.abs() ?: BigDecimal.ZERO)
    }
    var showCalculator by rememberSaveable {
        mutableStateOf(false)
    }

    // Category
    var selectedCategoryId by rememberSaveable {
        mutableStateOf(initialData?.categoryId)
    }
    var selectedCategoryPath by rememberSaveable {
        mutableStateOf(initialData?.categoryPath)
    }
    var selectedCategoryIcon by rememberSaveable {
        mutableStateOf(initialData?.categoryIcon)
    }
    var showCategoryPicker by rememberSaveable {
        mutableStateOf(false)
    }

    var loadedCategories by remember { mutableStateOf(allCategories) }

    LaunchedEffect(allCategories) {
        if (allCategories.isNotEmpty()) {
            loadedCategories = allCategories
        } else {
            withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.query(
                        BaseTransactionProvider.CATEGORY_TREE_URI,
                        null, null, null, null
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            loadedCategories = CategoryViewModel.ingest(
                                withColors = true,
                                cursor = cursor,
                                parentId = null,
                                level = 1
                            )
                        }
                    }
                } catch (_: Exception) {}
            }
        }
    }

    val categoryManagerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            if (data != null) {
                val catId = data.getLongExtra(KEY_ROWID, 0L)
                val catLabel = data.getStringExtra(KEY_LABEL)
                val catIcon = data.getStringExtra(KEY_ICON)
                if (catId != 0L && !catLabel.isNullOrBlank()) {
                    selectedCategoryId = catId
                    selectedCategoryPath = catLabel
                    selectedCategoryIcon = catIcon
                    showCategoryPicker = false
                }
            }
        }
    }

    val openManageCategories = {
        val intent = Intent(context, ManageCategories::class.java).apply {
            action = ManageAction.SELECT_MAPPING.name
            putExtra(KEY_ACCOUNTID, selectedAccountId)
            putExtra(KEY_TYPE, if (selectedType == TransactionTypeOption.INCOME) FLAG_INCOME else FLAG_EXPENSE)
        }
        categoryManagerLauncher.launch(intent)
    }

    val openCategoryPicker = {
        if (onOpenCategoryPicker != {}) {
            onOpenCategoryPicker()
        }
        showCategoryPicker = true
    }

    // Payee
    var payeeText by rememberSaveable {
        mutableStateOf(initialData?.party?.name ?: "")
    }

    // Date & Time
    var date by remember {
        mutableStateOf(initialData?.date?.toLocalDate() ?: LocalDate.now())
    }
    var time by remember {
        mutableStateOf(initialData?.date?.toLocalTime() ?: LocalTime.now())
    }

    // Tags
    var selectedTags by remember {
        mutableStateOf(initialData?.tags ?: emptyList())
    }
    var showTagSheet by remember { mutableStateOf(false) }

    // Method
    var selectedMethodId by rememberSaveable {
        mutableStateOf(initialData?.methodId)
    }

    // Notes & Metadata
    var comment by rememberSaveable {
        mutableStateOf(initialData?.comment ?: "")
    }
    var referenceNumber by rememberSaveable {
        mutableStateOf(initialData?.referenceNumber ?: "")
    }
    var attachments by remember(initialData?.id, initialData?.uuid) {
        mutableStateOf(initialData?.allAttachments ?: emptyList())
    }
    var pictureUri by remember(initialData?.id, initialData?.uuid) {
        mutableStateOf(initialData?.pictureUri ?: initialData?.allAttachments?.firstOrNull())
    }

    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val copiedUri = copyUriToAppStorage(context, uri)
            attachments = attachments + copiedUri
            pictureUri = attachments.firstOrNull()
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success && tempCameraUri != null) {
            attachments = attachments + tempCameraUri!!
            pictureUri = attachments.firstOrNull()
        }
    }

    val documentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val copiedUri = copyUriToAppStorage(context, uri)
            attachments = attachments + copiedUri
            pictureUri = attachments.firstOrNull()
        }
    }

    val launchCamera = {
        try {
            val app = context.applicationContext as MyApplication
            val uri = PictureDirHelper.getOutputMediaUri(false, app, prefix = "receipt_")
            tempCameraUri = uri
            cameraLauncher.launch(uri)
        } catch (e: Exception) {
            Timber.e(e, "Failed to launch camera")
        }
    }

    val launchGallery = {
        try {
            galleryLauncher.launch("image/*")
        } catch (e: Exception) {
            Timber.e(e, "Failed to launch gallery picker")
        }
    }

    val launchDocument = {
        try {
            documentLauncher.launch(arrayOf("*/*"))
        } catch (e: Exception) {
            Timber.e(e, "Failed to launch document picker")
        }
    }

    // Update state when initialData changes (e.g. OCR completes asynchronously)
    LaunchedEffect(initialData) {
        if (initialData != null) {
            amount = initialData.amount.amountMajor.abs()
            selectedAccountId = initialData.accountId
            transferAccountId = initialData.transferEditData?.transferAccountId ?: accounts.getOrNull(1)?.id ?: 0L
            selectedCategoryId = initialData.categoryId
            selectedCategoryPath = initialData.categoryPath
            selectedCategoryIcon = initialData.categoryIcon
            payeeText = initialData.party?.name ?: ""
            date = initialData.date.toLocalDate()
            time = initialData.date.toLocalTime()
            selectedTags = initialData.tags
            selectedMethodId = initialData.methodId
            comment = initialData.comment ?: ""
            referenceNumber = initialData.referenceNumber ?: ""
            attachments = initialData.allAttachments
            pictureUri = initialData.pictureUri ?: initialData.allAttachments.firstOrNull()
            selectedType = when {
                initialData.isTransfer -> TransactionTypeOption.TRANSFER
                initialData.amount.amountMajor.signum() == 1 -> TransactionTypeOption.INCOME
                else -> TransactionTypeOption.EXPENSE
            }
        }
    }

    // Dialog & Picker States
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showAccountMenu by remember { mutableStateOf(false) }
    var showTransferAccountMenu by remember { mutableStateOf(false) }
    var showMethodMenu by remember { mutableStateOf(false) }

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    )
    val timePickerState = rememberTimePickerState(
        initialHour = time.hour,
        initialMinute = time.minute
    )

    fun handleSave() {
        val currency = selectedAccount?.currency ?: CurrencyUnit("INR", "₹", 2)
        val sign = if (selectedType == TransactionTypeOption.INCOME) 1 else -1
        val finalAmountPaise = (amount * BigDecimal.TEN.pow(currency.fractionDigits)).toLong() * sign

        val fullDateTime = LocalDateTime.of(date, time)
        val isTransfer = selectedType == TransactionTypeOption.TRANSFER

        val resultData = (initialData ?: TransactionEditData(
            uuid = generateUuid(),
            amount = Money(currency, finalAmountPaise),
            accountId = selectedAccountId
        )).copy(
            amount = Money(currency, finalAmountPaise),
            accountId = selectedAccountId,
            date = fullDateTime,
            valueDate = date,
            party = if (payeeText.isNotBlank()) DisplayParty(id = initialData?.party?.id, name = payeeText.trim()) else null,
            categoryId = if (!isTransfer) selectedCategoryId else null,
            categoryPath = if (!isTransfer) selectedCategoryPath else null,
            categoryIcon = if (!isTransfer) selectedCategoryIcon else null,
            tags = selectedTags,
            methodId = selectedMethodId,
            methodLabel = availableMethods.find { it.id == selectedMethodId }?.label,
            comment = comment.takeIf { it.isNotBlank() },
            referenceNumber = referenceNumber.takeIf { it.isNotBlank() },
            pictureUri = attachments.firstOrNull(),
            attachments = attachments,
            transferEditData = if (isTransfer) TransferEditData(transferAccountId = transferAccountId) else null
        )

        onSave(resultData)
    }

    val isLarge = booleanResource(R.bool.isLarge)
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = isLarge
        )
    ) {
        Surface(
            modifier = modifier
                .testTag(TEST_TAG_DIALOG)
                .conditional(
                    isLarge,
                    ifTrue = {
                        widthIn(max = 560.dp)
                            .heightIn(max = 800.dp)
                            .fillMaxHeight(0.95f)
                    }, ifFalse = { fillMaxSize() }
                ),
            shape = if (isLarge) MaterialTheme.shapes.extraLarge else RectangleShape
        ) {
            Scaffold(
                topBar = {

            TopAppBar(
                title = {
                    Text(
                        text = if (initialData != null && initialData.id > 0) {
                            stringResource(R.string.menu_edit_transaction)
                        } else when (selectedType) {
                            TransactionTypeOption.EXPENSE -> stringResource(R.string.menu_create_transaction)
                            TransactionTypeOption.INCOME -> stringResource(R.string.income)
                            TransactionTypeOption.TRANSFER -> stringResource(R.string.menu_create_transfer)
                        },
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.menu_close))
                    }
                },
                actions = {
                    Button(
                        onClick = ::handleSave,
                        enabled = amount > BigDecimal.ZERO && selectedAccount != null,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.menu_save))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {

            // 1. Transaction Type Toggle
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth()
            ) {
                TransactionTypeOption.entries.forEachIndexed { index, option ->
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = TransactionTypeOption.entries.size),
                        onClick = { selectedType = option },
                        selected = selectedType == option,
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = when (option) {
                                TransactionTypeOption.EXPENSE -> colors.expense.copy(alpha = 0.15f)
                                TransactionTypeOption.INCOME -> colors.income.copy(alpha = 0.15f)
                                TransactionTypeOption.TRANSFER -> colors.transfer.copy(alpha = 0.15f)
                            },
                            activeContentColor = when (option) {
                                TransactionTypeOption.EXPENSE -> colors.expense
                                TransactionTypeOption.INCOME -> colors.income
                                TransactionTypeOption.TRANSFER -> colors.transfer
                            }
                        )
                    ) {
                        Text(
                            text = when (option) {
                                TransactionTypeOption.EXPENSE -> stringResource(R.string.expense)
                                TransactionTypeOption.INCOME -> stringResource(R.string.income)
                                TransactionTypeOption.TRANSFER -> stringResource(R.string.transfer)
                            },
                            fontWeight = if (selectedType == option) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            // 2. Card 1: Primary Details (Amount, Keypad, Account, Payee, Category)
            OutlinedCard(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Amount Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = stringResource(R.string.amount),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = selectedAccount?.currency?.symbol ?: "₹",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        color = when (selectedType) {
                                            TransactionTypeOption.EXPENSE -> colors.expense
                                            TransactionTypeOption.INCOME -> colors.income
                                            TransactionTypeOption.TRANSFER -> colors.transfer
                                        },
                                        fontWeight = FontWeight.Bold
                                    ),
                                    modifier = Modifier.padding(end = 6.dp)
                                )
                                Text(
                                    text = if (amount == BigDecimal.ZERO) "0.00" else amount.stripTrailingZeros().toPlainString(),
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        IconButton(
                            onClick = { showCalculator = !showCalculator },
                            modifier = Modifier
                                .background(
                                    if (showCalculator) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                    shape = CircleShape
                                )
                        ) {
                            Icon(
                                Icons.Default.Calculate,
                                contentDescription = "Toggle Calculator",
                                tint = if (showCalculator) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = showCalculator,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(modifier = Modifier.padding(top = 4.dp)) {
                            HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))
                            CalculatorKeypad(
                                initialAmount = amount,
                                onAmountCommitted = { newAmount ->
                                    amount = newAmount
                                    showCalculator = false
                                }
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Account Row
                    Box {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .clickable { showAccountMenu = true }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(Color(selectedAccount?.color ?: android.graphics.Color.GRAY))
                                )
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = if (selectedType == TransactionTypeOption.TRANSFER) stringResource(R.string.transfer_from_account) else stringResource(R.string.account),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = selectedAccount?.label ?: stringResource(R.string.account),
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                }
                            }
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        DropdownMenu(
                            expanded = showAccountMenu,
                            onDismissRequest = { showAccountMenu = false }
                        ) {
                            accounts.forEach { account ->
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(10.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(account.color))
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(account.label)
                                        }
                                    },
                                    onClick = {
                                        selectedAccountId = account.id
                                        showAccountMenu = false
                                    }
                                )
                            }
                        }
                    }

                    // Target Account for Transfers
                    if (selectedType == TransactionTypeOption.TRANSFER) {
                        Box {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .clickable { showTransferAccountMenu = true }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clip(CircleShape)
                                            .background(Color(transferAccount?.color ?: android.graphics.Color.GRAY))
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = stringResource(R.string.transfer_to_account),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = transferAccount?.label ?: stringResource(R.string.account),
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                        )
                                    }
                                }
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }

                            DropdownMenu(
                                expanded = showTransferAccountMenu,
                                onDismissRequest = { showTransferAccountMenu = false }
                            ) {
                                accounts.filter { it.id != selectedAccountId }.forEach { account ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(10.dp)
                                                        .clip(CircleShape)
                                                        .background(Color(account.color))
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Text(account.label)
                                            }
                                        },
                                        onClick = {
                                            transferAccountId = account.id
                                            showTransferAccountMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Payee / Payer Field
                    if (selectedType != TransactionTypeOption.TRANSFER) {
                        OutlinedTextField(
                            value = payeeText,
                            onValueChange = { payeeText = it },
                            label = {
                                Text(
                                    if (selectedType == TransactionTypeOption.INCOME) stringResource(R.string.payer)
                                    else stringResource(R.string.payee)
                                )
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(20.dp))
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        // Category Selection & Carousel
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            // 1. Selected Category Banner or "Select Category..." Action Card
                            if (selectedCategoryId != null && selectedCategoryPath != null) {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { openCategoryPicker() },
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CategoryIconBadge(
                                            icon = selectedCategoryIcon,
                                            color = null,
                                            type = if (selectedType == TransactionTypeOption.INCOME) FLAG_INCOME else FLAG_EXPENSE,
                                            size = 32.dp
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = stringResource(R.string.category),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = selectedCategoryPath!!.replace(" : ", " > "),
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                selectedCategoryId = null
                                                selectedCategoryPath = null
                                                selectedCategoryIcon = null
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "Clear category",
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            } else {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { openCategoryPicker() },
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.Category,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Spacer(Modifier.width(10.dp))
                                        Text(
                                            text = "Select Category...",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Icon(
                                            Icons.Default.Search,
                                            contentDescription = "Browse categories",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            // 2. Quick Category Carousel
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                quickCategories.forEach { cat ->
                                    val isSelected = selectedCategoryId == cat.id
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            if (isSelected) {
                                                selectedCategoryId = null
                                                selectedCategoryPath = null
                                                selectedCategoryIcon = null
                                            } else {
                                                selectedCategoryId = cat.id
                                                selectedCategoryPath = cat.path
                                                selectedCategoryIcon = cat.icon
                                            }
                                        },
                                        label = { Text(cat.label, style = MaterialTheme.typography.labelMedium) },
                                        leadingIcon = {
                                            if (!cat.icon.isNullOrBlank()) {
                                                org.totschnig.myexpenses.compose.Icon(
                                                    icon = cat.icon,
                                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else (cat.color?.let { Color(it) } ?: MaterialTheme.colorScheme.onSurfaceVariant),
                                                    size = 14.sp,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            } else {
                                                Icon(
                                                    Icons.Default.Category,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    )
                                }

                                InputChip(
                                    selected = false,
                                    onClick = { openCategoryPicker() },
                                    label = { Text("All Categories...", style = MaterialTheme.typography.labelMedium) },
                                    leadingIcon = {
                                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, modifier = Modifier.size(14.dp))
                                    },
                                    shape = RoundedCornerShape(10.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 3. Card 2: Additional Details (Date/Time, Method, Receipt, Reference #, Tags, Notes)
            OutlinedCard(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Date & Time Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .clickable { showDatePicker = true }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.DateRange, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(stringResource(R.string.date), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .clickable { showTimePicker = true }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(stringResource(R.string.time), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = time.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                            }
                        }
                    }

                    // Payment Method (if methods available and not transfer)
                    if (availableMethods.isNotEmpty() && selectedType != TransactionTypeOption.TRANSFER) {
                        val methodLabel = availableMethods.find { it.id == selectedMethodId }?.label ?: stringResource(R.string.method)
                        Box {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .clickable { showMethodMenu = true }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Payment, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text(stringResource(R.string.method), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(methodLabel, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                    }
                                }
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }

                            DropdownMenu(
                                expanded = showMethodMenu,
                                onDismissRequest = { showMethodMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.none)) },
                                    onClick = {
                                        selectedMethodId = null
                                        showMethodMenu = false
                                    }
                                )
                                availableMethods.forEach { method ->
                                    DropdownMenuItem(
                                        text = { Text(method.label) },
                                        onClick = {
                                            selectedMethodId = method.id
                                            showMethodMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Receipt / Attachment Section
                    AttachmentSection(
                        attachments = attachments,
                        onAttachCamera = launchCamera,
                        onAttachGallery = launchGallery,
                        onAttachDocument = launchDocument,
                        onRemove = { removedUri ->
                            attachments = attachments - removedUri
                            pictureUri = attachments.firstOrNull()
                        }
                    )

                    // Reference Number / UTR Field
                    if (selectedType != TransactionTypeOption.TRANSFER) {
                        OutlinedTextField(
                            value = referenceNumber,
                            onValueChange = { referenceNumber = it },
                            label = { Text("Reference / UTR #") },
                            leadingIcon = {
                                Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null, modifier = Modifier.size(18.dp))
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }

                    // Tags Section
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = stringResource(R.string.tags),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            selectedTags.forEach { tag ->
                                InputChip(
                                    selected = true,
                                    onClick = {
                                        selectedTags = selectedTags.filter { it.id != tag.id }
                                    },
                                    label = { Text(tag.label, style = MaterialTheme.typography.labelSmall) },
                                    trailingIcon = {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Remove tag",
                                            modifier = Modifier.size(12.dp)
                                        )
                                    },
                                    shape = RoundedCornerShape(10.dp)
                                )
                            }

                            InputChip(
                                selected = false,
                                onClick = { showTagSheet = true },
                                label = { Text(stringResource(R.string.tags), style = MaterialTheme.typography.labelSmall) },
                                leadingIcon = {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                },
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    }

                    // Notes / Comment Field
                    OutlinedTextField(
                        value = comment,
                        onValueChange = { comment = it },
                        label = { Text(stringResource(R.string.notes)) },
                        leadingIcon = {
                            Icon(Icons.AutoMirrored.Filled.Notes, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 1,
                        maxLines = 3
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }

    // DatePicker Dialog
    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        date = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
                    }
                    showDatePicker = false
                }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // TimePicker Dialog
    if (showTimePicker) {
        Dialog(onDismissRequest = { showTimePicker = false }) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    TimePicker(state = timePickerState)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showTimePicker = false }) {
                            Text(stringResource(android.R.string.cancel))
                        }
                        TextButton(onClick = {
                            time = LocalTime.of(timePickerState.hour, timePickerState.minute)
                            showTimePicker = false
                        }) {
                            Text(stringResource(android.R.string.ok))
                        }
                    }
                }
            }
        }
    }
        }
    }

    // Tags Selection Dialog
    if (showTagSheet) {
        Dialog(
            onDismissRequest = { showTagSheet = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                TagsScreen(
                    action = ManageAction.SELECT_MAPPING,
                    tags = allTags,
                    selectedTagIds = selectedTags.map { it.id }.toSet(),
                    onToggleSelectTag = { tagId ->
                        val tag = allTags.find { it.id == tagId }
                        if (tag != null) {
                            selectedTags = if (selectedTags.any { it.id == tagId }) {
                                selectedTags.filter { it.id != tagId }
                            } else {
                                selectedTags + tag
                            }
                        }
                    },
                    onAddTag = onAddTag,
                    onUpdateTag = { _, _, _ -> },
                    onDeleteTag = {},
                    onConfirm = { showTagSheet = false },
                    onClose = { showTagSheet = false }
                )
            }
        }
    }

    // Category Selection Dialog
    if (showCategoryPicker) {
        Dialog(
            onDismissRequest = { showCategoryPicker = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                CategoryPickerDialog(
                    categories = loadedCategories,
                    selectedCategoryId = selectedCategoryId,
                    currentType = if (selectedType == TransactionTypeOption.INCOME) FLAG_INCOME else FLAG_EXPENSE,
                    onCategorySelected = { catId, catPath, catIcon ->
                        selectedCategoryId = catId
                        selectedCategoryPath = catPath
                        selectedCategoryIcon = catIcon
                        showCategoryPicker = false
                    },
                    onDismissRequest = { showCategoryPicker = false },
                    onOpenManageCategories = openManageCategories
                )
            }
        }
    }
}

@Composable
fun CategoryIconBadge(
    icon: String?,
    color: Int?,
    type: Byte = FLAG_EXPENSE,
    size: androidx.compose.ui.unit.Dp = 32.dp,
    modifier: Modifier = Modifier
) {
    val typeColor = when (type) {
        FLAG_EXPENSE -> LocalColors.current.expense
        FLAG_INCOME -> LocalColors.current.income
        FLAG_TRANSFER -> LocalColors.current.transfer
        else -> MaterialTheme.colorScheme.primary
    }
    val tintColor = color?.let { Color(it) } ?: typeColor

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(tintColor.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        if (!icon.isNullOrBlank()) {
            org.totschnig.myexpenses.compose.Icon(
                icon = icon,
                color = tintColor,
                size = (size.value * 0.55f).sp,
                modifier = Modifier.size((size.value * 0.6f).dp)
            )
        } else {
            Icon(
                imageVector = Icons.Default.Category,
                contentDescription = null,
                tint = tintColor,
                modifier = Modifier.size((size.value * 0.55f).dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryPickerDialog(
    categories: List<Category>,
    selectedCategoryId: Long?,
    currentType: Byte,
    onCategorySelected: (Long?, String?, String?) -> Unit,
    onDismissRequest: () -> Unit,
    onOpenManageCategories: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedTypeFilter by remember { mutableStateOf<Byte?>(currentType) }
    val expandedIds = remember { mutableStateListOf<Long>() }

    val allFlat = remember(categories) {
        categories.flatMap { it.flatten() }
    }

    val searchResults = remember(searchQuery, selectedTypeFilter, allFlat) {
        if (searchQuery.isBlank()) emptyList()
        else {
            val q = searchQuery.trim().lowercase()
            allFlat.filter { cat ->
                (selectedTypeFilter == null || cat.typeFlags == selectedTypeFilter || cat.typeFlags == FLAG_NEUTRAL) &&
                (cat.label.lowercase().contains(q) || cat.path.lowercase().contains(q))
            }
        }
    }

    val filteredRoots = remember(categories, selectedTypeFilter) {
        categories.filter { cat ->
            selectedTypeFilter == null || cat.typeFlags == selectedTypeFilter || cat.typeFlags == FLAG_NEUTRAL
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.select_category), fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = onDismissRequest) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = onOpenManageCategories) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Manage")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Search TextField
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search categories and subcategories...") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp))
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search", modifier = Modifier.size(18.dp))
                        }
                    }
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Type Filter Chips: All | Expense | Income
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = selectedTypeFilter == null,
                    onClick = { selectedTypeFilter = null },
                    label = { Text("All") },
                    shape = RoundedCornerShape(10.dp)
                )
                FilterChip(
                    selected = selectedTypeFilter == FLAG_EXPENSE,
                    onClick = { selectedTypeFilter = FLAG_EXPENSE },
                    label = { Text(stringResource(R.string.expense)) },
                    shape = RoundedCornerShape(10.dp)
                )
                FilterChip(
                    selected = selectedTypeFilter == FLAG_INCOME,
                    onClick = { selectedTypeFilter = FLAG_INCOME },
                    label = { Text(stringResource(R.string.income)) },
                    shape = RoundedCornerShape(10.dp)
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

            // Category List / Tree
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                // Option to unmap / clear category
                item(key = "unmapped_root") {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                onCategorySelected(null, null, null)
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = if (selectedCategoryId == null) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            }
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = stringResource(R.string.unmapped),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (selectedCategoryId == null) FontWeight.Bold else FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                if (searchQuery.isNotBlank()) {
                    // Search results mode (flat)
                    if (searchResults.isEmpty()) {
                        item(key = "empty_search") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No matching categories found",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        items(searchResults, key = { "search_${it.id}" }) { cat ->
                            CategorySearchResultRow(
                                category = cat,
                                isSelected = selectedCategoryId == cat.id,
                                onClick = {
                                    onCategorySelected(cat.id, cat.path, cat.icon)
                                }
                            )
                        }
                    }
                } else {
                    // Tree mode
                    if (filteredRoots.isEmpty()) {
                        item(key = "empty_tree") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No categories available",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        filteredRoots.forEach { rootCat ->
                            renderCategoryTreeItem(
                                category = rootCat,
                                level = 0,
                                selectedCategoryId = selectedCategoryId,
                                expandedIds = expandedIds,
                                onToggleExpand = { id ->
                                    if (expandedIds.contains(id)) {
                                        expandedIds.remove(id)
                                    } else {
                                        expandedIds.add(id)
                                    }
                                },
                                onSelect = { cat ->
                                    onCategorySelected(cat.id, cat.path, cat.icon)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategorySearchResultRow(
    category: Category,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
            }
        ),
        shape = RoundedCornerShape(10.dp),
        border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CategoryIconBadge(
                icon = category.icon,
                color = category.color,
                type = category.typeFlags,
                size = 32.dp
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = category.label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (category.path != category.label) {
                    Text(
                        text = category.path.replace(" : ", " > "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (isSelected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.renderCategoryTreeItem(
    category: Category,
    level: Int,
    selectedCategoryId: Long?,
    expandedIds: List<Long>,
    onToggleExpand: (Long) -> Unit,
    onSelect: (Category) -> Unit
) {
    val isExpanded = expandedIds.contains(category.id)
    val hasChildren = category.children.isNotEmpty()
    val isSelected = selectedCategoryId == category.id

    item(key = "tree_${category.id}") {
        CategoryTreeNodeRow(
            category = category,
            level = level,
            isSelected = isSelected,
            isExpanded = isExpanded,
            hasChildren = hasChildren,
            onToggleExpand = { onToggleExpand(category.id) },
            onSelect = { onSelect(category) }
        )
    }

    if (hasChildren && isExpanded) {
        category.children.forEach { child ->
            renderCategoryTreeItem(
                category = child,
                level = level + 1,
                selectedCategoryId = selectedCategoryId,
                expandedIds = expandedIds,
                onToggleExpand = onToggleExpand,
                onSelect = onSelect
            )
        }
    }
}

@Composable
private fun CategoryTreeNodeRow(
    category: Category,
    level: Int,
    isSelected: Boolean,
    isExpanded: Boolean,
    hasChildren: Boolean,
    onToggleExpand: () -> Unit,
    onSelect: () -> Unit
) {
    val indent = (level * 20).dp

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = indent)
            .clip(RoundedCornerShape(10.dp))
            .clickable {
                if (hasChildren) {
                    onToggleExpand()
                } else {
                    onSelect()
                }
            },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            } else if (level == 0) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f)
            }
        ),
        shape = RoundedCornerShape(10.dp),
        border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CategoryIconBadge(
                icon = category.icon,
                color = category.color,
                type = category.typeFlags,
                size = 32.dp
            )

            Spacer(Modifier.width(10.dp))

            // Label & subcategory count
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = category.label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (level == 0) FontWeight.SemiBold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (hasChildren) {
                    Text(
                        text = "${category.children.size} subcategories",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // If has children, show direct Select button AND Expand/Collapse chevron
            if (hasChildren) {
                IconButton(
                    onClick = onSelect,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Select this category",
                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = onToggleExpand,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        if (isExpanded) Icons.Default.ExpandMore else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else if (isSelected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun AttachmentSection(
    attachments: List<Uri>,
    onAttachCamera: () -> Unit,
    onAttachGallery: () -> Unit,
    onAttachDocument: () -> Unit,
    onRemove: (Uri) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (attachments.isNotEmpty()) {
            attachments.forEach { uri ->
                val normalizedUri = remember(uri) {
                    try {
                        org.totschnig.myexpenses.util.AppDirHelper.ensureContentUri(uri, context)
                    } catch (_: Exception) {
                        uri
                    }
                }
                val mimeType = remember(normalizedUri) {
                    try {
                        context.contentResolver.getType(normalizedUri)
                            ?: org.totschnig.myexpenses.util.io.getMimeType(normalizedUri.lastPathSegment ?: "")
                            ?: "image/*"
                    } catch (_: Exception) {
                        "image/*"
                    }
                }
                val isImage = mimeType.startsWith("image")
                val fileName = remember(normalizedUri) {
                    normalizedUri.lastPathSegment?.substringAfterLast('/') ?: "Attachment"
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            try {
                                val viewIntent = SystemViewIntentProvider().getViewIntent(context, normalizedUri, mimeType)
                                context.startActivity(viewIntent)
                            } catch (e: Exception) {
                                Timber.e(e, "Failed to open attachment viewer")
                            }
                        },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Thumbnail or Document Icon
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isImage) {
                                var thumbnail by remember(normalizedUri) { mutableStateOf<Bitmap?>(null) }
                                var isLoading by remember(normalizedUri) { mutableStateOf(true) }

                                LaunchedEffect(normalizedUri) {
                                    isLoading = true
                                    withContext(Dispatchers.IO) {
                                        try {
                                            val decoded = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                                val source = ImageDecoder.createSource(context.contentResolver, normalizedUri)
                                                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                                                    decoder.setTargetSampleSize(4)
                                                }
                                            } else {
                                                context.contentResolver.openInputStream(normalizedUri)?.use {
                                                    BitmapFactory.decodeStream(it)
                                                }
                                            }
                                            thumbnail = decoded
                                        } catch (_: Exception) {
                                            try {
                                                val file = PictureDirHelper.getFileForUri(context, normalizedUri)
                                                if (file.exists() && file.length() > 0) {
                                                    thumbnail = BitmapFactory.decodeFile(file.absolutePath)
                                                }
                                            } catch (_: Exception) {}
                                        } finally {
                                            isLoading = false
                                        }
                                    }
                                }

                                when {
                                    isLoading -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    thumbnail != null -> Image(
                                        bitmap = thumbnail!!.asImageBitmap(),
                                        contentDescription = "Receipt thumbnail",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    else -> Icon(
                                        imageVector = Icons.Default.Image,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // Text info
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (isImage) Icons.AutoMirrored.Filled.ReceiptLong else Icons.Default.Description,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (isImage) "Receipt Image" else fileName,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = if (isImage) "Tap to view full screen" else "Tap to open document",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Remove action
                        IconButton(onClick = { onRemove(uri) }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove attachment",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            // Compact Add Buttons below list
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = onAttachCamera,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp).weight(1f)
                ) {
                    Icon(painterResource(id = R.drawable.ic_menu_camera), contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Camera", style = MaterialTheme.typography.labelSmall)
                }
                FilledTonalButton(
                    onClick = onAttachGallery,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp).weight(1f)
                ) {
                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Gallery", style = MaterialTheme.typography.labelSmall)
                }
                FilledTonalButton(
                    onClick = onAttachDocument,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp).weight(1f)
                ) {
                    Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Doc", style = MaterialTheme.typography.labelSmall)
                }
            }
        } else {
            // Empty state: Card with 3 buttons
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_attachment),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Attach Receipt / Document",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = onAttachCamera,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.height(34.dp).weight(1f)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_menu_camera),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Camera", style = MaterialTheme.typography.labelMedium)
                        }

                        FilledTonalButton(
                            onClick = onAttachGallery,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.height(34.dp).weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Gallery", style = MaterialTheme.typography.labelMedium)
                        }

                        FilledTonalButton(
                            onClick = onAttachDocument,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.height(34.dp).weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Doc", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

private fun copyUriToAppStorage(context: Context, sourceUri: Uri): Uri {
    return try {
        val app = context.applicationContext as MyApplication
        val contentResolver = context.contentResolver
        val mimeType = contentResolver.getType(sourceUri)
        val ext = when {
            mimeType?.contains("pdf") == true -> "pdf"
            mimeType?.contains("png") == true -> "png"
            mimeType?.contains("webp") == true -> "webp"
            mimeType?.contains("jpeg") == true || mimeType?.contains("jpg") == true -> "jpg"
            else -> MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "jpg"
        }
        val prefix = if (ext == "pdf") "doc_" else "receipt_"
        val destUri = PictureDirHelper.getOutputMediaUri(false, app, prefix = prefix, extension = ext)
        contentResolver.openInputStream(sourceUri)?.use { input ->
            contentResolver.openOutputStream(destUri)?.use { output ->
                input.copyTo(output)
            }
        }
        destUri
    } catch (e: Exception) {
        Timber.e(e, "Failed to copy file to app storage, using source uri")
        sourceUri
    }
}


