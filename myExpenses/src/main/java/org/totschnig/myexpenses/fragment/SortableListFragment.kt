package org.totschnig.myexpenses.fragment

import android.view.Menu
import android.view.MenuItem
import androidx.annotation.NonNull
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.model.sort.Sort
import org.totschnig.myexpenses.preference.PrefHandler
import org.totschnig.myexpenses.preference.PrefKey
import javax.inject.Inject

abstract class SortableListFragment : ContextualActionBarFragment() {

    @Inject
    lateinit var prefHandler: PrefHandler

    fun configureSortMenu(menu: Menu) {
        val menuItem = menu.findItem(R.id.SORT_COMMAND)
        menuItem?.subMenu?.findItem(getCurrentSortOrder().commandId)?.isChecked = true
    }

    protected open fun getDefaultSortOrder(): Sort = Sort.USAGES

    @NonNull
    protected fun getCurrentSortOrder(): Sort {
        val defaultSortOrder = getDefaultSortOrder()
        return try {
            Sort.valueOf(prefHandler.getString(getSortOrderPrefKey(), defaultSortOrder.name)!!)
        } catch (e: IllegalArgumentException) {
            defaultSortOrder
        }
    }

    protected fun handleSortOption(item: MenuItem): Boolean {
        val newSortOrder = Sort.fromCommandId(item.itemId)
        if (newSortOrder != null) {
            if (!item.isChecked) {
                prefHandler.putString(getSortOrderPrefKey(), newSortOrder.name)
                requireActivity().invalidateOptionsMenu()
                loadData()
            }
            return true
        }
        return false
    }

    protected abstract fun getSortOrderPrefKey(): PrefKey
    protected abstract fun loadData()
}
