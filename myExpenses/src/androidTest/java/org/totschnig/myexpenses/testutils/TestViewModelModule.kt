package org.totschnig.myexpenses.testutils

import android.app.Application
import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import androidx.test.espresso.idling.CountingIdlingResource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import org.totschnig.myexpenses.MyApplication
import org.totschnig.myexpenses.TestApp
import org.totschnig.myexpenses.adapter.TransactionPagingSource
import org.totschnig.myexpenses.di.ViewModelModule
import org.totschnig.myexpenses.model.CurrencyContext
import org.totschnig.myexpenses.preference.PrefHandler
import org.totschnig.myexpenses.provider.filter.Criterion
import org.totschnig.myexpenses.viewmodel.MyExpensesV2ViewModel
import org.totschnig.myexpenses.viewmodel.data.PageAccount
import org.totschnig.myexpenses.viewmodel.data.Transaction2

object TestViewModelModule : ViewModelModule() {
    override fun provideMyExpensesViewModelClass(): Class<out MyExpensesV2ViewModel> {
        return DecoratingMyExpensesViewModel::class.java
    }
}

class DecoratingMyExpensesViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle,
) : MyExpensesV2ViewModel(application, savedStateHandle) {
    val countingResource = CountingIdlingResource("TransactionPaging", true)

    override fun buildTransactionPagingSource(account: PageAccount) =
        DecoratedTransactionPagingSource(
            getApplication(),
            account,
            filterPersistence.getValue(account.id).whereFilter,
            tags,
            currencyContext,
            viewModelScope,
            countingResource,
            prefHandler
        )
}

class DecoratedTransactionPagingSource(
    context: Context,
    account: PageAccount,
    whereFilter: StateFlow<Criterion?>,
    tags: StateFlow<Map<String, Pair<String, Int?>>>,
    currencyContext: CurrencyContext,
    coroutineScope: CoroutineScope,
    private val countingIdlingResource: CountingIdlingResource,
    prefHandler: PrefHandler,
) : TransactionPagingSource(
    context,
    account,
    whereFilter,
    tags,
    currencyContext,
    coroutineScope,
    prefHandler
) {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Transaction2> {
        countingIdlingResource.increment()
        return super.load(params)
    }

    override fun onLoadFinished() {
        countingIdlingResource.decrement()
    }
}
