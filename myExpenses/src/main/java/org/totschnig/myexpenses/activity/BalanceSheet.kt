package org.totschnig.myexpenses.activity

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import org.totschnig.myexpenses.compose.LocalCurrencyFormatter
import org.totschnig.myexpenses.util.convAmount
import org.totschnig.myexpenses.model.PREDEFINED_NAME_ASSET
import org.totschnig.myexpenses.model.PREDEFINED_NAME_BANK
import org.totschnig.myexpenses.model.PREDEFINED_NAME_CASH
import org.totschnig.myexpenses.model.PREDEFINED_NAME_CCARD
import org.totschnig.myexpenses.model.PREDEFINED_NAME_INVESTMENT
import org.totschnig.myexpenses.model.PREDEFINED_NAME_LIABILITY
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.core.content.res.ResourcesCompat
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.PieEntry
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.compose.AmountText
import org.totschnig.myexpenses.compose.CheckableMenuEntry
import org.totschnig.myexpenses.compose.ColoredAmountText
import org.totschnig.myexpenses.compose.LocalCurrencyContext
import org.totschnig.myexpenses.compose.LocalDateFormatter
import org.totschnig.myexpenses.compose.PieChartCompose
import org.totschnig.myexpenses.compose.TooltipIconButton
import org.totschnig.myexpenses.compose.TooltipIconMenu
import org.totschnig.myexpenses.compose.conditional
import org.totschnig.myexpenses.compose.filter.ActionButton
import org.totschnig.myexpenses.compose.optional
import org.totschnig.myexpenses.model.AccountType
import org.totschnig.myexpenses.model.CurrencyUnit
import org.totschnig.myexpenses.util.epochMillis2LocalDate
import org.totschnig.myexpenses.util.toEpoch
import org.totschnig.myexpenses.viewmodel.data.BalanceSection
import org.totschnig.myexpenses.viewmodel.data.FullAccount
import org.totschnig.myexpenses.viewmodel.data.FullAccount.Companion.partitionByAccountType
import timber.log.Timber
import java.time.LocalDate
import java.util.Currency
import kotlin.math.abs
import kotlin.math.absoluteValue
import kotlin.math.roundToLong

