package org.totschnig.myexpenses.compose.parties

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.activity.Action
import org.totschnig.myexpenses.model.Money
import org.totschnig.myexpenses.viewmodel.data.DisplayDebt
import org.totschnig.myexpenses.viewmodel.data.Party

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartiesScreen(
    action: Action,
    parties: List<Party>,
    unusedCount: Int,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    debtsLookup: (Long) -> List<DisplayDebt>?,
    onPartySelected: (Party) -> Unit,
    onSaveParty: (id: Long?, name: String, shortName: String?, iban: String?) -> Unit,
    onDeleteParty: (Party) -> Unit,
    onNewDebt: (Party) -> Unit,
    onCleanupUnused: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var isSearchActive by remember { mutableStateOf(searchQuery.isNotEmpty()) }
    var partyToEdit by remember { mutableStateOf<Party?>(null) }
    var isCreatingNewParty by remember { mutableStateOf(false) }
    var partyToDelete by remember { mutableStateOf<Party?>(null) }
    var showCleanupDialog by remember { mutableStateOf(false) }

    // Add or Edit Dialog
    if (isCreatingNewParty || partyToEdit != null) {
        val editing = partyToEdit
        var nameInput by remember { mutableStateOf(editing?.name ?: "") }
        var shortNameInput by remember { mutableStateOf(editing?.shortName ?: "") }
        var ibanInput by remember { mutableStateOf(editing?.iban ?: "") }
        var hasError by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = {
                isCreatingNewParty = false
                partyToEdit = null
            },
            title = {
                Text(
                    if (editing == null) stringResource(R.string.menu_create_party)
                    else stringResource(R.string.menu_edit_party)
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = {
                            nameInput = it
                            if (it.isNotBlank()) hasError = false
                        },
                        label = { Text("Full Name *") },
                        placeholder = { Text("e.g. Amazon, Landlord, John") },
                        isError = hasError,
                        supportingText = if (hasError) {
                            { Text(stringResource(R.string.required), color = MaterialTheme.colorScheme.error) }
                        } else null,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = shortNameInput,
                        onValueChange = { shortNameInput = it },
                        label = { Text("Nickname / Alias (Optional)") },
                        placeholder = { Text("Short display name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = ibanInput,
                        onValueChange = { ibanInput = it },
                        label = { Text("UPI ID / Phone (Optional)") },
                        placeholder = { Text("name@bank or 9876543210") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val trimmed = nameInput.trim()
                    if (trimmed.isEmpty()) {
                        hasError = true
                    } else {
                        onSaveParty(
                            editing?.id,
                            trimmed,
                            shortNameInput.trim().ifEmpty { null },
                            ibanInput.trim().ifEmpty { null }
                        )
                        isCreatingNewParty = false
                        partyToEdit = null
                    }
                }) {
                    Text(if (editing == null) stringResource(R.string.menu_add) else stringResource(R.string.menu_save))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    isCreatingNewParty = false
                    partyToEdit = null
                }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    // Delete confirmation dialog
    partyToDelete?.let { party ->
        AlertDialog(
            onDismissRequest = { partyToDelete = null },
            title = { Text("Delete Payee?") },
            text = {
                Text("Delete \"${party.name}\"? Associated transactions will remain, but this payee reference will be removed.")
            },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteParty(party)
                    partyToDelete = null
                }) {
                    Text(stringResource(R.string.menu_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { partyToDelete = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    // Cleanup Unused Dialog
    if (showCleanupDialog) {
        AlertDialog(
            onDismissRequest = { showCleanupDialog = false },
            title = { Text("Cleanup Unused Payees?") },
            text = {
                Text("This will remove $unusedCount payees that have no transactions, templates, or debts.")
            },
            confirmButton = {
                TextButton(onClick = {
                    onCleanupUnused()
                    showCleanupDialog = false
                }) {
                    Text("Cleanup", color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCleanupDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (action) {
                            Action.MANAGE -> stringResource(R.string.pref_manage_parties_title)
                            Action.SELECT_FILTER -> stringResource(R.string.search_payee)
                            Action.SELECT_MAPPING -> stringResource(R.string.select_payee)
                        },
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
                    IconButton(onClick = { isSearchActive = !isSearchActive }) {
                        Icon(
                            imageVector = if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = "Search"
                        )
                    }
                    if (action == Action.MANAGE && unusedCount > 0) {
                        IconButton(onClick = { showCleanupDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.CleaningServices,
                                contentDescription = "Cleanup Unused",
                                tint = MaterialTheme.colorScheme.primary
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
            if (action == Action.MANAGE) {
                ExtendedFloatingActionButton(
                    onClick = { isCreatingNewParty = true },
                    icon = { Icon(Icons.Default.Add, null) },
                    text = { Text("New Payee") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Inline Search Bar
            AnimatedVisibility(visible = isSearchActive) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = { Text("Search by name, nickname, or UPI...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            // Summary Header Pill Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${parties.size} Payees & Merchants",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (unusedCount > 0 && action == Action.MANAGE) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.clickable { showCleanupDialog = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Default.CleaningServices,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "$unusedCount unused",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Payees List
            if (parties.isEmpty()) {
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
                            imageVector = Icons.Default.People,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Text(
                            text = if (searchQuery.isNotEmpty()) "No matching payees" else "No payees found",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (searchQuery.isNotEmpty()) "Try a different search term" else "Tap '+' to add your first payee or merchant",
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
                    items(parties, key = { it.id }) { party ->
                        val debts = debtsLookup(party.id) ?: emptyList()
                        val netDebt = debts.filter { !it.isSealed }.sumOf { it.currentBalance }

                        PayeeCard(
                            party = party,
                            netDebt = netDebt,
                            action = action,
                            onCardClick = { onPartySelected(party) },
                            onEdit = { partyToEdit = party },
                            onDelete = { partyToDelete = party },
                            onNewDebt = { onNewDebt(party) },
                            onCopyUpi = { upi ->
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("UPI ID", upi))
                                Toast.makeText(context, "Copied UPI ID: $upi", Toast.LENGTH_SHORT).show()
                            }
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
private fun PayeeCard(
    party: Party,
    netDebt: Long,
    action: Action,
    onCardClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onNewDebt: () -> Unit,
    onCopyUpi: (String) -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onCardClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar with initial letter
            val initial = party.name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initial,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Spacer(Modifier.width(12.dp))

            // Name and Details
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = party.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (party.isUnused) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest
                        ) {
                            Text(
                                text = "Unused",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                if (!party.shortName.isNullOrBlank()) {
                    Text(
                        text = "Alias: ${party.shortName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (!party.iban.isNullOrBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { onCopyUpi(party.iban) }
                    ) {
                        Text(
                            text = "UPI: ${party.iban}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy",
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Debt pill
                if (netDebt != 0L) {
                    Spacer(Modifier.height(4.dp))
                    val isOwesMe = netDebt > 0
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isOwesMe) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.errorContainer
                    ) {
                        Text(
                            text = if (isOwesMe) "OWES YOU" else "YOU OWE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isOwesMe) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Action Menu
            if (action == Action.MANAGE) {
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Options")
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Edit Payee") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onEdit()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Record Debt") },
                            leadingIcon = { Icon(Icons.Default.Handshake, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onNewDebt()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete Payee", color = MaterialTheme.colorScheme.error) },
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
}
