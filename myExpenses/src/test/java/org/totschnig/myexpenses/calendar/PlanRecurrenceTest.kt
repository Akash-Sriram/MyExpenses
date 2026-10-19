package org.totschnig.myexpenses.calendar

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.totschnig.myexpenses.db2.entities.prettyTimeInfo
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class PlanRecurrenceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun testOneTimePlan() {
        val start = LocalDate.of(2026, 10, 19)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        val info = prettyTimeInfo(context, null, start)
        assertThat(info).isNotEmpty()
    }

    @Test
    fun testDailyRecurrence() {
        val start = LocalDate.of(2026, 10, 19)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        val info = prettyTimeInfo(context, "FREQ=DAILY;INTERVAL=1", start)
        assertThat(info.lowercase()).contains("daily")
    }

    @Test
    fun testWeeklyRecurrence() {
        val start = LocalDate.of(2026, 10, 19)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        val info = prettyTimeInfo(context, "FREQ=WEEKLY;INTERVAL=1;BYDAY=MO", start)
        assertThat(info.lowercase()).contains("weekly")
        assertThat(info).contains("Monday")
    }

    @Test
    fun testMonthlyRecurrence() {
        val start = LocalDate.of(2026, 10, 19)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        val info = prettyTimeInfo(context, "FREQ=MONTHLY;INTERVAL=1", start)
        assertThat(info.lowercase()).contains("monthly")
        assertThat(info).contains("19")
    }

    @Test
    fun testLastDayOfMonthRecurrence() {
        val start = LocalDate.of(2026, 10, 31)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        val info = prettyTimeInfo(
            context,
            "FREQ=MONTHLY;INTERVAL=1;BYDAY=SU,MO,TU,WE,TH,FR,SA;BYSETPOS=-1",
            start
        )
        assertThat(info.lowercase()).contains("monthly")
        assertThat(info.lowercase()).contains("last day")
    }

    @Test
    fun testYearlyRecurrence() {
        val start = LocalDate.of(2026, 10, 19)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        val info = prettyTimeInfo(context, "FREQ=YEARLY;INTERVAL=1", start)
        assertThat(info.lowercase()).contains("yearly")
    }

    @Test
    fun testRecurrenceWithUntil() {
        val start = LocalDate.of(2026, 10, 19)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        val info = prettyTimeInfo(context, "FREQ=DAILY;INTERVAL=1;UNTIL=20261231T235959Z", start)
        assertThat(info.lowercase()).contains("daily")
        assertThat(info.lowercase()).contains("until")
    }

    @Test
    fun testParseUntilFormats() {
        assertThat(EventRecurrenceFormatter.parseUntilToMillis("20261231")).isGreaterThan(0L)
        assertThat(EventRecurrenceFormatter.parseUntilToMillis("2026-12-31")).isGreaterThan(0L)
        assertThat(EventRecurrenceFormatter.parseUntilToMillis("20261231T235959Z")).isGreaterThan(0L)
        assertThat(EventRecurrenceFormatter.parseUntilToMillis("2026-12-31T23:59:59Z")).isGreaterThan(0L)
        assertThat(EventRecurrenceFormatter.parseUntilToMillis("invalid")).isEqualTo(0L)
    }
}
