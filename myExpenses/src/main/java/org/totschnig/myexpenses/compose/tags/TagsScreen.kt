package org.totschnig.myexpenses.compose.tags

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedFilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.activity.Action
import org.totschnig.myexpenses.viewmodel.data.Tag

private val tagColors = listOf(
    null, // Default / no color
    Color(0xFFE53935), // Red
    Color(0xFFD81B60), // Pink
    Color(0xFF8E24AA), // Purple
    Color(0xFF3949AB), // Indigo
    Color(0xFF1E88E5), // Blue
    Color(0xFF00ACC1), // Cyan
    Color(0xFF00897B), // Teal
    Color(0xFF43A047), // Green
    Color(0xFF7CB342), // Light Green
    Color(0xFFFDD835), // Yellow
    Color(0xFFFB8C00), // Orange
    Color(0xFF6D4C41), // Brown
    Color(0xFF546E7A)  // Blue Grey
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TagsScreen(
    action: Action,
    tags: List<Tag>,
    selectedTagIds: Set<Long>,
    onToggleSelectTag: (Long) -> Unit,
    onAddTag: (String) -> Unit,
    onUpdateTag: (Tag, String, Int?) -> Unit,
    onDeleteTag: (Tag) -> Unit,
    onConfirm: () -> Unit,
    onClose: () -> Unit
) {
    val allowSelection = action != Action.MANAGE
    val allowModifications = action != Action.SELECT_FILTER

    var newTagInput by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    var tagToEdit by remember { mutableStateOf<Tag?>(null) }
    var tagToDelete by remember { mutableStateOf<Tag?>(null) }

    // Filter tags
    val filteredTags = remember(tags, searchQuery) {
        val query = searchQuery.trim()
        if (query.isEmpty()) tags
        else tags.filter { it.label.contains(query, ignoreCase = true) }
    }

    // Edit Tag Dialog
    tagToEdit?.let { tag ->
        var editLabel by remember { mutableStateOf(tag.label) }
        var chosenColorInt by remember { mutableStateOf(tag.color) }
        var hasError by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { tagToEdit = null },
            title = { Text(stringResource(R.string.menu_edit_tag)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    OutlinedTextField(
                        value = editLabel,
                        onValueChange = {
                            editLabel = it
                            if (it.isNotBlank()) hasError = false
                        },
                        label = { Text("Tag Name *") },
                        isError = hasError,
                        supportingText = if (hasError) {
                            { Text(stringResource(R.string.required), color = MaterialTheme.colorScheme.error) }
                        } else null,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = "Tag Color",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        tagColors.forEach { color ->
                            val isSelected = (color == null && chosenColorInt == null) ||
                                    (color != null && chosenColorInt == color.toArgb())
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(color ?: MaterialTheme.colorScheme.surfaceContainerHighest)
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        chosenColorInt = color?.toArgb()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (color == null) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "No color",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        modifier = Modifier.size(18.dp),
                                        tint = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val trimmed = editLabel.trim()
                    if (trimmed.isEmpty()) {
                        hasError = true
                    } else {
                        onUpdateTag(tag, trimmed, chosenColorInt)
                        tagToEdit = null
                    }
                }) {
                    Text(stringResource(R.string.menu_save), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        tagToDelete = tag
                        tagToEdit = null
                    }) {
                        Text(stringResource(R.string.menu_delete), color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = { tagToEdit = null }) {
                        Text(stringResource(android.R.string.cancel))
                    }
                }
            }
        )
    }

    // Delete Tag Dialog
    tagToDelete?.let { tag ->
        AlertDialog(
            onDismissRequest = { tagToDelete = null },
            title = { Text(stringResource(R.string.dialog_title_warning_delete_tag)) },
            text = {
                val warningText = if (tag.count > 0) {
                    pluralStringResource(R.plurals.warning_delete_tag, tag.count, tag.count, tag.label)
                } else {
                    "Delete tag \"${tag.label}\"?"
                }
                Text(warningText)
            },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteTag(tag)
                    tagToDelete = null
                }) {
                    Text(stringResource(R.string.menu_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { tagToDelete = null }) {
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
                            Action.MANAGE -> stringResource(R.string.tags)
                            Action.SELECT_FILTER -> stringResource(R.string.search_tag)
                            else -> stringResource(R.string.tags_create_or_select)
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
                    IconButton(onClick = {
                        isSearchActive = !isSearchActive
                        if (!isSearchActive) searchQuery = ""
                    }) {
                        Icon(
                            imageVector = if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = "Search"
                        )
                    }
                    if (allowSelection) {
                        IconButton(onClick = onConfirm) {
                            Icon(
                                imageVector = Icons.Default.Done,
                                contentDescription = stringResource(R.string.confirm),
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
            if (allowSelection) {
                ExtendedFloatingActionButton(
                    onClick = onConfirm,
                    icon = { Icon(Icons.Default.Done, null) },
                    text = { Text(stringResource(R.string.confirm)) },
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Search Bar
            AnimatedVisibility(visible = isSearchActive) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Filter tags...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Quick Add Tag input (when modifications allowed)
            if (allowModifications) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newTagInput,
                            onValueChange = { newTagInput = it },
                            placeholder = { Text("Type new tag name...") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                val trimmed = newTagInput.trim()
                                if (trimmed.isNotEmpty()) {
                                    onAddTag(trimmed)
                                    newTagInput = ""
                                }
                            }),
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                val trimmed = newTagInput.trim()
                                if (trimmed.isNotEmpty()) {
                                    onAddTag(trimmed)
                                    newTagInput = ""
                                }
                            },
                            enabled = newTagInput.isNotBlank()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add Tag",
                                tint = if (newTagInput.isNotBlank()) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                        }
                    }
                }
            }

            // Tags Section Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${filteredTags.size} Tags",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (allowSelection && selectedTagIds.isNotEmpty()) {
                    Text(
                        text = "${selectedTagIds.size} selected",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // FlowRow of Tags
            if (filteredTags.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Label,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Text(
                            text = if (searchQuery.isNotEmpty()) "No tags matching \"$searchQuery\"" else "No tags yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (allowModifications) {
                            Text(
                                text = "Type above to create your first tag",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    filteredTags.forEach { tag ->
                        val isSelected = selectedTagIds.contains(tag.id)

                        ElevatedFilterChip(
                            selected = isSelected,
                            onClick = {
                                if (allowSelection) {
                                    onToggleSelectTag(tag.id)
                                } else {
                                    tagToEdit = tag
                                }
                            },
                            label = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = tag.label,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                    if (tag.count > 0) {
                                        Text(
                                            text = "(${tag.count})",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            },
                            leadingIcon = {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        modifier = Modifier.size(16.dp)
                                    )
                                } else if (tag.color != null) {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clip(CircleShape)
                                            .background(Color(tag.color))
                                    )
                                }
                            },
                            trailingIcon = if (allowModifications && !allowSelection) {
                                {
                                    IconButton(
                                        onClick = { tagToEdit = tag },
                                        modifier = Modifier.size(18.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Edit",
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            } else null,
                            colors = FilterChipDefaults.elevatedFilterChipColors(
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
