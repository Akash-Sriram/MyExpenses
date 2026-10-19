package org.totschnig.myexpenses.testutils

import org.totschnig.myexpenses.MyApplication
import org.totschnig.myexpenses.di.LicenceModule
import org.totschnig.myexpenses.util.licence.LicenceHandler
import java.time.Clock

class MockLicenceModule(private val clock: Clock) : LicenceModule() {
    override fun providesLicenceHandler(
        application: MyApplication
    ): LicenceHandler = MockLicenceHandler(application)
}