@Composable
fun BalanceSheetOptions(
    showHiddenState: MutableState<Boolean>?,
    showZeroState: MutableState<Boolean>?,
    showChartState: MutableState<Boolean>,
    highlight: MutableState<Triple<Boolean, Int, Long?>?>,
    onPrint: () -> Unit,
    isFullScreen: Boolean = true,
    onToggleFullScreen: (() -> Unit)? = null,
) {
    TooltipIconButton(
        tooltip = stringResource(R.string.menu_print),
        imageVector = Icons.Filled.Print,
        onClick = onPrint
    )
    TooltipIconMenu(
        tooltip = stringResource(R.string.options),
        imageVector = Icons.Filled.Tune,
        menu = listOfNotNull(
            showHiddenState?.let {
                CheckableMenuEntry(
                    label = R.string.show_invisible,
                    isChecked = it.value,
                    command = "TOGGLE_SHOW_INVISIBLE"
                ) {
                    showHiddenState.value = !showHiddenState.value
                }
            },
            showZeroState?.let {
                CheckableMenuEntry(
                    label = R.string.show_zero,
                    isChecked = it.value,
                    command = "TOGGLE_SHOW_ZERO"
                ) {
                    showZeroState.value = !showZeroState.value
                }
            },
            CheckableMenuEntry(
                label = R.string.menu_chart,
                isChecked = showChartState.value,
                command = "TOGGLE_CHART_BALANCE"
            ) {
                showChartState.value = !showChartState.value
                highlight.value = null
            },
            onToggleFullScreen?.let {
                CheckableMenuEntry(
                    label = R.string.full_screen,
                    isChecked = isFullScreen,
                    command = "FULL_SCREEN",
                    action = onToggleFullScreen
                )
            }
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BalanceSheetView(
    modifier: Modifier = Modifier,
    accounts: List<FullAccount>,
    debtSum: Long = 0L,
    date: LocalDate = LocalDate.now(),
    onClose: () -> Unit = {},
    onNavigate: (FullAccount) -> Unit = {},
    onSetDate: (LocalDate) -> Unit = {},
    onPrint: () -> Unit = {},
    showHiddenState: MutableState<Boolean>,
    showZeroState: MutableState<Boolean>,
    showChartState: MutableState<Boolean>,
) {

    Column(modifier) {
        val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
        //Triple of asset or liability, position in section, index of account or null for Debts
        val highlight = remember { mutableStateOf<Triple<Boolean, Int, Long?>?>(null) }
        TopAppBar(
            windowInsets = WindowInsets(0, 0, 0, 0),
            title = {
                Text(text = stringResource(R.string.balance_sheet))
            },
            navigationIcon = {
                IconButton(onClick = onClose) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Close"
                    )
                }
            },
            scrollBehavior = scrollBehavior,
            actions = {
                BalanceSheetOptions(
                    showHiddenState.takeIf { accounts.any { !it.isVisible } },
                    showZeroState.takeIf { accounts.any { it.effectiveBalance == 0L } },
                    showChartState,
                    highlight,
                    onPrint,
                )
            },
            colors = TopAppBarDefaults.topAppBarColors()
        )

        BalanceSheetViewInner(
            accounts = accounts,
            debtSum = debtSum,
            date = date,
            onSetDate = onSetDate,
            showChart = showChartState.value,
            onToggleChart = { showChartState.value = !showChartState.value },
            highlight = highlight,
            nestedScrollConnection = scrollBehavior.nestedScrollConnection,
            showHidden = showHiddenState.value,
            showZero = showZeroState.value,
            onNavigate = onNavigate,
            bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        )
    }
}

fun getAccountTypeIcon(accountTypeName: String, isAsset: Boolean = true): ImageVector {
    return when (accountTypeName) {
        PREDEFINED_NAME_BANK -> Icons.Default.AccountBalance
        PREDEFINED_NAME_CASH -> Icons.Default.Payments
        PREDEFINED_NAME_CCARD -> Icons.Default.CreditCard
        PREDEFINED_NAME_INVESTMENT -> Icons.AutoMirrored.Filled.ShowChart
        PREDEFINED_NAME_ASSET -> Icons.Default.AccountBalanceWallet
        PREDEFINED_NAME_LIABILITY -> Icons.AutoMirrored.Filled.ReceiptLong
        else -> if (isAsset) Icons.Default.AccountBalanceWallet else Icons.Default.CreditCard
    }
}

@Composable
fun HeroNetWorthCard(
    netWorth: Long,
    totalAssets: Long,
    totalLiabilities: Long,
    debtSum: Long,
    currency: CurrencyUnit,
    date: LocalDate,
    showChart: Boolean,
    onToggleChart: (() -> Unit)?,
    onOpenDatePicker: () -> Unit,
) {
    val format = LocalCurrencyFormatter.current
    val dateFormatter = LocalDateFormatter.current
    val isNegative = netWorth < 0

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header title + Date Chip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(
                                MaterialTheme.colorScheme.primaryContainer,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountBalanceWallet,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.balance_sheet_net_worth),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.clickable(onClick = onOpenDatePicker)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (date == LocalDate.now()) "Today • " + dateFormatter.format(date) else dateFormatter.format(date),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Net Worth display
            Text(
                text = format.convAmount(netWorth, currency),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = if (isNegative) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Sub-badges row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Assets Pill
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF2E7D32).copy(alpha = 0.12f),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(Color(0xFF2E7D32), CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.balance_sheet_section_assets),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = format.convAmount(totalAssets, currency),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2E7D32)
                            )
                        }
                    }
                }

                // Liabilities Pill
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(MaterialTheme.colorScheme.error, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.balance_sheet_section_liabilities),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = format.convAmount(totalLiabilities.absoluteValue, currency),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                // Chart toggle chip
                if (onToggleChart != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (showChart) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, if (showChart) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.clickable(onClick = onToggleChart)
                    ) {
                        Box(
                            modifier = Modifier.padding(10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ShowChart,
                                contentDescription = stringResource(R.string.menu_chart),
                                modifier = Modifier.size(20.dp),
                                tint = if (showChart) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColumnScope.BalanceSheetViewInner(
    accounts: List<FullAccount>,
    debtSum: Long,
    date: LocalDate,
    onSetDate: (LocalDate) -> Unit,
    showChart: Boolean,
    onToggleChart: (() -> Unit)? = null,
    highlight: MutableState<Triple<Boolean, Int, Long?>?>,
    nestedScrollConnection: NestedScrollConnection? = null,
    showHidden: Boolean,
    showZero: Boolean,
    onNavigate: (FullAccount) -> Unit,
    bottomPadding: Dp,
) {

    val horizontalPadding = dimensionResource(R.dimen.padding_main_screen)
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = date.toEpoch() * 1000
    )
    var showDatePicker by rememberSaveable { mutableStateOf(false) }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    showDatePicker = false
                    datePickerState.selectedDateMillis?.let {
                        onSetDate(epochMillis2LocalDate(it))
                    }
                }) {
                    Text(stringResource(R.string.confirm))
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

    val (assets, totalAssets, liabilities, totalLiabilities) = accounts.partitionByAccountType()

    val assetsWithDebts = totalAssets + (debtSum.takeIf { it > 0L } ?: 0L)
    val liabilitiesWithDebts = totalLiabilities + (debtSum.takeIf { it < 0L } ?: 0L)
    val ratio = if (assetsWithDebts > 0L && liabilitiesWithDebts < 0L)
        assetsWithDebts.toFloat() / -liabilitiesWithDebts else 1f
    val angles = if (ratio > 1f) 360f to 360f / ratio else
        360f * ratio to 360f
    LayoutHelper(
        showChart = showChart,
        chart = { modifier ->
            Box(
                modifier = modifier
                    .fillMaxWidth()
            ) {
                RenderChart(
                    modifier = Modifier
                        .fillMaxSize(0.95f)
                        .align(Alignment.Center),
                    inner = false,
                    accounts = assets.flatMap { it.accounts },
                    debts = debtSum.takeIf { it > 0 },
                    highlight = highlight,
                    angle = angles.first,
                )
                RenderChart(
                    modifier = Modifier
                        .fillMaxSize(0.75f)
                        .align(Alignment.Center),
                    inner = true,
                    accounts = liabilities.flatMap { it.accounts },
                    debts = debtSum.takeIf { it < 0 },
                    highlight = highlight,
                    angle = angles.second,
                )
            }
        },
        data = { modifier ->

            val listState = rememberLazyListState()
            fun lookup(id: Long?): Int {
                var totalIndex = 0 // Hero card
                totalIndex++ // Assets chapter header
                assets.forEach { section ->
                    val visibleAccounts = section.accounts.filter { (showHidden || it.isVisible) && (showZero || it.effectiveBalance != 0L) }
                    if (visibleAccounts.isNotEmpty()) {
                        if (visibleAccounts.any { it.id == id }) {
                            return totalIndex
                        }
                        totalIndex++
                    }
                }
                totalIndex++ // Liabilities chapter header
                liabilities.forEach { section ->
                    val visibleAccounts = section.accounts.filter { (showHidden || it.isVisible) && (showZero || it.effectiveBalance != 0L) }
                    if (visibleAccounts.isNotEmpty()) {
                        if (visibleAccounts.any { it.id == id }) {
                            return totalIndex
                        }
                        totalIndex++
                    }
                }
                return totalIndex
            }
            LazyColumn(
                modifier = modifier.optional(nestedScrollConnection) {
                    nestedScroll(it)
                },
                contentPadding = PaddingValues(
                    start = horizontalPadding,
                    end = horizontalPadding,
                    bottom = bottomPadding
                ),
                state = listState
            ) {
                item {
                    HeroNetWorthCard(
                        netWorth = totalAssets + totalLiabilities + debtSum,
                        totalAssets = totalAssets,
                        totalLiabilities = totalLiabilities,
                        debtSum = debtSum,
                        currency = LocalCurrencyContext.current.homeCurrencyUnit,
                        date = date,
                        showChart = showChart,
                        onToggleChart = onToggleChart,
                        onOpenDatePicker = {
                            datePickerState.selectedDateMillis = date.toEpoch() * 1000
                            showDatePicker = true
                        }
                    )
                }

                accountTypeChapter(
                    title = R.string.balance_sheet_section_assets,
                    total = totalAssets,
                    sections = assets,
                    showHidden = showHidden,
                    showZero = showZero,
                    highlight = highlight.value?.third,
                    onNavigate = onNavigate,
                    isAsset = true
                )
                accountTypeChapter(
                    title = R.string.balance_sheet_section_liabilities,
                    total = totalLiabilities,
                    sections = liabilities,
                    showHidden = showHidden,
                    showZero = showZero,
                    highlight = highlight.value?.third,
                    onNavigate = onNavigate,
                    isAsset = false
                )

                if (debtSum != 0L) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            BalanceSheetSectionHeaderView(
                                name = stringResource(R.string.debts),
                                total = debtSum,
                                highlight = highlight.value != null && highlight.value?.third == null,
                                absolute = false
                            )
                        }
                    }
                }
            }
            LaunchedEffect(highlight.value) {
                highlight.value?.let {
                    lookup(it.third)
                }?.let {
                    Timber.d("Scrolling to $it")
                    listState.animateScrollToItem(it)
                }
            }
        }
    )
}

