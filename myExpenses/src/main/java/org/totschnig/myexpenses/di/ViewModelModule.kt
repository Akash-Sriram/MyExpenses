package org.totschnig.myexpenses.di

import dagger.Module
import dagger.Provides
import org.totschnig.myexpenses.viewmodel.MyExpensesV2ViewModel

@Module
open class ViewModelModule {
    @Provides
    open fun provideMyExpensesViewModelClass(): Class<out MyExpensesV2ViewModel> = MyExpensesV2ViewModel::class.java
}