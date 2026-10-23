package org.totschnig.myexpenses.util.contacts

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import timber.log.Timber

object ContactPayeeResolver {

    private val MOBILE_PHONE_REGEX = """(?:\+?91)?[6-9]\d{9}""".toRegex()

    fun resolvePayeeName(context: Context, candidateText: String?): String? {
        if (candidateText.isNullOrBlank()) return null

        // Check if candidateText contains a mobile phone number
        val match = MOBILE_PHONE_REGEX.find(candidateText) ?: return null
        val rawPhone = match.value

        // Check READ_CONTACTS permission
        if (ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_CONTACTS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }

        return try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(rawPhone)
            )
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        cursor.getString(nameIndex)?.takeIf { it.isNotBlank() }
                    } else null
                } else null
            }
        } catch (e: Exception) {
            Timber.e(e, "Error looking up contact for phone %s", rawPhone)
            null
        }
    }
}
