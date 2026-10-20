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

import android.util.Log
import java.time.DayOfWeek
import java.time.ZonedDateTime
import java.util.HashMap
import java.util.Locale

/**
 * Event recurrence utility functions.
 */
class EventRecurrence {

    companion object {
        private const val TAG = "EventRecur"

        const val SECONDLY = 1
        const val MINUTELY = 2
        const val HOURLY = 3
        const val DAILY = 4
        const val WEEKLY = 5
        const val MONTHLY = 6
        const val YEARLY = 7

        const val SU = 0x00010000
        const val MO = 0x00020000
        const val TU = 0x00040000
        const val WE = 0x00080000
        const val TH = 0x00100000
        const val FR = 0x00200000
        const val SA = 0x00400000

        private val sParsePartMap = HashMap<String, PartParser>().apply {
            put("FREQ", ParseFreq())
            put("UNTIL", ParseUntil())
            put("COUNT", ParseCount())
            put("INTERVAL", ParseInterval())
            put("BYSECOND", ParseBySecond())
            put("BYMINUTE", ParseByMinute())
            put("BYHOUR", ParseByHour())
            put("BYDAY", ParseByDay())
            put("BYMONTHDAY", ParseByMonthDay())
            put("BYYEARDAY", ParseByYearDay())
            put("BYWEEKNO", ParseByWeekNo())
            put("BYMONTH", ParseByMonth())
            put("BYSETPOS", ParseBySetPos())
            put("WKST", ParseWkst())
        }

        private const val PARSED_FREQ = 1 shl 0
        private const val PARSED_UNTIL = 1 shl 1
        private const val PARSED_COUNT = 1 shl 2
        private const val PARSED_INTERVAL = 1 shl 3
        private const val PARSED_BYSECOND = 1 shl 4
        private const val PARSED_BYMINUTE = 1 shl 5
        private const val PARSED_BYHOUR = 1 shl 6
        private const val PARSED_BYDAY = 1 shl 7
        private const val PARSED_BYMONTHDAY = 1 shl 8
        private const val PARSED_BYYEARDAY = 1 shl 9
        private const val PARSED_BYWEEKNO = 1 shl 10
        private const val PARSED_BYMONTH = 1 shl 11
        private const val PARSED_BYSETPOS = 1 shl 12
        private const val PARSED_WKST = 1 shl 13

        private val sParseFreqMap = HashMap<String, Int>().apply {
            put("SECONDLY", SECONDLY)
            put("MINUTELY", MINUTELY)
            put("HOURLY", HOURLY)
            put("DAILY", DAILY)
            put("WEEKLY", WEEKLY)
            put("MONTHLY", MONTHLY)
            put("YEARLY", YEARLY)
        }

        private val sParseWeekdayMap = HashMap<String, Int>().apply {
            put("SU", SU)
            put("MO", MO)
            put("TU", TU)
            put("WE", WE)
            put("TH", TH)
            put("FR", FR)
            put("SA", SA)
        }

        private const val ALLOW_LOWER_CASE = true
        private const val VALIDATE_UNTIL = false
        private const val ONLY_ONE_UNTIL_COUNT = false

        fun dayOfWeek2Day(day: DayOfWeek): Int {
            return when (day) {
                DayOfWeek.SUNDAY -> SU
                DayOfWeek.MONDAY -> MO
                DayOfWeek.TUESDAY -> TU
                DayOfWeek.WEDNESDAY -> WE
                DayOfWeek.THURSDAY -> TH
                DayOfWeek.FRIDAY -> FR
                DayOfWeek.SATURDAY -> SA
            }
        }


        fun day2DayOfWeek(day: Int): DayOfWeek {
            return when (day) {
                SU -> DayOfWeek.SUNDAY
                MO -> DayOfWeek.MONDAY
                TU -> DayOfWeek.TUESDAY
                WE -> DayOfWeek.WEDNESDAY
                TH -> DayOfWeek.THURSDAY
                FR -> DayOfWeek.FRIDAY
                SA -> DayOfWeek.SATURDAY
                else -> throw IllegalArgumentException("bad day of week: $day")
            }
        }

        fun day2String(day: Int): String {
            return when (day) {
                SU -> "SU"
                MO -> "MO"
                TU -> "TU"
                WE -> "WE"
                TH -> "TH"
                FR -> "FR"
                SA -> "SA"
                else -> throw IllegalArgumentException("bad day argument: $day")
            }
        }

        private fun appendNumbers(s: StringBuilder, label: String, count: Int, values: IntArray?) {
            var c = count
            if (c > 0 && values != null) {
                s.append(label)
                c--
                for (i in 0 until c) {
                    s.append(values[i])
                    s.append(",")
                }
                s.append(values[c])
            }
        }

        private fun arraysEqual(array1: IntArray?, count1: Int, array2: IntArray?, count2: Int): Boolean {
            if (count1 != count2) return false
            if (array1 == null || array2 == null) return array1 === array2
            for (i in 0 until count1) {
                if (array1[i] != array2[i]) return false
            }
            return true
        }
    }

