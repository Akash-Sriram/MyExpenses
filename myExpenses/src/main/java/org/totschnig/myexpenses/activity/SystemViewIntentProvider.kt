package org.totschnig.myexpenses.activity

import android.content.Context
import android.content.Intent
import android.net.Uri
import org.totschnig.myexpenses.util.AppDirHelper
import org.totschnig.myexpenses.util.NougatFileProviderException

class SystemViewIntentProvider : ViewIntentProvider {
    override fun getViewIntent(context: Context, uri: Uri, type: String?): Intent {
        val normalizedUri = AppDirHelper.normalizeAttachmentUri(context, uri)
        return try {
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(AppDirHelper.ensureContentUri(normalizedUri, context), type)
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
        } catch (_: Exception) {
            getFallbackIntent(context, normalizedUri)
        }
    }

    private fun getFallbackIntent(context: Context, pictureUri: Uri) =
        Intent(Intent.ACTION_VIEW, AppDirHelper.normalizeAttachmentUri(context, pictureUri), context, SimpleImageActivity::class.java)
}