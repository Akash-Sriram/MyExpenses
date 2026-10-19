package org.totschnig.myexpenses.testutils

import android.icu.text.DateFormat
import android.icu.text.DisplayContext
import java.util.Date
import java.util.Locale

//copied from material-components-android
fun getMonthDayOfWeekDay(timeInMillis: Long, locale: Locale?): String? {
    return getMonthWeekdayDayFormat(locale).format(Date(timeInMillis))
}

fun getMonthWeekdayDayFormat(locale: Locale?): DateFormat {
    return getAndroidFormat(DateFormat.MONTH_WEEKDAY_DAY, locale)
}

private fun getAndroidFormat(pattern: String?, locale: Locale?): DateFormat {
    val format =
        DateFormat.getInstanceForSkeleton(pattern, locale)
    format.setContext(DisplayContext.CAPITALIZATION_FOR_STANDALONE)
    return format
}
