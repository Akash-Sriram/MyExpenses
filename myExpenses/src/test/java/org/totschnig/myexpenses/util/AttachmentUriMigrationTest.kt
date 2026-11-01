package org.totschnig.myexpenses.util

import android.content.Context
import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AttachmentUriMigrationTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun testIsMyExpensesAuthority() {
        assertTrue(AppDirHelper.isMyExpensesAuthority("org.totschnig.myexpenses.fileprovider"))
        assertTrue(AppDirHelper.isMyExpensesAuthority("org.totschnig.myexpenses.debug.fileprovider"))
        assertTrue(AppDirHelper.isMyExpensesAuthority("com.myexpenses.fileprovider"))
        assertTrue(AppDirHelper.isMyExpensesAuthority("com.myexpenses.debug.fileprovider"))
        assertTrue(AppDirHelper.isMyExpensesAuthority("org.totschnig.myexpenses"))
        assertTrue(AppDirHelper.isMyExpensesAuthority("com.myexpenses"))
        assertTrue(AppDirHelper.isMyExpensesAuthority("com.myexpenses.debug"))

        assertFalse(AppDirHelper.isMyExpensesAuthority("com.google.android.apps.photos"))
        assertFalse(AppDirHelper.isMyExpensesAuthority("com.whatsapp.provider.media"))
        assertFalse(AppDirHelper.isMyExpensesAuthority(null))
    }

    @Test
    fun testNormalizeAttachmentUri() {
        val currentAuthority = AppDirHelper.getFileProviderAuthority(context)

        val legacyUri = "content://org.totschnig.myexpenses.fileprovider/external-files/Pictures/receipt.jpg".toUri()
        val normalizedUri = AppDirHelper.normalizeAttachmentUri(context, legacyUri)

        assertEquals(currentAuthority, normalizedUri.authority)
        assertEquals("/external-files/Pictures/receipt.jpg", normalizedUri.path)

        val externalUri = "content://com.android.providers.media.documents/document/image%3A123".toUri()
        val unchangedUri = AppDirHelper.normalizeAttachmentUri(context, externalUri)
        assertEquals(externalUri, unchangedUri)
    }

    @Test
    fun testGetFileForUriWithLegacyAuthority() {
        val legacyUri = "content://org.totschnig.myexpenses.fileprovider/external-files/Pictures/receipt.jpg".toUri()
        val file = PictureDirHelper.getFileForUri(context, legacyUri)

        assertNotNull(file)
        assertEquals("receipt.jpg", file.name)
        assertEquals(PictureDirHelper.getPictureDir(context, false).absolutePath, file.parentFile?.absolutePath)
    }

    @Test
    fun testPermissionHelperCanReadLegacyUri() {
        val legacyUri = "content://org.totschnig.myexpenses.fileprovider/external-files/Pictures/receipt.jpg".toUri()
        assertTrue(PermissionHelper.canReadUri(legacyUri, context))
    }
}
