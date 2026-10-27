package org.totschnig.myexpenses.compose.methods

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.model2.PAYMENT_METHOD_EXPENSE
import org.totschnig.myexpenses.model2.PAYMENT_METHOD_INCOME
import org.totschnig.myexpenses.model2.PAYMENT_METHOD_NEUTRAL
import org.totschnig.myexpenses.viewmodel.DisplayPaymentMethod

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MethodsScreen(
    methods: List<DisplayPaymentMethod>,
    onNewMethod: () -> Unit,
    onEditMethod: (Long) -> Unit,
    onDeleteMethod: (DisplayPaymentMethod) -> Unit,
    onClose: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var selectedTypeFilter by remember { mutableIntStateOf(-99) } // -99: All
    var methodToDelete by remember { mutableStateOf<DisplayPaymentMethod?>(null) }

    // Filtered methods
    val filteredMethods = remember(methods, searchQuery, selectedTypeFilter) {
        methods.filter { method ->
            val matchesQuery = searchQuery.isBlank() ||
                    method.label.contains(searchQuery, ignoreCase = true)
            val matchesType = selectedTypeFilter == -99 || method.type == selectedTypeFilter
            matchesQuery && matchesType
        }
    }

    // Delete dialog
    methodToDelete?.let { method ->
        val hasMappedData = method.mappedTransactions > 0 || method.mappedTemplates > 0
        AlertDialog(
            onDismissRequest = { methodToDelete = null },
            title = { Text(if (hasMappedData) "Cannot Delete Method" else "Delete Method?") },
            text = {
                if (hasMappedData) {
                    val reasons = buildList {
                        if (method.mappedTransactions > 0) add("${method.mappedTransactions} transactions")
                        if (method.mappedTemplates > 0) add("${method.mappedTemplates} templates")
                    }.joinToString(" and ")
                    Text("Cannot delete \"${method.label}\" because it is currently linked to $reasons. Please reassign those items before deleting.")
                } else {
                    Text("Are you sure you want to delete payment method \"${method.label}\"?")
                }
            },
            confirmButton = {
                if (!hasMappedData) {
                    TextButton(onClick = {
                        onDeleteMethod(method)
                        methodToDelete = null
                    }) {
                        Text(stringResource(R.string.menu_delete), color = MaterialTheme.colorScheme.error)
                    }
                } else {
                    TextButton(onClick = { methodToDelete = null }) {
                        Text(stringResource(android.R.string.ok))
                    }
                }
            },
            dismissButton = {
                if (!hasMappedData) {
                    TextButton(onClick = { methodToDelete = null }) {
                        Text(stringResource(android.R.string.cancel))
                    }
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.pref_manage_methods_title),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.menu_back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        isSearchActive = !isSearchActive
                        if (!isSearchActive) searchQuery = ""
                    }) {
                        Icon(
                            imageVector = if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = "Search"
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
                onClick = onNewMethod,
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text(stringResource(R.string.menu_create_method)) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Search Bar
            AnimatedVisibility(visible = isSearchActive) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search payment methods...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }

            // Type Filter Chips Row
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item {
                    FilterChip(
                        selected = selectedTypeFilter == -99,
                        onClick = { selectedTypeFilter = -99 },
                        label = { Text("All (${methods.size})") }
                    )
                }
                item {
                    FilterChip(
                        selected = selectedTypeFilter == PAYMENT_METHOD_EXPENSE,
                        onClick = {
                            selectedTypeFilter =
                                if (selectedTypeFilter == PAYMENT_METHOD_EXPENSE) -99 else PAYMENT_METHOD_EXPENSE
                        },
                        label = { Text("Expense") }
                    )
                }
                item {
                    FilterChip(
                        selected = selectedTypeFilter == PAYMENT_METHOD_NEUTRAL,
                        onClick = {
                            selectedTypeFilter =
                                if (selectedTypeFilter == PAYMENT_METHOD_NEUTRAL) -99 else PAYMENT_METHOD_NEUTRAL
                        },
                        label = { Text("Neutral") }
                    )
                }
                item {
                    FilterChip(
                        selected = selectedTypeFilter == PAYMENT_METHOD_INCOME,
                        onClick = {
                            selectedTypeFilter =
                                if (selectedTypeFilter == PAYMENT_METHOD_INCOME) -99 else PAYMENT_METHOD_INCOME
                        },
                        label = { Text("Income") }
                    )
                }
            }

            // List of Payment Methods
            if (filteredMethods.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Payment,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Text(
                            text = if (searchQuery.isNotEmpty() || selectedTypeFilter != -99) "No matching methods" else "No payment methods",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Tap '+ Create Method' to add custom payment options",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredMethods, key = { it.id }) { method ->
                        PaymentMethodCard(
                            method = method,
                            onEdit = { onEditMethod(method.id) },
                            onDelete = { methodToDelete = method }
                        )
                    }

                    item {
                        Spacer(Modifier.height(80.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PaymentMethodCard(
    method: DisplayPaymentMethod,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            val iconVector = resolveMethodIcon(method.preDefinedMethodName, method.icon)
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = iconVector,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(Modifier.width(14.dp))

            // Method Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = method.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Type Badge
                    when (method.type) {
                        PAYMENT_METHOD_EXPENSE -> {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.errorContainer
                            ) {
                                Text(
                                    text = "Expense",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        PAYMENT_METHOD_INCOME -> {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "Income",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        else -> {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest
                            ) {
                                Text(
                                    text = "Neutral",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    // Cheque / Reference number badge
                    if (method.isNumbered) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer
                        ) {
                            Text(
                                text = "Ref / Cheque #",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    // Usage Stats
                    val totalUsage = method.mappedTransactions + method.mappedTemplates
                    Text(
                        text = if (totalUsage > 0) "$totalUsage used" else "Unused",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Options menu
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Options")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Edit Method") },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onEdit()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete Method", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

private fun resolveMethodIcon(preDefined: String?, iconName: String?): ImageVector {
    val key = (preDefined ?: iconName ?: "").lowercase()
    return when {
        key.contains("cash") -> Icons.Default.Payments
        key.contains("upi") || key.contains("mobile") -> Icons.Default.PhoneAndroid
        key.contains("card") || key.contains("credit") || key.contains("debit") -> Icons.Default.CreditCard
        key.contains("bank") || key.contains("netbanking") -> Icons.Default.AccountBalance
        key.contains("wallet") -> Icons.Default.AccountBalanceWallet
        key.contains("cheque") || key.contains("check") -> Icons.Default.ReceiptLong
        key.contains("debit") || key.contains("auto") -> Icons.Default.Sync
        else -> Icons.Default.Payment
    }
}
