package org.totschnig.myexpenses.contract

import android.net.Uri
import android.provider.BaseColumns
import androidx.annotation.IntDef

/**
 * This contract defines the data needed for communicating with My Expenses via Intents. Currently
 * only adding new transactions via [android.content.Intent.ACTION_INSERT] is supported.
 */
object TransactionsContract {
    const val AUTHORITY = "org.totschnig.myexpenses"

    class Accounts : BaseColumns

    /**
     * Example code:
     * ```kotlin
     * val intent = Intent(Intent.ACTION_INSERT).apply {
     *     data = TransactionsContract.Transactions.CONTENT_URI
     *     putExtra(TransactionsContract.Transactions.AMOUNT_MICROS, 10500000L)
     *     putExtra(TransactionsContract.Transactions.PAYEE_NAME, "Aldi")
     *     putExtra(TransactionsContract.Transactions.CATEGORY_LABEL, "Food:Supermarket")
     * }
     * ```
     */
    object Transactions : BaseColumns {
        @IntDef(TYPE_TRANSACTION, TYPE_TRANSFER, TYPE_SPLIT)
        @Retention(AnnotationRetention.SOURCE)
        annotation class TransactionType

        const val TYPE_TRANSACTION = 0
        const val TYPE_TRANSFER = 1
        const val TYPE_SPLIT = 2

        @JvmField
        val CONTENT_URI: Uri = Uri.parse("content://org.totschnig.myexpenses/transactions")

        const val OPERATION_TYPE = "operationType"
        const val ACCOUNT_LABEL = "accountLabel"
        const val TRANSFER_ACCOUNT_LABEL = "transferAccountLabel"
        const val CURRENCY = "currency"
        const val AMOUNT_MICROS = "amountMicros"
        const val DATE = "date"
        const val PAYEE_NAME = "payeeName"
        const val CATEGORY_LABEL = "categoryLabel"
        const val COMMENT = "comment"
        const val METHOD_LABEL = "methodLabel"
        const val REFERENCE_NUMBER = "referenceNumber"
    }
}
