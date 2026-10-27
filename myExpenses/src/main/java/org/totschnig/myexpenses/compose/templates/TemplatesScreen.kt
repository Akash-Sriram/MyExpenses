package org.totschnig.myexpenses.compose.templates

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.provider.CalendarContract
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.compose.LocalColors
import org.totschnig.myexpenses.compose.LocalCurrencyFormatter
import org.totschnig.myexpenses.db2.FLAG_EXPENSE
import org.totschnig.myexpenses.db2.FLAG_INCOME
import org.totschnig.myexpenses.db2.FLAG_TRANSFER
import org.totschnig.myexpenses.db2.entities.Template
import org.totschnig.myexpenses.injector
import org.totschnig.myexpenses.provider.KEY_ACCOUNT_LABEL
import org.totschnig.myexpenses.provider.KEY_AMOUNT
import org.totschnig.myexpenses.provider.KEY_CATID
import org.totschnig.myexpenses.provider.KEY_COLOR
import org.totschnig.myexpenses.provider.KEY_COMMENT
import org.totschnig.myexpenses.provider.KEY_CURRENCY
import org.totschnig.myexpenses.provider.KEY_DEFAULT_ACTION
import org.totschnig.myexpenses.provider.KEY_PATH
import org.totschnig.myexpenses.provider.KEY_PAYEE_NAME
import org.totschnig.myexpenses.provider.KEY_PLANID
import org.totschnig.myexpenses.provider.KEY_PLAN_INFO
import org.totschnig.myexpenses.provider.KEY_ROWID
import org.totschnig.myexpenses.provider.KEY_SEALED
import org.totschnig.myexpenses.provider.KEY_TITLE
import org.totschnig.myexpenses.provider.KEY_TRANSFER_ACCOUNT
import org.totschnig.myexpenses.provider.KEY_TRANSFER_ACCOUNT_LABEL
import org.totschnig.myexpenses.provider.KEY_TYPE
import org.totschnig.myexpenses.provider.getInt
import org.totschnig.myexpenses.provider.getIntIfExists
import org.totschnig.myexpenses.provider.getLong
import org.totschnig.myexpenses.provider.getLongOrNull
import org.totschnig.myexpenses.provider.getString
import org.totschnig.myexpenses.provider.getStringIfExists
import org.totschnig.myexpenses.provider.getStringOrNull
import org.totschnig.myexpenses.provider.isNull
import org.totschnig.myexpenses.util.convAmount
import org.totschnig.myexpenses.util.enumValueOrDefault
import kotlin.math.absoluteValue

data class TemplateItem(
    val id: Long,
    val title: String,
    val amount: Long,
    val currency: String,
    val type: Byte,
    val isTransfer: Boolean,
    val accountLabel: String,
    val transferAccountLabel: String?,
    val category: String,
    val payee: String?,
    val comment: String?,
    val color: Int,
    val isSealed: Boolean,
    val hasPlan: Boolean,
    val planId: Long?,
    val planInfo: String?,
    val defaultAction: Template.Action,
    val catId: Long
)

enum class TemplateFilter {
    ALL, PLANS, TEMPLATES
}

