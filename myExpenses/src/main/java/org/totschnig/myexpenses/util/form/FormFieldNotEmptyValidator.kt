package org.totschnig.myexpenses.util.form

import android.widget.TextView
import org.totschnig.myexpenses.R

class FormFieldNotEmptyValidator(field: TextView) : AbstractFormFieldValidator(field) {
    override val message: Int
        get() = R.string.validate_error_not_empty

    override val isValid: Boolean
        get() = fields[0].text.toString().trim().isNotEmpty()
}
