package org.totschnig.myexpenses.activity

import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.PluralsRes
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.compose.AppTheme
import org.totschnig.myexpenses.compose.CategoryEdit
import org.totschnig.myexpenses.compose.CategoryMerge
import org.totschnig.myexpenses.compose.categories.CategoriesScreen
import org.totschnig.myexpenses.db2.FLAG_NEUTRAL
import org.totschnig.myexpenses.dialog.MessageDialogFragment
import org.totschnig.myexpenses.dialog.SelectCategoryMoveTargetDialogFragment
import org.totschnig.myexpenses.dialog.SetupCategoriesConfirmDialogFragment
import org.totschnig.myexpenses.injector
import org.totschnig.myexpenses.model.ContribFeature
import org.totschnig.myexpenses.model.sort.Sort
import org.totschnig.myexpenses.preference.PrefKey
import org.totschnig.myexpenses.provider.KEY_ACCOUNTID
import org.totschnig.myexpenses.provider.KEY_ICON
import org.totschnig.myexpenses.provider.KEY_LABEL
import org.totschnig.myexpenses.provider.KEY_ROWID
import org.totschnig.myexpenses.provider.KEY_TYPE
import org.totschnig.myexpenses.provider.filter.NULL_ITEM_ID
import org.totschnig.myexpenses.provider.filter.preSelected
import org.totschnig.myexpenses.util.safeMessage
import org.totschnig.myexpenses.viewmodel.LoadingState
import org.totschnig.myexpenses.viewmodel.ManageCategoriesViewModel
import org.totschnig.myexpenses.viewmodel.ManageCategoriesViewModel.DeleteResult.OperationComplete
import org.totschnig.myexpenses.viewmodel.ManageCategoriesViewModel.DeleteResult.OperationPending
import org.totschnig.myexpenses.viewmodel.data.Category
import java.io.Serializable

class ManageCategories : ProtectedFragmentActivity(), ContribIFace {

    private val viewModel: ManageCategoriesViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val action = intent.asAction
        super.onCreate(savedInstanceState)
        injector.inject(viewModel)

        val initialSort = try {
            Sort.valueOf(prefHandler.getString(PrefKey.SORT_ORDER_CATEGORIES, Sort.LABEL.name) ?: Sort.LABEL.name)
        } catch (e: Exception) {
            Sort.LABEL
        }
        viewModel.setSortOrder(initialSort)

        observeDeleteResult()
        observeMoveResult()
        observeImportResult()
        observeExportResult()
        observeSyncResult()
        observeMergeResult()

        val preSelectedList = this.preSelected ?: emptyList()

        supportFragmentManager.setFragmentResultListener(
            SetupCategoriesConfirmDialogFragment.IMPORT_OK,
            this
        ) { _, _ ->
            showSnackBarIndefinite(R.string.menu_categories_setup_default)
            viewModel.importCats()
        }

