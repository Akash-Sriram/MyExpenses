/*   This file is part of My Expenses.
 *   My Expenses is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   My Expenses is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with My Expenses.  If not, see <http://www.gnu.org/licenses/>.
*/

package org.totschnig.myexpenses.fragment

import android.annotation.SuppressLint
import android.content.Intent
import android.database.Cursor
import android.os.Bundle
import android.util.SparseBooleanArray
import android.view.ContextMenu
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView.AdapterContextMenuInfo
import android.widget.ListView
import android.widget.SimpleCursorAdapter
import androidx.annotation.NonNull
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.activity.ManageMethods
import org.totschnig.myexpenses.activity.MethodEdit
import org.totschnig.myexpenses.provider.KEY_LABEL
import org.totschnig.myexpenses.provider.KEY_MAPPED_TEMPLATES
import org.totschnig.myexpenses.provider.KEY_MAPPED_TRANSACTIONS
import org.totschnig.myexpenses.provider.KEY_ROWID
import org.totschnig.myexpenses.provider.TransactionProvider
import org.totschnig.myexpenses.viewmodel.MethodViewModel

class MethodList : ContextualActionBarFragment() {
    private val viewModel: MethodViewModel by activityViewModels()
    private lateinit var mAdapter: SimpleCursorAdapter
    private var mMethodsCursor: Cursor? = null

    @SuppressLint("InlinedApi")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val v = inflater.inflate(R.layout.methods_list, container, false)
        val lv = v.findViewById<ListView>(R.id.list)
        val from = arrayOf(KEY_LABEL)
        val to = intArrayOf(android.R.id.text1)
        mAdapter = SimpleCursorAdapter(
            activity,
            android.R.layout.simple_list_item_activated_1,
            null,
            from,
            to,
            0
        )
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.methodsCursor.collect { c ->
                    mMethodsCursor = c
                    mAdapter.swapCursor(c)
                }
            }
        }
        lv.adapter = mAdapter
        lv.emptyView = v.findViewById(R.id.empty)
        registerForContextualActionBar(lv)
        lv.isNestedScrollingEnabled = true
        return v
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mMethodsCursor = null
        mAdapter.swapCursor(null)
    }

    override fun dispatchCommandSingle(command: Int, info: ContextMenu.ContextMenuInfo?): Boolean {
        if (super.dispatchCommandSingle(command, info)) return true
        val menuInfo = info as AdapterContextMenuInfo
        if (command == R.id.EDIT_COMMAND) {
            val i = Intent(activity, MethodEdit::class.java)
            i.putExtra(KEY_ROWID, menuInfo.id)
            startActivity(i)
            finishActionMode()
            return true
        }
        return false
    }

    override fun dispatchCommandMultiple(
        command: Int,
        @NonNull positions: SparseBooleanArray,
        @NonNull itemIds: LongArray
    ): Boolean {
        if (super.dispatchCommandMultiple(command, positions, itemIds)) return true
        if (command == R.id.DELETE_COMMAND) {
            val cursor = mMethodsCursor ?: return false
            val columnIndexMappedTransactions = cursor.getColumnIndex(KEY_MAPPED_TRANSACTIONS)
            val columnIndexMappedTemplates = cursor.getColumnIndex(KEY_MAPPED_TEMPLATES)
            val columnIndexRowId = cursor.getColumnIndex(KEY_ROWID)
            var mappedTransactionsCount = 0
            var mappedTemplatesCount = 0
            val idList = mutableListOf<Long>()

            for (i in 0 until positions.size()) {
                if (positions.valueAt(i)) {
                    var deletable = true
                    cursor.moveToPosition(positions.keyAt(i))
                    if (cursor.getInt(columnIndexMappedTransactions) > 0) {
                        mappedTransactionsCount++
                        deletable = false
                    }
                    if (cursor.getInt(columnIndexMappedTemplates) > 0) {
                        mappedTemplatesCount++
                        deletable = false
                    }
                    if (deletable) {
                        idList.add(cursor.getLong(columnIndexRowId))
                    }
                }
            }

            val activity = requireActivity() as ManageMethods
            if (idList.isNotEmpty()) {
                activity.deleteMethods(idList)
            }
            if (mappedTransactionsCount > 0 || mappedTemplatesCount > 0) {
                var message = ""
                if (mappedTransactionsCount > 0)
                    message += resources.getQuantityString(
                        R.plurals.not_deletable_mapped_transactions,
                        mappedTransactionsCount,
                        mappedTransactionsCount
                    )
                if (mappedTemplatesCount > 0)
                    message += resources.getQuantityString(
                        R.plurals.not_deletable_mapped_templates,
                        mappedTemplatesCount,
                        mappedTemplatesCount
                    )
                activity.showSnackBar(message)
            }
            return true
        }
        return false
    }
}
