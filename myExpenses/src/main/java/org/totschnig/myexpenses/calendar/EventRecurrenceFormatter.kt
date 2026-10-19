/*
 * Copyright (C) 2006 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.totschnig.myexpenses.calendar

import android.content.Context
import android.content.res.Resources
import android.text.format.DateUtils
import android.text.format.Time
import android.util.TimeFormatException
import org.totschnig.myexpenses.R
import java.text.SimpleDateFormat
import java.util.Calendar

@Suppress("deprecation")
object EventRecurrenceFormatter {

    private var mMonthRepeatByDayOfWeekIds: IntArray? = null
    private var mMonthRepeatByDayOfWeekStrs: Array<Array<String>?>? = null

    @JvmStatic
    fun getRepeatString(
        context: Context,
        r: Resources,
        recurrence: EventRecurrence,
        includeEndString: Boolean
    ): String? {
        var endString = ""
        if (includeEndString) {
            val sb = StringBuilder()
            if (recurrence.until != null) {
                try {
                    val t = Time()
                    t.parse(recurrence.until)
                    val dateStr = DateUtils.formatDateTime(
                        context,
                        t.toMillis(false),
                        DateUtils.FORMAT_NUMERIC_DATE
                    )
                    sb.append(r.getString(R.string.endByDate, dateStr))
                } catch (ignored: TimeFormatException) {
                }
            }

            if (recurrence.count > 0) {
                sb.append(
                    r.getQuantityString(
                        R.plurals.endByCount,
                        recurrence.count,
                        recurrence.count
                    )
                )
            }
            endString = sb.toString()
        }

        val interval = Math.max(recurrence.interval, 1)
        when (recurrence.freq) {
            EventRecurrence.DAILY -> return r.getQuantityString(
                R.plurals.daily,
                interval,
                interval
            ) + endString

            EventRecurrence.WEEKLY -> {
                if (recurrence.repeatsOnEveryWeekDay()) {
                    return r.getString(R.string.every_weekday) + endString
                } else {
                    val string: String
                    var dayOfWeekLength = DateUtils.LENGTH_MEDIUM
                    if (recurrence.bydayCount == 1) {
                        dayOfWeekLength = DateUtils.LENGTH_LONG
                    }

                    val days = StringBuilder()
                    if (recurrence.bydayCount > 0) {
                        val count = recurrence.bydayCount - 1
                        for (i in 0 until count) {
                            days.append(dayToString(recurrence.byday!![i], dayOfWeekLength))
                            days.append(", ")
                        }
                        days.append(dayToString(recurrence.byday!![count], dayOfWeekLength))
                        string = days.toString()
                    } else {
                        if (recurrence.startDate == null) {
                            return null
                        }
                        val day = EventRecurrence.timeDay2Day(recurrence.startDate!!.weekDay)
                        string = dayToString(day, DateUtils.LENGTH_LONG)
                    }
                    return r.getQuantityString(R.plurals.weekly, interval, interval, string) + endString
                }
            }

            EventRecurrence.MONTHLY -> {
                val sb = StringBuilder()
                sb.append(r.getQuantityString(R.plurals.monthly, interval, interval))
                if (recurrence.bydayCount == 1) {
                    val weekday = recurrence.startDate!!.weekDay
                    cacheMonthRepeatStrings(r, weekday)
                    val dayNumber = (recurrence.startDate!!.monthDay - 1) / 7
                    sb.append(" (")
                    sb.append(mMonthRepeatByDayOfWeekStrs!![weekday]!![dayNumber])
                    sb.append(")")
                    sb.append(endString)
                } else if (recurrence.bysetposCount == 1 && recurrence.bysetpos!![0] == -1) {
                    sb.append(" (")
                    sb.append(r.getString(R.string.recurrence_last_day_of_month))
                    sb.append(")")
                } else {
                    sb.append(r.getString(R.string.monthly_on_day, recurrence.startDate!!.monthDay.toString()))
                }
                sb.append(endString)
                return sb.toString()
            }

            EventRecurrence.YEARLY -> return r.getQuantityString(
                R.plurals.yearly,
                interval,
                interval
            ) + r.getString(
                R.string.yearly_on_day,
                DateUtils.formatDateTime(context, recurrence.startDate!!.toMillis(false), 0)
            ) + endString
        }

        return null
    }

    private fun cacheMonthRepeatStrings(r: Resources, weekday: Int) {
        if (mMonthRepeatByDayOfWeekIds == null) {
            mMonthRepeatByDayOfWeekIds = intArrayOf(
                R.array.repeat_by_nth_sun,
                R.array.repeat_by_nth_mon,
                R.array.repeat_by_nth_tues,
                R.array.repeat_by_nth_wed,
                R.array.repeat_by_nth_thurs,
                R.array.repeat_by_nth_fri,
                R.array.repeat_by_nth_sat
            )
        }
        if (mMonthRepeatByDayOfWeekStrs == null) {
            mMonthRepeatByDayOfWeekStrs = arrayOfNulls(7)
        }
        if (mMonthRepeatByDayOfWeekStrs!![weekday] == null) {
            mMonthRepeatByDayOfWeekStrs!![weekday] =
                r.getStringArray(mMonthRepeatByDayOfWeekIds!![weekday])
        }
    }

    private fun dayToString(day: Int, dayOfWeekLength: Int): String {
        val format = if (dayOfWeekLength == DateUtils.LENGTH_MEDIUM) "EEE" else "EEEE"
        val c = Calendar.getInstance()
        c[Calendar.DAY_OF_WEEK] = dayToUtilDay(day)
        return SimpleDateFormat(format).format(c.time)
    }

    private fun dayToUtilDay(day: Int): Int {
        return when (day) {
            EventRecurrence.SU -> Calendar.SUNDAY
            EventRecurrence.MO -> Calendar.MONDAY
            EventRecurrence.TU -> Calendar.TUESDAY
            EventRecurrence.WE -> Calendar.WEDNESDAY
            EventRecurrence.TH -> Calendar.THURSDAY
            EventRecurrence.FR -> Calendar.FRIDAY
            EventRecurrence.SA -> Calendar.SATURDAY
            else -> throw IllegalArgumentException("bad day argument: $day")
        }
    }
}
