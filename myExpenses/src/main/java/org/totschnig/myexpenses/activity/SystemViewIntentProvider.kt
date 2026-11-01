package org.totschnig.myexpenses.activity

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import org.totschnig.myexpenses.util.AppDirHelper
import org.totschnig.myexpenses.util.io.getMimeType

class SystemViewIntentProvider : ViewIntentProvider {
    override fun getViewIntent(context: Context, uri: Uri, type: String?): Intent {
        val normalizedUri = AppDirHelper.normalizeAttachmentUri(context, uri)
        val mimeType = type ?: context.contentResolver.getType(normalizedUri) ?: normalizedUri.lastPathSegment?.let { getMimeType(it) } ?: "image/*"

        // For images: open SimpleImageActivity directly.
        // It provides full-screen zoom, pan, edge-to-edge, and an in-app Share button,
        // eliminating third-party gallery permission and broken placeholder issues.
        if (mimeType.startsWith("image")) {
            return Intent(Intent.ACTION_VIEW, normalizedUri, context, SimpleImageActivity::class.java)
        }

        // For documents/PDFs: launch system viewer with explicit URI grant and ClipData
        return try {
            val contentUri = AppDirHelper.ensureContentUri(normalizedUri, context)
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, mimeType)
                clipData = ClipData.newRawUri("", contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } catch (_: Exception) {
            getFallbackIntent(context, normalizedUri)
        }
    }

    private fun getFallbackIntent(context: Context, pictureUri: Uri) =
        Intent(Intent.ACTION_VIEW, AppDirHelper.normalizeAttachmentUri(context, pictureUri), context, SimpleImageActivity::class.java)
}