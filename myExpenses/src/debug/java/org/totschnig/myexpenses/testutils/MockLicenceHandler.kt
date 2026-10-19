package org.totschnig.myexpenses.testutils

import org.totschnig.myexpenses.MyApplication
import org.totschnig.myexpenses.util.licence.LicenceHandler
import org.totschnig.myexpenses.util.licence.LicenceStatus

class MockLicenceHandler(
    context: MyApplication
) : LicenceHandler(context) {
    fun setLockState(locked: Boolean) {
        this.licenceStatus = if (locked) null else LicenceStatus.PROFESSIONAL
        update()
    }
}
