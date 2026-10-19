package org.totschnig.myexpenses.viewmodel

import android.app.Application
import android.database.Cursor
import app.cash.copper.flow.observeQuery
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.totschnig.myexpenses.db2.createPaymentMethod
import org.totschnig.myexpenses.db2.deleteMethod
import org.totschnig.myexpenses.db2.loadPaymentMethod
import org.totschnig.myexpenses.db2.updatePaymentMethod
import org.totschnig.myexpenses.model2.PaymentMethod
import org.totschnig.myexpenses.provider.TransactionProvider

class MethodViewModel(application: Application) : ContentResolvingAndroidViewModel(application) {

    val methodsCursor: Flow<Cursor?>
        get() = contentResolver.observeQuery(TransactionProvider.METHODS_URI).map { it.run() }

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