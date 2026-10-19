package org.totschnig.myexpenses.dialog

import android.view.View
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.CompoundButton.OnCheckedChangeListener
import org.totschnig.myexpenses.R

abstract class TextSourceDialogFragment : ImportSourceDialogFragment(), OnCheckedChangeListener {
    protected var mImportCategories: CheckBox? = null
    protected var mImportParties: CheckBox? = null
    protected var mImportTransactions: CheckBox? = null

    override fun setupDialogView(view: View) {
        super.setupDialogView(view)
        mImportCategories = view.findViewById(R.id.import_select_categories)
        mImportCategories?.let { categories ->
            categories.setOnCheckedChangeListener(this)
            mImportParties = view.findViewById<CheckBox>(R.id.import_select_parties)
                .also { it.setOnCheckedChangeListener(this) }
            mImportTransactions = view.findViewById<CheckBox>(R.id.import_select_transactions)
                .also { it.setOnCheckedChangeListener(this) }
        }
    }

    override val isReady: Boolean
        get() = super.isReady && (
            (mImportCategories?.visibility == View.VISIBLE && mImportCategories?.isChecked == true) ||
            (mImportParties?.visibility == View.VISIBLE && mImportParties?.isChecked == true) ||
            (mImportTransactions?.visibility == View.VISIBLE && mImportTransactions?.isChecked == true)
        )

    override fun onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) {
        setButtonState()
    }
}
