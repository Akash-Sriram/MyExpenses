package org.totschnig.myexpenses.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.net.Uri
import android.provider.CalendarContract
import org.totschnig.myexpenses.BuildConfig
import java.time.Instant
import java.time.ZoneId

/**
 * Proxy for [CalendarContract.Instances] which allows to swap in
 * alternate implementation in context where the Instances table does not work, e.g. Blackberry
 */
class CalendarProviderProxy : ContentProvider() {

    companion object {
        const val AUTHORITY = BuildConfig.APPLICATION_ID + ".calendarinstances"
        @JvmField
        val INSTANCES_URI: Uri = Uri.parse("content://$AUTHORITY/instances/when")

        private val INSTANCE_PROJECTION = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.TITLE
        )

        private const val INSTANCES_WHEN = 1
        private const val EVENTS = 2 // currently not used, but possibly in the future

        private val URI_MATCHER = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(AUTHORITY, "instances/when/*/*", INSTANCES_WHEN)
            addURI(AUTHORITY, "events", EVENTS)
        }

        @JvmStatic
        fun calculateId(date: Long): Long {
            val localDate = Instant.ofEpochMilli(date)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
            return localDate.year * 1000L + localDate.dayOfYear
        }
    }

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor? {
        return when (URI_MATCHER.match(uri)) {
            INSTANCES_WHEN -> {
                if (projection != null) throw IllegalStateException("Must pass in null projection")
                val startMilliseconds = uri.pathSegments[2].toLong()
                val endMilliseconds = uri.pathSegments[3].toLong()
                // Instances.CONTENT_URI returns events that fall totally or partially in a given range
                // we additionally select only instances where the begin is inside the range
                // because we want to deal with each instance only once
                val selectionStr = (if (selection == null) "" else "$selection AND ") +
                        "${CalendarContract.Instances.BEGIN} BETWEEN $startMilliseconds AND $endMilliseconds"
                val proxiedUri = Uri.parse(
                    uri.toString().replace(INSTANCES_URI.toString(),
                        CalendarContract.Instances.CONTENT_URI.toString())
                )
                requireContext().contentResolver.query(
                    proxiedUri, INSTANCE_PROJECTION, selectionStr, selectionArgs, sortOrder
                )
            }
            EVENTS -> requireContext().contentResolver.query(
                CalendarContract.Events.CONTENT_URI, projection, selection, selectionArgs, sortOrder
            )
            else -> throw IllegalArgumentException("Unknown URL $uri")
        }
    }

    override fun getType(uri: Uri): String? = null

    /** not implemented */
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    /** not implemented */
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0

    /** not implemented */
    override fun update(
        uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?
    ): Int = 0
}
