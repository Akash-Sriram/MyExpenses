package org.totschnig.myexpenses.compose.categories

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.activity.Action
import org.totschnig.myexpenses.compose.ColorCircle
import org.totschnig.myexpenses.compose.Icon
import org.totschnig.myexpenses.compose.LocalColors
import org.totschnig.myexpenses.db2.FLAG_EXPENSE
import org.totschnig.myexpenses.db2.FLAG_INCOME
import org.totschnig.myexpenses.db2.FLAG_NEUTRAL
import org.totschnig.myexpenses.db2.FLAG_TRANSFER
import org.totschnig.myexpenses.model.sort.Sort
import org.totschnig.myexpenses.viewmodel.LoadingState
import org.totschnig.myexpenses.viewmodel.data.Category

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    action: Action,
    categoryTreeState: LoadingState,
    typeFilter: Byte?,
    onTypeFilterChange: (Byte?) -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    sortOrder: Sort,
    onSortOrderChange: (Sort) -> Unit,
    selectedCategoryIds: Set<Long>,
    onToggleSelectCategory: (Long) -> Unit,
    onClearSelection: () -> Unit,
    onCategoryClick: (Category) -> Unit,
    onAddMainCategory: () -> Unit,
    onAddSubcategory: (Category) -> Unit,
    onEditCategory: (Category) -> Unit,
    onDeleteCategory: (Category) -> Unit,
    onMoveCategory: (Category) -> Unit,
    onDeleteSelected: (List<Category>) -> Unit,
    onMergeSelected: (List<Category>) -> Unit,
    onConfirmFilterSelection: () -> Unit,
    onSetupDefaults: () -> Unit,
    onExportQif: (encoding: String) -> Unit,
    onSync: () -> Unit,
    onClose: () -> Unit
) {
    var isSearchActive by remember { mutableStateOf(searchQuery.isNotBlank()) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }

    val isSelectionMode = selectedCategoryIds.isNotEmpty()

    val screenTitle = when (action) {
        Action.MANAGE -> stringResource(R.string.pref_manage_categories_title)
        Action.SELECT_MAPPING -> stringResource(R.string.select_category)
        Action.SELECT_FILTER -> stringResource(R.string.search_category)
    }

    Scaffold(
        topBar = {
            if (isSelectionMode) {
                // Contextual Action TopAppBar
                TopAppBar(
                    title = {
                        Text(
                            text = "${selectedCategoryIds.size} selected",
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onClearSelection) {
                            Icon(Icons.Default.Close, contentDescription = "Clear selection")
                        }
                    },
                    actions = {
                        if (action == Action.SELECT_FILTER) {
                            IconButton(onClick = onConfirmFilterSelection) {
                                Icon(Icons.Default.Check, contentDescription = "Confirm selection")
                            }
                        } else if (action == Action.MANAGE) {
                            val data = (categoryTreeState as? LoadingState.Data)?.data
                            val selectedList = data?.flatten()?.filter { selectedCategoryIds.contains(it.id) } ?: emptyList()

                            // Merge option visible when >= 2 selected with identical type flags
                            if (selectedList.size >= 2 && selectedList.all { it.typeFlags == selectedList.first().typeFlags }) {
                                IconButton(onClick = { onMergeSelected(selectedList) }) {
                                    Icon(Icons.AutoMirrored.Filled.MergeType, contentDescription = stringResource(R.string.menu_merge))
                                }
                            }
                            // Delete option
                            IconButton(onClick = { onDeleteSelected(selectedList) }) {
                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.menu_delete))
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        titleContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            } else {
                // Standard Material 3 TopAppBar
                TopAppBar(
                    title = {
                        Text(
                            text = screenTitle,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        // Search action
                        IconButton(onClick = {
                            isSearchActive = !isSearchActive
                            if (!isSearchActive) onSearchQueryChange("")
                        }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }

                        // Sort action
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort")
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Alphabetical" + if (sortOrder == Sort.LABEL) " ✓" else "") },
                                    onClick = {
                                        onSortOrderChange(Sort.LABEL)
                                        showSortMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Most Used" + if (sortOrder == Sort.USAGES) " ✓" else "") },
                                    onClick = {
                                        onSortOrderChange(Sort.USAGES)
                                        showSortMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Last Used" + if (sortOrder == Sort.LAST_USED) " ✓" else "") },
                                    onClick = {
                                        onSortOrderChange(Sort.LAST_USED)
                                        showSortMenu = false
                                    }
                                )
                            }
                        }

                        // Overflow menu (Setup Defaults, Export, Sync)
                        if (action == Action.MANAGE) {
                            Box {
                                IconButton(onClick = { showOverflowMenu = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "More")
                                }
                                DropdownMenu(
                                    expanded = showOverflowMenu,
                                    onDismissRequest = { showOverflowMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.menu_categories_setup_default)) },
                                        onClick = {
                                            showOverflowMenu = false
                                            onSetupDefaults()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Export (QIF UTF-8)") },
                                        onClick = {
                                            showOverflowMenu = false
                                            onExportQif("UTF-8")
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Export (QIF ISO-8859-1)") },
                                        onClick = {
                                            showOverflowMenu = false
                                            onExportQif("ISO-8859-1")
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.synchronization)) },
                                        onClick = {
                                            showOverflowMenu = false
                                            onSync()
                                        }
                                    )
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        },
        floatingActionButton = {
            if (action == Action.MANAGE || action == Action.SELECT_MAPPING) {
                ExtendedFloatingActionButton(
                    onClick = onAddMainCategory,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.menu_create_main_cat)) },
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
            // Search Input Field
            AnimatedVisibility(visible = isSearchActive) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = { Text("Search categories...") },
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
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }

            // Type Filter Chips Row
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item {
                    ElevatedFilterChip(
                        selected = typeFilter == null,
                        onClick = { onTypeFilterChange(null) },
                        label = { Text("All") }
                    )
                }
                item {
                    val expenseColor = LocalColors.current.expense
                    ElevatedFilterChip(
                        selected = typeFilter == FLAG_EXPENSE,
                        onClick = { onTypeFilterChange(if (typeFilter == FLAG_EXPENSE) null else FLAG_EXPENSE) },
                        label = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(expenseColor)
                                )
                                Text("Expense")
                            }
                        }
                    )
                }
                item {
                    val incomeColor = LocalColors.current.income
                    ElevatedFilterChip(
                        selected = typeFilter == FLAG_INCOME,
                        onClick = { onTypeFilterChange(if (typeFilter == FLAG_INCOME) null else FLAG_INCOME) },
                        label = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(incomeColor)
                                )
                                Text("Income")
                            }
                        }
                    )
                }
                item {
                    val transferColor = LocalColors.current.transfer
                    ElevatedFilterChip(
                        selected = typeFilter == FLAG_TRANSFER,
                        onClick = { onTypeFilterChange(if (typeFilter == FLAG_TRANSFER) null else FLAG_TRANSFER) },
                        label = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(transferColor)
                                )
                                Text("Transfer")
                            }
                        }
                    )
                }
            }

            // Main Content Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when (categoryTreeState) {
                    is LoadingState.Loading -> {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(48.dp)
                                .align(Alignment.Center)
                        )
                    }

                    is LoadingState.Empty -> {
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Text(
                                text = stringResource(R.string.no_categories),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (!categoryTreeState.hasUnfiltered) {
                                TextButton(onClick = onSetupDefaults) {
                                    Text(stringResource(R.string.menu_categories_setup_default))
                                }
                            }
                        }
                    }

                    is LoadingState.Data -> {
                        val rootCategory = categoryTreeState.data
                        val expandedIds = remember { mutableStateListOf<Long>() }
                        val collapsedSearchIds = remember(searchQuery) { mutableStateListOf<Long>() }
                        val isSearchQueryActive = searchQuery.isNotBlank()

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(
                                items = rootCategory.children,
                                key = { it.id }
                            ) { mainCat ->
                                ModernCategoryNode(
                                    category = mainCat,
                                    level = 0,
                                    action = action,
                                    isSelectionMode = isSelectionMode,
                                    isSearchActive = isSearchQueryActive,
                                    selectedCategoryIds = selectedCategoryIds,
                                    expandedIds = expandedIds,
                                    collapsedSearchIds = collapsedSearchIds,
                                    onToggleExpand = { id ->
                                        if (isSearchQueryActive) {
                                            if (collapsedSearchIds.contains(id)) collapsedSearchIds.remove(id)
                                            else collapsedSearchIds.add(id)
                                        } else {
                                            if (expandedIds.contains(id)) expandedIds.remove(id)
                                            else expandedIds.add(id)
                                        }
                                    },
                                    onCategoryClick = onCategoryClick,
                                    onToggleSelect = onToggleSelectCategory,
                                    onAddSubcategory = onAddSubcategory,
                                    onEditCategory = onEditCategory,
                                    onMoveCategory = onMoveCategory,
                                    onDeleteCategory = onDeleteCategory
                                )
                            }
                            // Bottom padding for FAB
                            item {
                                Spacer(modifier = Modifier.height(80.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ModernCategoryNode(
    category: Category,
    level: Int,
    action: Action,
    isSelectionMode: Boolean,
    isSearchActive: Boolean,
    selectedCategoryIds: Set<Long>,
    expandedIds: List<Long>,
    collapsedSearchIds: List<Long>,
    onToggleExpand: (Long) -> Unit,
    onCategoryClick: (Category) -> Unit,
    onToggleSelect: (Long) -> Unit,
    onAddSubcategory: (Category) -> Unit,
    onEditCategory: (Category) -> Unit,
    onMoveCategory: (Category) -> Unit,
    onDeleteCategory: (Category) -> Unit
) {
    val isExpanded = if (isSearchActive) {
        !collapsedSearchIds.contains(category.id)
    } else {
        expandedIds.contains(category.id)
    }
    val isSelected = selectedCategoryIds.contains(category.id)
    val hasChildren = category.children.isNotEmpty()
    var showMenu by remember { mutableStateOf(false) }

    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        label = "chevronRotation"
    )

    val typeColor = when (category.typeFlags) {
        FLAG_EXPENSE -> LocalColors.current.expense
        FLAG_INCOME -> LocalColors.current.income
        FLAG_TRANSFER -> LocalColors.current.transfer
        else -> MaterialTheme.colorScheme.outline
    }

    val indent = (level * 16).dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = indent)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .combinedClickable(
                    onClick = {
                        if (isSelectionMode) {
                            onToggleSelect(category.id)
                        } else if (action == Action.SELECT_MAPPING) {
                            if (hasChildren) {
                                onToggleExpand(category.id)
                            } else {
                                onCategoryClick(category)
                            }
                        } else if (action == Action.SELECT_FILTER) {
                            onToggleSelect(category.id)
                        } else {
                            if (hasChildren) {
                                onToggleExpand(category.id)
                            } else {
                                onCategoryClick(category)
                            }
                        }
                    },
                    onLongClick = {
                        if (isSelectionMode) {
                            onToggleSelect(category.id)
                        } else {
                            showMenu = true
                        }
                    }
                ),
            colors = CardDefaults.cardColors(
                containerColor = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                } else if (level == 0) {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                }
            ),
            shape = RoundedCornerShape(12.dp),
            border = if (isSelected) {
                borderWidth(1.5.dp, MaterialTheme.colorScheme.primary)
            } else null
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Selection Checkbox in Selection Mode or Filter Mode
                if (isSelectionMode || action == Action.SELECT_FILTER) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onToggleSelect(category.id) },
                        modifier = Modifier.padding(end = 6.dp)
                    )
                }

                // Category Icon or Color Dot
                if (category.icon != null) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(typeColor.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            icon = category.icon,
                            color = category.color?.let { Color(it) } ?: typeColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                } else if (category.color != null) {
                    ColorCircle(
                        modifier = Modifier
                            .size(22.dp)
                            .padding(2.dp),
                        color = category.color
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(typeColor)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Label and Type Badge
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = category.label,
                        style = MaterialTheme.typography.bodyLarge,
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

                // In SELECT_MAPPING mode, if category has children, allow direct selection via checkmark button
                if (action == Action.SELECT_MAPPING && hasChildren) {
                    IconButton(
                        onClick = { onCategoryClick(category) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = stringResource(R.string.select_category),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Expand/Collapse Chevron if it has children
                if (hasChildren) {
                    IconButton(
                        onClick = { onToggleExpand(category.id) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ExpandMore,
                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                            modifier = Modifier.rotate(chevronRotation),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 3-dots Context Menu
                if (!isSelectionMode) {
                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Options",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.subcategory)) },
                                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    onAddSubcategory(category)
                                }
                            )
                            if (action == Action.SELECT_MAPPING) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.select_category)) },
                                    leadingIcon = { Icon(Icons.Default.Check, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        onCategoryClick(category)
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_edit)) },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    onEditCategory(category)
                                }
                            )
                            if (action == Action.MANAGE) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.menu_move)) },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        onMoveCategory(category)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.menu_delete), color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        showMenu = false
                                        onDeleteCategory(category)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Render Children if expanded
        if (hasChildren && isExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                category.children.forEach { child ->
                    ModernCategoryNode(
                        category = child,
                        level = level + 1,
                        action = action,
                        isSelectionMode = isSelectionMode,
                        isSearchActive = isSearchActive,
                        selectedCategoryIds = selectedCategoryIds,
                        expandedIds = expandedIds,
                        collapsedSearchIds = collapsedSearchIds,
                        onToggleExpand = onToggleExpand,
                        onCategoryClick = onCategoryClick,
                        onToggleSelect = onToggleSelect,
                        onAddSubcategory = onAddSubcategory,
                        onEditCategory = onEditCategory,
                        onMoveCategory = onMoveCategory,
                        onDeleteCategory = onDeleteCategory
                    )
                }
            }
        }
    }
}

private fun borderWidth(width: Dp, color: Color) = androidx.compose.foundation.BorderStroke(width, color)