fun LazyListScope.accountTypeChapter(
    @StringRes title: Int,
    total: Long,
    sections: List<BalanceSection>,
    showHidden: Boolean,
    showZero: Boolean,
    highlight: Long?,
    onNavigate: (FullAccount) -> Unit,
    isAsset: Boolean = true,
) {
    item {
        val homeCurrency = LocalCurrencyContext.current.homeCurrencyUnit
        val format = LocalCurrencyFormatter.current
        val accentColor = if (isAsset) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 8.dp, start = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(accentColor.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isAsset) Icons.Default.AccountBalance else Icons.Default.CreditCard,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = accentColor
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = format.convAmount(if (isAsset) total else total.absoluteValue, homeCurrency),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = accentColor
            )
        }
    }

    sections.forEach { section ->
        val visibleAccounts = section.accounts.filter {
            (showHidden || it.isVisible) && (showZero || it.effectiveBalance != 0L)
        }
        if (visibleAccounts.isNotEmpty()) {
            accountTypeSection(
                section = section,
                visibleAccounts = visibleAccounts,
                highlight = highlight,
                onNavigate = onNavigate,
                isAsset = isAsset
            )
        }
    }
}

@Composable
fun BalanceSheetSectionHeaderView(
    modifier: Modifier = Modifier,
    name: String,
    total: Long,
    highlight: Boolean = false,
    absolute: Boolean = true,
) {
    val homeCurrency = LocalCurrencyContext.current.homeCurrencyUnit
    Row(
        modifier = modifier
            .conditional(highlight) {
                background(MaterialTheme.colorScheme.primaryContainer)
            }
            .fillMaxWidth()
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        ColoredAmountText(
            amount = total,
            currency = homeCurrency,
            absolute = absolute,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium
        )
    }
}

