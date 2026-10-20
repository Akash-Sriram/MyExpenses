package org.totschnig.myexpenses.provider

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import junit.framework.Assert.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.totschnig.myexpenses.MyApplication
import org.totschnig.myexpenses.preference.PrefKey
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import java.util.Random
import java.util.TimeZone

/**
 * This test can be run from the command line with different timezones
 * TZ=Etc/GMT+5 ./gradlew testAcraDebugUnitTest --tests org.totschnig.myexpenses.provider.DateCalculationTest
 */
@RunWith(RobolectricTestRunner::class)
class DateCalculationTest {

    // Contains an SQLite database, used as test data
    private var mDb: SQLiteDatabase? = null

    private val TABLE = "test_dates"
    private val KEY_DATE = "date"

    @Before
    fun setUp() {
        val targetContext = RuntimeEnvironment.getApplication()
        WorkManagerTestInitHelper.initializeTestWorkManager(targetContext)
        mDb = MyDbHelper(targetContext).writableDatabase
        val v = ContentValues()
        for (year in 2020 until 2032) {
            var month = 11
            var day = 26
            do {
                val random = Random()
                val hour = random.nextInt(24)
                val minute = random.nextInt(60)
                v.put(KEY_DATE, LocalDateTime.of(year, month + 1, day, hour, minute).atZone(ZoneId.systemDefault()).toEpochSecond())
                mDb!!.insertOrThrow(TABLE, null, v)
                day++
                if (day == 32) {
                    month = 0
                    day = 1
                }
            } while (day != 7)
        }
    }

    @After
    fun cleanUp() {
        mDb?.close()
        mDb = null
    }

    @Test
    fun testDateCalculationsForWeekGroupsWithAllWeekDays() {
        val timeZone = TimeZone.getDefault().displayName
        println(timeZone)
        for (j in 1..7) {
            doTheTest(timeZone, j)
        }
    }

    private fun doTheTest(timeZone: String, configuredWeekStart: Int) {
        (ApplicationProvider.getApplicationContext<MyApplication>()).appComponent.prefHandler()
            .putString(PrefKey.GROUP_WEEK_STARTS, configuredWeekStart.toString())
        DatabaseConstants.buildLocalized(Locale.getDefault(), RuntimeEnvironment.getApplication() as MyApplication)
        assertEquals(configuredWeekStart, DatabaseConstants.weekStartsOn)
        val projection = arrayOf(
            "${DatabaseConstants.yearOfWeekStart} AS year",
            "${DatabaseConstants.week} AS week",
            "${DatabaseConstants.weekStart} AS week_start",
            KEY_DATE
        )
        val c: Cursor = mDb!!.query(
            TABLE,
            projection,
            null, null, null, null, null
        )
        assertEquals(12 * 12, c.count)
        c.moveToFirst()
        while (!c.isAfterLast) {
            val year = c.getInt(0)
            val week = c.getInt(1)
            val weekStart = c.getString(2)
            val dayOfYearOfWeekStart = getDayOfYearFromDate(weekStart)
            val unixTimeStamp = c.getLong(3)
            val date = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(Instant.ofEpochSecond(unixTimeStamp).atZone(ZoneId.systemDefault()))
            val weekStartFromGroupSqlExpression = DbUtils.weekStartFromGroupSqlExpression(year, week)
            val check = mDb!!.query(
                TABLE,
                arrayOf(weekStartFromGroupSqlExpression),
                null, null, null, null, null
            )
            check.moveToFirst()
            val weekStartFromGroup = check.getString(0)
            val dayOfYearOfWeekStartFromGroup = getDayOfYearFromDate(weekStartFromGroup)
            assertEquals(
                String.format(
                    Locale.ROOT,
                    "With timezone %s and week starts on %d, for date %s (%d) comparing weekStart %s did not match weekStart from group (%d,%d) %s",
                    timeZone, configuredWeekStart, date, unixTimeStamp, weekStart, year, week, weekStartFromGroup
                ),
                dayOfYearOfWeekStart, dayOfYearOfWeekStartFromGroup
            )
            check.close()
            c.moveToNext()
        }
        c.close()
    }

    private fun getDayOfYearFromDate(date: String): Int {
        return LocalDate.parse(date).dayOfYear
    }

    private inner class MyDbHelper(context: Context) :
        SQLiteOpenHelper(context, "dateCalculationTest", null, 1) {

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE $TABLE ($KEY_DATE DATETIME not null)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}
    }
}
