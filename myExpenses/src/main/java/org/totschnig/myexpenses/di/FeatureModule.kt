package org.totschnig.myexpenses.di

import dagger.Module
import dagger.Provides
import org.totschnig.myexpenses.feature.FeatureManager
import org.totschnig.myexpenses.feature.OcrFeature
import org.totschnig.myexpenses.preference.PrefHandler
import javax.inject.Singleton

@Module
open class FeatureModule {
    private var ocrFeature: OcrFeature? = null

    @Provides
    fun provideOcrFeature(prefHandler: PrefHandler): OcrFeature? = ocrFeature

    @Provides
    @Singleton
    open fun provideFeatureManager(prefHandler: PrefHandler): FeatureManager = object : FeatureManager() {}
}