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
import org.totschnig.myexpenses.R
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

object EventRecurrenceFormatter {

    private var mMonthRepeatByDayOfWeekIds: IntArray? = null
    private var mMonthRepeatByDayOfWeekStrs: Array<Array<String>?>? = null

    @JvmStatic
    fun parseUntilToMillis(until: String): Long {
        val trimmed = until.trim()
        return try {
            if (trimmed.contains("-")) {
                if (trimmed.contains("T")) {
                    Instant.parse(trimmed).toEpochMilli()
                } else {
                    LocalDate.parse(trimmed).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                }
            } else if (trimmed.contains("T")) {
                if (trimmed.endsWith("Z")) {
                    val formatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)
                    Instant.from(formatter.parse(trimmed)).toEpochMilli()
                } else {
                    val formatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
                    LocalDateTime.parse(trimmed, formatter).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                }
            } else {
                LocalDate.parse(trimmed, DateTimeFormatter.BASIC_ISO_DATE)
                    .atStartOfDay(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
            }
        } catch (_: Exception) {
            0L
        }
    }

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
                val millis = parseUntilToMillis(recurrence.until!!)
                if (millis > 0L) {
                    val dateStr = DateUtils.formatDateTime(
                        context,
                        millis,
                        DateUtils.FORMAT_NUMERIC_DATE
                    )
                    sb.append(r.getString(R.string.endByDate, dateStr))
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
                        val day = EventRecurrence.dayOfWeek2Day(recurrence.startDate!!.dayOfWeek)
                        string = dayToString(day, DateUtils.LENGTH_LONG)
                    }
                    return r.getQuantityString(R.plurals.weekly, interval, interval, string) + endString
                }
            }

            EventRecurrence.MONTHLY -> {
                val sb = StringBuilder()
                sb.append(r.getQuantityString(R.plurals.monthly, interval, interval))
                if (recurrence.bydayCount == 1) {
                    val weekday = when (recurrence.startDate!!.dayOfWeek) {
                        DayOfWeek.SUNDAY -> 0
                        DayOfWeek.MONDAY -> 1
                        DayOfWeek.TUESDAY -> 2
                        DayOfWeek.WEDNESDAY -> 3
                        DayOfWeek.THURSDAY -> 4
                        DayOfWeek.FRIDAY -> 5
                        DayOfWeek.SATURDAY -> 6
                    }
                    cacheMonthRepeatStrings(r, weekday)
                    val dayNumber = (recurrence.startDate!!.dayOfMonth - 1) / 7
                    sb.append(" (")
                    sb.append(mMonthRepeatByDayOfWeekStrs!![weekday]!![dayNumber])
                    sb.append(")")
                    sb.append(endString)
                } else if (recurrence.bysetposCount == 1 && recurrence.bysetpos!![0] == -1) {
                    sb.append(" (")
                    sb.append(r.getString(R.string.recurrence_last_day_of_month))
                    sb.append(")")
                } else {
                    sb.append(r.getString(R.string.monthly_on_day, recurrence.startDate!!.dayOfMonth.toString()))
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
                DateUtils.formatDateTime(context, recurrence.startDate!!.toInstant().toEpochMilli(), 0)
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
        val dayOfWeek = when (day) {
            EventRecurrence.SU -> DayOfWeek.SUNDAY
            EventRecurrence.MO -> DayOfWeek.MONDAY
            EventRecurrence.TU -> DayOfWeek.TUESDAY
            EventRecurrence.WE -> DayOfWeek.WEDNESDAY
            EventRecurrence.TH -> DayOfWeek.THURSDAY
            EventRecurrence.FR -> DayOfWeek.FRIDAY
            EventRecurrence.SA -> DayOfWeek.SATURDAY
            else -> throw IllegalArgumentException("bad day argument: $day")
        }
        val textStyle = if (dayOfWeekLength == DateUtils.LENGTH_MEDIUM) TextStyle.SHORT else TextStyle.FULL
        return dayOfWeek.getDisplayName(textStyle, Locale.getDefault())
    }
}
