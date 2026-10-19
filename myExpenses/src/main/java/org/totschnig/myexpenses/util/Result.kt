@file:Suppress("DEPRECATION")

package org.totschnig.myexpenses.util

import android.content.Context
import androidx.annotation.StringRes

@Deprecated("Legacy Result tuple")
class Result<T> private constructor(
    val isSuccess: Boolean,
    @get:StringRes val message: Int = 0,
    val extra: T? = null,
    private val messageArguments: Array<out Any?>? = null,
    private val messageString: String? = null
) {
    fun print(ctx: Context): String {
        return if (message == 0) {
            messageString ?: ""
        } else {
            val args = messageArguments ?: emptyArray()
            ctx.getString(message, *args)
        }
    }

    fun print0(ctx: Context): String? {
        return if (message == 0) {
            messageString
        } else {
            val args = messageArguments ?: emptyArray()
            ctx.getString(message, *args)
        }
    }

    companion object {
        @JvmField
        val SUCCESS = Result<Unit>(isSuccess = true)

        @JvmField
        val FAILURE = Result<Unit>(isSuccess = false)

        @JvmStatic
        fun <T> ofSuccess(@StringRes message: Int): Result<T> {
            return Result(isSuccess = true, message = message)
        }

        @JvmStatic
        fun <T> ofSuccess(messageString: String): Result<T> {
            return Result(isSuccess = true, messageString = messageString)
        }

        @JvmStatic
        fun <T> ofSuccess(@StringRes message: Int, extra: T?): Result<T> {
            return Result(isSuccess = true, message = message, extra = extra)
        }

        @JvmStatic
        fun <T> ofSuccess(@StringRes message: Int, extra: T?, vararg messageArguments: Any?): Result<T> {
            return Result(isSuccess = true, message = message, extra = extra, messageArguments = messageArguments)
        }

        @JvmStatic
        fun <T> ofFailure(@StringRes message: Int): Result<T> {
            return Result(isSuccess = false, message = message)
        }

        @JvmStatic
        fun <T> ofFailure(messageString: String): Result<T> {
            return Result(isSuccess = false, messageString = messageString)
        }

        @JvmStatic
        fun <T> ofFailure(@StringRes message: Int, vararg messageArguments: Any?): Result<T> {
            return Result(isSuccess = false, message = message, messageArguments = messageArguments)
        }
    }
}
