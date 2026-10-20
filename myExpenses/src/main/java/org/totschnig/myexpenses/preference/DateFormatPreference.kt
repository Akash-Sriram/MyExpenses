package org.totschnig.myexpenses.preference

import android.content.Context
import android.util.AttributeSet
import org.totschnig.myexpenses.util.Utils
import org.totschnig.myexpenses.util.validateDateFormat
class DateFormatPreference(context: Context, attrs: AttributeSet) :
        LocalizedFormatEditTextPreference(context, attrs) {
    override fun getDefaultValue(): String? = Utils.getLocalizedDatePattern(context)
    override fun validate(newValue: String) = validateDateFormat(context, newValue)
}