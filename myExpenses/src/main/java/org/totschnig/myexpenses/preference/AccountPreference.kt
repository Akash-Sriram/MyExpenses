package org.totschnig.myexpenses.preference

import android.content.Context
import android.util.AttributeSet
import androidx.preference.ListPreference
import org.totschnig.myexpenses.sync.GenericAccountService

class AccountPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ListPreference(context, attrs) {

    companion object {
        const val SYNCHRONIZATION_NONE = "_SYNCHRONIZATION_NONE_"
    }

    override fun findIndexOfValue(value: String?): Int {
        return maxOf(super.findIndexOfValue(value), 0)
    }

    fun setData(context: Context) {
        val accounts = GenericAccountService.getAccountNames(context)
        val entriesArr = Array(accounts.size + 1) { i ->
            if (i == 0) context.getString(androidx.preference.R.string.not_set)
            else accounts[i - 1]
        }
        val entryValuesArr = Array(accounts.size + 1) { i ->
            if (i == 0) SYNCHRONIZATION_NONE else accounts[i - 1]
        }
        entries = entriesArr
        entryValues = entryValuesArr
        notifyChanged()
    }
}