    var startDate: ZonedDateTime? = null
    var freq: Int = 0
    var until: String? = null
    var count: Int = 0
    var interval: Int = 0
    var wkst: Int = 0

    var bysecond: IntArray? = null
    var bysecondCount: Int = 0
    var byminute: IntArray? = null
    var byminuteCount: Int = 0
    var byhour: IntArray? = null
    var byhourCount: Int = 0
    var byday: IntArray? = null
    var bydayNum: IntArray? = null
    var bydayCount: Int = 0
    var bymonthday: IntArray? = null
    var bymonthdayCount: Int = 0
    var byyearday: IntArray? = null
    var byyeardayCount: Int = 0
    var byweekno: IntArray? = null
    var byweeknoCount: Int = 0
    var bymonth: IntArray? = null
    var bymonthCount: Int = 0
    var bysetpos: IntArray? = null
    var bysetposCount: Int = 0

    class InvalidFormatException(s: String) : RuntimeException(s)

    private fun appendByDay(s: StringBuilder, i: Int) {
        val n = bydayNum!![i]
        if (n != 0) {
            s.append(n)
        }
        val str = day2String(byday!![i])
        s.append(str)
    }

    override fun toString(): String {
        val s = StringBuilder()
        s.append("FREQ=")
        when (freq) {
            SECONDLY -> s.append("SECONDLY")
            MINUTELY -> s.append("MINUTELY")
            HOURLY -> s.append("HOURLY")
            DAILY -> s.append("DAILY")
            WEEKLY -> s.append("WEEKLY")
            MONTHLY -> s.append("MONTHLY")
            YEARLY -> s.append("YEARLY")
        }

        if (!until.isNullOrEmpty()) {
            s.append(";UNTIL=")
            s.append(until)
        }

        if (count != 0) {
            s.append(";COUNT=")
            s.append(count)
        }

        if (interval != 0) {
            s.append(";INTERVAL=")
            s.append(interval)
        }

        if (wkst != 0) {
            s.append(";WKST=")
            s.append(day2String(wkst))
        }

        appendNumbers(s, ";BYSECOND=", bysecondCount, bysecond)
        appendNumbers(s, ";BYMINUTE=", byminuteCount, byminute)
        appendNumbers(s, ";BYSECOND=", byhourCount, byhour)

        val c = bydayCount
        if (c > 0 && byday != null && bydayNum != null) {
            s.append(";BYDAY=")
            val limit = c - 1
            for (i in 0 until limit) {
                appendByDay(s, i)
                s.append(",")
            }
            appendByDay(s, limit)
        }

        appendNumbers(s, ";BYMONTHDAY=", bymonthdayCount, bymonthday)
        appendNumbers(s, ";BYYEARDAY=", byyeardayCount, byyearday)
        appendNumbers(s, ";BYWEEKNO=", byweeknoCount, byweekno)
        appendNumbers(s, ";BYMONTH=", bymonthCount, bymonth)
        appendNumbers(s, ";BYSETPOS=", bysetposCount, bysetpos)

        return s.toString()
    }

    fun repeatsOnEveryWeekDay(): Boolean {
        if (freq != WEEKLY) return false
        val c = bydayCount
        if (c != 5 || byday == null) return false
        for (i in 0 until c) {
            val day = byday!![i]
            if (day == SU || day == SA) return false
        }
        return true
    }

    fun repeatsMonthlyOnDayCount(): Boolean {
        if (freq != MONTHLY) return false
        if (bydayCount != 1 || bymonthdayCount != 0 || bydayNum == null) return false
        if (bydayNum!![0] <= 0) return false
        return true
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EventRecurrence) return false

