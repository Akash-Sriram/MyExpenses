package org.totschnig.myexpenses.util

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.PackageManager.NameNotFoundException
import android.graphics.Bitmap
import android.graphics.drawable.InsetDrawable
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.format.DateUtils.DAY_IN_MILLIS
import android.text.style.IconMarginSpan
import android.view.SubMenu
import androidx.loader.app.LoaderManager
import org.totschnig.myexpenses.MyApplication
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.model.CurrencyEnum
import org.totschnig.myexpenses.model.CurrencyUnit
import org.totschnig.myexpenses.model.Grouping
import org.totschnig.myexpenses.preference.PrefKey
import org.totschnig.myexpenses.provider.filter.LIKE_ESCAPE_CHAR
import org.totschnig.myexpenses.util.crashreporting.CrashHandler
import org.totschnig.myexpenses.util.distrib.DistributionHelper
import org.totschnig.myexpenses.util.ui.UiUtils
import org.xmlpull.v1.XmlPullParser
import java.math.BigDecimal
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.text.DateFormat
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.Normalizer
import java.text.NumberFormat
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Currency
import java.util.Date
import java.util.GregorianCalendar
import java.util.Locale

/**
 * Util class with helper methods
 *
 * @author Michael Totschnig
 */
object Utils {
    const val PLACEHOLDER_APP_NAME = "app_name"

    @JvmStatic
    fun getCountryFromTelephonyManager(context: Context?): String? {
        val country = Locale.getDefault().country
        return if (TextUtils.isEmpty(country)) "in" else country.lowercase(Locale.ROOT)
    }

    @JvmStatic
    fun getProjectDependencies(context: Context): List<Map<String, String>> {
        val result = ArrayList<Map<String, String>>()
        addProjectDependencies(result, context.resources.getXml(R.xml.project_dependencies))
        addProjectDependencies(result, context.resources.getXml(R.xml.additional_dependencies))
        return result
    }

