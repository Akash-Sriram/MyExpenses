package org.totschnig.myexpenses.compose.filter

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.booleanResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.LocalDate
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.compose.CharIcon
import org.totschnig.myexpenses.compose.TEST_TAG_DIALOG
import org.totschnig.myexpenses.compose.conditional
import org.totschnig.myexpenses.model.AccountType
import org.totschnig.myexpenses.model.CurrencyUnit
import org.totschnig.myexpenses.provider.filter.AccountCriterion
import org.totschnig.myexpenses.provider.filter.AmountCriterion
import org.totschnig.myexpenses.provider.filter.AndCriterion
import org.totschnig.myexpenses.provider.filter.AssetCriterion
import org.totschnig.myexpenses.provider.filter.CategoryCriterion
import org.totschnig.myexpenses.provider.filter.CommentCriterion
import org.totschnig.myexpenses.provider.filter.CrStatusCriterion
import org.totschnig.myexpenses.provider.filter.Criterion
import org.totschnig.myexpenses.provider.filter.DateCriterion
import org.totschnig.myexpenses.provider.filter.DisplayInfo
import org.totschnig.myexpenses.provider.filter.MethodCriterion
import org.totschnig.myexpenses.provider.filter.NotCriterion
import org.totschnig.myexpenses.provider.filter.Operation
import org.totschnig.myexpenses.provider.filter.OrCriterion
import org.totschnig.myexpenses.provider.filter.PayeeCriterion
import org.totschnig.myexpenses.provider.filter.SimpleCriterion
import org.totschnig.myexpenses.provider.filter.TagCriterion
import org.totschnig.myexpenses.provider.filter.TransferCriterion
import org.totschnig.myexpenses.provider.filter.asSet
import org.totschnig.myexpenses.viewmodel.SumInfo
import org.totschnig.myexpenses.viewmodel.data.PageAccount

const val COMPLEX_AND = 0
const val COMPLEX_OR = 1