        return (if (startDate == null) other.startDate == null else startDate == other.startDate) &&
                freq == other.freq &&
                (if (until == null) other.until == null else until == other.until) &&
                count == other.count &&
                interval == other.interval &&
                wkst == other.wkst &&
                arraysEqual(bysecond, bysecondCount, other.bysecond, other.bysecondCount) &&
                arraysEqual(byminute, byminuteCount, other.byminute, other.byminuteCount) &&
                arraysEqual(byhour, byhourCount, other.byhour, other.byhourCount) &&
                arraysEqual(byday, bydayCount, other.byday, other.bydayCount) &&
                arraysEqual(bydayNum, bydayCount, other.bydayNum, other.bydayCount) &&
                arraysEqual(bymonthday, bymonthdayCount, other.bymonthday, other.bymonthdayCount) &&
                arraysEqual(byyearday, byyeardayCount, other.byyearday, other.byyeardayCount) &&
                arraysEqual(byweekno, byweeknoCount, other.byweekno, other.byweeknoCount) &&
                arraysEqual(bymonth, bymonthCount, other.bymonth, other.bymonthCount) &&
                arraysEqual(bysetpos, bysetposCount, other.bysetpos, other.bysetposCount)
    }

    override fun hashCode(): Int {
        throw UnsupportedOperationException()
    }

    private fun resetFields() {
        until = null
        freq = 0
        count = 0
        interval = 0
        bysecondCount = 0
        byminuteCount = 0
        byhourCount = 0
        bydayCount = 0
        bymonthdayCount = 0
        byyeardayCount = 0
        byweeknoCount = 0
        bymonthCount = 0
        bysetposCount = 0
    }

    fun parse(recur: String) {
        resetFields()
        var parseFlags = 0
        val parts = if (ALLOW_LOWER_CASE) {
            recur.uppercase(Locale.US).split(";".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        } else {
            recur.split(";".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        }

        for (part in parts) {
            if (part.isNullOrEmpty()) continue
            val equalIndex = part.indexOf('=')
            if (equalIndex <= 0) {
                throw InvalidFormatException("Missing LHS in $part")
            }
            val lhs = part.substring(0, equalIndex)
            val rhs = part.substring(equalIndex + 1)
            if (rhs.isEmpty()) {
                throw InvalidFormatException("Missing RHS in $part")
            }

            val parser = sParsePartMap[lhs]
            if (parser == null) {
                if (lhs.startsWith("X-")) continue
                throw InvalidFormatException("Couldn't find parser for $lhs")
            } else {
                val flag = parser.parsePart(rhs, this)
                if ((parseFlags and flag) != 0) {
                    throw InvalidFormatException("Part $lhs was specified twice")
                }
                parseFlags = parseFlags or flag
            }
        }

        if ((parseFlags and PARSED_WKST) == 0) {
            wkst = MO
        }

        if ((parseFlags and PARSED_FREQ) == 0) {
            throw InvalidFormatException("Must specify a FREQ value")
        }

        if ((parseFlags and (PARSED_UNTIL or PARSED_COUNT)) == (PARSED_UNTIL or PARSED_COUNT)) {
            if (ONLY_ONE_UNTIL_COUNT) {
                throw InvalidFormatException("Must not specify both UNTIL and COUNT: $recur")
            } else {
                Log.w(TAG, "Warning: rrule has both UNTIL and COUNT: $recur")
            }
        }
    }

    abstract class PartParser {
        abstract fun parsePart(value: String, er: EventRecurrence): Int

        companion object {
            fun parseIntRange(str: String, minVal: Int, maxVal: Int, allowZero: Boolean): Int {
                var s = str
                try {
                    if (s.startsWith("+")) {
                        s = s.substring(1)
                    }
                    val `val` = s.toInt()
                    if (`val` < minVal || `val` > maxVal || (`val` == 0 && !allowZero)) {
                        throw InvalidFormatException("Integer value out of range: $s")
                    }
                    return `val`
                } catch (nfe: NumberFormatException) {
                    throw InvalidFormatException("Invalid integer value: $s")
                }
            }

            fun parseNumberList(
                listStr: String, minVal: Int, maxVal: Int, allowZero: Boolean
            ): IntArray {
                val values: IntArray
                if (!listStr.contains(",")) {
                    values = IntArray(1)
                    values[0] = parseIntRange(listStr, minVal, maxVal, allowZero)
                } else {
                    val valueStrs = listStr.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    val len = valueStrs.size
                    values = IntArray(len)
                    for (i in 0 until len) {
                        values[i] = parseIntRange(valueStrs[i], minVal, maxVal, allowZero)
                    }
                }
                return values
            }
        }
    }

    private class ParseFreq : PartParser() {
        override fun parsePart(value: String, er: EventRecurrence): Int {
            val freq = sParseFreqMap[value] ?: throw InvalidFormatException("Invalid FREQ value: $value")
            er.freq = freq
            return PARSED_FREQ
        }
    }

    private class ParseUntil : PartParser() {
        override fun parsePart(value: String, er: EventRecurrence): Int {
            if (VALIDATE_UNTIL) {
                try {
                    EventRecurrenceFormatter.parseUntilToMillis(value)
                } catch (e: Exception) {
                    throw InvalidFormatException("Invalid UNTIL value: $value")
                }
            }
            er.until = value
            return PARSED_UNTIL
        }
    }

    private class ParseCount : PartParser() {
        override fun parsePart(value: String, er: EventRecurrence): Int {
            er.count = parseIntRange(value, Int.MIN_VALUE, Int.MAX_VALUE, true)
            if (er.count < 0) {
                Log.d(TAG, "Invalid Count. Forcing COUNT to 1 from $value")
                er.count = 1
            }
            return PARSED_COUNT
        }
    }

    private class ParseInterval : PartParser() {
        override fun parsePart(value: String, er: EventRecurrence): Int {
            er.interval = parseIntRange(value, Int.MIN_VALUE, Int.MAX_VALUE, true)
            if (er.interval < 1) {
                Log.d(TAG, "Invalid Interval. Forcing INTERVAL to 1 from $value")
                er.interval = 1
            }
            return PARSED_INTERVAL
        }
    }

    private class ParseBySecond : PartParser() {
        override fun parsePart(value: String, er: EventRecurrence): Int {
            val bysecond = parseNumberList(value, 0, 59, true)
            er.bysecond = bysecond
            er.bysecondCount = bysecond.size
            return PARSED_BYSECOND
        }
    }

    private class ParseByMinute : PartParser() {
        override fun parsePart(value: String, er: EventRecurrence): Int {
            val byminute = parseNumberList(value, 0, 59, true)
            er.byminute = byminute
            er.byminuteCount = byminute.size
            return PARSED_BYMINUTE
        }
    }

    private class ParseByHour : PartParser() {
        override fun parsePart(value: String, er: EventRecurrence): Int {
            val byhour = parseNumberList(value, 0, 23, true)
            er.byhour = byhour
            er.byhourCount = byhour.size
            return PARSED_BYHOUR
        }
    }

    private class ParseByDay : PartParser() {
        override fun parsePart(value: String, er: EventRecurrence): Int {
            val byday: IntArray
            val bydayNum: IntArray
            val bydayCount: Int

            if (!value.contains(",")) {
                bydayCount = 1
                byday = IntArray(1)
                bydayNum = IntArray(1)
                parseWday(value, byday, bydayNum, 0)
            } else {
                val wdays = value.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                val len = wdays.size
                bydayCount = len
                byday = IntArray(len)
                bydayNum = IntArray(len)
                for (i in 0 until len) {
                    parseWday(wdays[i], byday, bydayNum, i)
                }
            }
            er.byday = byday
            er.bydayNum = bydayNum
            er.bydayCount = bydayCount
            return PARSED_BYDAY
        }

        private fun parseWday(str: String, byday: IntArray, bydayNum: IntArray, index: Int) {
            val wdayStrStart = str.length - 2
            val wdayStr: String

            if (wdayStrStart > 0) {
                val numPart = str.substring(0, wdayStrStart)
                val num = parseIntRange(numPart, -53, 53, false)
                bydayNum[index] = num
                wdayStr = str.substring(wdayStrStart)
            } else {
                wdayStr = str
            }
            val wday = sParseWeekdayMap[wdayStr] ?: throw InvalidFormatException("Invalid BYDAY value: $str")
            byday[index] = wday
        }
    }

    private class ParseByMonthDay : PartParser() {
        override fun parsePart(value: String, er: EventRecurrence): Int {
            val bymonthday = parseNumberList(value, -31, 31, false)
            er.bymonthday = bymonthday
            er.bymonthdayCount = bymonthday.size
            return PARSED_BYMONTHDAY
        }
    }

    private class ParseByYearDay : PartParser() {
        override fun parsePart(value: String, er: EventRecurrence): Int {
            val byyearday = parseNumberList(value, -366, 366, false)
            er.byyearday = byyearday
            er.byyeardayCount = byyearday.size
            return PARSED_BYYEARDAY
        }
    }

    private class ParseByWeekNo : PartParser() {
        override fun parsePart(value: String, er: EventRecurrence): Int {
            val byweekno = parseNumberList(value, -53, 53, false)
            er.byweekno = byweekno
            er.byweeknoCount = byweekno.size
            return PARSED_BYWEEKNO
        }
    }

    private class ParseByMonth : PartParser() {
        override fun parsePart(value: String, er: EventRecurrence): Int {
            val bymonth = parseNumberList(value, 1, 12, false)
            er.bymonth = bymonth
            er.bymonthCount = bymonth.size
            return PARSED_BYMONTH
        }
    }

    private class ParseBySetPos : PartParser() {
        override fun parsePart(value: String, er: EventRecurrence): Int {
            val bysetpos = parseNumberList(value, Int.MIN_VALUE, Int.MAX_VALUE, true)
            er.bysetpos = bysetpos
            er.bysetposCount = bysetpos.size
            return PARSED_BYSETPOS
        }
    }

    private class ParseWkst : PartParser() {
        override fun parsePart(value: String, er: EventRecurrence): Int {
            val wkst = sParseWeekdayMap[value] ?: throw InvalidFormatException("Invalid WKST value: $value")
            er.wkst = wkst
            return PARSED_WKST
        }
    }
}
