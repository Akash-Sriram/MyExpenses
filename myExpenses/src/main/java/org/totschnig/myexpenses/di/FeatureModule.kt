package org.totschnig.myexpenses.di

import dagger.Module
import dagger.Provides
import org.totschnig.myexpenses.feature.FeatureManager
import org.totschnig.myexpenses.preference.PrefHandler
import javax.inject.Singleton

@Module
open class FeatureModule {

    @Provides
    @Singleton
    open fun provideFeatureManager(prefHandler: PrefHandler): FeatureManager = object : FeatureManager() {}
}