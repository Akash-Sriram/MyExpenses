package org.totschnig.myexpenses.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.totschnig.myexpenses.util.updater.AppUpdateManager

@RunWith(RobolectricTestRunner::class)
class AppUpdateManagerTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testIsNewerVersion_majorMinorPatch() {
        // Newer patch version
        assertTrue(AppUpdateManager.isNewerVersion("v4.1.4", "4.1.3"))
        assertTrue(AppUpdateManager.isNewerVersion("4.1.4", "4.1.3"))

        // Newer minor version
        assertTrue(AppUpdateManager.isNewerVersion("v4.2.0", "4.1.9"))

        // Newer major version
        assertTrue(AppUpdateManager.isNewerVersion("v5.0.0", "4.9.9"))

        // Prefix 'r'
        assertTrue(AppUpdateManager.isNewerVersion("r4.1.4", "4.1.3"))

        // Same version
        assertFalse(AppUpdateManager.isNewerVersion("v4.1.3", "4.1.3"))
        assertFalse(AppUpdateManager.isNewerVersion("4.1.3", "4.1.3"))

        // Older version
        assertFalse(AppUpdateManager.isNewerVersion("v4.1.2", "4.1.3"))
        assertFalse(AppUpdateManager.isNewerVersion("v4.0.9", "4.1.3"))
        assertFalse(AppUpdateManager.isNewerVersion("v3.9.9", "4.1.3"))
    }

    @Test
    fun testIsNewerVersion_preReleaseHandling() {
        // Stable release is newer than pre-release with same base numbers
        assertTrue(AppUpdateManager.isNewerVersion("v4.1.3", "4.1.3-pre"))
        assertTrue(AppUpdateManager.isNewerVersion("4.1.3", "4.1.3-pre"))

        // Newer version is newer than pre-release
        assertTrue(AppUpdateManager.isNewerVersion("v4.1.4", "4.1.3-pre"))

        // Pre-release is NOT newer than stable of same version
        assertFalse(AppUpdateManager.isNewerVersion("v4.1.3-pre", "4.1.3"))

        // Same pre-release
        assertFalse(AppUpdateManager.isNewerVersion("v4.1.3-pre", "4.1.3-pre"))
    }

    @Test
    fun testShouldCheckAutomatically() {
        val prefs = context.getSharedPreferences("app_update_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()

        // Never checked before -> true
        assertTrue(AppUpdateManager.shouldCheckAutomatically(context))

        // Record check now
        AppUpdateManager.recordCheckTime(context)

        // Checked just now -> false
        assertFalse(AppUpdateManager.shouldCheckAutomatically(context))

        // Simulate 25 hours ago -> true
        val dayAndHourAgo = System.currentTimeMillis() - (25 * 60 * 60 * 1000L)
        prefs.edit().putLong("last_github_update_check_millis", dayAndHourAgo).commit()
        assertTrue(AppUpdateManager.shouldCheckAutomatically(context))
    }
}
