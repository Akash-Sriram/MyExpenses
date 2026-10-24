package org.totschnig.myexpenses.provider.filter

import android.content.Context
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.provider.CTE_SEARCH
import org.totschnig.myexpenses.provider.KEY_AMOUNT
import org.totschnig.myexpenses.provider.KEY_CATID
import org.totschnig.myexpenses.provider.KEY_COMMENT
import org.totschnig.myexpenses.provider.KEY_IBAN
import org.totschnig.myexpenses.provider.KEY_PARENTID
import org.totschnig.myexpenses.provider.KEY_PAYEEID
import org.totschnig.myexpenses.provider.KEY_PAYEE_NAME
import org.totschnig.myexpenses.provider.KEY_REFERENCE_NUMBER
import org.totschnig.myexpenses.provider.KEY_ROWID
import org.totschnig.myexpenses.provider.KEY_SHORT_NAME
import org.totschnig.myexpenses.provider.KEY_TRANSFER_PEER
import org.totschnig.myexpenses.provider.SPLIT_CATID
import org.totschnig.myexpenses.provider.TABLE_CATEGORIES
import org.totschnig.myexpenses.provider.TABLE_PAYEES
import org.totschnig.myexpenses.provider.TABLE_TRANSACTIONS
import org.totschnig.myexpenses.util.Utils
import kotlin.math.abs

@Parcelize
@Serializable
@SerialName("universal")
data class UniversalSearchCriterion(
    val query: String = "",
    val typeFilter: String? = null // "INCOME", "EXPENSE", "TRANSFER"
) : Criterion {

    private fun buildCondition(table: String): String {
        val conditions = mutableListOf<String>()

        val trimmedQuery = query.trim()
        if (trimmedQuery.isNotEmpty()) {
            val safeLike = "%" + Utils.escapeSqlLikeExpression(trimmedQuery).replace("'", "''") + "%"
            val likeClause = "LIKE '$safeLike' ESCAPE '/'"

            val textMatches = mutableListOf<String>()
            textMatches.add("$KEY_COMMENT $likeClause")
            textMatches.add("$KEY_REFERENCE_NUMBER $likeClause")
            textMatches.add("$KEY_PAYEEID IN (SELECT $KEY_ROWID FROM $TABLE_PAYEES WHERE $KEY_PAYEE_NAME $likeClause OR $KEY_SHORT_NAME $likeClause OR $KEY_IBAN $likeClause)")
            textMatches.add("$KEY_CATID IN (SELECT $KEY_ROWID FROM $TABLE_CATEGORIES WHERE label $likeClause)")

            val cleanNumStr = trimmedQuery.replace(",", "").replace("₹", "").replace("$", "").trim()
            val numLong = cleanNumStr.toLongOrNull() ?: cleanNumStr.toDoubleOrNull()?.let { (it * 100).toLong() }
            if (numLong != null && numLong != 0L) {
                textMatches.add("abs($KEY_AMOUNT) = ${abs(numLong)}")
                if (cleanNumStr.indexOf('.') == -1) {
                    textMatches.add("abs($KEY_AMOUNT) = ${abs(numLong) * 100}")
                }
            }
            conditions.add(textMatches.joinToString(separator = " OR ", prefix = "(", postfix = ")"))
        }

        when (typeFilter?.uppercase()) {
            "INCOME" -> conditions.add("$KEY_AMOUNT > 0")
            "EXPENSE" -> conditions.add("$KEY_AMOUNT < 0")
            "TRANSFER" -> conditions.add("$KEY_TRANSFER_PEER IS NOT NULL")
        }

        return if (conditions.isEmpty()) "1=1" else conditions.joinToString(" AND ")
    }

    override fun getSelectionForParents(tableName: String?, forExport: Boolean): String {
        val table = tableName ?: CTE_SEARCH
        val cond = buildCondition(table)
        return "($cond OR ($KEY_CATID = $SPLIT_CATID AND exists(SELECT 1 FROM $table children WHERE $KEY_PARENTID = $table.$KEY_ROWID AND ($cond))))"
    }

    override fun getSelectionForParts(tableName: String?): String {
        val table = tableName ?: CTE_SEARCH
        val cond = buildCondition(table)
        return "($cond OR exists(SELECT 1 FROM $TABLE_TRANSACTIONS parents WHERE $KEY_ROWID = $table.$KEY_PARENTID AND ($cond)))"
    }

    override fun getSelectionArgs(queryParts: Boolean): Array<String> = emptyArray()

    override fun prettyPrint(context: Context): String {
        val parts = mutableListOf<String>()
        if (query.isNotBlank()) parts.add(query)
        typeFilter?.let {
            val typeLabel = when (it.uppercase()) {
                "INCOME" -> context.getString(R.string.income)
                "EXPENSE" -> context.getString(R.string.expense)
                "TRANSFER" -> context.getString(R.string.transfer)
                else -> it
            }
            parts.add(typeLabel)
        }
        return parts.joinToString(" • ").ifEmpty { context.getString(R.string.menu_search) }
    }
}
