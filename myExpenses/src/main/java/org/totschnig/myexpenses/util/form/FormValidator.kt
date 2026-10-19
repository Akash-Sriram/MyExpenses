package org.totschnig.myexpenses.util.form

class FormValidator {
    private val fieldValidators = ArrayList<AbstractFormFieldValidator>()

    fun add(fieldValidator: AbstractFormFieldValidator) {
        fieldValidators.add(fieldValidator)
    }

    fun validate(): Boolean {
        for (validator in fieldValidators) {
            validator.clear()
        }
        var valid = true
        for (validator in fieldValidators) {
            if (!validator.validate()) {
                valid = false
            }
        }
        return valid
    }
}
