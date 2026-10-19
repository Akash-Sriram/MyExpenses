package org.totschnig.myexpenses.util.cursor

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.CalendarContract
import android.provider.CalendarContract.Events
import android.text.TextUtils
import androidx.collection.LongSparseArray
import androidx.collection.SparseArrayCompat
import org.totschnig.myexpenses.MyApplication
import org.totschnig.myexpenses.db2.entities.prettyTimeInfo
import org.totschnig.myexpenses.provider.CalendarProviderProxy
import org.totschnig.myexpenses.provider.KEY_PLANID
import org.totschnig.myexpenses.provider.KEY_PLAN_INFO
import java.util.Collections

class PlanInfoCursorWrapper(
    private val context: Context,
    cursor: Cursor?,
    shouldSortByNextInstance: Boolean,
    initializePlanInfo: Boolean
) : CursorWrapperHelper(cursor) {

    private val planInfo = LongSparseArray<String>()
    private val nextInstance = SparseArrayCompat<Long>()
    private val sortedPositions = ArrayList<Int>()
    private val shouldSortByNextInstance: Boolean

    init {
        if (initializePlanInfo) {
            this.shouldSortByNextInstance = shouldSortByNextInstance
            initializePlanInfo()
        } else {
            this.shouldSortByNextInstance = false
        }
    }

    private fun initializePlanInfo() {
        val calendarId = (context.applicationContext as MyApplication).appComponent.plannerUtils().checkPlanner()
        val wrapped = wrappedCursor
        if (wrapped != null && wrapped.moveToFirst()) {
            val plans = ArrayList<Long>()
            var planId: Long
            val columnIndexPlanId = getColumnIndexOrThrow(KEY_PLANID)
            while (!wrapped.isAfterLast) {
                val wrappedPos = wrapped.position
                planId = getLong(columnIndexPlanId)
                if (planId != 0L) {
                    plans.add(planId)
                    if (shouldSortByNextInstance) {
                        nextInstance.put(wrappedPos, getNextInstance(planId))
                    }
                }
                sortedPositions.add(wrappedPos)
                wrapped.moveToNext()
            }
            if (shouldSortByNextInstance) {
                Collections.sort(sortedPositions) { lhs, rhs ->
                    val lhNextInstance = nextInstance.get(lhs)
                    val rhNextInstance = nextInstance.get(rhs)
                    when {
                        lhNextInstance == null && rhNextInstance == null -> 0
                        lhNextInstance == null -> 1
                        rhNextInstance == null -> -1
                        else -> lhNextInstance.compareTo(rhNextInstance)
                    }
                }
            }
            if (plans.isNotEmpty()) {
                val c = context.contentResolver.query(
                    Events.CONTENT_URI,
                    arrayOf(
                        Events._ID,
                        Events.DTSTART,
                        Events.RRULE
                    ),
                    Events._ID + " IN (" + TextUtils.join(",", plans) + ") AND " + Events.CALENDAR_ID + " = ?",
                    arrayOf(calendarId),
                    null
                )
                c?.use {
                    if (it.moveToFirst()) {
                        val idIdx = it.getColumnIndexOrThrow(Events._ID)
                        val rruleIdx = it.getColumnIndexOrThrow(Events.RRULE)
                        val dtstartIdx = it.getColumnIndexOrThrow(Events.DTSTART)
                        while (!it.isAfterLast) {
                            planInfo.put(
                                it.getLong(idIdx),
                                prettyTimeInfo(
                                    context,
                                    it.getString(rruleIdx),
                                    it.getLong(dtstartIdx)
                                )
                            )
                            it.moveToNext()
                        }
                    }
                }
            }
        }
    }

    private fun getNextInstance(planId: Long): Long {
        val now = System.currentTimeMillis()
        val inOneWeek = now + (7 * 24 * 60 * 60 * 1000)
        val inOneMonth = now + (31L * 24 * 60 * 60 * 1000L)
        val inOneYear = now + (366L * 24 * 60 * 60 * 1000L)
        val intervals = arrayOf(
            longArrayOf(now, inOneWeek),
            longArrayOf(inOneWeek, inOneMonth),
            longArrayOf(inOneMonth, inOneYear)
        )
        for (interval in intervals) {
            val eventsUriBuilder = CalendarProviderProxy.INSTANCES_URI.buildUpon()
            ContentUris.appendId(eventsUriBuilder, interval[0])
            ContentUris.appendId(eventsUriBuilder, interval[1])
            val eventsUri = eventsUriBuilder.build()
            val c = context.contentResolver.query(
                eventsUri,
                null,
                CalendarContract.Instances.EVENT_ID + " = ?",
                arrayOf(planId.toString()),
                null
            )
            c?.use {
                if (it.moveToFirst()) {
                    return it.getLong(it.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN))
                }
            }
        }
        return Long.MAX_VALUE
    }

    override fun getColumnIndexOrThrow(columnName: String): Int {
        val result = getColumnIndex(columnName)
        if (result > -1) return result
        throw IllegalArgumentException("$columnName not found")
    }

    override fun getColumnIndex(columnName: String): Int {
        return if (columnName == KEY_PLAN_INFO) {
            columnCount
        } else {
            super.getColumnIndex(columnName)
        }
    }

    override fun getColumnName(columnIndex: Int): String {
        return if (columnIndex == columnCount) {
            KEY_PLAN_INFO
        } else {
            super.getColumnName(columnIndex)
        }
    }

    override fun getString(columnIndex: Int): String? {
        return if (columnIndex == columnCount) {
            planInfo.get(getLong(getColumnIndex(KEY_PLANID)))
        } else {
            super.getString(columnIndex)
        }
    }

    override fun isNull(columnIndex: Int): Boolean {
        return if (columnIndex == columnCount) {
            getString(columnIndex) == null
        } else {
            super.isNull(columnIndex)
        }
    }

    override fun getMappedPosition(pos: Int): Int {
        return if (shouldSortByNextInstance) sortedPositions[pos] else pos
    }
}