const val TYPE_QUICK = 0
const val TYPE_COMPLEX = 1

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FilterDialog(
    account: PageAccount,
    sumInfo: SumInfo,
    initialPreferredSearchType: Int = TYPE_COMPLEX,
    criterion: Criterion? = null,
    onDismissRequest: () -> Unit = {},
    onConfirmRequest: (Int, Criterion?) -> Unit = { _, _ -> },
) {

    val initialSet = criterion.asSet
    val initialSelectedComplex = if (criterion is OrCriterion) COMPLEX_OR else COMPLEX_AND

    var selectedComplex by rememberSaveable {
        mutableIntStateOf(initialSelectedComplex)
    }

    var preferredSearchType by rememberSaveable {
        mutableIntStateOf(initialPreferredSearchType)
    }

    val isComplexSearch = preferredSearchType == TYPE_COMPLEX

    val criteriaSet: MutableState<Set<Criterion>> = rememberSaveable(
        saver = Saver(
            save = { ArrayList(it.value) },
            restore = { mutableStateOf(it.toSet()) }
        )
    ) {
        mutableStateOf(initialSet)
    }

    val isDirty by remember {
        derivedStateOf {
            initialSet != criteriaSet.value || initialSelectedComplex != selectedComplex
        }
    }

    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }

    val onResult: (SimpleCriterion<*>?, SimpleCriterion<*>?) -> Unit = remember(isComplexSearch) {
        { oldValue, newValue ->
            if (newValue != null) {
                if (isComplexSearch) {
                    oldValue?.also { current ->
                        criteriaSet.value = criteriaSet.value.map {
                            if (it == current)
                                if (it is NotCriterion) NotCriterion(newValue) else newValue
                            else it
                        }.toSet()
                    } ?: run { criteriaSet.value += newValue }
                } else {
                    onConfirmRequest(
                        TYPE_QUICK,
                        if (criteriaSet.value.isEmpty()) newValue else AndCriterion(criteriaSet.value + newValue)
                    )
                }
            }
        }
    }

    val isLarge = booleanResource(R.bool.isLarge)

    val onDismiss = {
        if (isDirty) {
            confirmDiscard = true
        } else {
            onDismissRequest()
        }
    }

    Dialog(
        properties = DialogProperties(
            usePlatformDefaultWidth = isLarge,
            decorFitsSystemWindows = false
        ),
        onDismissRequest = onDismiss
    ) {

        Surface(
            modifier = Modifier
                .testTag(TEST_TAG_DIALOG)
                .conditional(
                    isLarge,
                    ifTrue = { defaultMinSize(minHeight = 400.dp) },
                    ifFalse = { fillMaxSize() }
                ),
            shape = if (isLarge) MaterialTheme.shapes.extraLarge else androidx.compose.ui.graphics.RectangleShape
        ) {
            FilterHandler(account, "confirmFilterDialog", onResult) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                ) {
                    // --- 1. Top App Bar / Header ---
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(android.R.string.cancel)
                            )
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 4.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.filter),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${account.label} • ${if (isComplexSearch) stringResource(R.string.complex_search) else stringResource(R.string.quick_search)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (criteriaSet.value.isNotEmpty()) {
                            TextButton(
                                onClick = { confirmClear = true },
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ClearAll,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.clear_all_filters),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }

                    // --- 2. Search Mode Selector (Quick vs Complex) ---
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            onClick = {
                                if (criteriaSet.value.isSimple(selectedComplex)) {
                                    preferredSearchType = TYPE_QUICK
                                }
                            },
                            selected = preferredSearchType == TYPE_QUICK,
                            enabled = criteriaSet.value.isSimple(selectedComplex)
                        ) {
                            Text(stringResource(R.string.quick_search))
                        }
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            onClick = { preferredSearchType = TYPE_COMPLEX },
                            selected = preferredSearchType == TYPE_COMPLEX
                        ) {
                            Text(stringResource(R.string.complex_search))
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(top = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )

                    // --- Criteria display catalog ---
                    val filters: List<DisplayInfo> = if (account.isPortfolio) listOf(
                        AssetCriterion, AmountCriterion, CommentCriterion, DateCriterion
                    )
                    else listOfNotNull(
                        if (sumInfo.mappedCategories) CategoryCriterion else null,
                        AmountCriterion,
                        CommentCriterion,
                        if (account.isAggregate || account.type.supportsReconciliation)
                            CrStatusCriterion else null,
                        if (sumInfo.mappedPayees) PayeeCriterion else null,
                        if (sumInfo.mappedMethods) MethodCriterion else null,
                        DateCriterion,
                        if (sumInfo.hasTransfers) TransferCriterion else null,
                        if (sumInfo.hasTags) TagCriterion else null,
                        if (account.isAggregate) AccountCriterion else null
                    )

                    // Date & Amount Preset calculations
                    val now = remember { LocalDate.now() }
                    val thisMonthStart = remember(now) { now.withDayOfMonth(1) }
                    val thisMonthEnd = remember(now) { now.withDayOfMonth(now.lengthOfMonth()) }
                    val lastMonth = remember(now) { now.minusMonths(1) }
                    val lastMonthStart = remember(lastMonth) { lastMonth.withDayOfMonth(1) }
                    val lastMonthEnd = remember(lastMonth) { lastMonth.withDayOfMonth(lastMonth.lengthOfMonth()) }
                    val last30DaysStart = remember(now) { now.minusDays(30) }
                    val last30DaysEnd = remember(now) { now }

                    val currentDateCriterion = criteriaSet.value.find {
                        it is DateCriterion || (it is NotCriterion && it.criterion is DateCriterion)
                    }
                    val actualDateCriterion = (currentDateCriterion as? DateCriterion)
                        ?: (currentDateCriterion as? NotCriterion)?.criterion as? DateCriterion

                    val isThisMonth = actualDateCriterion != null &&
                            actualDateCriterion.operation == Operation.BTW &&
                            actualDateCriterion.values.size == 2 &&
                            actualDateCriterion.values[0] == thisMonthStart &&
                            actualDateCriterion.values[1] == thisMonthEnd

                    val isLastMonth = actualDateCriterion != null &&
                            actualDateCriterion.operation == Operation.BTW &&
                            actualDateCriterion.values.size == 2 &&
                            actualDateCriterion.values[0] == lastMonthStart &&
                            actualDateCriterion.values[1] == lastMonthEnd

                    val isLast30Days = actualDateCriterion != null &&
                            actualDateCriterion.operation == Operation.BTW &&
                            actualDateCriterion.values.size == 2 &&
                            actualDateCriterion.values[0] == last30DaysStart &&
                            actualDateCriterion.values[1] == last30DaysEnd

                    val isCustomDate = actualDateCriterion != null && !isThisMonth && !isLastMonth && !isLast30Days
                    val isAllDates = currentDateCriterion == null

                    // Amount type preset calculations
                    val currentAmountCriterion = criteriaSet.value.find {
                        it is AmountCriterion || (it is NotCriterion && it.criterion is AmountCriterion)
                    }
                    val actualAmountCriterion = (currentAmountCriterion as? AmountCriterion)
                        ?: (currentAmountCriterion as? NotCriterion)?.criterion as? AmountCriterion

                    val isIncomeOnly = actualAmountCriterion != null &&
                            actualAmountCriterion.sign &&
                            actualAmountCriterion.operation == Operation.GTE &&
                            actualAmountCriterion.values == listOf(0L)

                    val isExpenseOnly = actualAmountCriterion != null &&
                            !actualAmountCriterion.sign &&
                            actualAmountCriterion.operation == Operation.LTE &&
                            actualAmountCriterion.values == listOf(0L)

                    val currentTransferCriterion = criteriaSet.value.find {
                        it is TransferCriterion || (it is NotCriterion && it.criterion is TransferCriterion)
                    }
                    val isTransferOnly = currentTransferCriterion != null
                    val isAllTypes = !isIncomeOnly && !isExpenseOnly && !isTransferOnly

                    // --- 3. Scrollable Content Body ---
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = 12.dp)
                    ) {
                        // === Section A: Quick Presets (Date & Transaction Type) ===
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = "DATE SHORTCUTS",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = isAllDates,
                                    onClick = {
                                        currentDateCriterion?.let { criteriaSet.value -= it }
                                    },
                                    label = { Text(stringResource(R.string.period_all_time)) }
                                )
                                FilterChip(
                                    selected = isThisMonth,
                                    onClick = {
                                        currentDateCriterion?.let { criteriaSet.value -= it }
                                        if (!isThisMonth) {
                                            val newCrit = DateCriterion(thisMonthStart, thisMonthEnd)
                                            criteriaSet.value += newCrit
                                        }
                                    },
                                    label = { Text(stringResource(R.string.period_this_month)) }
                                )
                                FilterChip(
                                    selected = isLastMonth,
                                    onClick = {
                                        currentDateCriterion?.let { criteriaSet.value -= it }
                                        if (!isLastMonth) {
                                            val newCrit = DateCriterion(lastMonthStart, lastMonthEnd)
                                            criteriaSet.value += newCrit
                                        }
                                    },
                                    label = { Text(stringResource(R.string.period_last_month)) }
                                )
                                FilterChip(
                                    selected = isLast30Days,
                                    onClick = {
                                        currentDateCriterion?.let { criteriaSet.value -= it }
                                        if (!isLast30Days) {
                                            val newCrit = DateCriterion(last30DaysStart, last30DaysEnd)
                                            criteriaSet.value += newCrit
                                        }
                                    },
                                    label = { Text("Last 30 Days") }
                                )
                                FilterChip(
                                    selected = isCustomDate,
                                    onClick = {
                                        handleDateEdit(actualDateCriterion)
                                    },
                                    label = { Text(if (isCustomDate && actualDateCriterion != null) actualDateCriterion.prettyPrint(LocalContext.current) else "Custom Range… 📅") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.DateRange,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "TRANSACTION TYPE",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = isAllTypes,
                                    onClick = {
                                        if (isIncomeOnly || isExpenseOnly) {
                                            currentAmountCriterion?.let { criteriaSet.value -= it }
                                        }
                                        if (isTransferOnly) {
                                            currentTransferCriterion?.let { criteriaSet.value -= it }
                                        }
                                    },
                                    label = { Text(stringResource(R.string.tab_all)) }
                                )
                                FilterChip(
                                    selected = isIncomeOnly,
                                    onClick = {
                                        currentAmountCriterion?.let { criteriaSet.value -= it }
                                        currentTransferCriterion?.let { criteriaSet.value -= it }
                                        if (!isIncomeOnly) {
                                            criteriaSet.value += AmountCriterion.create(
                                                Operation.GTE,
                                                account.currency,
                                                true,
                                                0L,
                                                null
                                            )
                                        }
                                    },
                                    label = { Text("🟢 ${stringResource(R.string.income)}") }
                                )
                                FilterChip(
                                    selected = isExpenseOnly,
                                    onClick = {
                                        currentAmountCriterion?.let { criteriaSet.value -= it }
                                        currentTransferCriterion?.let { criteriaSet.value -= it }
                                        if (!isExpenseOnly) {
                                            criteriaSet.value += AmountCriterion.create(
                                                Operation.GTE,
                                                account.currency,
                                                false,
                                                0L,
                                                null
                                            )
                                        }
                                    },
                                    label = { Text("🔴 ${stringResource(R.string.expense)}") }
                                )
                                if (sumInfo.hasTransfers) {
                                    FilterChip(
                                        selected = isTransferOnly,
                                        onClick = {
                                            if (isTransferOnly) {
                                                currentTransferCriterion?.let { criteriaSet.value -= it }
                                            } else {
                                                handleTransferEdit(currentTransferCriterion as? TransferCriterion)
                                            }
                                        },
                                        label = { Text("🔁 ${stringResource(R.string.transfer)}") }
                                    )
                                }
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )

                        // === Section B: Add Criteria Category Grid / Cards ===
                        if (isComplexSearch) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "FILTER CATEGORIES",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.8.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    filters.forEach { info ->
                                        val activeCount = criteriaSet.value.count {
                                            it::class == info.clazz || ((it as? NotCriterion)?.criterion?.let { c -> c::class == info.clazz } == true)
                                        }
                                        val hasFilter = activeCount > 0
                                        val accessibleButtonText =
                                            stringResource(R.string.add_filter) + ": " +
                                                    stringResource(info.extendedTitle)

                                        FilterChip(
                                            selected = hasFilter,
                                            onClick = { handleEdit(info.clazz) },
                                            label = {
                                                Text(
                                                    text = stringResource(info.title),
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = if (hasFilter) FontWeight.SemiBold else FontWeight.Normal
                                                )
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = info.icon,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            },
                                            trailingIcon = if (hasFilter) {
                                                {
                                                    Surface(
                                                        shape = CircleShape,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(18.dp)
                                                    ) {
                                                        Box(contentAlignment = Alignment.Center) {
                                                            Text(
                                                                text = if (activeCount > 1) "$activeCount" else "✓",
                                                                color = MaterialTheme.colorScheme.onPrimary,
                                                                style = MaterialTheme.typography.labelSmall,
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }
                                                    }
                                                }
                                            } else null,
                                            modifier = Modifier.clearAndSetSemantics {
                                                contentDescription = accessibleButtonText
                                            }
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // === Section C: Active Filters List (or Empty State) ===
                            if (criteriaSet.value.isEmpty()) {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                    ),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.FilterList,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                            modifier = Modifier.size(36.dp)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "No filters active",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = stringResource(R.string.filter_dialog_empty),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            } else {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "ACTIVE FILTERS (${criteriaSet.value.size})",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.8.sp
                                        )

                                        if (criteriaSet.value.size > 1) {
                                            Row(
                                                modifier = Modifier.selectableGroup(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                listOf(
                                                    COMPLEX_AND to R.string.matchAll,
                                                    COMPLEX_OR to R.string.matchAny
                                                ).forEach { (complexId, labelRes) ->
                                                    FilterChip(
                                                        selected = selectedComplex == complexId,
                                                        onClick = { selectedComplex = complexId },
                                                        label = {
                                                            Text(
                                                                text = stringResource(labelRes),
                                                                style = MaterialTheme.typography.labelSmall
                                                            )
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .semantics {
                                                collectionInfo = CollectionInfo(criteriaSet.value.size, 1)
                                            },
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        criteriaSet.value.forEachIndexed { index, criterion ->
                                            val negate = { criteriaSet.value = criteriaSet.value.negate(index) }
                                            val delete = { criteriaSet.value -= criterion }
                                            val edit = { handleEdit(criterion) }
                                            val title = stringResource(criterion.displayTitle)
                                            val symbol = criterion.displaySymbol.first
                                            val prettyPrint = ((criterion as? NotCriterion)?.criterion
                                                ?: criterion).prettyPrint(LocalContext.current)
                                            val contentDescription = criterion.contentDescription(LocalContext.current)
                                            val labelNegate = stringResource(R.string.negation)
                                            val labelDelete = stringResource(R.string.menu_delete)
                                            val labelEdit = stringResource(R.string.menu_edit)

                                            ElevatedCard(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clearAndSetSemantics {
                                                        this.contentDescription = contentDescription
                                                        collectionItemInfo = CollectionItemInfo(index, 1, 1, 1)
                                                        customActions = listOf(
                                                            CustomAccessibilityAction(
                                                                label = labelNegate,
                                                                action = {
                                                                    negate()
                                                                    true
                                                                }
                                                            ),
                                                            CustomAccessibilityAction(
                                                                label = labelDelete,
                                                                action = {
                                                                    delete()
                                                                    true
                                                                }
                                                            ),
                                                            CustomAccessibilityAction(
                                                                label = labelEdit,
                                                                action = {
                                                                    edit()
                                                                    true
                                                                }
                                                            )
                                                        )
                                                    },
                                                colors = CardDefaults.elevatedCardColors(
                                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                                ),
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Surface(
                                                        shape = CircleShape,
                                                        color = MaterialTheme.colorScheme.primaryContainer,
                                                        modifier = Modifier.size(34.dp)
                                                    ) {
                                                        Box(contentAlignment = Alignment.Center) {
                                                            Icon(
                                                                imageVector = criterion.displayIcon,
                                                                contentDescription = null,
                                                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                                modifier = Modifier.size(18.dp)
                                                            )
                                                        }
                                                    }

                                                    Column(
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .padding(horizontal = 12.dp)
                                                    ) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                        ) {
                                                            Text(
                                                                text = title,
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.primary,
                                                                fontWeight = FontWeight.SemiBold
                                                            )
                                                            if (criterion is NotCriterion) {
                                                                Surface(
                                                                    shape = RoundedCornerShape(4.dp),
                                                                    color = MaterialTheme.colorScheme.errorContainer
                                                                ) {
                                                                    Text(
                                                                        text = stringResource(R.string.filter_is_not),
                                                                        style = MaterialTheme.typography.labelSmall,
                                                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                                    )
                                                                }
                                                            }
                                                        }
                                                        Text(
                                                            text = prettyPrint,
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            color = MaterialTheme.colorScheme.onSurface,
                                                            fontWeight = FontWeight.Medium
                                                        )
                                                    }

                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        ActionButton(labelNegate, onClick = negate) {
                                                            CharIcon(symbol)
                                                        }
                                                        ActionButton(
                                                            hintText = labelEdit,
                                                            icon = Icons.Filled.Edit,
                                                            onClick = edit
                                                        )
                                                        ActionButton(
                                                            hintText = labelDelete,
                                                            icon = Icons.Filled.Close,
                                                            onClick = delete
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            // === Quick Search Checklist Mode ===
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                filters.forEach { info ->
                                    val definedCriterion =
                                        criteriaSet.value.find { it::class == info.clazz }
                                    val hasCriterion = definedCriterion != null

                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .toggleable(
                                                value = hasCriterion,
                                                role = Role.Checkbox,
                                                onValueChange = {
                                                    if (hasCriterion) {
                                                        onConfirmRequest(
                                                            TYPE_QUICK,
                                                            (criteriaSet.value - definedCriterion).wrap()
                                                        )
                                                    } else {
                                                        handleEdit(info.clazz)
                                                    }
                                                }
                                            ),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (hasCriterion)
                                                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                                            else
                                                MaterialTheme.colorScheme.surfaceContainerLow
                                        ),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 14.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = info.icon,
                                                contentDescription = null,
                                                tint = if (hasCriterion) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(22.dp)
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = stringResource(info.title),
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                if (hasCriterion && definedCriterion != null) {
                                                    Text(
                                                        text = definedCriterion.prettyPrint(LocalContext.current),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                            Checkbox(
                                                checked = hasCriterion,
                                                onCheckedChange = null
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // --- 4. Sticky Bottom Action Bar ---
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Text(stringResource(android.R.string.cancel))
                        }

                        Button(
                            onClick = {
                                onConfirmRequest(
                                    if (isComplexSearch) TYPE_COMPLEX else TYPE_QUICK,
                                    criteriaSet.value.wrap(selectedComplex)
                                )
                            },
                            modifier = Modifier.weight(1.4f),
                            enabled = isDirty || criteriaSet.value.isNotEmpty(),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Done,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(18.dp)
                                    .padding(end = 4.dp)
                            )
                            Text(
                                text = if (criteriaSet.value.isNotEmpty())
                                    "${stringResource(R.string.apply)} (${criteriaSet.value.size})"
                                else
                                    stringResource(R.string.apply)
                            )
                        }
                    }
                }
            }

            if (confirmDiscard) {
                AlertDialog(
                    onDismissRequest = { confirmDiscard = false },
                    confirmButton = {
                        TextButton(onClick = onDismissRequest) {
                            Text(stringResource(R.string.response_yes))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmDiscard = false }) {
                            Text(stringResource(R.string.response_no))
                        }
                    },
                    text = {
                        Text(stringResource(R.string.dialog_confirm_discard_changes))
                    }
                )

            }

            if (confirmClear) {
                AlertDialog(
                    onDismissRequest = { confirmClear = false },
                    confirmButton = {
                        TextButton(onClick = {
                            confirmClear = false
                            criteriaSet.value = emptySet()
                        }) {
                            Text(stringResource(android.R.string.ok))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmClear = false }) {
                            Text(stringResource(android.R.string.cancel))
                        }
                    },
                    text = {
                        Text(stringResource(R.string.clear_all_filters))
                    }
                )

            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionButton(
    hintText: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    ActionButton(hintText, modifier, enabled, onClick) {
        Icon(
            imageVector = icon,
            contentDescription = hintText
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionButton(
    hintText: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    //workaround for https://issuetracker.google.com/issues/283821298
    Box(modifier = modifier) {
        TooltipBox(
            positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
            tooltip = { PlainTooltip { Text(hintText) } },
            state = rememberTooltipState(),
        ) {
            IconButton(onClick = onClick, enabled = enabled, content = content)
        }
    }
}

private fun Set<Criterion>.negate(atIndex: Int) = mapIndexed { index, criterion ->
    if (index == atIndex) {
        if (criterion is NotCriterion) criterion.criterion else NotCriterion(criterion)
    } else criterion
}.toSet()

private fun Set<Criterion>.wrap(selectedComplex: Int = COMPLEX_AND) = when (size) {
    0 -> null
    1 -> first()
    else -> if (selectedComplex == COMPLEX_AND) AndCriterion(this) else OrCriterion(this)
}

/**
 * we can switch to quick search, if
 * - there are no criteria
 * - there are no negated criteria
 * - there is only 1 criterion
 * - criteria are joined with AND and there are no 2 criteria of the same type
 */
private fun Set<Criterion>.isSimple(selectedComplex: Int) =
    isEmpty() || (
            none { it is NotCriterion } && (
                    (size == 1) || (
                            (selectedComplex == COMPLEX_AND) && groupBy { it::class }.all { it.value.size == 1 }
                            )
                    )
            )

@Preview
@Composable
fun FilterDialogEmpty() {
    FilterDialog(
        account = PageAccount(
            id = 1,
            label = "Test account",
            currencyUnit = CurrencyUnit.DebugInstance,
            type = AccountType.CASH,
        ),
        sumInfo = SumInfo.EMPTY,
    )
}

@Preview
@Composable
fun FilterDialogPreview() {
    FilterDialog(
        account = PageAccount(
            id = 1,
            label = "Test account",
            currencyUnit = CurrencyUnit.DebugInstance,
            type = AccountType.CASH,
        ),
        sumInfo = SumInfo.EMPTY,
        criterion = AndCriterion(
            setOf(
                NotCriterion(CommentCriterion("search")),
                CommentCriterion("search")
            )
        )
    )
}