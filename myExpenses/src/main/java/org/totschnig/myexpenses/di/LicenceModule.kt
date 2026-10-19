package org.totschnig.myexpenses.di

import dagger.Module
import dagger.Provides
import org.totschnig.myexpenses.MyApplication
import org.totschnig.myexpenses.util.licence.LicenceHandler
import javax.inject.Singleton

@Module
open class LicenceModule {
    @Provides
    @Singleton
    open fun providesLicenceHandler(
        application: MyApplication
    ) = LicenceHandler(application)
}
