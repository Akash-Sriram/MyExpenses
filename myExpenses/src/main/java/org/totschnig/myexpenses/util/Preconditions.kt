package org.totschnig.myexpenses.util

object Preconditions {
    fun <T> checkNotNull(reference: T?): T {
        return reference ?: throw NullPointerException()
    }

    fun <T> checkNotNull(reference: T?, errorMessage: String?): T {
        return reference ?: throw NullPointerException(errorMessage)
    }

    fun checkArgument(expression: Boolean) {
        if (!expression) {
            throw IllegalArgumentException()
        }
    }

    fun checkArgument(argLabel: String, expected: Any?, actual: Any?) {
        if (expected != actual) {
            throw IllegalArgumentException("Expected $argLabel to be $expected, got $actual")
        }
    }

    fun checkArgument(expression: Boolean, errorMessage: Any?) {
        if (!expression) {
            throw IllegalArgumentException(errorMessage.toString())
        }
    }

    fun checkState(expression: Boolean) {
        if (!expression) {
            throw IllegalStateException()
        }
    }

    fun checkState(expression: Boolean, errorMessage: Any?) {
        if (!expression) {
            throw IllegalStateException(errorMessage.toString())
        }
    }

    fun checkState(expression: Boolean, errorMessageTemplate: String, vararg errorMessageArgs: Any?) {
        if (!expression) {
            throw IllegalStateException(String.format(errorMessageTemplate, *errorMessageArgs))
        }
    }
}
