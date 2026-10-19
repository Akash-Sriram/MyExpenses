package org.totschnig.myexpenses.util.form

import android.widget.TextView
import org.totschnig.myexpenses.R

class NumberRangeValidator(field: TextView, private val min: Int, private val max: Int) :
    AbstractFormFieldValidator(field) {

    override val message: Int
        get() = R.string.validation_error_number_out_of_range

    override val messageFormatArgs: Array<out Any>
        get() = arrayOf(min, max)

    override val isValid: Boolean
        get() = try {
            val input = fields[0].text.toString().trim().toInt()
            input in min..max
        } catch (e: NumberFormatException) {
            false
        }
}
