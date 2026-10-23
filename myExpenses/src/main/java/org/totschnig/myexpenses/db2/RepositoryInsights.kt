package org.totschnig.myexpenses.db2

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.Dispatchers
import org.totschnig.myexpenses.provider.KEY_ACCOUNTID
import org.totschnig.myexpenses.provider.KEY_AMOUNT
import org.totschnig.myexpenses.provider.KEY_CATID
import org.totschnig.myexpenses.provider.KEY_COLOR
import org.totschnig.myexpenses.provider.KEY_DATE
import org.totschnig.myexpenses.provider.KEY_LABEL
import org.totschnig.myexpenses.provider.KEY_PAYEEID
import org.totschnig.myexpenses.provider.KEY_ROWID
import org.totschnig.myexpenses.provider.TABLE_ACCOUNTS
import org.totschnig.myexpenses.provider.TABLE_CATEGORIES
import org.totschnig.myexpenses.provider.TABLE_PAYEES
import org.totschnig.myexpenses.provider.TABLE_TRANSACTIONS
import androidx.core.database.getIntOrNull
import org.totschnig.myexpenses.provider.useAndMapToList
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class CashflowSummary(
    val inflowPaise: Long = 0L,
    val outflowPaise: Long = 0L,
    val netSavingsPaise: Long = 0L,
    val savingsRatePercent: Float = 0f,
    val dailyAveragePaise: Long = 0L,
    val daysInMonth: Int = 30,
    val daysElapsed: Int = 30,
    val previousMonthOutflowPaise: Long = 0L,
    val monthOverMonthChangePercent: Float? = null
)

data class MonthlyTrendItem(
    val yearMonth: YearMonth,
    val monthShortName: String,
    val yearShort: String,
    val expensePaise: Long,
    val isSelected: Boolean
)

data class CategorySpendItem(
    val catId: Long,
    val label: String,
    val color: Int?,
    val totalPaise: Long,
    val count: Int,
    val percentOfTotal: Float
)

data class MerchantSpendItem(
    val name: String,
    val totalPaise: Long,
    val count: Int,
    val percentOfTotal: Float
)

data class InsightAccount(
    val id: Long,
    val label: String,
    val currency: String
)

