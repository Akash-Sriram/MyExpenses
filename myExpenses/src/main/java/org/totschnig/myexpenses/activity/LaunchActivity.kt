package org.totschnig.myexpenses.activity

import android.annotation.SuppressLint
import org.totschnig.myexpenses.injector
import org.totschnig.myexpenses.provider.PlannerUtils
import org.totschnig.myexpenses.util.PermissionHelper

abstract class LaunchActivity : ProtectedFragmentActivity() {

    private val plannerUtils: PlannerUtils get() = injector.plannerUtils()

    override fun onPermissionsDenied(requestCode: Int, perms: List<String>) {
        super.onPermissionsDenied(requestCode, perms)
        if (requestCode == PermissionHelper.PERMISSIONS_REQUEST_WRITE_CALENDAR &&
            (PermissionHelper.PermissionGroup.CALENDAR.androidPermissions.any { perms.contains(it) }) &&
            PermissionHelper.somePermissionPermanentlyDenied(
                this,
                PermissionHelper.PermissionGroup.CALENDAR.androidPermissions
            )
        ) {
            plannerUtils.removePlanner(prefHandler)
        }
    }
}