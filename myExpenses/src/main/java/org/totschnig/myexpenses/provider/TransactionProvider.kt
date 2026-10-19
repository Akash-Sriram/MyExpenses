/*   This file is part of My Expenses.
 *   My Expenses is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   My Expenses is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with My Expenses.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.totschnig.myexpenses.provider

import android.content.ContentProviderOperation
import android.content.ContentProviderResult
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.OperationApplicationException
import android.content.UriMatcher
import android.database.Cursor
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import androidx.annotation.VisibleForTesting
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.SupportSQLiteQueryBuilder
import org.totschnig.myexpenses.BuildConfig
import org.totschnig.myexpenses.db2.basePaymentMethodProjection
import org.totschnig.myexpenses.db2.fullProjection
import org.totschnig.myexpenses.db2.localizedLabelForPaymentMethod
import org.totschnig.myexpenses.model.AccountGrouping
import org.totschnig.myexpenses.model.CrStatus
import org.totschnig.myexpenses.model.sort.Sort
import org.totschnig.myexpenses.model2.Account
import org.totschnig.myexpenses.preference.PrefKey
import org.totschnig.myexpenses.provider.DataBaseAccount.Companion.HOME_AGGREGATE_ID
import org.totschnig.myexpenses.provider.DatabaseConstants.WHERE_NOT_SPLIT
import org.totschnig.myexpenses.provider.DatabaseConstants.WHERE_SELF_OR_RELATED
import org.totschnig.myexpenses.provider.SyncContract.METHOD_APPLY_CHANGES
import org.totschnig.myexpenses.provider.filter.Operation
import org.totschnig.myexpenses.sync.json.TransactionChange
import org.totschnig.myexpenses.util.PermissionHelper.PermissionGroup.CALENDAR
import org.totschnig.myexpenses.util.Preconditions
import org.totschnig.myexpenses.util.Utils
import org.totschnig.myexpenses.util.crashreporting.CrashHandler
import org.totschnig.myexpenses.util.cursor.PlanInfoCursorWrapper
import timber.log.Timber
import java.util.Locale

open class TransactionProvider : BaseTransactionProvider() {

  override fun query(
    uri: Uri,
    projection: Array<String>?,
    selection: String?,
    selectionArgs: Array<String>?,
    sortOrder: String?
  ): Cursor? {
    var qb: SupportSQLiteQueryBuilder? = null
    val additionalWhere = StringBuilder()
    val db: SupportSQLiteDatabase = helper.readableDatabase

    var c: Cursor
    var proj = projection
    var sel = selection
    var selArgs: Array<Any>? = selectionArgs?.map { it as Any }?.toTypedArray()
    var sOrder = sortOrder

    var groupBy = uri.getQueryParameter(QUERY_PARAMETER_GROUP_BY)
    var having: String? = null
    var limit: String? = null
    var cte: String? = null
    var extras = Bundle()

    val aggregateFunc = aggregateFunction

    val uriMatch = URI_MATCHER.match(uri)
    val queryParameterLimit = uri.getQueryParameter(ContentResolver.QUERY_ARG_LIMIT)
    if (queryParameterLimit != null) {
      val queryParameterOffset = uri.getQueryParameter(ContentResolver.QUERY_ARG_OFFSET)
      limit = if (queryParameterOffset == null) queryParameterLimit else "$queryParameterOffset,$queryParameterLimit"
      log("limit %s", limit)
    }

    when (uriMatch) {
      TRANSACTIONS -> {
        if (uri.getBooleanQueryParameter(QUERY_PARAMETER_MAPPED_OBJECTS, false)) {
          if (sel != null) CrashHandler.throwOrReport("mapped object query ignores selection")
          val sql = transactionMappedObjectQuery(uri.accountSelector)
          c = db.measureAndLogQuery(uri, sql, sel, selArgs)
          return c
        }
        val hasSearch = uri.getBooleanQueryParameter(QUERY_PARAMETER_SEARCH, false)
        val forCatId = uri.getQueryParameter(KEY_CATID)
        val extended = uri.getQueryParameter(QUERY_PARAMETER_EXTENDED) != null
        val forHome = uri.getQueryParameter(KEY_ACCOUNTID) == null && uri.getQueryParameter(KEY_CURRENCY) == null && uri.getQueryParameter(KEY_PARENTID) == null
        var table = if (forHome || extended) VIEW_EXTENDED else VIEW_COMMITTED

        if (proj == null) {
          proj = if (extended) DatabaseConstants.projectionExtended else DatabaseConstants.projectionBase
        }
        if (sOrder == null) {
          sOrder = "$KEY_DATE DESC"
        }
        sOrder += ", $KEY_ROWID" + (if (sOrder.contains(" DESC")) " DESC" else " ASC")

        if (forCatId != null) {
          val selector = uri.transactionQuerySelector(CTE_SEARCH)
          sel = if (TextUtils.isEmpty(sel)) selector else (if (TextUtils.isEmpty(selector)) sel else "$sel AND $selector")
          proj = prepareProjectionForTransactions(proj, CTE_SEARCH, uri.getBooleanQueryParameter(QUERY_PARAMETER_SHORTEN_COMMENT, false), false)
          val sql = transactionListAsCTE(forCatId, if (forHome) homeCurrency else null) + " " + SupportSQLiteQueryBuilder.builder(CTE_SEARCH).columns(proj)
            .selection(computeWhere(sel, "$KEY_CATID IN (SELECT $KEY_ROWID FROM Tree )"), selArgs).groupBy(groupBy)
            .orderBy(sOrder).create().sql
          c = db.measureAndLogQuery(uri, sql, sel, selArgs)
          return c
        }
        val tableForQueryBuilder = if (hasSearch) {
          cte = buildSearchCte(table, homeCurrency, forHome)
          table = CTE_SEARCH
          CTE_SEARCH
        } else {
          if (needsExtendedJoin(proj)) {
            exchangeRateJoin(table, KEY_ACCOUNTID, homeCurrency, table) + equivalentAmountJoin(homeCurrency)
          } else {
            table
          }
        }
        val selector = uri.transactionQuerySelector(table)
        sel = if (TextUtils.isEmpty(sel)) selector else (if (TextUtils.isEmpty(selector)) sel else "$sel AND $selector")
        proj = prepareProjectionForTransactions(proj, table, uri.getBooleanQueryParameter(QUERY_PARAMETER_SHORTEN_COMMENT, false), !hasSearch)
        qb = SupportSQLiteQueryBuilder.builder(tableForQueryBuilder)
        if (uri.getQueryParameter(QUERY_PARAMETER_DISTINCT) != null) {
          qb.distinct()
        }
      }

      TRANSACTION_ID -> {
        qb = SupportSQLiteQueryBuilder.builder(VIEW_EXTENDED + equivalentAmountJoin(homeCurrency))
        proj = prepareProjectionForTransactions(proj, VIEW_EXTENDED, false, true)
        additionalWhere.append("$KEY_ROWID=").append(uri.pathSegments[1])
      }

      TRANSACTIONS_SUMS -> {
        val sql = transactionSumQuery(
          uri,
          proj!!,
          sel,
          typeWithFallBack,
          aggregateFunc,
          homeCurrency
        )
        c = db.measureAndLogQuery(uri, sql, sel, selArgs)
        return c
      }

      ARCHIVE_SUMS -> {
        return archiveSumQuery(db, uri, typeWithFallBack)
      }

      TRANSACTIONS_GROUPS -> {
        return transactionGroupsQuery(db, uri, sel, selArgs)
      }

      CATEGORIES -> {
        val mappedObjects = uri.getQueryParameter(QUERY_PARAMETER_MAPPED_OBJECTS)
        if (mappedObjects != null) {
          val sql = categoryTreeWithMappedObjects(sel!!, proj!!, mappedObjects == "2")
          return db.measureAndLogQuery(uri, sql, sel, selArgs)
        }
        if (uri.getBooleanQueryParameter(QUERY_PARAMETER_DEPTH, false)) {
          return db.measureAndLogQuery(uri, CATEGORY_DEPTH_QUERY, null, null)
        }
        if (uri.getBooleanQueryParameter(QUERY_PARAMETER_HIERARCHICAL, false)) {
          val withSum = proj != null && proj.contains(KEY_SUM)
          val withType = uri.getQueryParameter(KEY_TYPE)
          val sql = if (withSum) {
            categoryTreeWithSum(
              aggregateFunc,
              currencyContext.homeCurrencyString,
              sOrder,
              sel,
              proj,
              uri
            )
          } else {
            categoryTreeSelect(
              sOrder,
              sel,
              proj,
              null,
              null,
              uri.getQueryParameter(QUERY_PARAMETER_CATEGORY_SEPARATOR),
              withType
            )
          }
          c = db.measureAndLogQuery(uri, sql, sel, selArgs)
          c.setNotificationUri(context!!.contentResolver, uri)
          return if (withType != null && c.count == 0) wrapWithResultCompat(c, hasCategories(db)) else c
        } else {
          qb = SupportSQLiteQueryBuilder.builder(TABLE_CATEGORIES)
          val transactionId = uri.getQueryParameter(KEY_TRANSACTIONID)
          if (transactionId != null) {
            sel = "$KEY_ROWID IN (SELECT distinct $KEY_CATID FROM $TABLE_TRANSACTIONS WHERE $KEY_PARENTID = ?)"
            selArgs = arrayOf<Any>(transactionId)
          } else {
            additionalWhere.append(KEY_ROWID).append(" != ").append(SPLIT_CATID)
          }
          if (proj == null) {
            proj = arrayOf(KEY_ROWID, KEY_LABEL, KEY_PARENTID)
          }
        }
      }

      CATEGORY_ID -> {
        val rowId = uri.pathSegments[1]
        if (uri.getBooleanQueryParameter(QUERY_PARAMETER_HIERARCHICAL, false)) {
          c = db.measureAndLogQuery(uri, categoryPathFromLeave(rowId), sel, selArgs)
          c.setNotificationUri(context!!.contentResolver, uri)
          return c
        } else {
          qb = SupportSQLiteQueryBuilder.builder(TABLE_CATEGORIES)
          additionalWhere.append("$KEY_ROWID=").append(rowId)
        }
      }

      ACCOUNTS, ACCOUNTS_BASE, ACCOUNTS_MINIMAL -> {
        val minimal = uriMatch == ACCOUNTS_MINIMAL
        val sumsForDate = uri.getQueryParameter(QUERY_PARAMETER_FULL_PROJECTION_WITH_SUMS)
        val mergeAggregate = uri.getQueryParameter(QUERY_PARAMETER_MERGE_CURRENCY_AGGREGATES)
        if (mergeAggregate != null || sumsForDate != null) {
          if (sOrder == null) {
            sOrder = if (minimal) {
              KEY_LABEL
            } else {
              Sort.preferredOrderByForAccounts(PrefKey.SORT_ORDER_ACCOUNTS, prefHandler, Sort.LABEL, collate, null)
            }
            sOrder = if (uri.getBooleanQueryParameter(QUERY_PARAMETER_BALANCE_SHEET, false)) {
              "$KEY_TYPE_SORT_KEY DESC, $sOrder"
            } else {
              sortByFlag + sOrder
            }
          }
          if (proj != null) {
            CrashHandler.throwOrReport("When calling accounts cursor with sums or aggregates, projection is ignored ", TAG)
          }
          val sql = buildAccountQuery(minimal, mergeAggregate, sel, sOrder, sumsForDate)
          c = db.measureAndLogQuery(uri, sql, sel, selArgs)
          c.setNotificationUri(context!!.contentResolver, uri)
          return c
        } else {
          if (sOrder == null) {
            sOrder = if (minimal) {
              "$TABLE_ACCOUNTS.$KEY_LABEL"
            } else {
              Sort.preferredOrderByForAccounts(PrefKey.SORT_ORDER_ACCOUNTS, prefHandler, Sort.LABEL, collate, TABLE_ACCOUNTS)
            }
            sOrder = "$KEY_FLAG_SORT_KEY DESC, $sOrder"
          }
          qb = SupportSQLiteQueryBuilder.builder(if (minimal) accountWithTypeAndFlag else accountsWithExchangeRate)
          if (proj == null) {
            proj = Account.getProjection(minimal)
          }
          proj = mapAccountProjection(proj)
        }
      }

      AGGREGATE_ID -> {
        val currencyId = uri.pathSegments[2]
        if (currencyId.toLong() == HOME_AGGREGATE_ID) {
          if (uri.getBooleanQueryParameter(QUERY_PARAMETER_BREAKDOWN_BY_ACCOUNT, false)) {
            qb = SupportSQLiteQueryBuilder.builder(exchangeRateJoin(TABLE_ACCOUNTS, KEY_ROWID, homeCurrency))
            proj = arrayOf(KEY_ROWID, KEY_CURRENCY, KEY_OPENING_BALANCE, KEY_DYNAMIC, KEY_EXCHANGE_RATE)
            additionalWhere.append(KEY_EXCLUDE_FROM_TOTALS).append("=0 AND ").append(KEY_PARENTID).append(" IS NULL")
          } else {
            qb = SupportSQLiteQueryBuilder.builder(TABLE_ACCOUNTS)
            proj = aggregateHomeProjection(proj)
          }
        } else {
          qb = SupportSQLiteQueryBuilder.builder(TABLE_CURRENCIES)
          proj = aggregateCurrencyProjection(proj)
          additionalWhere.append("$TABLE_CURRENCIES.$KEY_ROWID= abs(").append(currencyId).append(")")
        }
      }

      AGGREGATE_V2 -> {
        val grouping = AccountGrouping.valueOf(uri.pathSegments[1])
        val group = uri.pathSegments[2]
        if (uri.getBooleanQueryParameter(QUERY_PARAMETER_BREAKDOWN_BY_ACCOUNT, false)) {
          qb = SupportSQLiteQueryBuilder.builder(exchangeRateJoin(TABLE_ACCOUNTS, KEY_ROWID, homeCurrency))
          proj = arrayOf(KEY_ROWID, KEY_CURRENCY, KEY_OPENING_BALANCE, KEY_DYNAMIC, KEY_EXCHANGE_RATE)
          additionalWhere.append(KEY_EXCLUDE_FROM_TOTALS).append("=0 AND ").append(KEY_PARENTID).append(" IS NULL")
          if (grouping == AccountGrouping.FLAG) {
            additionalWhere.append(" AND ").append(KEY_FLAG).append("=").append(group)
          } else if (grouping == AccountGrouping.TYPE) {
            additionalWhere.append(" AND ").append(KEY_TYPE).append("=").append(group)
          } else if (grouping == AccountGrouping.CURRENCY) {
            additionalWhere.append(" AND ").append(KEY_CURRENCY).append("='").append(group).append("'")
          }
        } else {
          if (grouping == AccountGrouping.CURRENCY) {
            qb = SupportSQLiteQueryBuilder.builder(TABLE_CURRENCIES)
            proj = aggregateCurrencyProjection(proj)
            additionalWhere.append(KEY_CODE).append("='").append(group).append("'")
          } else if (grouping == AccountGrouping.FLAG) {
            qb = SupportSQLiteQueryBuilder.builder(TABLE_ACCOUNT_FLAGS)
            proj = aggregateFlagProjection(proj)
            additionalWhere.append(KEY_ROWID).append("=").append(group)
          } else if (grouping == AccountGrouping.TYPE) {
            qb = SupportSQLiteQueryBuilder.builder(TABLE_ACCOUNT_TYPES)
            proj = aggregateTypeProjection(proj)
            additionalWhere.append(KEY_ROWID).append("=").append(group)
          } else {
            qb = SupportSQLiteQueryBuilder.builder(TABLE_ACCOUNTS)
            additionalWhere.append(KEY_EXCLUDE_FROM_TOTALS).append("=0")
            proj = aggregateHomeProjection(proj)
          }
        }
      }

      ACCOUNT_ID -> {
        qb = SupportSQLiteQueryBuilder.builder(accountsWithExchangeRate)
        additionalWhere.append("$TABLE_ACCOUNTS.$KEY_ROWID=").append(uri.pathSegments[1])
        proj = mapAccountProjection(proj)
      }

      PAYEES -> {
        if (uri.getBooleanQueryParameter(QUERY_PARAMETER_HIERARCHICAL, false)) {
          val sql = getPayeeWithDuplicatesCTE(sel, collate)
          c = db.measureAndLogQuery(uri, sql, sel, selArgs)
          c.setNotificationUri(context!!.contentResolver, uri)
          return c
        }
        if (uri.getBooleanQueryParameter(QUERY_PARAMETER_COUNT_UNUSED, false)) {
          c = db.measureAndLogQuery(uri, COUNT_UNUSED_PAYEE_QUERY, sel, selArgs)
          c.setNotificationUri(context!!.contentResolver, uri)
          return c
        }

        qb = SupportSQLiteQueryBuilder.builder(TABLE_PAYEES)
        if (sOrder == null) {
          sOrder = "$KEY_PAYEE_NAME COLLATE $collate"
        }
        if (proj == null) {
          proj = payeeProjection(TABLE_PAYEES)
        }
        additionalWhere.append("$KEY_ROWID!=$NULL_ROW_ID")
      }

      PAYEE_ID -> {
        qb = SupportSQLiteQueryBuilder.builder(TABLE_PAYEES)
        additionalWhere.append("$KEY_ROWID=").append(uri.pathSegments[1])
      }

      MAPPED_TRANSFER_ACCOUNTS -> {
        qb = SupportSQLiteQueryBuilder.builder("$TABLE_ACCOUNTS JOIN $TABLE_TRANSACTIONS ON ($KEY_TRANSFER_ACCOUNT = $TABLE_ACCOUNTS.$KEY_ROWID)")
        proj = arrayOf("DISTINCT $TABLE_ACCOUNTS.$KEY_ROWID", KEY_LABEL)
        if (sOrder == null) {
          sOrder = KEY_LABEL
        }
      }

      METHODS -> {
        qb = SupportSQLiteQueryBuilder.builder(tableForPaymentMethodQuery(proj))
        groupBy = groupByForPaymentMethodQuery(proj)
        having = havingForPaymentMethodQuery(proj)
        if (proj == null) {
          proj = fullProjection(wrappedContext)
        } else {
          proj = mapPaymentMethodProjection(proj, wrappedContext)
        }
        if (sOrder == null) {
          sOrder = localizedLabelForPaymentMethod(wrappedContext, KEY_LABEL) + " COLLATE " + collate
        }
        additionalWhere.append("$KEY_ROWID!=$NULL_ROW_ID")
      }

      MAPPED_METHODS -> {
        val localizedLabel = localizedLabelForPaymentMethod(wrappedContext, KEY_LABEL)
        qb = SupportSQLiteQueryBuilder.builder("$TABLE_METHODS JOIN $TABLE_TRANSACTIONS ON ($KEY_METHODID = $TABLE_METHODS.$KEY_ROWID)")
        proj = arrayOf("DISTINCT $TABLE_METHODS.$KEY_ROWID", "$localizedLabel AS $KEY_LABEL")
        if (sOrder == null) {
          sOrder = "$localizedLabel COLLATE $collate"
        }
      }

      METHOD_ID -> {
        qb = SupportSQLiteQueryBuilder.builder(TABLE_METHODS)
        if (proj == null) {
          proj = basePaymentMethodProjection(wrappedContext)
        }
        additionalWhere.append("$KEY_ROWID=").append(uri.pathSegments[1])
      }

      METHODS_FILTERED -> {
        val localizedLabel = localizedLabelForPaymentMethod(wrappedContext, KEY_LABEL)
        qb = SupportSQLiteQueryBuilder.builder("$TABLE_METHODS JOIN $TABLE_ACCOUNTTYES_METHODS ON ($KEY_ROWID = $KEY_METHODID)")
        proj = arrayOf(KEY_ROWID, "$localizedLabel AS $KEY_LABEL", KEY_IS_NUMBERED)
        val paymentType = uri.pathSegments[2]
        val typeSelect = when (paymentType) {
          "1" -> "> -1"
          "-1" -> "< 1"
          else -> "= 0"
        }
        sel = String.format("%s.%s %s", TABLE_METHODS, KEY_TYPE, typeSelect)
        val accountTypes = uri.getQueryParameter(QUERY_PARAMETER_ACCOUNT_TYPE_LIST)!!.split(";").toTypedArray()

        sel += " AND $TABLE_ACCOUNTTYES_METHODS.type " + Operation.IN.getOp(accountTypes.size)
        selArgs = accountTypes.map { it as Any }.toTypedArray()
        if (sOrder == null) {
          sOrder = "$localizedLabel COLLATE $collate"
        }
        groupBy = KEY_ROWID
        having = "count(*) = " + accountTypes.size
      }

      ACCOUNTTYPES_METHODS -> {
        qb = SupportSQLiteQueryBuilder.builder(TABLE_ACCOUNTTYES_METHODS)
      }

      TEMPLATES -> {
        val instanceId = uri.getQueryParameter(QUERY_PARAMETER_WITH_INSTANCE)
        if (instanceId == null) {
          val selector = uri.templateQuerySelector
          sel = if (TextUtils.isEmpty(sel)) selector else (sel + (if (TextUtils.isEmpty(selector)) "" else " AND $selector"))
          qb = SupportSQLiteQueryBuilder.builder(VIEW_TEMPLATES_EXTENDED)
          if (proj == null) {
            proj = templateProjection(VIEW_TEMPLATES_EXTENDED)
          }
        } else {
          qb = SupportSQLiteQueryBuilder.builder(
            String.format(
              Locale.ROOT,
              "%1\$s LEFT JOIN %2\$s ON %1\$s.%3\$s = %4\$s AND %5\$s = %6\$s LEFT JOIN %7\$s ON %7\$s.%3\$s = %2\$s.%8\$s",
              VIEW_TEMPLATES_EXTENDED, TABLE_PLAN_INSTANCE_STATUS, KEY_ROWID, KEY_TEMPLATEID, KEY_INSTANCEID, instanceId,
              TABLE_TRANSACTIONS, KEY_TRANSACTIONID
            )
          )
          if (proj != null) {
            report("When calling templates cursor with QUERY_PARAMETER_WITH_INSTANCE, projection is ignored ")
          }
          proj = arrayOf(
            KEY_TITLE, KEY_INSTANCEID, KEY_TRANSACTIONID, KEY_COLOR, KEY_CURRENCY,
            String.format(Locale.ROOT, "coalesce(%1\$s.%2\$s, %3\$s.%2\$s) AS %2\$s", TABLE_TRANSACTIONS, KEY_AMOUNT, VIEW_TEMPLATES_EXTENDED),
            "$VIEW_TEMPLATES_EXTENDED.$KEY_ROWID AS $KEY_ROWID", KEY_SEALED
          )
        }
      }

      TEMPLATE_ID -> {
        qb = SupportSQLiteQueryBuilder.builder(VIEW_TEMPLATES_ALL)
        additionalWhere.append("$KEY_ROWID=").append(uri.pathSegments[1])
        if (proj == null) {
          proj = templateProjection(VIEW_TEMPLATES_ALL)
        }
      }

      SQLITE_SEQUENCE_TABLE -> {
        qb = SupportSQLiteQueryBuilder.builder("SQLITE_SEQUENCE")
        proj = arrayOf("seq")
        sel = "name = ?"
        selArgs = arrayOf<Any>(uri.pathSegments[1])
      }

      PLANINSTANCE_TRANSACTION_STATUS -> {
        qb = SupportSQLiteQueryBuilder.builder(TABLE_PLAN_INSTANCE_STATUS)
      }

      PLANINSTANCE_STATUS_SINGLE -> {
        qb = SupportSQLiteQueryBuilder.builder(String.format(Locale.ROOT, "%1\$s LEFT JOIN %2\$s ON %3\$s = %4\$s", TABLE_PLAN_INSTANCE_STATUS, TABLE_TRANSACTIONS, KEY_ROWID, KEY_TRANSACTIONID))
        additionalWhere.append(String.format(Locale.ROOT, "%s = %s AND %s = %s", KEY_TEMPLATEID, uri.pathSegments[1], KEY_INSTANCEID, uri.pathSegments[2]))
        proj = arrayOf(KEY_TRANSACTIONID, KEY_AMOUNT)
      }

      CURRENCIES -> {
        if (proj == null) {
          proj = arrayOf(
            KEY_ROWID, KEY_CODE, KEY_GROUPING, KEY_LABEL, KEY_USAGES, KEY_SYMBOL, KEY_FRACTION_DIGITS, KEY_COMMODITY_TYPE
          )
          qb = SupportSQLiteQueryBuilder.builder(CURRENCIES_USAGES_TABLE_EXPRESSION)
        } else {
          qb = SupportSQLiteQueryBuilder.builder(TABLE_CURRENCIES)
        }
      }

      DUAL -> {
        return db.dualQuery(proj!!)
      }

      EVENT_CACHE -> {
        qb = SupportSQLiteQueryBuilder.builder(TABLE_EVENT_CACHE)
      }

      DEBUG_SCHEMA -> {
        return db.query(SupportSQLiteQueryBuilder.builder("sqlite_master").columns(arrayOf("name", "sql")).selection("type = 'table'", emptyArray()).create())
      }

      STALE_IMAGES -> {
        qb = SupportSQLiteQueryBuilder.builder(TABLE_ATTACHMENTS)
        sel = STALE_ATTACHMENT_SELECTION
        if (proj == null) {
          proj = arrayOf(KEY_ROWID, KEY_URI)
        }
      }

      STALE_IMAGES_ID -> {
        qb = SupportSQLiteQueryBuilder.builder(TABLE_ATTACHMENTS)
        sel = "$STALE_ATTACHMENT_SELECTION AND $KEY_ROWID = ?"
        selArgs = arrayOf<Any>(uri.pathSegments[1])
        proj = arrayOf(KEY_URI)
      }

      CHANGES -> {
        sel = "$KEY_ACCOUNTID = ? AND $KEY_SYNC_SEQUENCE_LOCAL = ?"
        selArgs = arrayOf<Any>(uri.getQueryParameter(KEY_ACCOUNTID)!!, uri.getQueryParameter(KEY_SYNC_SEQUENCE_LOCAL)!!)
        qb = SupportSQLiteQueryBuilder.builder(VIEW_CHANGES_EXTENDED)
        if (proj == null) {
          proj = TransactionChange.PROJECTION
        }
      }

      SETTINGS -> {
        qb = SupportSQLiteQueryBuilder.builder(TABLE_SETTINGS)
      }

      CURRENCIES_CODE -> {
        qb = SupportSQLiteQueryBuilder.builder(TABLE_CURRENCIES)
        sel = "$KEY_CODE = ?"
        selArgs = arrayOf<Any>(uri.pathSegments[1])
      }

      AUTOFILL -> {
        qb = SupportSQLiteQueryBuilder.builder(VIEW_EXTENDED)
        sel = "$KEY_ROWID= (SELECT max($KEY_ROWID) FROM $TABLE_TRANSACTIONS WHERE $WHERE_NOT_SPLIT AND $KEY_PAYEEID IN (?, (SELECT $KEY_PARENTID FROM $TABLE_PAYEES WHERE $KEY_ROWID = ?)))"
        val id = uri.pathSegments[1]
        selArgs = arrayOf<Any>(id, id)
      }

      ACCOUNT_EXCHANGE_RATE -> {
        qb = SupportSQLiteQueryBuilder.builder(TABLE_ACCOUNT_EXCHANGE_RATES)
        additionalWhere.append("$KEY_ACCOUNTID=").append(uri.pathSegments[1])
          .append(" AND $KEY_CURRENCY_SELF='").append(uri.pathSegments[2]).append("'")
          .append(" AND $KEY_CURRENCY_OTHER='").append(uri.pathSegments[3]).append("'")
        proj = arrayOf(KEY_EXCHANGE_RATE)
      }

      BUDGETS -> {
        qb = SupportSQLiteQueryBuilder.builder(budgetTableJoin)
        proj = proj ?: budgetProjection
      }

      BUDGET_ID -> {
        qb = SupportSQLiteQueryBuilder.builder(budgetTableJoin)
        proj = proj ?: budgetProjection
        additionalWhere.append("$TABLE_BUDGETS.$KEY_ROWID=").append(uri.pathSegments[1])
      }

      ACCOUNT_DEFAULT_BUDGET_ALLOCATIONS -> {
        return budgetAllocationGroupsQuery(db, uri)
      }

      BUDGET_FOR_PERIOD -> {
        val sql = if (proj != null && proj.size == 1 && proj[0] == KEY_BUDGET) totalBudgetAllocation(uri) else budgetAllocation(uri)
        c = db.measureAndLogQuery(uri, sql, null, null)
        return c
      }

      TAGS -> {
        val withCount = uri.getBooleanQueryParameter(QUERY_PARAMETER_WITH_COUNT, false)
        val withFilter = uri.getBooleanQueryParameter(QUERY_PARAMETER_WITH_FILTER, false)
        val tableName = if (withCount) {
          val t = "$TABLE_TAGS LEFT JOIN $TABLE_TRANSACTIONS_TAGS ON ($KEY_ROWID = $KEY_TAGID)"
          proj = arrayOf(KEY_ROWID, KEY_LABEL, KEY_COLOR, String.format("count(%s) AS %s", KEY_TAGID, KEY_COUNT))
          groupBy = KEY_ROWID
          t
        } else if (withFilter) {
          val t = "$TABLE_TAGS LEFT JOIN $TABLE_TRANSACTIONS_TAGS ON ($TABLE_TAGS.$KEY_ROWID = $KEY_TAGID) LEFT JOIN $TABLE_TRANSACTIONS ON ($TABLE_TRANSACTIONS.$KEY_ROWID = $KEY_TRANSACTIONID)"
          proj = arrayOf("$TABLE_TAGS.$KEY_ROWID", KEY_LABEL, KEY_COLOR)
          groupBy = "$TABLE_TAGS.$KEY_ROWID"
          t
        } else {
          TABLE_TAGS
        }
        qb = SupportSQLiteQueryBuilder.builder(tableName)
      }

      TRANSACTIONS_TAGS -> {
        qb = SupportSQLiteQueryBuilder.builder("$TABLE_TRANSACTIONS_TAGS LEFT JOIN $TABLE_TAGS ON ($KEY_TAGID = $KEY_ROWID)")
      }

      TEMPLATES_TAGS -> {
        qb = SupportSQLiteQueryBuilder.builder("$TABLE_TEMPLATES_TAGS LEFT JOIN $TABLE_TAGS ON ($KEY_TAGID = $KEY_ROWID)")
      }

      ACCOUNTS_TAGS -> {
        qb = SupportSQLiteQueryBuilder.builder("$TABLE_ACCOUNTS_TAGS LEFT JOIN $TABLE_TAGS ON ($KEY_TAGID = $KEY_ROWID)")
      }

      TAG_ID -> {
        qb = SupportSQLiteQueryBuilder.builder(TABLE_TAGS)
        additionalWhere.append("$KEY_ROWID=").append(uri.pathSegments[1])
      }

      DEBTS -> {
        val date = uri.getQueryParameter(KEY_DATE)
        val dateExpression = if (date != null) "cast(strftime('%s', '$date', 'localtime', 'start of day', '+1 day', '-1 second', 'utc') as integer)" else null
        cte = amountCteForDebts(homeCurrency, dateExpression)
        if (dateExpression != null) {
          additionalWhere.append(KEY_DATE).append(" <= ").append(dateExpression)
        }
        val transactionId = uri.getQueryParameter(KEY_TRANSACTIONID)
        if (proj == null) {
          proj = debtProjection(transactionId, true)
        }
        qb = SupportSQLiteQueryBuilder.builder(DEBT_PAYEE_JOIN)
      }

      DEBT_ID -> {
        if (proj == null) {
          proj = debtProjection(null, false)
        }
        qb = SupportSQLiteQueryBuilder.builder(DEBT_PAYEE_JOIN)
        additionalWhere.append("$TABLE_DEBTS.$KEY_ROWID=").append(uri.pathSegments[1])
      }

      BANKS -> {
        proj = BANK_PROJECTION
        qb = SupportSQLiteQueryBuilder.builder(TABLE_BANKS)
      }

      TRANSACTION_ATTRIBUTES -> {
        qb = SupportSQLiteQueryBuilder.builder(TRANSACTION_ATTRIBUTES_JOIN)
      }

      ACCOUNT_ATTRIBUTES -> {
        qb = SupportSQLiteQueryBuilder.builder(ACCOUNT_ATTRIBUTES_JOIN)
      }

      ATTACHMENTS -> {
        qb = SupportSQLiteQueryBuilder.builder(TABLE_ATTACHMENTS)
        if (sel == null) {
          val uuidSelection = uri.getQueryParameter(KEY_UUID)
          sel = LIVE_ATTACHMENT_SELECTION(uuidSelection != null)
          selArgs = if (uuidSelection != null) arrayOf(uuidSelection) else null
        }
      }

      TRANSACTION_ATTACHMENTS -> {
        if (proj == null) {
          proj = arrayOf(KEY_URI)
        }
        qb = SupportSQLiteQueryBuilder.builder("$TABLE_TRANSACTION_ATTACHMENTS LEFT JOIN $TABLE_ATTACHMENTS ON ($KEY_ATTACHMENT_ID = $KEY_ROWID)")
      }

      BUDGET_ALLOCATIONS -> {
        qb = SupportSQLiteQueryBuilder.builder(TABLE_BUDGET_ALLOCATIONS)
      }

      PRICES -> {
        qb = SupportSQLiteQueryBuilder.builder(VIEW_PRIORITIZED_PRICES)
        val commodity = uri.getQueryParameter(KEY_COMMODITY)
        val currency = uri.getQueryParameter(KEY_CURRENCY)
        if (commodity != null) {
          sel = "$KEY_CURRENCY = ? AND $KEY_COMMODITY= ?"
          selArgs = arrayOf<Any>(currency!!, commodity)
          extras = oldestTransactionForCurrency(db, commodity)
        }
      }

      LATEST_PRICES -> {
        return db.measureAndLogQuery(uri, latestPricesQuery(uri), sel, selArgs)
      }

      DYNAMIC_CURRENCIES -> {
        val hCurrency = homeCurrency
        var dynamicFilter = dynamicExchangeRatesDefault
        if (dynamicFilter == KEY_DYNAMIC) {
          dynamicFilter = "a.$dynamicFilter"
        }
        val sql = "WITH pairs AS (" +
          "SELECT a.$KEY_CURRENCY AS commodity, " +
          "COALESCE(p.$KEY_CURRENCY, ?) AS base " +
          "FROM $TABLE_ACCOUNTS a " +
          "LEFT JOIN $TABLE_ACCOUNTS p ON a.$KEY_PARENTID = p.$KEY_ROWID " +
          "WHERE $dynamicFilter) " +
          "SELECT DISTINCT commodity, base FROM pairs WHERE commodity != base"
        return db.measureAndLogQuery(uri, sql, null, arrayOf<Any>(hCurrency))
      }

      ACCOUNT_TYPES -> {
        proj = arrayOf("*", "(SELECT count(*) FROM $TABLE_ACCOUNTS WHERE $KEY_TYPE = $TABLE_ACCOUNT_TYPES.$KEY_ROWID) AS $KEY_COUNT")
        qb = SupportSQLiteQueryBuilder.builder(TABLE_ACCOUNT_TYPES)
      }

      ACCOUNT_TYPE_ID -> {
        qb = SupportSQLiteQueryBuilder.builder(TABLE_ACCOUNT_TYPES)
        additionalWhere.append("$KEY_ROWID=").append(uri.pathSegments[1])
      }

      ACCOUNT_FLAGS -> {
        proj = arrayOf("*", "(SELECT count(*) FROM $TABLE_ACCOUNTS WHERE $KEY_FLAG = $TABLE_ACCOUNT_FLAGS.$KEY_ROWID) AS $KEY_COUNT")
        qb = SupportSQLiteQueryBuilder.builder(TABLE_ACCOUNT_FLAGS)
      }

      else -> throw unknownUri(uri)
    }

    @Suppress("UNCHECKED_CAST")
    c = qb.measureAndLogQuery(uri, db, proj, computeWhere(sel, additionalWhere.toString()), selArgs, groupBy, having, sOrder, limit, cte)

    c = wrapWithResultCompat(c, extras)

    val withPlanInfo = uri.getQueryParameter(QUERY_PARAMETER_WITH_PLAN_INFO)
    if (uriMatch == TEMPLATES && withPlanInfo != null) {
      c = PlanInfoCursorWrapper(wrappedContext, c, sOrder == null, withPlanInfo == "2" || CALENDAR.hasPermission(context!!))
    }
    c.setNotificationUri(context!!.contentResolver, uri)
    return c
  }

  override fun getType(uri: Uri): String? = null

  private fun unknownUri(uri: Uri): IllegalArgumentException = IllegalArgumentException("Unknown URL $uri")

  override fun insert(uri: Uri, values: ContentValues?): Uri? {
    log("INSERT Uri: %s, values: %s", uri, values)
    val db = helper.writableDatabase
    val id: Long
    val newUri: Uri
    val uriMatch = URI_MATCHER.match(uri)
    maybeSetDirty(uriMatch)
    val v = values ?: ContentValues()
    when (uriMatch) {
      TRANSACTIONS -> {
        val equivalentAmount = v.getAsLong(KEY_EQUIVALENT_AMOUNT)
        v.remove(KEY_EQUIVALENT_AMOUNT)
        val tagList = v.getAsString(KEY_TAGLIST)
        v.remove(KEY_TAGLIST)
        if (!v.containsKey(KEY_UUID)) {
          throw IllegalStateException("required value uuid is missing")
        }
        db.beginTransaction()
        try {
          id = db.insert(TABLE_TRANSACTIONS, v)
          if (equivalentAmount != null) {
            db.insertEquivalentAmount(id, equivalentAmount)
          }
          db.saveTransactionTags(id, tagList, true)
          db.setTransactionSuccessful()
        } finally {
          db.endTransaction()
        }
        newUri = ContentUris.withAppendedId(TRANSACTIONS_URI, id)
      }

      ACCOUNTS -> {
        Preconditions.checkArgument(!v.containsKey(KEY_GROUPING))
        id = db.insert(TABLE_ACCOUNTS, v)
        newUri = ContentUris.withAppendedId(ACCOUNTS_URI, id)
      }

      METHODS -> {
        id = db.insert(TABLE_METHODS, v)
        newUri = ContentUris.withAppendedId(METHODS_URI, id)
      }

      ACCOUNTTYPES_METHODS -> {
        id = db.insert(TABLE_ACCOUNTTYES_METHODS, v)
        newUri = ContentUris.withAppendedId(ACCOUNTTYPES_METHODS_URI, id)
      }

      TEMPLATES -> {
        val tagList = v.getAsString(KEY_TAGLIST)
        v.remove(KEY_TAGLIST)
        db.beginTransaction()
        try {
          id = db.insert(TABLE_TEMPLATES, v)
          db.saveTransactionTags(id, tagList, true, true)
          db.setTransactionSuccessful()
        } finally {
          db.endTransaction()
        }
        newUri = ContentUris.withAppendedId(TEMPLATES_URI, id)
      }

      PAYEES -> {
        id = db.insert(TABLE_PAYEES, v)
        newUri = ContentUris.withAppendedId(PAYEES_URI, id)
      }

      PLANINSTANCE_TRANSACTION_STATUS -> {
        val templateId = v.getAsLong(KEY_TEMPLATEID)
        val instanceId = v.getAsLong(KEY_INSTANCEID)
        db.insert(TABLE_PLAN_INSTANCE_STATUS, CONFLICT_REPLACE, v)
        val changeUri = Uri.parse("$PLAN_INSTANCE_STATUS_URI/$templateId/$instanceId")
        notifyChange(changeUri, false)
        return changeUri
      }

      EVENT_CACHE -> {
        id = db.insert(TABLE_EVENT_CACHE, v)
        newUri = ContentUris.withAppendedId(EVENT_CACHE_URI, id)
      }

      ACCOUNT_EXCHANGE_RATE -> {
        v.put(KEY_ACCOUNTID, uri.pathSegments[1])
        v.put(KEY_CURRENCY_SELF, uri.pathSegments[2])
        v.put(KEY_CURRENCY_OTHER, uri.pathSegments[3])
        id = db.insert(TABLE_ACCOUNT_EXCHANGE_RATES, CONFLICT_REPLACE, v)
        newUri = uri
      }

      DUAL -> {
        if ("1" == uri.getQueryParameter(QUERY_PARAMETER_SYNC_BEGIN)) {
          id = pauseChangeTrigger(db)
          newUri = uri
        } else {
          throw unknownUri(uri)
        }
      }

      SETTINGS -> {
        id = db.insert(TABLE_SETTINGS, CONFLICT_REPLACE, v)
        newUri = ContentUris.withAppendedId(SETTINGS_URI, id)
      }

      BUDGETS -> {
        val budget = v.getAsLong(KEY_BUDGET)
        if (budget != null) {
          v.remove(KEY_BUDGET)
        }
        id = db.insert(TABLE_BUDGETS, v)
        if (budget != null) {
          val budgetInitialAmount = ContentValues(2)
          budgetInitialAmount.put(KEY_BUDGETID, id)
          budgetInitialAmount.put(KEY_BUDGET, budget)
          budgetInitialAmount.put(KEY_CATID, 0)
          db.insert(TABLE_BUDGET_ALLOCATIONS, budgetInitialAmount)
        }
        newUri = BaseTransactionProvider.budgetUri(id)
      }

      CURRENCIES -> {
        try {
          id = db.insert(TABLE_CURRENCIES, v)
        } catch (e: SQLiteConstraintException) {
          return null
        }
        newUri = ContentUris.withAppendedId(CURRENCIES_URI, id)
      }

      TAGS -> {
        id = db.insert(TABLE_TAGS, v)
        newUri = ContentUris.withAppendedId(TAGS_URI, id)
      }

      TRANSACTIONS_TAGS -> {
        if (callerIsNotSyncAdapter(uri)) throw IllegalArgumentException("Can only be called from sync adapter")
        db.insert(TABLE_TRANSACTIONS_TAGS, CONFLICT_IGNORE, v)
        notifyChange(uri, callerIsNotSyncAdapter(uri))
        return TRANSACTIONS_TAGS_URI
      }

      TEMPLATES_TAGS -> {
        db.insert(TABLE_TEMPLATES_TAGS, CONFLICT_IGNORE, v)
        notifyChange(uri, false)
        return TEMPLATES_TAGS_URI
      }

      ACCOUNTS_TAGS -> {
        db.insert(TABLE_ACCOUNTS_TAGS, CONFLICT_IGNORE, v)
        notifyChange(uri, false)
        return ACCOUNTS_TAGS_URI
      }

      DEBTS -> {
        id = db.insert(TABLE_DEBTS, v)
        newUri = ContentUris.withAppendedId(DEBTS_URI, id)
      }

      BANKS -> {
        id = db.insert(TABLE_BANKS, v)
        newUri = ContentUris.withAppendedId(BANKS_URI, id)
      }

      TRANSACTION_ATTRIBUTES -> {
        insertTransactionAttribute(db, v)
        notifyChange(TRANSACTIONS_ATTRIBUTES_URI, false)
        return TRANSACTIONS_ATTRIBUTES_URI
      }

      ACCOUNT_ATTRIBUTES -> {
        insertAccountAttribute(db, v)
        notifyChange(ACCOUNTS_ATTRIBUTES_URI, false)
        return ACCOUNTS_ATTRIBUTES_URI
      }

      ATTACHMENTS -> {
        id = requireAttachment(db, v.getAsString(KEY_URI), v.getAsString(KEY_UUID))
        newUri = ContentUris.withAppendedId(ATTACHMENTS_URI, id)
      }

      TRANSACTION_ATTACHMENTS -> {
        val uuid = v.getAsString(KEY_UUID)
        var attId: Long
        if (uuid != null) {
          val attachmentByUuid = findAttachmentByUuid(db, uuid)
          if (attachmentByUuid == null) {
            attId = -1
          } else {
            v.remove(KEY_UUID)
            attId = attachmentByUuid
          }
        } else {
          attId = requireAttachment(db, v.getAsString(KEY_URI), null)
          v.remove(KEY_URI)
        }
        if (attId != -1L) {
          v.put(KEY_ATTACHMENT_ID, attId)
          db.insert(TABLE_TRANSACTION_ATTACHMENTS, CONFLICT_IGNORE, v)
        }
        id = attId
        newUri = ContentUris.withAppendedId(ATTACHMENTS_URI, id)
      }

      TRANSACTION_TRANSFORM_TO_TRANSFER -> {
        id = transformToTransfer(db, uri, prefHandler.defaultTransferCategory)
        newUri = ContentUris.withAppendedId(TRANSACTIONS_URI, id)
      }

      BUDGET_ALLOCATIONS -> {
        db.insert(TABLE_BUDGET_ALLOCATIONS, v)
        return BUDGET_ALLOCATIONS_URI
      }

      PRICES -> {
        id = db.insert(TABLE_PRICES, CONFLICT_REPLACE, v)
        newUri = ContentUris.withAppendedId(PRICES_URI, id)
      }

      ACCOUNT_TYPES -> {
        id = db.insert(TABLE_ACCOUNT_TYPES, v)
        newUri = ContentUris.withAppendedId(ACCOUNT_TYPES_URI, id)
      }

      ACCOUNT_FLAGS -> {
        id = db.insert(TABLE_ACCOUNT_FLAGS, v)
        newUri = ContentUris.withAppendedId(ACCOUNT_FLAGS_URI, id)
      }

      else -> throw unknownUri(uri)
    }

    notifyChange(uri, uriMatch == TRANSACTIONS && callerIsNotSyncAdapter(uri))
    if (uriMatch == TRANSACTIONS) {
      notifyChange(ACCOUNTS_URI, false)
      notifyChange(DEBTS_URI, false)
    } else if (uriMatch == ACCOUNTS) {
      notifyAccountChange()
      notifyChange(BANKS_URI, false)
    } else if (uriMatch == DEBTS) {
      notifyChange(PAYEES_URI, false)
    } else if (uriMatch == TRANSACTION_ATTACHMENTS) {
      notifyChange(TRANSACTIONS_URI, false)
    } else if (uriMatch == TRANSACTION_TRANSFORM_TO_TRANSFER) {
      notifyChange(TRANSACTIONS_URI, false)
      notifyChange(ACCOUNTS_URI, false)
      notifyChange(DEBTS_URI, false)
    } else if (uriMatch == PRICES) {
      notifyChange(LATEST_PRICES_URI, false)
      notifyAccountChange()
    }
    return newUri
  }

  override fun delete(uri: Uri, where: String?, whereArgs: Array<String>?): Int {
    log("Delete for URL: %s", uri)
    val db = helper.writableDatabase
    val count: Int
    val uriMatch = URI_MATCHER.match(uri)
    maybeSetDirty(uriMatch)
    when (uriMatch) {
      TRANSACTIONS -> count = db.delete(TABLE_TRANSACTIONS, where, whereArgs)
      TRANSACTION_ID -> count = deleteTransaction(db, uri)
      TEMPLATES -> count = db.delete(TABLE_TEMPLATES, where, whereArgs)
      TEMPLATE_ID -> count = db.delete(TABLE_TEMPLATES, "$KEY_ROWID = ${uri.lastPathSegment}${prefixAnd(where)}", whereArgs)
      ACCOUNTTYPES_METHODS -> count = db.delete(TABLE_ACCOUNTTYES_METHODS, where, whereArgs)
      ACCOUNTS -> count = db.delete(TABLE_ACCOUNTS, where, whereArgs)
      ACCOUNT_ID -> count = db.delete(TABLE_ACCOUNTS, "$KEY_ROWID = ${uri.lastPathSegment}${prefixAnd(where)}", whereArgs)
      CATEGORIES -> count = db.delete(TABLE_CATEGORIES, "$KEY_ROWID != $SPLIT_CATID${prefixAnd(where)}", whereArgs)
      CATEGORY_ID -> {
        val lastPathSegment = uri.lastPathSegment!!
        if (lastPathSegment.toLong() == SPLIT_CATID) {
          throw IllegalArgumentException("split category can not be deleted")
        }
        count = db.delete(TABLE_CATEGORIES, "$KEY_ROWID = $lastPathSegment${prefixAnd(where)}", whereArgs)
      }
      PAYEE_ID -> count = db.delete(TABLE_PAYEES, "$KEY_ROWID = ${uri.lastPathSegment}${prefixAnd(where)}", whereArgs)
      METHOD_ID -> count = db.delete(TABLE_METHODS, "$KEY_ROWID = ${uri.lastPathSegment}${prefixAnd(where)}", whereArgs)
      PLANINSTANCE_TRANSACTION_STATUS -> count = db.delete(TABLE_PLAN_INSTANCE_STATUS, where, whereArgs)
      PLANINSTANCE_STATUS_SINGLE -> {
        count = db.delete(
          TABLE_PLAN_INSTANCE_STATUS,
          String.format(Locale.ROOT, "%s = ? AND %s = ?", KEY_TEMPLATEID, KEY_INSTANCEID),
          arrayOf(uri.pathSegments[1], uri.pathSegments[2])
        )
        notifyChange(uri, false)
        return count
      }
      EVENT_CACHE -> count = db.delete(TABLE_EVENT_CACHE, where, whereArgs)
      STALE_IMAGES_ID -> {
        val segment = uri.pathSegments[1]
        count = db.delete(TABLE_ATTACHMENTS, "$KEY_ROWID = ?", arrayOf(segment))
      }
      STALE_IMAGES -> count = db.delete(TABLE_ATTACHMENTS, STALE_ATTACHMENT_SELECTION, null)
      CHANGES -> count = db.delete(TABLE_CHANGES, where, whereArgs)
      SETTINGS -> count = db.delete(TABLE_SETTINGS, where, whereArgs)
      BUDGET_ID -> count = db.delete(TABLE_BUDGETS, "$KEY_ROWID = ${uri.lastPathSegment}${prefixAnd(where)}", whereArgs)
      PAYEES -> count = db.delete(TABLE_PAYEES, where, whereArgs)
      TAG_ID -> count = db.delete(TABLE_TAGS, "$KEY_ROWID = ${uri.lastPathSegment}${prefixAnd(where)}", whereArgs)
      DUAL -> {
        if ("1" == uri.getQueryParameter(QUERY_PARAMETER_SYNC_END)) {
          count = resumeChangeTrigger(db)
        } else {
          throw unknownUri(uri)
        }
      }
      TRANSACTIONS_TAGS -> {
        if (callerIsNotSyncAdapter(uri)) throw IllegalArgumentException("Can only be called from sync adapter")
        count = db.delete(TABLE_TRANSACTIONS_TAGS, where, whereArgs)
      }
      TEMPLATES_TAGS -> count = db.delete(TABLE_TEMPLATES_TAGS, where, whereArgs)
      ACCOUNTS_TAGS -> count = db.delete(TABLE_ACCOUNTS_TAGS, where, whereArgs)
      DEBT_ID -> count = db.deleteDebt(uri.lastPathSegment!!, where, whereArgs)
      BANK_ID -> count = db.delete(TABLE_BANKS, "$KEY_ROWID = ${uri.lastPathSegment}${prefixAnd(where)}", whereArgs)
      TRANSACTION_ID_ATTACHMENT_ID -> {
        val transactionId = uri.pathSegments[2]
        val attachmentId = uri.pathSegments[3]
        count = db.delete(
          TABLE_TRANSACTION_ATTACHMENTS,
          "$KEY_TRANSACTIONID = ? AND $KEY_ATTACHMENT_ID = ?",
          arrayOf(transactionId, attachmentId)
        )
        deleteAttachment(db, attachmentId.toLong(), null)
      }
      TAGS -> count = db.delete(TABLE_TAGS, where, whereArgs)
      BUDGET_ALLOCATIONS -> count = db.delete(TABLE_BUDGET_ALLOCATIONS, where, whereArgs)
      PRICES -> count = db.delete(TABLE_PRICES, where, whereArgs)
      ACCOUNT_TYPE_ID -> count = db.delete(TABLE_ACCOUNT_TYPES, "$KEY_ROWID = ${uri.lastPathSegment}${prefixAnd(where)}", whereArgs)
      ACCOUNT_FLAG_ID -> count = db.delete(TABLE_ACCOUNT_FLAGS, "$KEY_ROWID = ${uri.lastPathSegment}${prefixAnd(where)}", whereArgs)
      CURRENCIES_CODE -> {
        val currency = uri.lastPathSegment!!
        if (Utils.isKnownCurrency(currency)) {
          throw IllegalArgumentException("Can only delete custom currencies")
        }
        try {
          count = db.delete(TABLE_CURRENCIES, String.format("%s = '%s'%s", KEY_CODE, currency, prefixAnd(where)), whereArgs)
        } catch (e: SQLiteConstraintException) {
          return 0
        }
      }
      else -> throw unknownUri(uri)
    }

    if (uriMatch == TRANSACTIONS || (uriMatch == TRANSACTION_ID && callerIsNotInBulkOperation(uri))) {
      notifyChange(TRANSACTIONS_URI, callerIsNotSyncAdapter(uri))
      notifyChange(ACCOUNTS_URI, false)
      notifyChange(DEBTS_URI, false)
    } else {
      if (uriMatch == ACCOUNTS || uriMatch == ACCOUNT_ID) {
        notifyAccountChange()
      }
      if (uriMatch == DEBT_ID) {
        notifyChange(PAYEES_URI, false)
      } else if (uriMatch == TRANSACTION_ID_ATTACHMENT_ID) {
        notifyChange(TRANSACTIONS_URI, false)
      } else if (uriMatch == BANK_ID) {
        notifyChange(ACCOUNTS_URI, false)
      } else if (uriMatch == PRICES) {
        notifyChange(LATEST_PRICES_URI, false)
        notifyAccountChange()
      }
      notifyChange(uri, uriMatch == TRANSACTION_ID)
    }
    return count
  }

  override fun update(
    uri: Uri,
    values: ContentValues?,
    where: String?,
    whereArgs: Array<String>?
  ): Int {
    val db = helper.writableDatabase
    val count: Int
    val uriMatch = URI_MATCHER.match(uri)
    maybeSetDirty(uriMatch)
    log("UPDATE Uri: %s, values: %s", uri, values)
    val lastPathSegment = uri.lastPathSegment
    val v = values ?: ContentValues()
    var wArgs = whereArgs

    when (uriMatch) {
      TRANSACTIONS -> {
        val hasEquivalentAmount = v.containsKey(KEY_EQUIVALENT_AMOUNT)
        val equivalentAmount = v.getAsLong(KEY_EQUIVALENT_AMOUNT)
        v.remove(KEY_EQUIVALENT_AMOUNT)
        db.beginTransaction()
        try {
          count = if (v.size() > 0) {
            db.update(TABLE_TRANSACTIONS, v, where, wArgs)
          } else {
            0
          }
          if (hasEquivalentAmount) {
            @Suppress("UNCHECKED_CAST") db.insertOrReplaceEquivalentAmount(equivalentAmount, where, wArgs as? Array<Any>)
          }
          db.setTransactionSuccessful()
        } finally {
          db.endTransaction()
        }
      }

      TRANSACTION_ID -> {
        val hasEquivalentAmount = v.containsKey(KEY_EQUIVALENT_AMOUNT)
        val equivalentAmount = v.getAsLong(KEY_EQUIVALENT_AMOUNT)
        v.remove(KEY_EQUIVALENT_AMOUNT)
        val tagList = v.getAsString(KEY_TAGLIST)
        v.remove(KEY_TAGLIST)
        db.beginTransaction()
        try {
          val id = lastPathSegment!!.toLong()
          count = if (v.size() > 0) {
            db.update(TABLE_TRANSACTIONS, v, "$KEY_ROWID = $lastPathSegment${prefixAnd(where)}", wArgs)
          } else {
            0
          }
          if (hasEquivalentAmount) {
            db.insertOrReplaceEquivalentAmount(id, equivalentAmount)
          }
          db.saveTransactionTags(id, tagList, true)
          db.setTransactionSuccessful()
        } finally {
          db.endTransaction()
        }
      }

      TRANSACTION_UNDELETE -> {
        val segment = uri.pathSegments[1]
        wArgs = arrayOf(segment, segment, segment, segment)
        val updateValues = ContentValues()
        updateValues.put(KEY_CR_STATUS, CrStatus.UNRECONCILED.name)
        count = db.update(TABLE_TRANSACTIONS, updateValues, "($WHERE_SELF_OR_RELATED) AND $KEY_CR_STATUS='${CrStatus.VOID.name}'", wArgs)
      }

      ACCOUNTS -> count = db.update(TABLE_ACCOUNTS, v, where, wArgs)
      ACCOUNT_ID -> count = db.update(TABLE_ACCOUNTS, v, "$KEY_ROWID = $lastPathSegment${prefixAnd(where)}", wArgs)
      TEMPLATES -> count = db.update(TABLE_TEMPLATES, v, where, wArgs)
      TEMPLATE_ID -> {
        val tagList = v.getAsString(KEY_TAGLIST)
        v.remove(KEY_TAGLIST)
        db.beginTransaction()
        try {
          count = db.update(TABLE_TEMPLATES, v, "$KEY_ROWID = $lastPathSegment${prefixAnd(where)}", wArgs)
          db.saveTransactionTags(lastPathSegment!!.toLong(), tagList, true, true)
          db.setTransactionSuccessful()
        } finally {
          db.endTransaction()
        }
      }

      PAYEE_ID -> {
        count = db.update(TABLE_PAYEES, v, "$KEY_ROWID = $lastPathSegment${prefixAnd(where)}", wArgs)
        notifyChange(TRANSACTIONS_URI, false)
      }

      CATEGORIES -> throw UnsupportedOperationException("Bulk update of categories is not supported")

      CATEGORY_ID -> {
        if (v.containsKey(KEY_PARENTID)) {
          val parentId = v.getAsLong(KEY_PARENTID)
          if (parentId == null && !v.containsKey(KEY_COLOR)) {
            v.put(KEY_COLOR, suggestNewCategoryColor(db))
          }
          if (parentId != null) {
            v.putNull(KEY_COLOR)
          }
        }
        val segment = lastPathSegment
        count = db.update(TABLE_CATEGORIES, v, "$KEY_ROWID = $segment${prefixAnd(where)}", wArgs)
      }

      METHOD_ID -> count = db.update(TABLE_METHODS, v, "$KEY_ROWID = $lastPathSegment${prefixAnd(where)}", wArgs)

      TEMPLATES_INCREASE_USAGE -> {
        db.execSQL("UPDATE $TABLE_TEMPLATES SET $KEY_USAGES = $KEY_USAGES + 1, $KEY_LAST_USED = strftime('%s', 'now') WHERE $KEY_ROWID = ${uri.pathSegments[1]}")
        count = 1
      }

      TRANSACTION_MOVE -> {
        val segment = uri.pathSegments[1]
        val target = uri.pathSegments[3]
        db.execSQL(
          "UPDATE $TABLE_TRANSACTIONS SET $KEY_ACCOUNTID = ?, $KEY_METHODID = " +
            "CASE WHEN exists (SELECT 1 FROM $TABLE_ACCOUNTTYES_METHODS WHERE $KEY_TYPE = " +
            "(SELECT $KEY_TYPE FROM $TABLE_ACCOUNTS WHERE $KEY_ROWID = ?) AND $KEY_METHODID = $TABLE_TRANSACTIONS.$KEY_METHODID) " +
            "THEN $KEY_METHODID ELSE null END " +
            "WHERE $KEY_ROWID = ? AND ( $KEY_TRANSFER_ACCOUNT IS NULL OR $KEY_TRANSFER_ACCOUNT != ? )",
          arrayOf(target, target, segment, target)
        )
        count = 1
      }

      TRANSACTION_TOGGLE_CRSTATUS -> {
        db.execSQL(
          "UPDATE $TABLE_TRANSACTIONS SET $KEY_CR_STATUS = CASE $KEY_CR_STATUS " +
            "WHEN 'CLEARED' THEN 'UNRECONCILED' " +
            "WHEN 'UNRECONCILED' THEN 'CLEARED' " +
            "ELSE $KEY_CR_STATUS END WHERE $KEY_ROWID = ? ",
          arrayOf(uri.pathSegments[1])
        )
        count = 1
      }

      CHANGES -> {
        if (uri.getBooleanQueryParameter(QUERY_PARAMETER_INIT, false)) {
          initChangeLog(db, uri.getQueryParameter(KEY_ACCOUNTID)!!)
          count = 1
        } else {
          count = db.update(TABLE_CHANGES, v, where, wArgs)
        }
      }

      ACCOUNT_ID_GROUPING -> count = handleAccountProperty(db, uri, KEY_GROUPING)
      ACCOUNT_ID_SORT -> count = handleAccountProperty(db, uri, KEY_SORT_BY, KEY_SORT_DIRECTION)
      UNSPLIT -> count = db.unsplit(v, callerIsNotSyncAdapter(uri))
      UNARCHIVE -> count = db.unarchive(v, callerIsNotSyncAdapter(uri))
      BUDGET_ID -> count = db.update(TABLE_BUDGETS, v, "$KEY_ROWID = $lastPathSegment${prefixAnd(where)}", wArgs)
      BANK_ID -> count = db.update(TABLE_BANKS, v, "$KEY_ROWID = $lastPathSegment${prefixAnd(where)}", wArgs)
      BUDGET_CATEGORY -> count = budgetCategoryUpsert(db, uri, v)
      BUDGET_ALLOCATIONS -> count = db.update(TABLE_BUDGET_ALLOCATIONS, v, where, wArgs)
      TAG_ID -> count = db.update(TABLE_TAGS, v, "$KEY_ROWID = $lastPathSegment${prefixAnd(where)}", wArgs)
      TRANSACTION_LINK_TRANSFER -> count = linkTransfers(db, uri.pathSegments[2], v.getAsString(KEY_UUID), callerIsNotSyncAdapter(uri))
      TRANSACTION_UNLINK_TRANSFER -> count = unlinkTransfers(db, lastPathSegment!!)
      DEBTS -> count = db.update(TABLE_DEBTS, v, where, wArgs)
      DEBT_ID -> count = db.update(TABLE_DEBTS, v, "$KEY_ROWID = $lastPathSegment${prefixAnd(where)}", wArgs)
      PLANINSTANCE_TRANSACTION_STATUS -> count = db.update(TABLE_PLAN_INSTANCE_STATUS, v, where, wArgs)
      ATTACHMENTS -> count = db.update(TABLE_ATTACHMENTS, v, where, wArgs)
      ACCOUNT_TYPE_ID -> count = db.update(TABLE_ACCOUNT_TYPES, v, "$KEY_ROWID = $lastPathSegment${prefixAnd(where)}", wArgs)
      ACCOUNT_FLAG_ID -> count = db.update(TABLE_ACCOUNT_FLAGS, v, "$KEY_ROWID = $lastPathSegment${prefixAnd(where)}", wArgs)
      else -> throw unknownUri(uri)
    }

    if (uriMatch == TRANSACTIONS || uriMatch == TRANSACTION_ID || uriMatch == ACCOUNTS || uriMatch == ACCOUNT_ID ||
      uriMatch == CURRENCIES || uriMatch == TRANSACTION_UNDELETE ||
      uriMatch == TRANSACTION_MOVE || uriMatch == TRANSACTION_TOGGLE_CRSTATUS ||
      uriMatch == TRANSACTION_LINK_TRANSFER || uriMatch == TRANSACTION_UNLINK_TRANSFER || uriMatch == UNARCHIVE
    ) {
      notifyChange(TRANSACTIONS_URI, callerIsNotSyncAdapter(uri))
      notifyChange(ACCOUNTS_URI, false)
      notifyChange(DEBTS_URI, false)
      notifyChange(CATEGORIES_URI, false)
    } else if (uriMatch != TEMPLATES_INCREASE_USAGE) {
      notifyChange(uri, false)
    }
    if (uriMatch == ACCOUNT_ID_GROUPING || uriMatch == ACCOUNT_ID_SORT) {
      notifyChange(ACCOUNTS_URI, false)
    }
    if (uriMatch == CURRENCIES || uriMatch == TEMPLATES_INCREASE_USAGE) {
      notifyChange(TEMPLATES_URI, false)
    }
    if (uriMatch == BUDGET_CATEGORY) {
      notifyChange(CATEGORIES_URI, false)
      notifyChange(BUDGETS_URI, false)
    }
    if (uriMatch == BUDGET_ALLOCATIONS) {
      notifyChange(CATEGORIES_URI, false)
      notifyChange(BUDGETS_URI, false)
    }
    if (uriMatch == ACCOUNTS || uriMatch == ACCOUNT_ID) {
      notifyAccountChange()
    }
    if (uriMatch == CATEGORY_ID || uriMatch == METHOD_ID) {
      notifyChange(TRANSACTIONS_URI, false)
    }
    if (uriMatch == ACCOUNT_TYPE_ID) {
      notifyChange(ACCOUNTS_URI, false)
    }
    if (uriMatch == ACCOUNT_FLAG_ID) {
      notifyChange(ACCOUNTS_URI, false)
    }
    if (uriMatch == BUDGET_ID) {
      notifyChange(
        BUDGETS_URI.buildUpon()
          .appendPath("defaultBudgetAllocations")
          .build(), false
      )
    }
    return count
  }

  @Throws(OperationApplicationException::class)
  override fun applyBatch(operations: ArrayList<ContentProviderOperation>): Array<ContentProviderResult> {
    val db = helper.writableDatabase
    val alreadyInBulk = bulkInProgress
    if (!alreadyInBulk) {
      bulkInProgress = true
    }
    db.beginTransaction()
    try {
      val numOperations = operations.size
      val results = arrayOfNulls<ContentProviderResult>(numOperations)
      for (i in 0 until numOperations) {
        val contentProviderOperation = operations[i]
        try {
          Timber.d("URI: %s", contentProviderOperation.uri)
          @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS", "UNCHECKED_CAST")
          val result = contentProviderOperation.apply(this, results as Array<ContentProviderResult>, i)
          Timber.d("Result: %s", result)
          results[i] = result
        } catch (e: Exception) {
          val customData = HashMap<String, String>()
          customData["i"] = i.toString()
          customData["operation"] = contentProviderOperation.toString()
          CrashHandler.report(e, customData, TAG)
          throw e
        }
      }
      db.setTransactionSuccessful()
      @Suppress("UNCHECKED_CAST")
      return results as Array<ContentProviderResult>
    } finally {
      db.endTransaction()
      if (!alreadyInBulk) {
        bulkInProgress = false
        notifyBulk()
      }
    }
  }

  override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
    log("Call method: %s, arg: %s, extras: %s", method, arg, extras)
    when (method) {
      METHOD_BULK_START -> bulkInProgress = true
      METHOD_BULK_END -> {
        bulkInProgress = false
        notifyBulk()
      }
      METHOD_SORT_ACCOUNTS -> {
        val db = helper.writableDatabase
        if (extras != null) {
          val sortedIds = extras.getLongArray(KEY_SORT_KEY)
          if (sortedIds != null) {
            val values = ContentValues(1)
            for (i in sortedIds.indices) {
              values.put(KEY_SORT_KEY, i)
              db.update(TABLE_ACCOUNTS, values, "$KEY_ROWID = ?", arrayOf(sortedIds[i].toString()))
            }
            notifyChange(ACCOUNTS_URI, false)
          }
        }
      }
      METHOD_SETUP_CATEGORIES -> {
        val result = Bundle(1)
        result.putSerializable(KEY_RESULT, setupDefaultCategories(helper.writableDatabase, wrappedContext.resources))
        notifyChange(CATEGORIES_URI, false)
        return result
      }
      METHOD_SETUP_CATEGORIES_DRY_RUN -> {
        val result = Bundle(1)
        result.putParcelable(KEY_RESULT, getImportableCategories(helper.writableDatabase, wrappedContext.resources))
        return result
      }
      METHOD_CHECK_CORRUPTED_DATA_987 -> {
        return checkCorruptedData987()
      }
      METHOD_DELETE_ATTACHMENTS -> {
        val result = Bundle(1)
        result.putBoolean(KEY_RESULT, deleteAttachments(helper.writableDatabase, extras!!.getLong(KEY_TRANSACTIONID), extras.getStringArray(KEY_URI_LIST)!!.toMutableList()))
        return result
      }
      METHOD_SAVE_CATEGORY -> {
        val bundle = saveCategory(helper.writableDatabase, extras!!)
        notifyChange(CATEGORIES_URI, false)
        notifyChange(TRANSACTIONS_URI, false)
        return bundle
      }
      METHOD_ENSURE_CATEGORY -> {
        val bundle = ensureCategory(helper.writableDatabase, extras!!)
        notifyChange(CATEGORIES_URI, false)
        notifyChange(TRANSACTIONS_URI, false)
        return bundle
      }
      METHOD_ENSURE_CATEGORY_TREE -> {
        val bundle = ensureCategoryTree(helper.writableDatabase, extras!!)
        notifyChange(CATEGORIES_URI, false)
        notifyChange(TRANSACTIONS_URI, false)
        return bundle
      }
      METHOD_SAVE_TRANSACTION_TAGS -> {
        helper.writableDatabase.saveTransactionTags(extras!!)
        notifyChange(TRANSACTIONS_URI, true)
      }
      METHOD_MERGE_CATEGORIES -> {
        mergeCategories(helper.writableDatabase, extras!!)
        notifyChange(CATEGORIES_URI, false)
        notifyChange(TRANSACTIONS_URI, false)
      }
      METHOD_ARCHIVE -> {
        val result = Bundle(1)
        result.putLong(KEY_TRANSACTIONID, helper.writableDatabase.archive(extras!!))
        notifyChange(TRANSACTIONS_URI, true)
        notifyChange(ACCOUNTS_URI, false)
        return result
      }
      METHOD_CAN_BE_ARCHIVED -> {
        val result = Bundle(1)
        result.putParcelable(KEY_RESULT, helper.writableDatabase.canBeArchived(extras!!))
        return result
      }
      METHOD_RECALCULATE_EQUIVALENT_AMOUNTS -> {
        val result = Bundle(1)
        result.putSerializable(KEY_RESULT, helper.writableDatabase.recalculateEquivalentAmounts(extras!!))
        notifyChange(TRANSACTIONS_URI, true)
        notifyChange(ACCOUNTS_URI, false)
        return result
      }
      METHOD_RECALCULATE_EQUIVALENT_AMOUNTS_FOR_DATE -> {
        val result = Bundle(1)
        result.putInt(KEY_RESULT, helper.writableDatabase.recalculateEquivalentAmountsForDate(extras!!))
        notifyChange(TRANSACTIONS_URI, true)
        notifyChange(ACCOUNTS_URI, false)
        return result
      }
      METHOD_FLAG_SORT -> {
        val result = helper.writableDatabase.setFlagSort(extras!!)
        notifyChange(ACCOUNT_FLAGS_URI, false)
        return result
      }
      METHOD_TYPE_SORT -> {
        val result = helper.writableDatabase.setTypeSort(extras!!)
        notifyChange(ACCOUNT_TYPES_URI, false)
        notifyChange(ACCOUNTS_URI, false)
        return result
      }
      METHOD_CLEANUP_UNUSED_PAYEES -> {
        helper.writableDatabase.cleanupUnusedPayees()
        notifyChange(PAYEES_URI, false)
        return null
      }
      METHOD_APPLY_CHANGES -> {
        return applyChangesFromSync(extras!!)
      }
      METHOD_UPDATE_CURRENCY -> {
        val db = helper.writableDatabase
        val result = db.updateCurrency(extras!!)
        notifyChange(TRANSACTIONS_URI, false)
        notifyChange(ACCOUNTS_URI, false)
        notifyChange(CURRENCIES_URI, false)
        notifyChange(TEMPLATES_URI, false)
        return result
      }
      METHOD_CHECK_CURRENCY_IN_USE -> {
        val db = helper.writableDatabase
        return db.checkCurrencyInUse(arg!!)
      }
      METHOD_STORE_FRACTION_DIGITS -> {
        val db = helper.writableDatabase
        db.storeFractionDigits(arg!!, extras!!)
        return null
      }
    }
    return null
  }

  @VisibleForTesting
  val openHelperForTest: SupportSQLiteOpenHelper
    get() = helper

  companion object {
    @JvmField
    val AUTHORITY = BuildConfig.APPLICATION_ID

    @JvmField
    val ACCOUNTS_URI: Uri = Uri.parse("content://$AUTHORITY/accounts")

    //when we need the accounts cursor without the current balance
    //we do not want the cursor to be reloaded when a transaction is added
    //hence we access it through a different URI
    @JvmField
    val ACCOUNTS_BASE_URI: Uri = Uri.parse("content://$AUTHORITY/accountsbase")

    @JvmField
    val ACCOUNTS_AGGREGATE_URI: Uri = Uri.parse("content://$AUTHORITY/accounts/aggregates")

    @JvmField
    val AGGREGATE_V2_URI: Uri = Uri.parse("content://$AUTHORITY/aggregates")

    //returns accounts with aggregate accounts, limited to id and label
    @JvmField
    val ACCOUNTS_MINIMAL_URI: Uri = Uri.parse("content://$AUTHORITY/accountsMinimal")

    @JvmField
    val TRANSACTIONS_URI: Uri = Uri.parse("content://$AUTHORITY/transactions")

    @JvmField
    val EXTENDED_URI: Uri = TRANSACTIONS_URI.buildUpon().appendQueryParameter(
      QUERY_PARAMETER_EXTENDED, "1"
    ).build()

    @JvmField
    val TEMPLATES_URI: Uri = Uri.parse("content://$AUTHORITY/templates")

    @JvmField
    val CATEGORIES_URI: Uri = Uri.parse("content://$AUTHORITY/categories")

    @JvmField
    val PAYEES_URI: Uri = Uri.parse("content://$AUTHORITY/payees")

    @JvmField
    val METHODS_URI: Uri = Uri.parse("content://$AUTHORITY/methods")

    @JvmField
    val MAPPED_METHODS_URI: Uri = Uri.parse("content://$AUTHORITY/methods_transactions")

    @JvmField
    val ACCOUNTTYPES_METHODS_URI: Uri = Uri.parse("content://$AUTHORITY/accounttypes_methods")

    @JvmField
    val SQLITE_SEQUENCE_TRANSACTIONS_URI: Uri = Uri.parse("content://$AUTHORITY/sqlite_sequence/$TABLE_TRANSACTIONS")

    @JvmField
    val PLAN_INSTANCE_STATUS_URI: Uri = Uri.parse("content://$AUTHORITY/planinstance_transaction")

    @JvmStatic
    fun PLAN_INSTANCE_SINGLE_URI(templateId: Long, instanceId: Long): Uri =
      ContentUris.appendId(ContentUris.appendId(PLAN_INSTANCE_STATUS_URI.buildUpon(), templateId), instanceId).build()

    @JvmStatic
    fun TRANSACTION_ATTACHMENT_SINGLE_URI(transactionId: Long, attachmentId: Long): Uri =
      ContentUris.appendId(ContentUris.appendId(TRANSACTIONS_ATTACHMENTS_URI.buildUpon(), transactionId), attachmentId).build()

    const val URI_SEGMENT_SORT = "sortBy"
    const val URI_SEGMENT_GROUPING = "accountGrouping"
    const val URI_SEGMENT_SUMS_FOR_ACCOUNTS = "sumsForAccounts"
    const val URI_SEGMENT_SUMS_FOR_ARCHIVE = "sumsForArchive"

    @JvmField
    val CURRENCIES_URI: Uri = Uri.parse("content://$AUTHORITY/currencies")

    @JvmField
    val TRANSACTIONS_SUM_URI: Uri = Uri.parse("content://$AUTHORITY/transactions/$URI_SEGMENT_SUMS_FOR_ACCOUNTS")

    @JvmStatic
    fun ARCHIVE_SUMS_URI(archiveId: Long): Uri =
      Uri.parse("content://$AUTHORITY/transactions/$archiveId/$URI_SEGMENT_SUMS_FOR_ARCHIVE")

    @JvmField
    val EVENT_CACHE_URI: Uri = Uri.parse("content://$AUTHORITY/eventcache")

    @JvmField
    val DEBUG_SCHEMA_URI: Uri = Uri.parse("content://$AUTHORITY/debug_schema")

    @JvmField
    val STALE_IMAGES_URI: Uri = Uri.parse("content://$AUTHORITY/stale_images")

    @JvmField
    val MAPPED_TRANSFER_ACCOUNTS_URI: Uri = Uri.parse("content://$AUTHORITY/transfer_account_transactions")

    @JvmField
    val CHANGES_URI: Uri = Uri.parse("content://$AUTHORITY/changes")

    @JvmField
    val SETTINGS_URI: Uri = Uri.parse("content://$AUTHORITY/settings")

    @JvmField
    val AUTOFILL_URI: Uri = Uri.parse("content://$AUTHORITY/autofill")

    /**
     * select info from DB without table, e.g. CategoryList#DATEINFO_CURSOR
     * or set control flags like sync_state
     */
    @JvmField
    val DUAL_URI: Uri = Uri.parse("content://$AUTHORITY/dual")

    @JvmField
    val ACCOUNT_EXCHANGE_RATE_URI: Uri = Uri.parse("content://$AUTHORITY/account_exchangerates")

    @JvmField
    val SORT_URI: Uri = Uri.parse("content://$AUTHORITY/$URI_SEGMENT_SORT")

    @JvmField
    val ACCOUNT_GROUPINGS_URI: Uri = Uri.parse("content://$AUTHORITY/$URI_SEGMENT_GROUPING")

    @JvmField
    val BUDGETS_URI: Uri = Uri.parse("content://$AUTHORITY/budgets")

    @JvmField
    val TAGS_URI: Uri = Uri.parse("content://$AUTHORITY/tags")

    @JvmField
    val TRANSACTIONS_TAGS_URI: Uri = Uri.parse("content://$AUTHORITY/transactions/tags")

    @JvmField
    val TEMPLATES_TAGS_URI: Uri = Uri.parse("content://$AUTHORITY/templates/tags")

    @JvmField
    val ACCOUNTS_TAGS_URI: Uri = Uri.parse("content://$AUTHORITY/accounts/tags")

    @JvmField
    val DEBTS_URI: Uri = Uri.parse("content://$AUTHORITY/debts")

    @JvmField
    val BANKS_URI: Uri = Uri.parse("content://$AUTHORITY/banks")

    @JvmField
    val TRANSACTIONS_ATTRIBUTES_URI: Uri = Uri.parse("content://$AUTHORITY/transactions/attributes")

    @JvmField
    val ACCOUNTS_ATTRIBUTES_URI: Uri = Uri.parse("content://$AUTHORITY/accounts/attributes")

    @JvmField
    val ATTRIBUTES_URI: Uri = Uri.parse("content://$AUTHORITY/attributes")

    @JvmField
    val ATTACHMENTS_URI: Uri = Uri.parse("content://$AUTHORITY/attachments")

    @JvmField
    val TRANSACTIONS_ATTACHMENTS_URI: Uri = Uri.parse("content://$AUTHORITY/transactions/attachments")

    @JvmField
    val PRICES_URI: Uri = Uri.parse("content://$AUTHORITY/prices")

    @JvmField
    val LATEST_PRICES_URI: Uri = Uri.parse("content://$AUTHORITY/latest_prices")

    @JvmField
    val DYNAMIC_CURRENCIES_URI: Uri = Uri.parse("content://$AUTHORITY/dynamicCurrencies")

    @JvmField
    val ACCOUNT_TYPES_URI: Uri = Uri.parse("content://$AUTHORITY/accountTypes")

    @JvmField
    val ACCOUNT_FLAGS_URI: Uri = Uri.parse("content://$AUTHORITY/accountFlags")

    const val URI_SEGMENT_MOVE = "move"
    const val URI_SEGMENT_TOGGLE_CRSTATUS = "toggleCrStatus"
    const val URI_SEGMENT_UNDELETE = "undelete"
    const val URI_SEGMENT_INCREASE_USAGE = "increaseUsage"
    const val URI_SEGMENT_TYPE_FILTER = "typeFilter"
    const val URI_SEGMENT_BUDGET_ALLOCATIONS = "allocations"
    const val URI_SEGMENT_DEFAULT_BUDGET_ALLOCATIONS = "defaultBudgetAllocations"

    @JvmField
    val UNSPLIT_URI: Uri = Uri.parse("content://$AUTHORITY/transactions/unsplit")

    const val URI_SEGMENT_LINK_TRANSFER = "link_transfer"
    const val URI_SEGMENT_UNLINK_TRANSFER = "unlink_transfer"
    const val URI_SEGMENT_TRANSFORM_TO_TRANSFER = "transform_to_transfer"
    const val URI_SEGMENT_UNARCHIVE = "unarchive"

    @JvmField
    val BUDGET_ALLOCATIONS_URI: Uri = Uri.parse("content://$AUTHORITY/budgets/$URI_SEGMENT_BUDGET_ALLOCATIONS")

    //"1" merge all currency aggregates, < 0 only return one specific aggregate
    const val QUERY_PARAMETER_MERGE_CURRENCY_AGGREGATES = "mergeCurrencyAggregates"
    const val QUERY_PARAMETER_FULL_PROJECTION_WITH_SUMS = "fullProjectionWithSums"
    const val QUERY_PARAMETER_BALANCE_SHEET = "balanceSheet"

    //uses full projection with sums for each account
    @JvmField
    val ACCOUNTS_FULL_URI: Uri = BaseTransactionProvider.balanceUri("now", false)

    const val QUERY_PARAMETER_EXTENDED = "extended"
    const val QUERY_PARAMETER_DISTINCT = "distinct"
    const val QUERY_PARAMETER_GROUP_BY = "groupBy"
    const val QUERY_PARAMETER_MARK_VOID = "markVoid"
    //"1" from production, "2" from test
    const val QUERY_PARAMETER_WITH_PLAN_INFO = "withPlanInfo"
    const val QUERY_PARAMETER_INIT = "init"
    const val QUERY_PARAMETER_CALLER_IS_SYNCADAPTER = "caller_is_syncadapter"

    const val QUERY_PARAMETER_CALLER_IS_IN_BULK = "caller_is_in_bulk"
    private const val QUERY_PARAMETER_SYNC_BEGIN = "syncBegin"
    private const val QUERY_PARAMETER_SYNC_END = "syncEnd"
    const val QUERY_PARAMETER_WITH_JULIAN_START = "withJulianStart"
    const val QUERY_PARAMETER_WITH_COUNT = "count"
    const val QUERY_PARAMETER_WITH_INSTANCE = "withInstance"
    const val QUERY_PARAMETER_HIERARCHICAL = "hierarchical"
    const val QUERY_PARAMETER_DEPTH = "depth"
    const val QUERY_PARAMETER_CATEGORY_SEPARATOR = "categorySeparator"
    const val QUERY_PARAMETER_SHORTEN_COMMENT = "shortenComment"
    const val QUERY_PARAMETER_SEARCH = "search"
    const val QUERY_PARAMETER_COUNT_UNUSED = "countUnused"
    const val QUERY_PARAMETER_OLD_FRACTION_DIGITS = "oldFractionDigits"
    const val QUERY_PARAMETER_OLD_CODE = "oldCode"

    /**
     * do not honour EXCLUDE_FROM_TOTAL flag
     */
    const val QUERY_PARAMETER_INCLUDE_ALL = "includeAll"

    /**
     * 1 -> mapped objects for each row
     * 2 -> aggregate sums for all mapped objects
     */
    const val QUERY_PARAMETER_MAPPED_OBJECTS = "mappedObjects"

    /**
     * Transfers are included into in and out sums, instead of reported in extra field
     */
    const val QUERY_PARAMETER_INCLUDE_TRANSFERS = "includeTransfers"

    const val QUERY_PARAMETER_AGGREGATE_NEUTRAL = "aggregateNeutral"

    /**
     * Colon separated list of account types
     */
    const val QUERY_PARAMETER_ACCOUNT_TYPE_LIST = "accountTypeList"

    const val QUERY_PARAMETER_WITH_FILTER = "withFilter"

    const val QUERY_PARAMETER_BREAKDOWN_BY_ACCOUNT = "breakdownByAccount"

    const val METHOD_BULK_START = "bulkStart"
    const val METHOD_BULK_END = "bulkEnd"
    const val METHOD_SORT_ACCOUNTS = "sort_accounts"
    const val METHOD_SETUP_CATEGORIES = "setup_categories"
    const val METHOD_SETUP_CATEGORIES_DRY_RUN = "setup_categories_dry_run"
    const val METHOD_CHECK_CORRUPTED_DATA_987 = "checkCorruptedData"

    const val METHOD_DELETE_ATTACHMENTS = "deleteAttachments"

    const val METHOD_SAVE_CATEGORY = "saveCategory"
    const val KEY_CATEGORY = "category"
    const val METHOD_ENSURE_CATEGORY = "ensureCategory"
    const val KEY_CATEGORY_INFO = "categoryInfo"
    const val METHOD_ENSURE_CATEGORY_TREE = "ensureCategoryTree"
    const val KEY_CATEGORY_EXPORT = "categoryExport"
    const val METHOD_SAVE_TRANSACTION_TAGS = "saveTransactionTags"
    const val KEY_REPLACE = "replace"

    const val KEY_RESULT = "result"

    const val METHOD_MERGE_CATEGORIES = "mergeCategories"
    const val KEY_MERGE_SOURCE = "mergeSource"
    const val KEY_MERGE_TARGET = "mergeTarget"

    const val METHOD_ARCHIVE = "archive"
    const val METHOD_CAN_BE_ARCHIVED = "canBeArchived"

    const val METHOD_RECALCULATE_EQUIVALENT_AMOUNTS = "recalculateEquivalentAmounts"
    const val METHOD_RECALCULATE_EQUIVALENT_AMOUNTS_FOR_DATE = "recalculateEquivalentAmountsForDate"

    const val METHOD_CLEANUP_UNUSED_PAYEES = "cleanupUnusedPayees"

    const val METHOD_UPDATE_CURRENCY = "updateCurrency"
    const val KEY_UPDATED_ACCOUNTS_COUNT = "updatedAccountsCount"

    const val METHOD_CHECK_CURRENCY_IN_USE = "checkCurrencyInUse"
    const val METHOD_STORE_FRACTION_DIGITS = "storeFractionDigits"

    private val URI_MATCHER = UriMatcher(UriMatcher.NO_MATCH).apply {
      addURI(AUTHORITY, "transactions", TRANSACTIONS)
      addURI(AUTHORITY, "transactions/$URI_SEGMENT_GROUPS/*", TRANSACTIONS_GROUPS)
      addURI(AUTHORITY, "transactions/$URI_SEGMENT_SUMS_FOR_ACCOUNTS", TRANSACTIONS_SUMS)
      addURI(AUTHORITY, "transactions/#", TRANSACTION_ID)
      addURI(AUTHORITY, "transactions/#/$URI_SEGMENT_MOVE/#", TRANSACTION_MOVE)
      addURI(AUTHORITY, "transactions/#/$URI_SEGMENT_TOGGLE_CRSTATUS", TRANSACTION_TOGGLE_CRSTATUS)
      addURI(AUTHORITY, "transactions/#/$URI_SEGMENT_UNDELETE", TRANSACTION_UNDELETE)
      //uses uuid in order to be usable from sync adapter
      addURI(AUTHORITY, "transactions/unsplit", UNSPLIT)
      addURI(AUTHORITY, "categories", CATEGORIES)
      addURI(AUTHORITY, "categories/#", CATEGORY_ID)
      addURI(AUTHORITY, "accounts", ACCOUNTS)
      addURI(AUTHORITY, "accountsbase", ACCOUNTS_BASE)
      addURI(AUTHORITY, "accounts/#", ACCOUNT_ID)
      addURI(AUTHORITY, "$URI_SEGMENT_GROUPING/*/*", ACCOUNT_ID_GROUPING)
      addURI(AUTHORITY, "$URI_SEGMENT_SORT/*/*/*", ACCOUNT_ID_SORT)
      addURI(AUTHORITY, "payees", PAYEES)
      addURI(AUTHORITY, "payees/#", PAYEE_ID)
      addURI(AUTHORITY, "methods", METHODS)
      addURI(AUTHORITY, "methods/#", METHOD_ID)
      //methods/typeFilter/{TransactionType}/{AccountType}
      //TransactionType: 1 Income, -1 Expense
      //AccountType: CASH BANK CCARD ASSET LIABILITY
      addURI(AUTHORITY, "methods/$URI_SEGMENT_TYPE_FILTER/*", METHODS_FILTERED)
      addURI(AUTHORITY, "accounttypes_methods", ACCOUNTTYPES_METHODS)
      addURI(AUTHORITY, "templates", TEMPLATES)
      addURI(AUTHORITY, "templates/#", TEMPLATE_ID)
      addURI(AUTHORITY, "templates/#/$URI_SEGMENT_INCREASE_USAGE", TEMPLATES_INCREASE_USAGE)
      addURI(AUTHORITY, "sqlite_sequence/*", SQLITE_SEQUENCE_TABLE)
      addURI(AUTHORITY, "planinstance_transaction", PLANINSTANCE_TRANSACTION_STATUS)
      addURI(AUTHORITY, "planinstance_transaction/#/#", PLANINSTANCE_STATUS_SINGLE)
      addURI(AUTHORITY, "currencies", CURRENCIES)
      addURI(AUTHORITY, "accounts/aggregates/*", AGGREGATE_ID)
      addURI(AUTHORITY, "aggregates/*/*", AGGREGATE_V2)
      addURI(AUTHORITY, "methods_transactions", MAPPED_METHODS)
      addURI(AUTHORITY, "dual", DUAL)
      addURI(AUTHORITY, "eventcache", EVENT_CACHE)
      addURI(AUTHORITY, "debug_schema", DEBUG_SCHEMA)
      addURI(AUTHORITY, "stale_images", STALE_IMAGES)
      addURI(AUTHORITY, "stale_images/#", STALE_IMAGES_ID)
      addURI(AUTHORITY, "transfer_account_transactions", MAPPED_TRANSFER_ACCOUNTS)
      addURI(AUTHORITY, "changes", CHANGES)
      addURI(AUTHORITY, "settings", SETTINGS)
      addURI(AUTHORITY, "autofill/#", AUTOFILL)
      addURI(AUTHORITY, "account_exchangerates/#/*/*", ACCOUNT_EXCHANGE_RATE)
      addURI(AUTHORITY, "budgets", BUDGETS)
      addURI(AUTHORITY, "budgets/#", BUDGET_ID)
      addURI(AUTHORITY, "budgets/#/#", BUDGET_CATEGORY)
      addURI(AUTHORITY, "budgets/#/$URI_SEGMENT_BUDGET_ALLOCATIONS", BUDGET_FOR_PERIOD)
      addURI(AUTHORITY, "currencies/*", CURRENCIES_CODE)
      addURI(AUTHORITY, "accountsMinimal", ACCOUNTS_MINIMAL)
      addURI(AUTHORITY, "tags", TAGS)
      addURI(AUTHORITY, "transactions/tags", TRANSACTIONS_TAGS)
      addURI(AUTHORITY, "tags/#", TAG_ID)
      addURI(AUTHORITY, "templates/tags", TEMPLATES_TAGS)
      addURI(AUTHORITY, "transactions/$URI_SEGMENT_LINK_TRANSFER/*", TRANSACTION_LINK_TRANSFER)
      addURI(AUTHORITY, "transactions/$URI_SEGMENT_UNLINK_TRANSFER/*", TRANSACTION_UNLINK_TRANSFER)
      addURI(AUTHORITY, "transactions/#/$URI_SEGMENT_TRANSFORM_TO_TRANSFER/#", TRANSACTION_TRANSFORM_TO_TRANSFER)
      addURI(AUTHORITY, "accounts/tags", ACCOUNTS_TAGS)
      addURI(AUTHORITY, "debts", DEBTS)
      addURI(AUTHORITY, "debts/#", DEBT_ID)
      addURI(AUTHORITY, "budgets/$URI_SEGMENT_BUDGET_ALLOCATIONS", BUDGET_ALLOCATIONS)
      addURI(AUTHORITY, "budgets/$URI_SEGMENT_DEFAULT_BUDGET_ALLOCATIONS/*/*", ACCOUNT_DEFAULT_BUDGET_ALLOCATIONS)
      addURI(AUTHORITY, "banks", BANKS)
      addURI(AUTHORITY, "banks/#", BANK_ID)
      addURI(AUTHORITY, "attributes", ATTRIBUTES)
      addURI(AUTHORITY, "transactions/attributes", TRANSACTION_ATTRIBUTES)
      addURI(AUTHORITY, "accounts/attributes", ACCOUNT_ATTRIBUTES)
      addURI(AUTHORITY, "transactions/attachments", TRANSACTION_ATTACHMENTS)
      addURI(AUTHORITY, "attachments", ATTACHMENTS)
      addURI(AUTHORITY, "transactions/attachments/#/#", TRANSACTION_ID_ATTACHMENT_ID)
      addURI(AUTHORITY, "transactions/$URI_SEGMENT_UNARCHIVE", UNARCHIVE)
      addURI(AUTHORITY, "transactions/#/$URI_SEGMENT_SUMS_FOR_ARCHIVE", ARCHIVE_SUMS)
      addURI(AUTHORITY, "prices", PRICES)
      addURI(AUTHORITY, "latest_prices", LATEST_PRICES)
      addURI(AUTHORITY, "dynamicCurrencies", DYNAMIC_CURRENCIES)
      addURI(AUTHORITY, "accountTypes", ACCOUNT_TYPES)
      addURI(AUTHORITY, "accountTypes/#", ACCOUNT_TYPE_ID)
      addURI(AUTHORITY, "accountFlags", ACCOUNT_FLAGS)
      addURI(AUTHORITY, "accountFlags/#", ACCOUNT_FLAG_ID)
    }

    @JvmStatic
    fun resumeChangeTrigger(): ContentProviderOperation =
      ContentProviderOperation.newDelete(
        DUAL_URI.buildUpon()
          .appendQueryParameter(QUERY_PARAMETER_SYNC_END, "1").build()
      ).build()

    @JvmStatic
    fun resumeChangeTrigger(db: SupportSQLiteDatabase): Int =
      db.delete(TABLE_SYNC_STATE, null, null)

    @JvmStatic
    fun pauseChangeTrigger(): ContentProviderOperation =
      ContentProviderOperation.newInsert(
        DUAL_URI.buildUpon()
          .appendQueryParameter(QUERY_PARAMETER_SYNC_BEGIN, "1").build()
      ).build()

    @JvmStatic
    fun pauseChangeTrigger(db: SupportSQLiteDatabase): Long {
      val values = ContentValues(1)
      values.put(KEY_STATUS, "1")
      return db.insert(TABLE_SYNC_STATE, values)
    }
  }
}