@Singleton
class RepositoryInsights @Inject constructor(
    private val openHelper: SupportSQLiteOpenHelper,
    private val coroutineDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    private val _invalidation = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    fun invalidate() {
        _invalidation.tryEmit(Unit)
    }

    private val invalidationSignal: Flow<Unit> = _invalidation.onStart { emit(Unit) }

    fun getAccounts(): Flow<List<InsightAccount>> = invalidationSignal.map {
        val db = openHelper.readableDatabase
        db.query(
            "SELECT _id, label, currency FROM $TABLE_ACCOUNTS WHERE sealed = 0 ORDER BY label COLLATE NOCASE ASC",
            emptyArray()
        ).useAndMapToList { cursor ->
            InsightAccount(
                id = cursor.getLong(0),
                label = cursor.getString(1),
                currency = cursor.getString(2)
            )
        }
    }.flowOn(coroutineDispatcher)

    fun getCashflowSummary(yearMonth: YearMonth, accountId: Long?): Flow<CashflowSummary> = invalidationSignal.map {
        val db = openHelper.readableDatabase
        val zoneId = ZoneId.systemDefault()
        val today = LocalDate.now()

        val startOfMonthSecs = yearMonth.atDay(1).atStartOfDay(zoneId).toEpochSecond()
        val endOfMonthSecs = yearMonth.atEndOfMonth().atTime(23, 59, 59).atZone(zoneId).toEpochSecond()

        val prevMonth = yearMonth.minusMonths(1)
        val startOfPrevSecs = prevMonth.atDay(1).atStartOfDay(zoneId).toEpochSecond()
        val endOfPrevSecs = prevMonth.atEndOfMonth().atTime(23, 59, 59).atZone(zoneId).toEpochSecond()

        // 1. Current Month Totals
        val accClause = if (accountId != null && accountId > 0) " AND t.$KEY_ACCOUNTID = $accountId" else ""
        val curQuery = """
            SELECT 
                COALESCE(SUM(CASE WHEN t.$KEY_AMOUNT > 0 THEN t.$KEY_AMOUNT ELSE 0 END), 0) as inflow,
                COALESCE(SUM(CASE WHEN t.$KEY_AMOUNT < 0 THEN ABS(t.$KEY_AMOUNT) ELSE 0 END), 0) as outflow
            FROM $TABLE_TRANSACTIONS t
            WHERE t.$KEY_DATE BETWEEN ? AND ? 
              AND t.transfer_peer IS NULL
              $accClause
        """.trimIndent()

        var inflow = 0L
        var outflow = 0L
        db.query(curQuery, arrayOf(startOfMonthSecs.toString(), endOfMonthSecs.toString())).use {
            if (it.moveToFirst()) {
                inflow = it.getLong(0)
                outflow = it.getLong(1)
            }
        }

        // 2. Previous Month Outflow
        val prevQuery = """
            SELECT COALESCE(SUM(ABS(t.$KEY_AMOUNT)), 0)
            FROM $TABLE_TRANSACTIONS t
            WHERE t.$KEY_AMOUNT < 0 
              AND t.$KEY_DATE BETWEEN ? AND ? 
              AND t.transfer_peer IS NULL
              $accClause
        """.trimIndent()

        var prevOutflow = 0L
        db.query(prevQuery, arrayOf(startOfPrevSecs.toString(), endOfPrevSecs.toString())).use {
            if (it.moveToFirst()) {
                prevOutflow = it.getLong(0)
            }
        }

        val netSavings = inflow - outflow
        val savingsRate = if (inflow > 0) ((netSavings.toFloat() / inflow.toFloat()) * 100f).coerceIn(-100f, 100f) else 0f

        val daysInMonth = yearMonth.lengthOfMonth()
        val daysElapsed = if (yearMonth == YearMonth.from(today)) {
            today.dayOfMonth.coerceAtLeast(1)
        } else {
            daysInMonth
        }

        val dailyAvg = if (daysElapsed > 0) outflow / daysElapsed else 0L

        val momChangePercent = if (prevOutflow > 0) {
            ((outflow.toFloat() - prevOutflow.toFloat()) / prevOutflow.toFloat()) * 100f
        } else null

        CashflowSummary(
            inflowPaise = inflow,
            outflowPaise = outflow,
            netSavingsPaise = netSavings,
            savingsRatePercent = savingsRate,
            dailyAveragePaise = dailyAvg,
            daysInMonth = daysInMonth,
            daysElapsed = daysElapsed,
            previousMonthOutflowPaise = prevOutflow,
            monthOverMonthChangePercent = momChangePercent
        )
    }.flowOn(coroutineDispatcher)

    fun getMonthlySpendingTrends(selectedMonth: YearMonth, monthsBack: Int = 6, accountId: Long?): Flow<List<MonthlyTrendItem>> = invalidationSignal.map {
        val db = openHelper.readableDatabase
        val zoneId = ZoneId.systemDefault()
        val result = mutableListOf<MonthlyTrendItem>()
        val monthShortFmt = DateTimeFormatter.ofPattern("MMM", Locale.getDefault())
        val yearShortFmt = DateTimeFormatter.ofPattern("yy", Locale.getDefault())
        val accClause = if (accountId != null && accountId > 0) " AND t.$KEY_ACCOUNTID = $accountId" else ""

        for (i in (monthsBack - 1) downTo 0) {
            val targetMonth = selectedMonth.minusMonths(i.toLong())
            val startSecs = targetMonth.atDay(1).atStartOfDay(zoneId).toEpochSecond()
            val endSecs = targetMonth.atEndOfMonth().atTime(23, 59, 59).atZone(zoneId).toEpochSecond()

            val query = """
                SELECT COALESCE(SUM(ABS(t.$KEY_AMOUNT)), 0)
                FROM $TABLE_TRANSACTIONS t
                WHERE t.$KEY_AMOUNT < 0
                  AND t.$KEY_DATE BETWEEN ? AND ?
                  AND t.transfer_peer IS NULL
                  $accClause
            """.trimIndent()

            var expense = 0L
            db.query(query, arrayOf(startSecs.toString(), endSecs.toString())).use {
                if (it.moveToFirst()) {
                    expense = it.getLong(0)
                }
            }

            result.add(
                MonthlyTrendItem(
                    yearMonth = targetMonth,
                    monthShortName = targetMonth.format(monthShortFmt),
                    yearShort = targetMonth.format(yearShortFmt),
                    expensePaise = expense,
                    isSelected = targetMonth == selectedMonth
                )
            )
        }
        result
    }.flowOn(coroutineDispatcher)

    fun getTopCategories(yearMonth: YearMonth, accountId: Long?, limit: Int = 8): Flow<List<CategorySpendItem>> = invalidationSignal.map {
        val db = openHelper.readableDatabase
        val zoneId = ZoneId.systemDefault()
        val startSecs = yearMonth.atDay(1).atStartOfDay(zoneId).toEpochSecond()
        val endSecs = yearMonth.atEndOfMonth().atTime(23, 59, 59).atZone(zoneId).toEpochSecond()
        val accClause = if (accountId != null && accountId > 0) " AND t.$KEY_ACCOUNTID = $accountId" else ""

        val query = """
            SELECT c.$KEY_ROWID, c.$KEY_LABEL, c.$KEY_COLOR, 
                   COALESCE(SUM(ABS(t.$KEY_AMOUNT)), 0) as total, 
                   COUNT(*) as count
            FROM $TABLE_TRANSACTIONS t
            JOIN $TABLE_CATEGORIES c ON t.$KEY_CATID = c.$KEY_ROWID
            WHERE t.$KEY_AMOUNT < 0
              AND t.$KEY_DATE BETWEEN ? AND ?
              AND t.transfer_peer IS NULL
              $accClause
            GROUP BY c.$KEY_ROWID
            ORDER BY total DESC
            LIMIT $limit
        """.trimIndent()

        val items = db.query(query, arrayOf(startSecs.toString(), endSecs.toString())).useAndMapToList { cursor ->
            val id = cursor.getLong(0)
            val label = cursor.getString(1)
            val color = cursor.getIntOrNull(2)
            val total = cursor.getLong(3)
            val count = cursor.getInt(4)
            Triple(id, label, Triple(color, total, count))
        }

        val totalOutflow = items.sumOf { it.third.second }

        items.map { (id, label, data) ->
            val (color, total, count) = data
            val pct = if (totalOutflow > 0) (total.toFloat() / totalOutflow.toFloat()) * 100f else 0f
            CategorySpendItem(
                catId = id,
                label = label,
                color = color,
                totalPaise = total,
                count = count,
                percentOfTotal = pct
            )
        }
    }.flowOn(coroutineDispatcher)

    fun getTopMerchants(yearMonth: YearMonth, accountId: Long?, limit: Int = 8): Flow<List<MerchantSpendItem>> = invalidationSignal.map {
        val db = openHelper.readableDatabase
        val zoneId = ZoneId.systemDefault()
        val startSecs = yearMonth.atDay(1).atStartOfDay(zoneId).toEpochSecond()
        val endSecs = yearMonth.atEndOfMonth().atTime(23, 59, 59).atZone(zoneId).toEpochSecond()
        val accClause = if (accountId != null && accountId > 0) " AND t.$KEY_ACCOUNTID = $accountId" else ""

        // Aggregate child branch payees up to their root parent merchant name
        val query = """
            SELECT COALESCE(root.name, p.name) as merchant_name,
                   COALESCE(SUM(ABS(t.$KEY_AMOUNT)), 0) as total,
                   COUNT(*) as count
            FROM $TABLE_TRANSACTIONS t
            JOIN $TABLE_PAYEES p ON t.$KEY_PAYEEID = p.$KEY_ROWID
            LEFT JOIN $TABLE_PAYEES root ON p.parent_id = root.$KEY_ROWID
            WHERE t.$KEY_AMOUNT < 0
              AND t.$KEY_DATE BETWEEN ? AND ?
              AND t.transfer_peer IS NULL
              $accClause
            GROUP BY merchant_name
            ORDER BY total DESC
            LIMIT $limit
        """.trimIndent()

        val items = db.query(query, arrayOf(startSecs.toString(), endSecs.toString())).useAndMapToList { cursor ->
            val name = cursor.getString(0)
            val total = cursor.getLong(1)
            val count = cursor.getInt(2)
            Pair(name, Pair(total, count))
        }

        val totalOutflow = items.sumOf { it.second.first }

        items.map { (name, data) ->
            val (total, count) = data
            val pct = if (totalOutflow > 0) (total.toFloat() / totalOutflow.toFloat()) * 100f else 0f
            MerchantSpendItem(
                name = name,
                totalPaise = total,
                count = count,
                percentOfTotal = pct
            )
        }
    }.flowOn(coroutineDispatcher)
}