        setContent {
            AppTheme {
                val categoryTreeState by viewModel.categoryTree.collectAsState(initial = LoadingState.Loading)
                var currentSort by remember { mutableStateOf(initialSort) }
                var searchQuery by remember { mutableStateOf(viewModel.filter ?: "") }
                var typeFilter by remember { mutableStateOf(viewModel.typeFilter) }
                val selectedIds = remember { mutableStateListOf<Long>().apply { addAll(preSelectedList) } }

                // Dialog rendering (Edit / Merge)
                when (val dialogState = viewModel.dialogState) {
                    is ManageCategoriesViewModel.Edit -> CategoryEdit(
                        dialogState = dialogState,
                        onDismissRequest = {
                            viewModel.dialogState = ManageCategoriesViewModel.NoShow
                        },
                        onSave = viewModel::saveCategory
                    )

                    is ManageCategoriesViewModel.Merge -> CategoryMerge(
                        dialogState = dialogState,
                        onDismissRequest = {
                            viewModel.dialogState = ManageCategoriesViewModel.NoShow
                        },
                        onMerge = viewModel::mergeCategories
                    )

                    else -> {}
                }

                CategoriesScreen(
                    action = action,
                    categoryTreeState = categoryTreeState,
                    typeFilter = typeFilter,
                    onTypeFilterChange = { newFilter ->
                        typeFilter = newFilter
                        viewModel.typeFilter = newFilter
                    },
                    searchQuery = searchQuery,
                    onSearchQueryChange = { query ->
                        searchQuery = query
                        viewModel.filter = query
                    },
                    sortOrder = currentSort,
                    onSortOrderChange = { newSort ->
                        currentSort = newSort
                        prefHandler.putString(PrefKey.SORT_ORDER_CATEGORIES, newSort.name)
                        viewModel.setSortOrder(newSort)
                    },
                    selectedCategoryIds = selectedIds.toSet(),
                    onToggleSelectCategory = { id ->
                        if (selectedIds.contains(id)) {
                            selectedIds.remove(id)
                        } else {
                            selectedIds.add(id)
                        }
                    },
                    onClearSelection = {
                        selectedIds.clear()
                    },
                    onCategoryClick = { category ->
                        if (action == Action.SELECT_MAPPING) {
                            doSingleSelection(category)
                        } else if (action == Action.SELECT_FILTER) {
                            if (selectedIds.contains(category.id)) {
                                selectedIds.remove(category.id)
                            } else {
                                selectedIds.add(category.id)
                            }
                        }
                    },
                    onAddMainCategory = { createCat(null) },
                    onAddSubcategory = { createCat(it) },
                    onEditCategory = { editCat(it) },
                    onDeleteCategory = { cat ->
                        if (checkDefaultTransferCategory(listOf(cat))) {
                            viewModel.deleteCategories(listOf(cat))
                        }
                    },
                    onMoveCategory = { showMoveTargetDialog(it) },
                    onDeleteSelected = { list ->
                        if (checkDefaultTransferCategory(list)) {
                            viewModel.deleteCategories(list)
                        }
                    },
                    onMergeSelected = { list ->
                        viewModel.dialogState = ManageCategoriesViewModel.Merge(list)
                    },
                    onConfirmFilterSelection = {
                        doMultiSelection(selectedIds.toSet())
                    },
                    onSetupDefaults = {
                        checkImportableCategories()
                    },
                    onExportQif = { encoding ->
                        exportCats(encoding)
                    },
                    onSync = {
                        viewModel.syncCatsExport("")
                    },
                    onClose = {
                        finish()
                    }
                )
            }
        }
    }

    private fun checkDefaultTransferCategory(categories: Collection<Category>): Boolean {
        val defaultTransferCategory = prefHandler.defaultTransferCategory
        return categories.firstNotNullOfOrNull { cat ->
            cat.flatten().find { it.id == defaultTransferCategory }
        }?.also {
            showSnackBar(
                getString(
                    R.string.warning_delete_default_transfer_category,
                    it.path
                )
            )
        } == null
    }

    private fun doSingleSelection(category: Category) {
        val result = Intent().apply {
            putExtra(KEY_ACCOUNTID, intent.getLongExtra(KEY_ACCOUNTID, 0))
            putExtra(KEY_ROWID, category.id)
            putExtra(KEY_LABEL, category.path)
            putExtra(KEY_ICON, category.icon)
            putExtra(KEY_TYPE, category.typeFlags)
        }
        setResult(RESULT_OK, result)
        finish()
    }

    private fun doMultiSelection(selected: Set<Long>) {
        if (selected.size == 1 || !selected.contains(NULL_ITEM_ID)) {
            (viewModel.categoryTree.value as? LoadingState.Data)?.also { data ->
                val label = data.data.flatten()
                    .filter { selected.contains(it.id) }
                    .joinToString(separator = ",") { it.label }
                setResult(RESULT_FIRST_USER, Intent().apply {
                    putExtra(KEY_ROWID, selected.toLongArray())
                    putExtra(KEY_LABEL, label)
                })
                finish()
            }
        } else {
            Toast.makeText(this, R.string.unmapped_filter_only_single, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showMoveTargetDialog(category: Category) {
        SelectCategoryMoveTargetDialogFragment.newInstance(category)
            .show(supportFragmentManager, "SELECT_TARGET")
    }

    private fun MutableList<String>.mapToMessage(quantity: Int, @PluralsRes resId: Int) {
        if (quantity > 0) add(
            resources.getQuantityString(
                resId,
                quantity,
                quantity
            )
        )
    }

    private val dismissCallback = object : Snackbar.Callback() {
        override fun onDismissed(
            transientBottomBar: Snackbar,
            event: Int,
        ) {
            if (event == DISMISS_EVENT_SWIPE || event == DISMISS_EVENT_ACTION || event == DISMISS_EVENT_TIMEOUT)
                viewModel.messageShown()
        }
    }

    private fun observeMoveResult() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.moveResult.collect { result ->
                    result?.let {
                        showDismissibleSnackBar(
                            if (it) R.string.move_category_success else R.string.move_category_failure,
                            dismissCallback
                        )
                    }
                }
            }
        }
    }

    private fun observeDeleteResult() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.deleteResult.collect { result ->
                    result?.onSuccess { deleteResult ->
                        when (deleteResult) {
                            is OperationComplete -> {
                                val messages = buildList {
                                    mapToMessage(deleteResult.deleted, R.plurals.delete_success)
                                    mapToMessage(
                                        deleteResult.mappedToTransactions,
                                        R.plurals.not_deletable_mapped_transactions
                                    )
                                    mapToMessage(
                                        deleteResult.mappedToTemplates,
                                        R.plurals.not_deletable_mapped_templates
                                    )
                                }
                                showDismissibleSnackBar(
                                    messages.joinToString(" "),
                                    dismissCallback
                                )
                            }

                            is OperationPending -> {
                                val messages = buildList {
                                    if (deleteResult.hasDescendants > 0) {
                                        mapToMessage(
                                            deleteResult.hasDescendants,
                                            R.plurals.warning_delete_main_category
                                        )
                                    }
                                    if (deleteResult.mappedToBudgets > 0) {
                                        add(getString(R.string.warning_delete_category_with_budget))
                                    }
                                    add(getString(R.string.continue_confirmation))
                                }
                                val labels = deleteResult.categories.joinToString { it.label }
                                MessageDialogFragment.newInstance(
                                    getString(R.string.dialog_title_warning_delete_category) +
                                            " ($labels)",
                                    messages.joinToString(" "),
                                    MessageDialogFragment.Button(
                                        R.string.response_yes,
                                        R.id.DELETE_COMMAND_DO,
                                        deleteResult.categories.map { it.id }.toTypedArray()
                                    ),
                                    null,
                                    MessageDialogFragment.Button(
                                        R.string.response_no,
                                        R.id.CANCEL_CALLBACK_COMMAND,
                                        null
                                    )
                                )
                                    .show(supportFragmentManager, "DELETE_CATEGORY")
                                viewModel.messageShown()
                            }
                        }
                    }?.onFailure {
                        showDeleteFailureFeedback(it.message, dismissCallback)
                    }
                }
            }
        }
    }

    private fun observeExportResult() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.exportResult.collect { result ->
                    result?.onSuccess { pair ->
                        updateDismissibleSnackBar(
                            getString(
                                R.string.export_sdcard_success,
                                pair.second
                            )
                        )
                        if (prefHandler.getBoolean(PrefKey.PERFORM_SHARE, false)) {
                            baseViewModel.share(
                                this@ManageCategories, listOf(pair.first),
                                prefHandler.requireString(PrefKey.SHARE_TARGET, "").trim(),
                                "text/qif"
                            )
                        }
                    }?.onFailure {
                        updateDismissibleSnackBar(it.safeMessage)
                    }
                }
            }
        }
    }

    private fun observeImportResult() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.importResult.filterNotNull().collect { pair ->
                    showDismissibleSnackBar(
                        buildList {
                            pair.first.takeIf { it != 0 }?.let {
                                add(
                                    resources.getQuantityString(
                                        R.plurals.import_categories_result,
                                        it,
                                        it
                                    )
                                )
                            }
                            pair.second.takeIf { it != 0 }?.let {
                                add(
                                    resources.getQuantityString(
                                        R.plurals.import_categories_icons_updated,
                                        it,
                                        it
                                    )
                                )
                            }
                        }.ifEmpty {
                            listOf(
                                resources.getQuantityString(
                                    R.plurals.import_categories_result,
                                    0,
                                    0
                                )
                            )
                        }.joinToString(separator = " "),
                        dismissCallback
                    )
                }
            }
        }
    }

    private fun observeSyncResult() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.syncResult.collect {
                    showSnackBar(it, callback = dismissCallback)
                }
            }
        }
    }

    private fun observeMergeResult() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.mergeResult.collect {
                    viewModel.messageShown()
                }
            }
        }
    }

    override fun dispatchCommand(command: Int, tag: Any?) =
        if (super.dispatchCommand(command, tag)) {
            true
        } else when (command) {
            R.id.CANCEL_CALLBACK_COMMAND -> {
                true
            }

            R.id.DELETE_COMMAND_DO -> {
                showSnackBarIndefinite(R.string.progress_dialog_deleting)
                @Suppress("UNCHECKED_CAST")
                viewModel.deleteCategoriesDo((tag as Array<Long>).toList())
                true
            }

            R.id.SETUP_CATEGORIES_DEFAULT_COMMAND -> {
                checkImportableCategories()
                true
            }

            R.id.EXPORT_CATEGORIES_COMMAND_ISO88591 -> {
                exportCats("ISO-8859-1")
                true
            }

            R.id.EXPORT_CATEGORIES_COMMAND_UTF8 -> {
                exportCats("UTF-8")
                true
            }

            else -> false
        }

    private val protectionInfo: ProtectionInfo?
        get() = androidx.core.content.IntentCompat.getParcelableExtra(intent, KEY_PROTECTION_INFO, ProtectionInfo::class.java)

    private fun checkImportableCategories() {
        viewModel.checkImportableCategories().observe(this) {
            SetupCategoriesConfirmDialogFragment.newInstance(it).show(
                supportFragmentManager, "CONFIRM"
            )
        }
    }

    private fun exportCats(encoding: String) {
        showDismissibleSnackBar(getString(R.string.export_to_format, "QIF"), dismissCallback)
        viewModel.exportCats(encoding)
    }

    private fun createCat(parent: Category?) {
        viewModel.dialogState = ManageCategoriesViewModel.Edit(
            parent = parent,
            category = if (parent == null) Category(
                typeFlags = viewModel.typeFilter ?: FLAG_NEUTRAL
            ) else null
        )
    }

    private fun editCat(category: Category) {
        viewModel.dialogState =
            ManageCategoriesViewModel.Edit(category = category)
    }

    override fun contribFeatureCalled(feature: ContribFeature, tag: Serializable?) {
        if (feature == ContribFeature.CATEGORY_TREE) {
            val pair = tag as? Pair<*, *>
            val command = pair?.first as? Int
            val category = pair?.second as? Category
            if (command == R.id.CREATE_SUB_COMMAND && category != null) {
                createCat(category)
            } else if (command == R.id.SELECT_COMMAND && category != null) {
                doSingleSelection(category)
            }
        }
    }

    companion object {
        const val KEY_PROTECTION_INFO = "protection_info"
    }

    @Parcelize
    data class ProtectionInfo(val id: Long, val isTemplate: Boolean) : Parcelable
}