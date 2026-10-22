package org.totschnig.myexpenses.ui

import android.content.Context
import android.content.ContextWrapper
import android.database.Cursor
import android.os.Parcelable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.AutoCompleteTextView
import android.widget.FilterQueryProvider
import android.widget.ImageView
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ListPopupWindow
import android.widget.SimpleCursorAdapter
import android.widget.ViewSwitcher
import androidx.core.os.BundleCompat
import com.google.android.material.chip.Chip
import kotlinx.parcelize.Parcelize
import android.widget.TextView
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.activity.ExpenseEdit
import org.totschnig.myexpenses.databinding.PartyInputBinding
import org.totschnig.myexpenses.dialog.buildPartyEditDialog
import org.totschnig.myexpenses.fragment.PartiesList.Companion.DIALOG_EDIT_PARTY
import org.totschnig.myexpenses.model2.Party
import org.totschnig.myexpenses.provider.KEY_IBAN
import org.totschnig.myexpenses.provider.KEY_PARENTID
import org.totschnig.myexpenses.provider.KEY_PAYEEID
import org.totschnig.myexpenses.provider.KEY_PAYEE_NAME
import org.totschnig.myexpenses.provider.KEY_ROWID
import org.totschnig.myexpenses.provider.KEY_SHORT_NAME
import org.totschnig.myexpenses.provider.TABLE_PAYEES
import org.totschnig.myexpenses.provider.TransactionProvider
import org.totschnig.myexpenses.provider.getLongIfExists
import org.totschnig.myexpenses.provider.getLongOrNull
import org.totschnig.myexpenses.provider.getString
import org.totschnig.myexpenses.provider.getStringIfExists
import org.totschnig.myexpenses.provider.getStringOrNull
import org.totschnig.myexpenses.util.Utils
import org.totschnig.myexpenses.util.crashreporting.CrashHandler

@Parcelize
data class DisplayParty(
    val id: Long?,
    val name: String,
    val shortName: String? = null,
    val iban: String? = null
) : Parcelable {

    val displayName: String
        get() = shortName ?: name

    companion object {
        fun fromCursor(cursor: Cursor) = cursor.getLongIfExists(KEY_PAYEEID)?.let {
            DisplayParty(
                it,
                cursor.getStringIfExists(KEY_PAYEE_NAME) ?: "",
                cursor.getStringIfExists(KEY_SHORT_NAME),
                cursor.getStringIfExists(KEY_IBAN)
            )
        }
    }
}

class PartyInput @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {


    // Declare a variable for the binding class
    // Use the new layout file name here
    private val binding =
        PartyInputBinding.inflate(LayoutInflater.from(context), this, true)

    private val viewSwitcher: ViewSwitcher
        get() = binding.viewSwitcher
    private val autoCompleteTextView: AutoCompleteTextView
        get() = binding.autoCompleteTextview
    private val buttonClear: ImageView
        get() = binding.buttonClear
    private val selectedItemChip: Chip
        get() = binding.selectedItemChip

    val partyForSave: DisplayParty?
        get() = party ?: Party.validate(autoCompleteTextView.text.toString())
            ?.let { DisplayParty(null, it) }

    var party: DisplayParty? = null
        set(value) {
            field = value
            if (value?.id == null) {
                autoCompleteTextView.setText(value?.name)
                switchView(INDEX_AUTOCOMPLETE_TEXTVIEW)
            } else {
                selectedItemChip.text = value.name
                switchView(INDEX_CHIP_CONTAINER)
            }
        }

    private lateinit var payeeAdapter: SimpleCursorAdapter
    private lateinit var partySelectListener: PartySelectListener

