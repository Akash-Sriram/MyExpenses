package org.totschnig.myexpenses.compose

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.model.CurrencyUnit
import org.totschnig.myexpenses.model.Grouping
import org.totschnig.myexpenses.provider.filter.SimpleCriterion
import org.totschnig.myexpenses.viewmodel.data.AccountMinimal
import org.totschnig.myexpenses.viewmodel.data.getLabelForBudgetType
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BudgetEditScreen(
    isNewBudget: Boolean,
    initialTitle: String = "",
    initialDescription: String = "",
    initialAmount: String = "",
    initialAccountId: Long,
    initialGrouping: Grouping = Grouping.MONTH,
    initialStartDate: LocalDate = LocalDate.now(),
    initialEndDate: LocalDate = LocalDate.now(),
    initialIsDefault: Boolean = false,
    accounts: List<AccountMinimal>,
    currencyContextLookup: (String) -> CurrencyUnit,
    criteria: List<SimpleCriterion<*>>,
    onFilterClick: (Int) -> Unit,
    onRemoveFilter: (Int) -> Unit,
    onSave: (
        title: String,
        description: String,
        allocationAmount: BigDecimal?,
        accountId: Long,
        grouping: Grouping,
        startDate: LocalDate?,
        endDate: LocalDate?,
        isDefault: Boolean
    ) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(initialTitle) }
    var description by remember { mutableStateOf(initialDescription) }
    var amountStr by remember { mutableStateOf(initialAmount) }
    var selectedAccountId by remember { mutableStateOf(initialAccountId) }
    var selectedGrouping by remember { mutableStateOf(initialGrouping) }
    var startDate by remember { mutableStateOf(initialStartDate) }
    var endDate by remember { mutableStateOf(initialEndDate) }
    var isDefaultBudget by remember { mutableStateOf(initialIsDefault) }

    var titleError by remember { mutableStateOf(false) }
    var amountError by remember { mutableStateOf(false) }
    var dateOrderError by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }

    var datePickerTarget by remember { mutableStateOf<String?>(null) } // "start" or "end"

    val selectedAccount = remember(selectedAccountId, accounts) {
        accounts.find { it.id == selectedAccountId } ?: accounts.firstOrNull()
    }
    val currentCurrencyUnit = remember(selectedAccount) {
        selectedAccount?.let { currencyContextLookup(it.currency) }
    }

    val isDirty = title != initialTitle ||
            description != initialDescription ||
            amountStr != initialAmount ||
            selectedAccountId != initialAccountId ||
            selectedGrouping != initialGrouping ||
            startDate != initialStartDate ||
            endDate != initialEndDate ||
            isDefaultBudget != initialIsDefault

    fun handleBack() {
        if (isDirty) {
            showDiscardDialog = true
        } else {
            onClose()
        }
    }

    fun submitSave() {
        val trimmedTitle = title.trim()
        if (trimmedTitle.isEmpty()) {
            titleError = true
            return
        }

        val parsedAmount = if (isNewBudget) {
            try {
                val base = BigDecimal(amountStr.ifBlank { "0" }.replace(',', '.'))
                if (base <= BigDecimal.ZERO) {
                    amountError = true
                    return
                }
                base
            } catch (_: Exception) {
                amountError = true
                return
            }
        } else null

        if (selectedGrouping == Grouping.NONE && endDate.isBefore(startDate)) {
            dateOrderError = true
            return
        }

        val chosenStart = if (selectedGrouping == Grouping.NONE) startDate else null
        val chosenEnd = if (selectedGrouping == Grouping.NONE) endDate else null

        onSave(
            trimmedTitle,
            description.trim(),
            parsedAmount,
            selectedAccount?.id ?: selectedAccountId,
            selectedGrouping,
            chosenStart,
            chosenEnd,
            if (criteria.isEmpty() && selectedGrouping != Grouping.NONE) isDefaultBudget else false
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

    datePickerTarget?.let { target ->
        val initialDate = if (target == "start") startDate else endDate
        val initialMillis = remember(initialDate) {
            initialDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { datePickerTarget = null },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val picked = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
                        if (target == "start") {
                            startDate = picked
                        } else {
                            endDate = picked
                        }
                        dateOrderError = false
                    }
                    datePickerTarget = null
                }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { datePickerTarget = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isNewBudget) stringResource(R.string.menu_create_budget)
                        else stringResource(R.string.menu_edit_budget),
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
                        if (isNewBudget) stringResource(R.string.menu_create_budget)
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

            // Live Budget Preview Card
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
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PieChart,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title.ifBlank { "Budget Name" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${stringResource(selectedGrouping.getLabelForBudgetType())} • ${selectedAccount?.label ?: "Account"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (isNewBudget) {
                        Text(
                            text = "${currentCurrencyUnit?.symbol ?: ""} ${amountStr.ifBlank { "0.00" }}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Budget Title
            OutlinedTextField(
                value = title,
                onValueChange = {
                    title = it
                    if (it.isNotBlank()) titleError = false
                },
                label = { Text(stringResource(R.string.title) + " *") },
                placeholder = { Text("e.g. Monthly Groceries, Dining Out") },
                isError = titleError,
                supportingText = if (titleError) {
                    { Text(stringResource(R.string.required), color = MaterialTheme.colorScheme.error) }
                } else null,
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Label, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Description
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text(stringResource(R.string.description)) },
                placeholder = { Text("Optional notes") },
                leadingIcon = { Icon(Icons.Default.Description, contentDescription = null) },
                maxLines = 2,
                modifier = Modifier.fillMaxWidth()
            )

            // Allocated Amount (only for new budgets)
            if (isNewBudget) {
                OutlinedTextField(
                    value = amountStr,
                    onValueChange = {
                        amountStr = it
                        if (it.isNotBlank()) amountError = false
                    },
                    label = { Text(stringResource(R.string.amount) + " *") },
                    placeholder = { Text("0.00") },
                    prefix = { Text((currentCurrencyUnit?.symbol ?: "") + " ") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = amountError,
                    supportingText = if (amountError) {
                        { Text("Please enter a valid amount", color = MaterialTheme.colorScheme.error) }
                    } else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Account Selection
            var accountDropdownExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = accountDropdownExpanded,
                onExpandedChange = { accountDropdownExpanded = !accountDropdownExpanded }
            ) {
                OutlinedTextField(
                    value = selectedAccount?.label ?: "Select Account",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.account)) },
                    leadingIcon = { Icon(Icons.Default.AccountBalance, contentDescription = null) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = accountDropdownExpanded) },
                    modifier = Modifier
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = accountDropdownExpanded,
                    onDismissRequest = { accountDropdownExpanded = false }
                ) {
                    accounts.forEach { acc ->
                        DropdownMenuItem(
                            text = { Text("${acc.label} (${acc.currency})") },
                            onClick = {
                                selectedAccountId = acc.id
                                accountDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            // Period Type (Grouping)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Budget Period",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    val periods = listOf(
                        Grouping.MONTH to "Monthly",
                        Grouping.WEEK to "Weekly",
                        Grouping.YEAR to "Yearly",
                        Grouping.DAY to "Daily",
                        Grouping.NONE to "Custom"
                    )
                    periods.forEachIndexed { index, (grouping, name) ->
                        SegmentedButton(
                            selected = selectedGrouping == grouping,
                            onClick = { if (isNewBudget) selectedGrouping = grouping },
                            enabled = isNewBudget,
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = periods.size)
                        ) {
                            Text(name, fontSize = 11.sp, fontWeight = if (selectedGrouping == grouping) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            }

            // Custom Date Range (when Grouping == NONE)
            if (selectedGrouping == Grouping.NONE) {
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
                        Text(
                            text = "Custom Date Duration",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { datePickerTarget = "start" },
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                )
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text("From", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(startDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)), fontWeight = FontWeight.Bold)
                                }
                            }
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { datePickerTarget = "end" },
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                )
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text("To", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(endDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        if (dateOrderError) {
                            Text(
                                text = stringResource(R.string.budget_date_end_after_start),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            // Default Budget Toggle
            if (selectedGrouping != Grouping.NONE) {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .clickable(enabled = criteria.isEmpty()) { isDefaultBudget = !isDefaultBudget }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Default Budget", fontWeight = FontWeight.SemiBold)
                            Text(
                                text = if (criteria.isNotEmpty()) "Cannot set as default when filters are applied"
                                else "Use as primary budget overview for this period",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isDefaultBudget && criteria.isEmpty(),
                            onCheckedChange = { isDefaultBudget = it },
                            enabled = criteria.isEmpty()
                        )
                    }
                }
            }

            // Filter Chips Section
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Budget Criteria & Filters",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                val filterItems = listOf(
                    R.id.FILTER_CATEGORY_COMMAND to ("Category" to Icons.Default.Category),
                    R.id.FILTER_PAYEE_COMMAND to ("Payee" to Icons.Default.Person),
                    R.id.FILTER_METHOD_COMMAND to ("Payment Method" to Icons.Default.CreditCard),
                    R.id.FILTER_STATUS_COMMAND to ("Status" to Icons.Default.CheckCircle),
                    R.id.FILTER_TAG_COMMAND to ("Tag" to Icons.Default.Tag)
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    filterItems.forEach { (commandId, meta) ->
                        val (title, icon) = meta
                        val activeCriterion = criteria.find { it.id == commandId }
                        val isSelected = activeCriterion != null

                        InputChip(
                            selected = isSelected,
                            onClick = { onFilterClick(commandId) },
                            label = {
                                Text(activeCriterion?.prettyPrint(context) ?: title)
                            },
                            leadingIcon = {
                                Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            trailingIcon = if (isSelected) {
                                {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove",
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clickable { onRemoveFilter(commandId) }
                                    )
                                }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }
            }

            Spacer(Modifier.height(80.dp))
        }
    }
}
