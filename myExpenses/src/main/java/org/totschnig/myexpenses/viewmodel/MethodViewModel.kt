package org.totschnig.myexpenses.viewmodel

import android.app.Application
import android.database.Cursor
import androidx.core.database.getStringOrNull
import app.cash.copper.flow.mapToList
import app.cash.copper.flow.observeQuery
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.totschnig.myexpenses.db2.createPaymentMethod
import org.totschnig.myexpenses.db2.deleteMethod
import org.totschnig.myexpenses.db2.loadPaymentMethod
import org.totschnig.myexpenses.db2.updatePaymentMethod
import org.totschnig.myexpenses.model2.PaymentMethod
import org.totschnig.myexpenses.provider.KEY_ICON
import org.totschnig.myexpenses.provider.KEY_IS_NUMBERED
import org.totschnig.myexpenses.provider.KEY_LABEL
import org.totschnig.myexpenses.provider.KEY_MAPPED_TEMPLATES
import org.totschnig.myexpenses.provider.KEY_MAPPED_TRANSACTIONS
import org.totschnig.myexpenses.provider.KEY_PREDEFINED_METHOD_NAME
import org.totschnig.myexpenses.provider.KEY_ROWID
import org.totschnig.myexpenses.provider.KEY_TYPE
import org.totschnig.myexpenses.provider.TransactionProvider

data class DisplayPaymentMethod(
    val id: Long,
    val label: String,
    val icon: String?,
    val type: Int,
    val isNumbered: Boolean,
    val preDefinedMethodName: String?,
    val mappedTransactions: Int,
    val mappedTemplates: Int
)

class MethodViewModel(application: Application) : ContentResolvingAndroidViewModel(application) {

    val methodsCursor: Flow<Cursor?>
        get() = contentResolver.observeQuery(TransactionProvider.METHODS_URI).map { it.run() }

    val paymentMethods: Flow<List<DisplayPaymentMethod>> =
        contentResolver.observeQuery(TransactionProvider.METHODS_URI)
            .mapToList { cursor ->
                DisplayPaymentMethod(
                    id = cursor.getLong(cursor.getColumnIndexOrThrow(KEY_ROWID)),
                    label = cursor.getString(cursor.getColumnIndexOrThrow(KEY_LABEL)),
                    icon = cursor.getStringOrNull(cursor.getColumnIndex(KEY_ICON)),
                    type = cursor.getInt(cursor.getColumnIndexOrThrow(KEY_TYPE)),
                    isNumbered = cursor.getInt(cursor.getColumnIndexOrThrow(KEY_IS_NUMBERED)) == 1,
                    preDefinedMethodName = cursor.getStringOrNull(cursor.getColumnIndex(KEY_PREDEFINED_METHOD_NAME)),
                    mappedTransactions = cursor.getInt(cursor.getColumnIndexOrThrow(KEY_MAPPED_TRANSACTIONS)),
                    mappedTemplates = cursor.getInt(cursor.getColumnIndexOrThrow(KEY_MAPPED_TEMPLATES))
                )
            }

    suspend fun loadPaymentMethod(id: Long) = withContext(coroutineDispatcher) {
        repository.loadPaymentMethod(localizedContext, id)
    }

    suspend fun saveMethod(paymentMethod: PaymentMethod) {
        withContext(coroutineDispatcher) {
            if (paymentMethod.id == 0L) {
                repository.createPaymentMethod(localizedContext, paymentMethod)
            } else {
                repository.updatePaymentMethod(localizedContext, paymentMethod)
            }
        }
    }

    fun deleteMethods(methodsIs: List<Long>) {
        methodsIs.forEach {
            repository.deleteMethod(it)
        }
    }
}