    private fun addProjectDependencies(result: MutableList<Map<String, String>>, xpp: XmlPullParser) {
        var eventType: Int
        try {
            eventType = xpp.eventType
            var project: MutableMap<String, String>? = null
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    if (xpp.name == "project") {
                        project = HashMap()
                    } else if (project != null) {
                        val key = xpp.name
                        xpp.next()
                        project[key] = xpp.text
                    }
                } else if (eventType == XmlPullParser.END_TAG) {
                    if (xpp.name == "project" && project != null) {
                        result.add(project)
                    }
                }
                eventType = xpp.next()
            }
        } catch (e: Exception) {
            CrashHandler.report(e)
        }
    }

    @JvmStatic
    fun getDefaultDecimalSeparator(): Char {
        var sep = '.'
        val nfDLocal = NumberFormat.getNumberInstance()
        if (nfDLocal is DecimalFormat) {
            val symbols = nfDLocal.decimalFormatSymbols
            sep = symbols.decimalSeparator
        }
        return sep
    }

    /**
     * @return the float retrieved from the string or null if parse did not succeed
     */
    @JvmStatic
    fun validateNumber(df: DecimalFormat, strFloat: String): BigDecimal? {
        val pp = ParsePosition(0)
        df.isParseBigDecimal = true
        val n = df.parse(strFloat, pp) as? BigDecimal
        return if (strFloat.length != pp.index || n == null) {
            null
        } else {
            n
        }
    }

    /**
     * @return a DecimalFormat with the number of fraction digits appropriate for
     * currency, and with the given separator, but without the currency
     * symbol appropriate for CSV and QIF export
     */
    @JvmStatic
    fun getDecimalFormat(currency: CurrencyUnit, separator: Char): DecimalFormat {
        val nf = DecimalFormat()
        val symbols = DecimalFormatSymbols()
        symbols.decimalSeparator = separator
        nf.decimalFormatSymbols = symbols
        val fractionDigits = currency.fractionDigits
        nf.minimumFractionDigits = fractionDigits
        nf.maximumFractionDigits = fractionDigits
        nf.isGroupingUsed = false
        return nf
    }

    /**
     * utility method that calls formatter for date
     *
     * @param date unixEpoch
     * @return formatted string
     */
    @JvmStatic
    fun convDateTime(date: Long, format: DateFormat): String {
        return format.format(Date(date * 1000L))
    }

    @JvmStatic
    fun getSaveDefault(): Currency {
        return try {
            Currency.getInstance(Locale.getDefault())
        } catch (ex: Exception) {
            Currency.getInstance(Locale.US)
        }
    }

    @JvmStatic
    fun isIntentAvailable(context: Context, intent: Intent): Boolean {
        return intent.resolveActivity(context.packageManager) != null
    }

    @JvmStatic
    fun isIntentReceiverAvailable(context: Context, intent: Intent): Boolean {
        val packageManager = context.packageManager
        val list = packageManager.queryBroadcastReceivers(intent, 0)
        return list.isNotEmpty()
    }

    @JvmStatic
    fun isComAndroidVendingInstalled(context: Context): Boolean {
        val pm = context.packageManager
        return try {
            pm.getPackageInfo("com.android.vending", PackageManager.GET_ACTIVITIES)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    @JvmStatic
    fun getDaysSinceInstall(context: Context): Long {
        return (System.currentTimeMillis() - getInstallTime(context)) / DAY_IN_MILLIS
    }

    @JvmStatic
    fun getInstallTime(context: Context): Long {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime
        } catch (e: NameNotFoundException) {
            0L
        }
    }

    @JvmStatic
    fun getDaysSinceUpdate(context: Context): Long {
        return try {
            (System.currentTimeMillis() - context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime) / DAY_IN_MILLIS
        } catch (e: NameNotFoundException) {
            0L
        }
    }

    @JvmStatic
    fun getFromExtra(extras: Bundle?, key: String, defaultValue: Long): Long {
        return extras?.getLong(key, defaultValue) ?: defaultValue
    }

    @SuppressLint("DefaultLocale")
    @JvmStatic
    fun toLocalizedString(i: Int): String {
        return String.format("%d", i)
    }

    @JvmStatic
    fun md5(s: String?): String {
        if (s == null) return ""
        return try {
            val digest = MessageDigest.getInstance("MD5")
            digest.update(s.toByteArray())
            val messageDigest = digest.digest()
            val hexString = StringBuilder()
            for (b in messageDigest) {
                hexString.append(Integer.toHexString(0xFF and b.toInt()))
            }
            hexString.toString()
        } catch (e: NoSuchAlgorithmException) {
            CrashHandler.report(e)
            ""
        }
    }

    @JvmStatic
    fun getFrameworkDateFormatSafe(context: Context?): DateFormat {
        val ctx = context ?: MyApplication.instance
        return try {
            android.text.format.DateFormat.getDateFormat(ctx)
        } catch (e: Exception) {
            CrashHandler.report(e)
            DateFormat.getDateInstance(DateFormat.SHORT, localeFromContext(ctx))
        }
    }

    @SuppressLint("SimpleDateFormat")
    @JvmStatic
    fun getDateFormatSafe(context: Context?): DateFormat {
        val ctx = context ?: MyApplication.instance
        val custom = (ctx.applicationContext as MyApplication).appComponent.prefHandler().getString(PrefKey.CUSTOM_DATE_FORMAT, "")
        if (!TextUtils.isEmpty(custom)) {
            try {
                return SimpleDateFormat(custom, localeFromContext(ctx))
            } catch (e: Exception) {
                CrashHandler.report(e)
            }
        }
        return getFrameworkDateFormatSafe(ctx)
    }

    @JvmStatic
    fun localizedYearLessDateFormat(context: Context?): DateFormat {
        val ctx = context ?: MyApplication.instance
        val dateFormat = getDateFormatSafe(ctx)
        return if (dateFormat is SimpleDateFormat) {
            val contextPattern = dateFormat.toPattern()
            val yearLessPattern = contextPattern.replace("\\W?[Yy]+\\W?".toRegex(), "")
            SimpleDateFormat(yearLessPattern, localeFromContext(ctx))
        } else {
            dateFormat
        }
    }

    @JvmStatic
    fun localeFromContext(context: Context?): Locale {
        val ctx = context ?: MyApplication.instance
        return ctx.resources.configuration.locales[0]
    }

    @JvmStatic
    fun ensureDateFormatWithShortYear(context: Context?): DateFormat {
        val ctx = context ?: MyApplication.instance
        val dateFormat = getDateFormatSafe(ctx)
        return if (dateFormat is SimpleDateFormat) {
            val contextPattern = dateFormat.toPattern()
            val shortPattern = contextPattern.replace("y+".toRegex(), "yy")
            SimpleDateFormat(shortPattern, localeFromContext(ctx))
        } else {
            dateFormat
        }
    }

    @JvmStatic
    fun getTextWithAppName(ctx: Context?, resId: Int): CharSequence {
        val context = ctx ?: MyApplication.instance
        return context.getString(resId).replace("{$PLACEHOLDER_APP_NAME}", context.getString(R.string.app_name))
    }

    @JvmStatic
    fun getTellAFriendMessage(ctx: Context?): CharSequence {
        val context = ctx ?: MyApplication.instance
        return context.getString(R.string.tell_a_friend_message)
            .replace("{$PLACEHOLDER_APP_NAME}", context.getString(R.string.app_name))
            .replace("{platform}", DistributionHelper.platform)
            .replace("{website}", context.getString(R.string.website))
    }

    @JvmStatic
    fun normalize(str: String?): String {
        if (str == null) return ""
        val lower = str.lowercase(Locale.ROOT)
        return Normalizer.normalize(lower, Normalizer.Form.NFD).replace("\\p{M}".toRegex(), "")
    }

    @JvmStatic
    fun escapeSqlLikeExpression(str: String?): String {
        if (str == null) return ""
        return str
            .replace(LIKE_ESCAPE_CHAR, "$LIKE_ESCAPE_CHAR$LIKE_ESCAPE_CHAR")
            .replace("%", "$LIKE_ESCAPE_CHAR%")
            .replace("_", "${LIKE_ESCAPE_CHAR}_")
    }

    @JvmStatic
    fun printDebug(objects: Array<Any?>?): String {
        if (objects == null) {
            return "null"
        }
        val result = StringBuilder()
        for (obj in objects) {
            if (result.isNotEmpty()) result.append(",")
            result.append(obj ?: "null")
        }
        return result.toString()
    }

    @JvmStatic
    fun escapeForFileName(`in`: String): String {
        return `in`.replace("/", "").replace("\\p{Cs}".toRegex(), "").replace("\\p{So}".toRegex(), "")
    }

    @JvmStatic
    fun getFirstDayOfWeek(locale: Locale): Int {
        return GregorianCalendar(locale).firstDayOfWeek
    }

    @JvmStatic
    fun configureGroupingMenu(groupingMenu: SubMenu?, currentGrouping: Grouping) {
        if (groupingMenu == null) return
        val item = when (currentGrouping) {
            Grouping.DAY -> groupingMenu.findItem(R.id.GROUPING_DAY_COMMAND)
            Grouping.WEEK -> groupingMenu.findItem(R.id.GROUPING_WEEK_COMMAND)
            Grouping.MONTH -> groupingMenu.findItem(R.id.GROUPING_MONTH_COMMAND)
            Grouping.YEAR -> groupingMenu.findItem(R.id.GROUPING_YEAR_COMMAND)
            else -> groupingMenu.findItem(R.id.GROUPING_NONE_COMMAND)
        }
        item?.isChecked = true
    }

    @JvmStatic
    fun getGroupingFromMenuItemId(id: Int): Grouping? {
        return when (id) {
            R.id.GROUPING_NONE_COMMAND -> Grouping.NONE
            R.id.GROUPING_DAY_COMMAND -> Grouping.DAY
            R.id.GROUPING_WEEK_COMMAND -> Grouping.WEEK
            R.id.GROUPING_MONTH_COMMAND -> Grouping.MONTH
            R.id.GROUPING_YEAR_COMMAND -> Grouping.YEAR
            else -> null
        }
    }

    @JvmStatic
    fun <D> requireLoader(
        manager: LoaderManager,
        loaderId: Int,
        args: Bundle?,
        callback: LoaderManager.LoaderCallbacks<D>
    ) {
        val loader = manager.getLoader<D>(loaderId)
        if (loader != null && !loader.isReset) {
            manager.restartLoader(loaderId, args, callback)
        } else {
            manager.initLoader(loaderId, args, callback)
        }
    }

    @JvmStatic
    fun makeBulletList(ctx: Context?, lines: List<CharSequence>, icon: Int): CharSequence {
        val context = ctx ?: MyApplication.instance
        val scaledBitmap = scaledBitmap(context, icon)
        val sb = SpannableStringBuilder()
        for (i in lines.indices) {
            sb.append(withIconMargin(scaledBitmap, lines[i], i < lines.size - 1))
        }
        return sb
    }

    private fun withIconMargin(bitmap: Bitmap, text: CharSequence, withNewLine: Boolean): CharSequence {
        val spannable = SpannableString(if (withNewLine) TextUtils.concat(text, "\n") else text)
        spannable.setSpan(IconMarginSpan(bitmap, 25), 0, text.length, Spanned.SPAN_INCLUSIVE_EXCLUSIVE)
        return spannable
    }

    private fun scaledBitmap(ctx: Context, icon: Int): Bitmap {
        val drawable = InsetDrawable(UiUtils.getTintedDrawableForContext(ctx, icon), 0, 20, 0, 0)
        val bitmap = UiUtils.drawableToBitmap(drawable)
        return Bitmap.createScaledBitmap(bitmap, (bitmap.width * 0.5).toInt(), (bitmap.height * 0.5).toInt(), true)
    }

    @JvmStatic
    fun getSimpleClassNameFromComponentName(componentName: ComponentName): String {
        val className = componentName.shortClassName
        return className.substring(className.lastIndexOf(".") + 1)
    }

    @JvmStatic
    fun getCause(e: Throwable): Throwable {
        var result = e
        var cause: Throwable?
        while (result.cause.also { cause = it } != null && result !== cause) {
            result = cause!!
        }
        return result
    }

    @JvmStatic
    fun isKnownCurrency(currencyCode: String): Boolean {
        return try {
            CurrencyEnum.valueOf(currencyCode)
            true
        } catch (e: IllegalArgumentException) {
            false
        }
    }
}
