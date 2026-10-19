package org.totschnig.myexpenses.preference

import android.content.Context
import android.util.AttributeSet
import androidx.preference.DialogPreference

/**
 * A preference for storing integer values
 */
open class IntegerDialogPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0
) : DialogPreference(context, attrs, defStyleAttr, defStyleRes) {

    private var mValueSet = false

    var value: Int
        get() = getPersistedInt(0)
        set(value) {
            val oldValue = getPersistedInt(0)
            val changed = value != oldValue
            if (changed || !mValueSet) {
                mValueSet = true
                persistInt(value)
                if (changed) {
                    notifyChanged()
                }
            }
        }

}