fun LazyListScope.accountTypeSection(
    section: BalanceSection,
    visibleAccounts: List<FullAccount>,
    highlight: Long?,
    onNavigate: (FullAccount) -> Unit,
    isAsset: Boolean = true,
) {
    item {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                val homeCurrency = LocalCurrencyContext.current.homeCurrencyUnit
                val format = LocalCurrencyFormatter.current

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = section.type.title(LocalContext.current),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = format.convAmount(if (isAsset) section.total else section.total.absoluteValue, homeCurrency),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    thickness = 0.5.dp,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )

                visibleAccounts.forEachIndexed { index, account ->
                    BalanceAccountItemView(
                        account = account,
                        highlight = highlight == account.id,
                        onNavigate = onNavigate,
                        isAsset = isAsset
                    )
                    if (index < visibleAccounts.lastIndex) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(horizontal = 14.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BalanceAccountItemView(
    account: FullAccount,
    highlight: Boolean,
    onNavigate: (FullAccount) -> Unit,
    isAsset: Boolean = true,
) {
    val homeCurrency = LocalCurrencyContext.current.homeCurrencyUnit
    val format = LocalCurrencyFormatter.current
    val isDebt = account.equivalentEffectiveBalance < 0
    val iconColor = Color(account.color(resources = LocalResources.current))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .conditional(highlight) {
                background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
            }
            .clickable { onNavigate(account) }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(iconColor.copy(alpha = 0.15f), shape = CircleShape)
                .border(1.dp, iconColor.copy(alpha = 0.35f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = getAccountTypeIcon(account.type.name, isAsset),
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = account.label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!account.description.isNullOrBlank()) {
                Text(
                    text = account.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = format.convAmount(account.equivalentEffectiveBalance.absoluteValue, homeCurrency),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (isDebt) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
            if (account.currencyUnit.code != homeCurrency.code && account.effectiveBalance != 0L) {
                AmountText(
                    account.effectiveBalance.absoluteValue,
                    account.currencyUnit,
                    fontSize = 11.sp
                )
            }
        }

        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(18.dp)
        )
    }
}


@Composable
fun RenderChart(
    modifier: Modifier,
    inner: Boolean,
    accounts: List<FullAccount>,
    debts: Long?,
    highlight: MutableState<Triple<Boolean, Int, Long?>?>,
    angle: Float = 360f,
) {
    val context = LocalContext.current
    val accounts = accounts.filter { it.equivalentEffectiveBalance != 0L }
    val pieEntries = accounts.map { account ->
        PieEntry(
            abs(account.equivalentEffectiveBalance.toFloat()),
            account.label
        )
    }
    val colors = accounts.map { it.color }
    PieChartCompose(
        modifier = modifier,
        factory = { ctx ->
            PieChart(ctx)
        },
        holeRadius = if (inner) 75f else 85f,
        angle = angle,
        onValueSelected = { index ->
            highlight.value = index?.let { Triple(inner, index, accounts.getOrNull(index)?.id) }
        },
        data = if (debts == null)
            pieEntries else
            pieEntries + PieEntry(
                debts.toFloat().absoluteValue,
                stringResource(R.string.debts)
            ),
        colors = if (debts == null) colors else colors +
                ResourcesCompat.getColor(
                    LocalResources.current,
                    if (inner) R.color.colorExpense else R.color.colorIncome,
                    context.theme
                )
    ) {
        if (highlight.value?.first == !inner) {
            it.highlightValue(null)
            it.centerText = ""
        }
    }
}

@Composable
fun LayoutHelper(
    showChart: Boolean,
    chart: @Composable (Modifier) -> Unit,
    data: @Composable (Modifier) -> Unit,
) {
    if (showChart) {
        BoxWithConstraints {
            if (this.maxHeight > 600.dp || this.maxWidth < 600.dp) {
                Column {
                    chart(Modifier.weight(1f))
                    data(Modifier.weight(1f))
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    chart(Modifier.weight(1f))
                    data(Modifier.weight(1f))
                }
            }
        }
    } else {
        data(Modifier)
    }
}

@Preview
@Composable
fun BalanceSheet() {
    BalanceSheetView(
        accounts = listOf(
            FullAccount(
                label = "Checking Account",
                currentBalance = 500000,
                id = 1,
                currencyUnit = CurrencyUnit.DebugInstance,
                type = AccountType.BANK, // $5,000.00 (assuming cents)
            ),
            FullAccount(
                label = "Savings Account",
                type = AccountType.BANK,
                id = 2,
                currencyUnit = CurrencyUnit.DebugInstance,
                currentBalance = 1250000, // $12,500.00
            ),
            FullAccount(
                label = "Credit Card 1",
                type = AccountType.CCARD,
                id = 3,
                currencyUnit = CurrencyUnit.DebugInstance,
                currentBalance = -25000, // -$250.00 (negative balance for debt)
            ),
            FullAccount(
                label = "Credit Card 2",
                type = AccountType.CCARD,
                id = 4,
                currencyUnit = CurrencyUnit.DebugInstance,
                currentBalance = -100000, // -$1,000.00
            ),
            FullAccount(
                label = "USD Cash",
                currentBalance = 200000, // €2,000.00
                id = 5,
                type = AccountType.CASH,
                currencyUnit = CurrencyUnit(Currency.getInstance("USD")),
                equivalentCurrentBalance = (200000 * 0.92).roundToLong()
            ),
            FullAccount(
                label = "JPY Account",
                type = AccountType.BANK,
                id = 6,
                currentBalance = 5000000, // ¥50,000.00
                currencyUnit = CurrencyUnit(Currency.getInstance("JPY")),
                equivalentCurrentBalance = (5000000 * 0.0075).roundToLong(),
            ),
            FullAccount(
                label = "USD Invest Account",
                type = AccountType.ASSET,
                id = 7,
                currencyUnit = CurrencyUnit.DebugInstance,
                currentBalance = 2000000, // $20,000
            ),
            FullAccount(
                label = "GBP Cash",
                id = 8,
                type = AccountType.CASH,
                currentBalance = 100000, // £1,000.00
                currencyUnit = CurrencyUnit(Currency.getInstance("GBP")),
                equivalentCurrentBalance = (100000 * 1.28).roundToLong(),
            ),
            FullAccount(
                label = "Loan Account",
                id = 9,
                type = AccountType.LIABILITY,
                currencyUnit = CurrencyUnit.DebugInstance,
                currentBalance = -1000000, // -$10,000.00 (loan debt)
            ),
            FullAccount(
                label = "Petty Cash",
                id = 10,
                type = AccountType.CASH,
                currencyUnit = CurrencyUnit.DebugInstance,
                currentBalance = 10000, // $100.00 (loan debt)
            )
        ),
        showHiddenState = remember { mutableStateOf(false) },
        showZeroState = remember { mutableStateOf(false) },
        showChartState = remember { mutableStateOf(false) },
    )
}