fun Cursor.toTemplateItemList(): List<TemplateItem> {
    val items = mutableListOf<TemplateItem>()
    if (moveToFirst()) {
        do {
            val id = getLong(KEY_ROWID)
            val title = getString(KEY_TITLE)
            val amount = getLong(KEY_AMOUNT)
            val currency = getString(KEY_CURRENCY)
            val type = getIntIfExists(KEY_TYPE)?.toByte() ?: FLAG_EXPENSE
            val isTransfer = !isNull(KEY_TRANSFER_ACCOUNT)
            val accountLabel = getString(KEY_ACCOUNT_LABEL)
            val transferAccountLabel = getStringOrNull(KEY_TRANSFER_ACCOUNT_LABEL)
            val category = getString(KEY_PATH)
            val payee = getStringOrNull(KEY_PAYEE_NAME)
            val comment = getStringOrNull(KEY_COMMENT)
            val color = getInt(KEY_COLOR)
            val isSealed = getInt(KEY_SEALED) != 0
            val planId = getLongOrNull(KEY_PLANID)
            val hasPlan = planId != null && planId != 0L
            val planInfo = getStringIfExists(KEY_PLAN_INFO)
            val defaultAction = enumValueOrDefault(
                getString(KEY_DEFAULT_ACTION),
                Template.Action.SAVE
            )
            val catId = getLongOrNull(KEY_CATID) ?: 0L

            items.add(
                TemplateItem(
                    id = id,
                    title = title,
                    amount = amount,
                    currency = currency,
                    type = type,
                    isTransfer = isTransfer,
                    accountLabel = accountLabel,
                    transferAccountLabel = transferAccountLabel,
                    category = category,
                    payee = payee,
                    comment = comment,
                    color = color,
                    isSealed = isSealed,
                    hasPlan = hasPlan,
                    planId = planId,
                    planInfo = planInfo,
                    defaultAction = defaultAction,
                    catId = catId
                )
            )
        } while (moveToNext())
    }
    return items
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplatesScreen(
    templates: List<TemplateItem>,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onNewTemplate: () -> Unit,
    onRunTemplate: (TemplateItem) -> Unit,
    onEditInstance: (TemplateItem) -> Unit,
    onEditTemplate: (TemplateItem) -> Unit,
    onToggleDefaultAction: (TemplateItem) -> Unit,
    onDeleteTemplate: (TemplateItem) -> Unit,
    onOpenPlanner: () -> Unit,
    onViewCalendarPlan: (Long) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf(TemplateFilter.ALL) }
    var templateToDelete by remember { mutableStateOf<TemplateItem?>(null) }

    val hasAnyPlans = remember(templates) { templates.any { it.hasPlan } }

    val filteredTemplates = remember(templates, searchQuery, selectedFilter) {
        templates.filter { item ->
            val matchesFilter = when (selectedFilter) {
                TemplateFilter.ALL -> true
                TemplateFilter.PLANS -> item.hasPlan
                TemplateFilter.TEMPLATES -> !item.hasPlan
            }
            val matchesSearch = if (searchQuery.isBlank()) {
                true
            } else {
                val q = searchQuery.trim().lowercase()
                item.title.lowercase().contains(q) ||
                        item.accountLabel.lowercase().contains(q) ||
                        item.category.lowercase().contains(q) ||
                        (item.payee?.lowercase()?.contains(q) == true) ||
                        (item.comment?.lowercase()?.contains(q) == true)
            }
            matchesFilter && matchesSearch
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    if (isSearchActive) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search templates & plans...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear")
                                    }
                                }
                            }
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.menu_manage_plans),
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isSearchActive) {
                            isSearchActive = false
                            searchQuery = ""
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(android.R.string.cancel)
                        )
                    }
                },
                actions = {
                    if (!isSearchActive) {
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                    }
                    if (hasAnyPlans) {
                        IconButton(onClick = onOpenPlanner) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = "Planner"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNewTemplate,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.menu_create_template)
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Filter Chips Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedFilter == TemplateFilter.ALL,
                    onClick = { selectedFilter = TemplateFilter.ALL },
                    label = { Text("All (${templates.size})") }
                )
                FilterChip(
                    selected = selectedFilter == TemplateFilter.PLANS,
                    onClick = { selectedFilter = TemplateFilter.PLANS },
                    label = { Text("Plans (${templates.count { it.hasPlan }})") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Repeat,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
                FilterChip(
                    selected = selectedFilter == TemplateFilter.TEMPLATES,
                    onClick = { selectedFilter = TemplateFilter.TEMPLATES },
                    label = { Text("Templates (${templates.count { !it.hasPlan }})") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.FlashOn,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
            }

            if (filteredTemplates.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Event,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) {
                                "No matching templates found"
                            } else {
                                stringResource(R.string.no_templates)
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Create templates for frequent transactions or recurring scheduled plans.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredTemplates, key = { it.id }) { template ->
                        TemplateCard(
                            template = template,
                            onRun = { onRunTemplate(template) },
                            onEditInstance = { onEditInstance(template) },
                            onEditTemplate = { onEditTemplate(template) },
                            onToggleDefaultAction = { onToggleDefaultAction(template) },
                            onDelete = { templateToDelete = template },
                            onViewCalendarPlan = {
                                template.planId?.let { onViewCalendarPlan(it) }
                            }
                        )
                    }
                }
            }
        }
    }

    templateToDelete?.let { template ->
        AlertDialog(
            onDismissRequest = { templateToDelete = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text(stringResource(R.string.dialog_title_warning_delete_template))
            },
            text = {
                Text("Are you sure you want to delete template \"${template.title}\"?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val toDelete = templateToDelete
                        templateToDelete = null
                        if (toDelete != null) {
                            onDeleteTemplate(toDelete)
                        }
                    }
                ) {
                    Text(
                        stringResource(R.string.menu_delete),
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { templateToDelete = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }
}

@Composable
fun TemplateCard(
    template: TemplateItem,
    onRun: () -> Unit,
    onEditInstance: () -> Unit,
    onEditTemplate: () -> Unit,
    onToggleDefaultAction: () -> Unit,
    onDelete: () -> Unit,
    onViewCalendarPlan: () -> Unit
) {
    val context = LocalContext.current
    val currencyFormatter = LocalCurrencyFormatter.current
    val currencyContext = context.injector.currencyContext()
    val currency = currencyContext[template.currency]

    var showMenu by remember { mutableStateOf(false) }

    val amountColor = when (template.type) {
        FLAG_EXPENSE -> LocalColors.current.expense
        FLAG_INCOME -> LocalColors.current.income
        FLAG_TRANSFER -> colorResource(R.color.colorTransfer)
        else -> MaterialTheme.colorScheme.onSurface
    }

    val displayAmount = currencyFormatter.convAmount(
        if (template.isTransfer) template.amount.absoluteValue else template.amount,
        currency
    )

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header: Account Indicator + Title + Amount + Overflow Menu
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    // Account badge
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(Color(template.color), CircleShape)
                        )
                        Text(
                            text = if (template.isTransfer && template.transferAccountLabel != null) {
                                "${template.accountLabel} → ${template.transferAccountLabel}"
                            } else {
                                template.accountLabel
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = template.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = displayAmount,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = amountColor
                    )

                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More Options",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_edit)) },
                                leadingIcon = {
                                    Icon(Icons.Default.Edit, contentDescription = null)
                                },
                                onClick = {
                                    showMenu = false
                                    onEditTemplate()
                                }
                            )

                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (template.defaultAction == Template.Action.SAVE) {
                                            "Default: Save directly (tap to switch to Edit)"
                                        } else {
                                            "Default: Open in editor (tap to switch to Save)"
                                        }
                                    )
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.FlashOn, contentDescription = null)
                                },
                                onClick = {
                                    showMenu = false
                                    onToggleDefaultAction()
                                }
                            )

                            if (template.hasPlan && template.planId != null) {
                                DropdownMenuItem(
                                    text = { Text("View in Calendar") },
                                    leadingIcon = {
                                        Icon(Icons.Default.CalendarMonth, contentDescription = null)
                                    },
                                    onClick = {
                                        showMenu = false
                                        onViewCalendarPlan()
                                    }
                                )
                            }

                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(R.string.menu_delete),
                                        color = MaterialTheme.colorScheme.error
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    onDelete()
                                }
                            )
                        }
                    }
                }
            }

            // Category & Payee metadata
            val details = buildList {
                if (template.category.isNotBlank()) add(template.category)
                if (!template.payee.isNullOrBlank()) add(template.payee)
                if (!template.comment.isNullOrBlank()) add(template.comment)
            }

            if (details.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = details.joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Recurrence plan info badge
            if (template.hasPlan && !template.planInfo.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.clickable { onViewCalendarPlan() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Repeat,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = template.planInfo,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action Buttons Row: Quick Run + Edit Instance
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = onRun,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = if (template.defaultAction == Template.Action.SAVE) {
                            Icons.Default.PlayArrow
                        } else {
                            Icons.Default.Edit
                        },
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (template.defaultAction == Template.Action.SAVE) {
                            "Execute"
                        } else {
                            "Edit & Run"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                OutlinedButton(
                    onClick = onEditInstance,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Customize",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}