    override fun onSaveInstanceState(): Parcelable {
        val bundle = Bundle()
        bundle.putParcelable(KEY_SUPER_STATE, super.onSaveInstanceState())
        bundle.putParcelable(KEY_PARTY, party)
        return bundle
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state is Bundle) {
            super.onRestoreInstanceState(BundleCompat.getParcelable(state, KEY_SUPER_STATE, Parcelable::class.java))
            party = BundleCompat.getParcelable(state, KEY_PARTY, DisplayParty::class.java)
        } else {
            super.onRestoreInstanceState(state)
        }
    }

    fun interface PartySelectListener {
        fun onPartySelected(partyId: Long?)
    }

    var text: String?
        get() = autoCompleteTextView.text.toString()
        set(value) {
            autoCompleteTextView.setText(value)
            // If text is set programmatically, we might need to reset the 'disabled' state
            // or decide on the desired behavior. For now, let's assume setting text re-enables.
            switchView(INDEX_AUTOCOMPLETE_TEXTVIEW)
        }

    var hint: CharSequence?
        get() = autoCompleteTextView.hint
        set(value) {
            autoCompleteTextView.hint = value
        }

    init {

        buttonClear.setOnClickListener {
            party = null
            switchView(INDEX_AUTOCOMPLETE_TEXTVIEW)
            autoCompleteTextView.requestFocus()
            if (::partySelectListener.isInitialized) {
                partySelectListener.onPartySelected(null)
            }
        }

        selectedItemChip.setOnCloseIconClickListener {
            buildPartyEditDialog(party?.id, party?.name, party?.shortName, party?.iban)
                .show(host, DIALOG_EDIT_PARTY)
        }
        selectedItemChip.closeIconContentDescription = context.getString(R.string.menu_edit_party)
    }

    private fun switchView(index: Int) {
        viewSwitcher.displayedChild = index
    }

    fun addTextChangedListener(watcher: TextWatcher) {
        autoCompleteTextView.addTextChangedListener(watcher)
    }

    private fun setInputMethodModeNeeded() {
        autoCompleteTextView.inputMethodMode = ListPopupWindow.INPUT_METHOD_NEEDED
    }


    fun createPayeeAdapter(
        withInputMethodNeeded: Boolean = false,
        partySelectListener: PartySelectListener
    ) {
        this.partySelectListener = partySelectListener
        payeeAdapter = SimpleCursorAdapter(
            context,
            androidx.appcompat.R.layout.support_simple_spinner_dropdown_item,
            null,
            arrayOf(KEY_PAYEE_NAME),
            intArrayOf(android.R.id.text1),
            0
        )
        payeeAdapter.viewBinder = SimpleCursorAdapter.ViewBinder { view, cursor, _ ->
            if (view.id == android.R.id.text1) {
                val name = cursor.getString(1)
                val alias = if (cursor.isNull(2)) null else cursor.getString(2)
                val upi = if (cursor.isNull(3)) null else cursor.getString(3)
                val displayText = buildString {
                    append(name)
                    if (!alias.isNullOrBlank()) {
                        append("  \u2022  aka: ").append(alias)
                    } else if (!upi.isNullOrBlank()) {
                        append("  \u2022  UPI: ").append(upi)
                    }
                }
                (view as TextView).text = displayText
                true
            } else false
        }
        if (withInputMethodNeeded) {
            setInputMethodModeNeeded()
        }
        autoCompleteTextView.setAdapter(payeeAdapter)
        payeeAdapter.filterQueryProvider = FilterQueryProvider { constraint: CharSequence? ->
            if (constraint != null) {
                val normalized = Utils.escapeSqlLikeExpression(Utils.normalize(constraint.toString()))
                val (selection, selectArgs) =
                    " AND ${Party.SELECTION_WITH_ALIASES}" to Party.selectionArgsWithAliases(normalized)
                context.contentResolver.query(
                    TransactionProvider.PAYEES_URI,
                    arrayOf(
                        KEY_ROWID,
                        KEY_PAYEE_NAME,
                        "(SELECT group_concat($KEY_PAYEE_NAME, ', ') FROM $TABLE_PAYEES WHERE $KEY_PARENTID = $TABLE_PAYEES.$KEY_ROWID) AS $KEY_SHORT_NAME",
                        KEY_IBAN
                    ),
                    "$KEY_PARENTID IS NULL $selection",
                    selectArgs,
                    null
                )
            } else null
        }
        payeeAdapter.stringConversionColumn = 1
        autoCompleteTextView.onItemClickListener =
            AdapterView.OnItemClickListener { _: AdapterView<*>?, _: View?, position: Int, _: Long ->
                val c = payeeAdapter.getItem(position) as Cursor
                if (c.moveToPosition(position)) {
                    val partyId = c.getLong(0)
                    val name = c.getString(1)
                    val iban = if (c.isNull(3)) null else c.getString(3)
                    party = DisplayParty(partyId, name, null, iban)
                    partySelectListener.onPartySelected(partyId)
                }
            }
    }

    fun onDestroy() {
        if (::payeeAdapter.isInitialized) {
            payeeAdapter.cursor?.let {
                if (!it.isClosed) {
                    it.close()
                }
            }
        } else {
            CrashHandler.report(IllegalStateException("PayeeAdapter not initialized"))
        }
    }

    private val host: ExpenseEdit
        get() {
            var context = context
            while (context is ContextWrapper) {
                if (context is ExpenseEdit) {
                    return context
                }
                context = context.baseContext
            }
            throw IllegalStateException("Host context expected to be ExpenseEdit")
        }

    companion object {
        private const val KEY_SUPER_STATE = "super_state"
        private const val KEY_PARTY = "party"
        // Constants for ViewSwitcher child indices
        private const val INDEX_AUTOCOMPLETE_TEXTVIEW = 0
        private const val INDEX_CHIP_CONTAINER = 1
    }
}

