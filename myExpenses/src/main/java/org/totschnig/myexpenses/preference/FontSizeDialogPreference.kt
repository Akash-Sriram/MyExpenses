package org.totschnig.myexpenses.preference

import android.content.Context
import android.util.AttributeSet
import org.totschnig.myexpenses.R

class FontSizeDialogPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0
) : IntegerDialogPreference(context, attrs, defStyleAttr, defStyleRes) {

    init {
        setOnPreferenceChangeListener { preference, newValue ->
            preference.summary = getEntry(preference.context, newValue as Int)
            true
        }
    }

    override fun getSummary(): CharSequence = getEntry(context, value)

    companion object {
        @JvmStatic
        fun getEntry(context: Context?, index: Int): String {
            if (context == null) return ""
            val standard = context.getString(R.string.system_default)
            return if (index == 0) standard else "$standard + ${index * 10}%"
        }

        @JvmStatic
        fun getEntries(context: Context?): Array<String> = arrayOf(
            getEntry(context, 0),
            getEntry(context, 1),
            getEntry(context, 2),
            getEntry(context, 3)
        )
    }
}
