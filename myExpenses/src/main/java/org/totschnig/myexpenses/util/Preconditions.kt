package org.totschnig.myexpenses.util

object Preconditions {
    @JvmStatic
    fun <T> checkNotNull(reference: T?): T {
        return reference ?: throw NullPointerException()
    }

    @JvmStatic
    fun <T> checkNotNull(reference: T?, errorMessage: String?): T {
        return reference ?: throw NullPointerException(errorMessage)
    }

    @JvmStatic
    fun checkArgument(expression: Boolean) {
        if (!expression) {
            throw IllegalArgumentException()
        }
    }

    @JvmStatic
    fun checkArgument(argLabel: String, expected: Any?, actual: Any?) {
        if (expected != actual) {
            throw IllegalArgumentException(String.format("Expected %s to be %s, got %s", argLabel, expected, actual))
        }
    }

    @JvmStatic
    fun checkArgument(expression: Boolean, errorMessage: Any?) {
        if (!expression) {
            throw IllegalArgumentException(errorMessage.toString())
        }
    }

    @JvmStatic
    fun checkState(expression: Boolean) {
        if (!expression) {
            throw IllegalStateException()
        }
    }

    @JvmStatic
    fun checkState(expression: Boolean, errorMessage: Any?) {
        if (!expression) {
            throw IllegalStateException(errorMessage.toString())
        }
    }

    @JvmStatic
    fun checkState(expression: Boolean, errorMessageTemplate: String, vararg errorMessageArgs: Any?) {
        if (!expression) {
            throw IllegalStateException(String.format(errorMessageTemplate, *errorMessageArgs))
        }
    }
}
