package org.totschnig.myexpenses.util.form

import android.content.Context
import android.widget.TextView
import com.google.android.material.textfield.TextInputLayout
import org.totschnig.myexpenses.util.ui.findParentWithTypeRecursively

abstract class AbstractFormFieldValidator(vararg val fields: TextView) {
    protected val context: Context = fields[0].context

    open fun clear() {
        for (field in fields) {
            setError(field, null)
        }
    }

    open fun validate(): Boolean {
        val valid = isValid
        for (field in fields) {
            if (!valid) {
                var error = field.error?.toString() ?: ""
                if (error.isNotEmpty()) {
                    error += "\n\n"
                }
                val formatArgs = messageFormatArgs
                val message = message
                error += if (formatArgs == null) {
                    context.getString(message)
                } else {
                    context.getString(message, *formatArgs)
                }
                setError(field, error)
            }
        }
        return valid
    }

    protected abstract val message: Int

    protected open val messageFormatArgs: Array<out Any>?
        get() = null

    protected abstract val isValid: Boolean

    private fun setError(field: TextView, error: String?) {
        val container = findParentWithTypeRecursively(field, TextInputLayout::class.java)
        if (container != null) {
            container.error = error
        } else {
            field.error = error
        }
    }
}
