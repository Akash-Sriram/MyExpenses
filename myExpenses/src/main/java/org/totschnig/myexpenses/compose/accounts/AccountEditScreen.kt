package org.totschnig.myexpenses.compose.accounts

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.activity.getAccountTypeIcon
import org.totschnig.myexpenses.model.AccountType
import org.totschnig.myexpenses.model.CurrencyUnit
import org.totschnig.myexpenses.model2.Account
import org.totschnig.myexpenses.viewmodel.data.Currency
import org.totschnig.myexpenses.viewmodel.data.Tag
import java.math.BigDecimal

private val CURATED_PALETTE = intArrayOf(
    android.graphics.Color.rgb(0, 150, 136),    // Teal
    android.graphics.Color.rgb(33, 150, 243),   // Blue
    android.graphics.Color.rgb(63, 81, 181),    // Indigo
    android.graphics.Color.rgb(156, 39, 176),   // Purple
    android.graphics.Color.rgb(233, 30, 99),    // Pink
    android.graphics.Color.rgb(244, 67, 54),    // Red
    android.graphics.Color.rgb(255, 87, 34),    // Deep Orange
    android.graphics.Color.rgb(255, 152, 0),    // Orange
    android.graphics.Color.rgb(76, 175, 80),    // Green
    android.graphics.Color.rgb(0, 188, 212),    // Cyan
    android.graphics.Color.rgb(96, 125, 139),   // Blue Grey
    android.graphics.Color.rgb(66, 66, 66)      // Dark Grey
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AccountEditScreen(
    isNewAccount: Boolean,
    initialLabel: String = "",
    initialDescription: String = "",
    initialOpeningBalance: String = "0",
    initialIsOpeningBalanceNegative: Boolean = false,
    initialCurrencyCode: String,
    initialAccountType: AccountType,
    initialColor: Int = Account.DEFAULT_COLOR,
    initialCriterion: String = "",
    initialIsCriterionNegative: Boolean = false,
    initialExchangeRate: String = "1.0",
    initialExcludeFromTotals: Boolean = false,
    initialDynamicExchangeRates: Boolean = false,
    availableCurrencies: List<Currency>,
    availableAccountTypes: List<AccountType>,
    homeCurrency: CurrencyUnit,
    currencyContextLookup: (String) -> CurrencyUnit,
    tags: List<Tag> = emptyList(),
    onSelectTags: () -> Unit,
    onRemoveTag: (Tag) -> Unit,
    onColorPaletteRequested: () -> Unit,
    onSave: (
        label: String,
        description: String,
        openingBalance: BigDecimal,
        currencyCode: String,
        accountType: AccountType,
        color: Int,
        criterion: BigDecimal?,
        exchangeRate: Double,
        excludeFromTotals: Boolean,
        dynamicExchangeRates: Boolean
    ) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var label by remember { mutableStateOf(initialLabel) }
    var description by remember { mutableStateOf(initialDescription) }
    var openingBalanceStr by remember { mutableStateOf(initialOpeningBalance) }
    var isOpeningBalanceNegative by remember { mutableStateOf(initialIsOpeningBalanceNegative) }
    var selectedCurrencyCode by remember { mutableStateOf(initialCurrencyCode) }
    var selectedAccountType by remember { mutableStateOf(initialAccountType) }
    var selectedColor by remember { mutableIntStateOf(initialColor) }
    var criterionStr by remember { mutableStateOf(initialCriterion) }
    var isCriterionNegative by remember { mutableStateOf(initialIsCriterionNegative) }
    var exchangeRateStr by remember { mutableStateOf(initialExchangeRate) }
    var excludeFromTotals by remember { mutableStateOf(initialExcludeFromTotals) }
    var dynamicExchangeRates by remember { mutableStateOf(initialDynamicExchangeRates) }

    var labelError by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }

    val currentCurrencyUnit = remember(selectedCurrencyCode) {
        currencyContextLookup(selectedCurrencyCode)
    }
    val isFx = selectedCurrencyCode != homeCurrency.code

    // Dirty check
    val isDirty = label != initialLabel ||
            description != initialDescription ||
            openingBalanceStr != initialOpeningBalance ||
            isOpeningBalanceNegative != initialIsOpeningBalanceNegative ||
            selectedCurrencyCode != initialCurrencyCode ||
            selectedAccountType.id != initialAccountType.id ||
            selectedColor != initialColor ||
            criterionStr != initialCriterion ||
            isCriterionNegative != initialIsCriterionNegative ||
            excludeFromTotals != initialExcludeFromTotals

    fun handleBack() {
        if (isDirty) {
            showDiscardDialog = true
        } else {
            onClose()
        }
    }

    fun submitSave() {
        val trimmedLabel = label.trim()
        if (trimmedLabel.isEmpty()) {
            labelError = true
            return
        }

        val parsedOpeningBalance = try {
            val base = BigDecimal(openingBalanceStr.ifBlank { "0" }.replace(',', '.'))
            if (isOpeningBalanceNegative) base.negate() else base
        } catch (_: Exception) {
            BigDecimal.ZERO
        }

        val parsedCriterion = if (criterionStr.isNotBlank()) {
            try {
                val base = BigDecimal(criterionStr.replace(',', '.'))
                if (isCriterionNegative) base.negate() else base
            } catch (_: Exception) {
                null
            }
        } else null

        val parsedRate = if (isFx) {
            try {
                exchangeRateStr.replace(',', '.').toDouble()
            } catch (_: Exception) {
                1.0
            }
        } else 1.0

        onSave(
            trimmedLabel,
            description.trim(),
            parsedOpeningBalance,
            selectedCurrencyCode,
            selectedAccountType,
            selectedColor,
            parsedCriterion,
            parsedRate,
            excludeFromTotals,
            dynamicExchangeRates
        )
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(stringResource(R.string.discard) + "?") },
            text = { Text(stringResource(R.string.dialog_confirm_discard_changes)) },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardDialog = false
                    onClose()
                }) {
                    Text(stringResource(R.string.response_yes), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text(stringResource(R.string.response_no))
                }
            }
        )
    }

    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isNewAccount) stringResource(R.string.menu_create_account)
                        else stringResource(R.string.menu_edit_account),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = ::handleBack) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.menu_back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = ::submitSave) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = stringResource(R.string.menu_save_help_text),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = ::submitSave,
                icon = { Icon(Icons.Default.Check, null) },
                text = {
                    Text(
                        if (isNewAccount) stringResource(R.string.menu_create_account)
                        else stringResource(R.string.menu_save_help_text)
                    )
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // Live Account Preview Card
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val previewColor = Color(selectedColor)
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .background(previewColor.copy(alpha = 0.18f), CircleShape)
                            .border(1.5.dp, previewColor, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = getAccountTypeIcon(selectedAccountType.name, selectedAccountType.isAsset),
                            contentDescription = null,
                            tint = previewColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = label.ifBlank { stringResource(R.string.account) },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${selectedAccountType.title(context)} • $selectedCurrencyCode",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "${if (isOpeningBalanceNegative) "-" else ""}${currentCurrencyUnit.symbol} ${openingBalanceStr.ifBlank { "0.00" }}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isOpeningBalanceNegative) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Account Name
            OutlinedTextField(
                value = label,
                onValueChange = {
                    label = it
                    if (it.isNotBlank()) labelError = false
                },
                label = { Text(stringResource(R.string.label) + " *") },
                placeholder = { Text("e.g. HDFC Salary, Cash, Wallet") },
                isError = labelError,
                supportingText = if (labelError) {
                    { Text(stringResource(R.string.required), color = MaterialTheme.colorScheme.error) }
                } else null,
                leadingIcon = {
                    Icon(Icons.Default.AccountBalance, contentDescription = null)
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Description
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text(stringResource(R.string.description)) },
                placeholder = { Text("Optional notes or account number") },
                maxLines = 2,
                modifier = Modifier.fillMaxWidth()
            )

            // Account Type Selector
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.account_types),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    availableAccountTypes.forEach { type ->
                        val isSelected = selectedAccountType.id == type.id
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedAccountType = type },
                            label = { Text(type.title(context)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = getAccountTypeIcon(type.name, type.isAsset),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }
            }

            // Currency Selector
            var currencyDropdownExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = currencyDropdownExpanded,
                onExpandedChange = { currencyDropdownExpanded = !currencyDropdownExpanded }
            ) {
                OutlinedTextField(
                    value = "$selectedCurrencyCode (${currentCurrencyUnit.symbol}) - ${currentCurrencyUnit.description}",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.currency)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = currencyDropdownExpanded) },
                    modifier = Modifier
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = currencyDropdownExpanded,
                    onDismissRequest = { currencyDropdownExpanded = false }
                ) {
                    availableCurrencies.forEach { cur ->
                        DropdownMenuItem(
                            text = { Text("${cur.code} - ${cur.displayName}") },
                            onClick = {
                                selectedCurrencyCode = cur.code
                                currencyDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            // Opening Balance with +/- toggle
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.opening_balance),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        // Sign toggle
                        SingleChoiceSegmentedButtonRow {
                            SegmentedButton(
                                selected = !isOpeningBalanceNegative,
                                onClick = { isOpeningBalanceNegative = false },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                            ) {
                                Text("+ Positive")
                            }
                            SegmentedButton(
                                selected = isOpeningBalanceNegative,
                                onClick = { isOpeningBalanceNegative = true },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                            ) {
                                Text("− Overdraft")
                            }
                        }
                    }

                    OutlinedTextField(
                        value = openingBalanceStr,
                        onValueChange = { openingBalanceStr = it },
                        prefix = { Text(currentCurrencyUnit.symbol + " ") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Exchange Rate (FX accounts)
            if (isFx) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Exchange Rate vs Home Currency (${homeCurrency.code})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("1 $selectedCurrencyCode =")
                            OutlinedTextField(
                                value = exchangeRateStr,
                                onValueChange = { exchangeRateStr = it },
                                suffix = { Text(homeCurrency.symbol) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Color Palette
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.color),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = onColorPaletteRequested) {
                        Icon(Icons.Default.ColorLens, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Custom Color")
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CURATED_PALETTE.forEach { colorInt ->
                        val isSelected = selectedColor == colorInt
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color(colorInt), CircleShape)
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { selectedColor = colorInt },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Exclude from Totals Switch
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Row(
                    modifier = Modifier
                        .clickable { excludeFromTotals = !excludeFromTotals }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                if (excludeFromTotals) MaterialTheme.colorScheme.errorContainer
                                else MaterialTheme.colorScheme.surfaceContainerHighest,
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.VisibilityOff,
                            contentDescription = null,
                            tint = if (excludeFromTotals) MaterialTheme.colorScheme.onErrorContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.menu_exclude_from_totals),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Exclude balance from grand total & aggregate accounts (ideal for wallets & closed vouchers)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = excludeFromTotals,
                        onCheckedChange = { excludeFromTotals = it }
                    )
                }
            }

            // Goal or Limit (Criterion)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val criterionHeader = when {
                        criterionStr.isBlank() -> stringResource(R.string.goal_or_limit)
                        isCriterionNegative -> stringResource(R.string.credit_limit)
                        else -> stringResource(R.string.saving_goal)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = criterionHeader,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        SingleChoiceSegmentedButtonRow {
                            SegmentedButton(
                                selected = !isCriterionNegative,
                                onClick = { isCriterionNegative = false },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                            ) {
                                Text("🎯 Goal")
                            }
                            SegmentedButton(
                                selected = isCriterionNegative,
                                onClick = { isCriterionNegative = true },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                            ) {
                                Text("💳 Limit")
                            }
                        }
                    }

                    OutlinedTextField(
                        value = criterionStr,
                        onValueChange = { criterionStr = it },
                        placeholder = { Text("Optional threshold") },
                        prefix = { Text(currentCurrencyUnit.symbol + " ") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Tags Section
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.active_tags),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    AssistChip(
                        onClick = onSelectTags,
                        label = { Text("Add Tag") },
                        leadingIcon = { Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp)) }
                    )
                }

                if (tags.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        tags.forEach { tag ->
                            InputChip(
                                selected = false,
                                onClick = { onRemoveTag(tag) },
                                label = { Text(tag.label) },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                        }
                    }
                }
            }

            // Bottom Spacer for FAB clearance
            Spacer(Modifier.height(80.dp))
        }
    }
}
