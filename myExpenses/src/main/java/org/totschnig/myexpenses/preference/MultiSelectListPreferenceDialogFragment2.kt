package org.totschnig.myexpenses.preference

import android.content.DialogInterface
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.preference.MultiSelectListPreference
import androidx.preference.PreferenceDialogFragmentCompat

/**
 * A variant of [PreferenceDialogFragmentCompat] for multi-select lists that instead of persisting
 * values directly, allows handling clicks and selections in the target fragment.
 */
class MultiSelectListPreferenceDialogFragment2 : PreferenceDialogFragmentCompat() {

    interface OnClickListener {
        fun onClick(preference: String, values: Set<String>, which: Int)
    }

    private val newValues = mutableSetOf<String>()
    private var preferenceChanged = false
    private var entries: Array<CharSequence> = emptyArray()
    private var entryValues: Array<CharSequence> = emptyArray()

    private val listPreference: MultiSelectListPreference
        get() = preference as MultiSelectListPreference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pref = listPreference
        entries = pref.entries ?: emptyArray()
        entryValues = pref.entryValues ?: emptyArray()

        newValues.clear()
        if (savedInstanceState == null) {
            pref.values?.let { newValues.addAll(it) }
        } else {
            savedInstanceState.getStringArrayList(SAVE_STATE_VALUES)?.let {
                newValues.addAll(it)
            }
            preferenceChanged = savedInstanceState.getBoolean(SAVE_STATE_CHANGED, false)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putStringArrayList(SAVE_STATE_VALUES, ArrayList(newValues))
        outState.putBoolean(SAVE_STATE_CHANGED, preferenceChanged)
    }

    override fun onPrepareDialogBuilder(builder: AlertDialog.Builder) {
        super.onPrepareDialogBuilder(builder)
        val checkedItems = BooleanArray(entryValues.size) { index ->
            newValues.contains(entryValues[index].toString())
        }
        builder.setMultiChoiceItems(entries, checkedItems) { _, which, isChecked ->
            val value = entryValues[which].toString()
            if (isChecked) {
                preferenceChanged = preferenceChanged or newValues.add(value)
            } else {
                preferenceChanged = preferenceChanged or newValues.remove(value)
            }
        }
    }

    override fun onClick(dialog: DialogInterface, which: Int) {
        (targetFragment as? OnClickListener)?.onClick(
            preference.key,
            newValues,
            which
        )
    }

    override fun onDialogClosed(positiveResult: Boolean) {}

    companion object {
        private const val SAVE_STATE_VALUES = "MultiSelectListPreferenceDialogFragment.values"
        private const val SAVE_STATE_CHANGED = "MultiSelectListPreferenceDialogFragment.changed"

        fun newInstance(key: String): MultiSelectListPreferenceDialogFragment2 =
            MultiSelectListPreferenceDialogFragment2().apply {
                arguments = Bundle(1).apply {
                    putString(ARG_KEY, key)
                }
            }
    }
}
