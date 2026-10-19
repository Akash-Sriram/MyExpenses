package org.totschnig.myexpenses.test.misc

import androidx.test.core.app.ApplicationProvider
import junit.framework.TestCase
import org.totschnig.myexpenses.MyApplication
import org.totschnig.myexpenses.util.licence.LicenceStatus

class SafeGuardTests : TestCase() {
    fun testContribIsEnabled() {
        val app = ApplicationProvider.getApplicationContext<MyApplication>()
        val licenceHandler = app.licenceHandler
        assertTrue(licenceHandler.isContribEnabled)
        assertEquals(LicenceStatus.PROFESSIONAL, licenceHandler.licenceStatus)
    }
}
