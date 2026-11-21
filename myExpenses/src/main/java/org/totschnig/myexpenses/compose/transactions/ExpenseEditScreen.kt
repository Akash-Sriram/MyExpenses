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
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.activity.Action as ManageAction
import org.totschnig.myexpenses.compose.LocalColors
import org.totschnig.myexpenses.compose.LocalCurrencyFormatter
import org.totschnig.myexpenses.compose.tags.TagsScreen
import org.totschnig.myexpenses.contract.TransactionsContract.Transactions.TYPE_SPLIT
import org.totschnig.myexpenses.contract.TransactionsContract.Transactions.TYPE_TRANSACTION
import org.totschnig.myexpenses.contract.TransactionsContract.Transactions.TYPE_TRANSFER
import org.totschnig.myexpenses.db2.FLAG_EXPENSE
import org.totschnig.myexpenses.db2.FLAG_INCOME
import org.totschnig.myexpenses.db2.FLAG_NEUTRAL
import org.totschnig.myexpenses.model.CurrencyUnit
import org.totschnig.myexpenses.model.Money
import org.totschnig.myexpenses.model.generateUuid
import org.totschnig.myexpenses.ui.DisplayParty
import org.totschnig.myexpenses.viewmodel.data.Account
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
    allTags: List<Tag> = emptyList(),
    onSave: (TransactionEditData) -> Unit,
    onClose: () -> Unit,
    onOpenCategoryPicker: () -> Unit = {},
    onAddTag: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val currencyFormatter = LocalCurrencyFormatter.current
    val colors = LocalColors.current

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
        mutableStateOf(amount == BigDecimal.ZERO)
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

    // Notes
    var comment by rememberSaveable {
        mutableStateOf(initialData?.comment ?: "")
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
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
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

            // 2. Amount & Interactive Calculator Card
            OutlinedCard(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                ),
                modifier = Modifier.fillMaxWidth()
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
                        Column {
                            Text(
                                text = stringResource(R.string.amount),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = selectedAccount?.currency?.symbol ?: "₹",
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        color = when (selectedType) {
                                            TransactionTypeOption.EXPENSE -> colors.expense
                                            TransactionTypeOption.INCOME -> colors.income
                                            TransactionTypeOption.TRANSFER -> colors.transfer
                                        },
                                        fontWeight = FontWeight.Bold
                                    ),
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Text(
                                    text = if (amount == BigDecimal.ZERO) "0.00" else amount.stripTrailingZeros().toPlainString(),
                                    style = MaterialTheme.typography.displaySmall.copy(
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
                        Column(modifier = Modifier.padding(top = 12.dp)) {
                            HorizontalDivider(modifier = Modifier.padding(bottom = 12.dp))
                            CalculatorKeypad(
                                initialAmount = amount,
                                onAmountCommitted = { newAmount ->
                                    amount = newAmount
                                    showCalculator = false
                                }
                            )
                        }
                    }
                }
            }

            // 3. Account Selection Card
            OutlinedCard(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (selectedType == TransactionTypeOption.TRANSFER) stringResource(R.string.transfer_from_account) else stringResource(R.string.account),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Box {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .clickable { showAccountMenu = true }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clip(CircleShape)
                                        .background(Color(selectedAccount?.color ?: android.graphics.Color.GRAY))
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = selectedAccount?.label ?: stringResource(R.string.account),
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                                )
                            }
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
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
                                                    .size(12.dp)
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
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.transfer_to_account),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Box {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .clickable { showTransferAccountMenu = true }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clip(CircleShape)
                                            .background(Color(transferAccount?.color ?: android.graphics.Color.GRAY))
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = transferAccount?.label ?: stringResource(R.string.account),
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                }
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
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
                                                        .size(12.dp)
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
                }
            }

            // 4. Quick Category Carousel (for Expense & Income)
            if (selectedType != TransactionTypeOption.TRANSFER) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.category),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (selectedCategoryPath != null) {
                            Text(
                                text = selectedCategoryPath!!,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                                        selectedCategoryPath = cat.label
                                        selectedCategoryIcon = cat.icon
                                    }
                                },
                                label = { Text(cat.label) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Category,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }

                        // "+ More Categories" chip
                        InputChip(
                            selected = false,
                            onClick = onOpenCategoryPicker,
                            label = { Text(stringResource(R.string.category)) },
                            leadingIcon = {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }

            // 5. Payee / Payer Field
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
                    Icon(Icons.Default.Person, contentDescription = null)
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // 6. In-Place Tags Section
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(R.string.tags),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    selectedTags.forEach { tag ->
                        InputChip(
                            selected = true,
                            onClick = {
                                selectedTags = selectedTags.filter { it.id != tag.id }
                            },
                            label = { Text(tag.label) },
                            trailingIcon = {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remove tag",
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    InputChip(
                        selected = false,
                        onClick = { showTagSheet = true },
                        label = { Text(stringResource(R.string.tags)) },
                        leadingIcon = {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            // 7. Date & Time Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedCard(
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { showDatePicker = true }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.DateRange, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(stringResource(R.string.date), style = MaterialTheme.typography.labelSmall)
                            Text(
                                text = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                        }
                    }
                }

                OutlinedCard(
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { showTimePicker = true }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(stringResource(R.string.time), style = MaterialTheme.typography.labelSmall)
                            Text(
                                text = time.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                        }
                    }
                }
            }

            // 8. Payment Method Dropdown (if methods available)
            if (availableMethods.isNotEmpty() && selectedType != TransactionTypeOption.TRANSFER) {
                Box {
                    val methodLabel = availableMethods.find { it.id == selectedMethodId }?.label ?: stringResource(R.string.method)
                    OutlinedCard(
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showMethodMenu = true }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Payment, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(stringResource(R.string.method), style = MaterialTheme.typography.labelSmall)
                                    Text(methodLabel, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                }
                            }
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                        }
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

            // 9. Notes / Comment Field
            OutlinedTextField(
                value = comment,
                onValueChange = { comment = it },
                label = { Text(stringResource(R.string.notes)) },
                leadingIcon = {
                    Icon(Icons.AutoMirrored.Filled.Notes, contentDescription = null)
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4
            )

            Spacer(Modifier.height(16.dp))
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
}

