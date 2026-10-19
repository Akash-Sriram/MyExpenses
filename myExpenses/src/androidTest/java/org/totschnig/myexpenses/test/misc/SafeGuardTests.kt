package org.totschnig.myexpenses.test.misc

import junit.framework.TestCase
import org.totschnig.myexpenses.MyApplication

class SafeGuardTests : TestCase() {
    fun testContribIsNotEnabled() {
        val licenceHandler = MyApplication.instance.licenceHandler
        assertFalse(licenceHandler.isContribEnabled)
        assertNull(licenceHandler.licenceStatus)
    }
}
