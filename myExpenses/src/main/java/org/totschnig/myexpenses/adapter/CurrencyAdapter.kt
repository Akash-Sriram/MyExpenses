package org.totschnig.myexpenses.adapter

import android.content.Context
import android.widget.ArrayAdapter
import org.totschnig.myexpenses.viewmodel.data.Currency

open class CurrencyAdapter(context: Context, resource: Int) :
    ArrayAdapter<Currency>(context, resource, android.R.id.text1) {
    init {
        setDropDownViewResource(androidx.appcompat.R.layout.support_simple_spinner_dropdown_item)
    }
}
