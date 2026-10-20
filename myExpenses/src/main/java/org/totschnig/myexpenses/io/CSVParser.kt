package org.totschnig.myexpenses.io

import android.content.Context
import org.totschnig.myexpenses.util.csv.CSVRecord
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.export.CSV_INDICATOR
import org.totschnig.myexpenses.export.CSV_PART_INDICATOR
import org.totschnig.myexpenses.export.CategoryInfo
import org.totschnig.myexpenses.export.qif.QifDateFormat
import org.totschnig.myexpenses.export.qif.QifUtils
import org.totschnig.myexpenses.model.AccountType
import org.totschnig.myexpenses.model.CurrencyUnit
import org.totschnig.myexpenses.util.timeFormatter
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

class CSVParser(
    private val context: Context,
    private val data: List<CSVRecord>,
    private val columnToFieldMap: IntArray,
    private val dateFormat: QifDateFormat,
    private val currency: CurrencyUnit,
    private val type: AccountType
) {

    private class MutableAccount(
        val memo: String,
        val type: String?,
        val transactions: MutableList<MutableTransaction> = mutableListOf()
    ) {
        fun toImportAccount(): ImportAccount = ImportAccount(
            type = type,
            memo = memo,
            transactions = transactions.map { it.toImportTransaction() }
        )
    }

    private class MutableTransaction(
        var date: Instant = Instant.now(),
        var valueDate: Instant? = null,
        var amount: BigDecimal = BigDecimal.ZERO,
        var payee: String? = null,
        var memo: String? = null,
        var category: String? = null,
        var categoryClass: String? = null,
        var toAccount: String? = null,
        var toAmount: BigDecimal? = null,
        var status: String? = null,
        var number: String? = null,
        var method: String? = null,
        val tags: MutableList<String> = mutableListOf(),
        val splits: MutableList<MutableTransaction> = mutableListOf()
    ) {
        fun toImportTransaction(): ImportTransaction = ImportTransaction(
            date = date,
            valueDate = valueDate,
            amount = amount,
            payee = payee,
            memo = memo,
            category = category,
            categoryClass = categoryClass,
            toAccount = toAccount,
            toAmount = toAmount,
            status = status,
            number = number,
            method = method,
            tags = tags.takeIf { it.isNotEmpty() },
            splits = splits.takeIf { it.isNotEmpty() }?.map { it.toImportTransaction() }
        )
    }

    private val accountList = mutableListOf<MutableAccount>()

    val accounts: List<ImportAccount> by lazy {
        accountList.map { it.toImportAccount() }
    }

    val categories: MutableSet<CategoryInfo> = mutableSetOf()
    val payees: MutableSet<String> = mutableSetOf()
    val tags: MutableSet<String> = mutableSetOf()

    private fun requireAccount(label: String) =
        accountList.find { it.memo == label } ?: MutableAccount(label, type.name).also {
            accountList.add(it)
        }

    private fun saveGetFromRecord(record: CSVRecord, index: Int) =
        if (record.size() > index) record[index].trim() else ""

    fun parse() {
        val columnIndexAccount = columnToFieldMap.indexOf(R.string.account)
        val columnIndexAmount = columnToFieldMap.indexOf(R.string.amount)
        val columnIndexExpense = columnToFieldMap.indexOf(R.string.expense)
        val columnIndexIncome = columnToFieldMap.indexOf(R.string.income)
        val columnIndexDate = columnToFieldMap.indexOf(R.string.date).takeIf { it > -1 }
            ?: columnToFieldMap.indexOf(R.string.booking_date)
        val columnIndexValueDate = columnToFieldMap.indexOf(R.string.value_date)
        val columnIndexTime = columnToFieldMap.indexOf(R.string.time)
        val columnIndexPayee = columnToFieldMap.indexOf(R.string.payer_or_payee)
        val columnIndexNotes = columnToFieldMap.indexOf(R.string.notes)
        val columnIndexCategory = columnToFieldMap.indexOf(R.string.category)
        val columnIndexSubcategory = columnToFieldMap.indexOf(R.string.subcategory)
        val columnIndexMethod = columnToFieldMap.indexOf(R.string.method)
        val columnIndexStatus = columnToFieldMap.indexOf(R.string.status)
        val columnIndexNumber = columnToFieldMap.indexOf(R.string.reference_number)
        val columnIndexSplit = columnToFieldMap.indexOf(R.string.split_transaction)
        val defaultAccount = MutableAccount(memo = "", type = null)

        var isSplitParent = false
        var isSplitPart = false
        var splitParent: MutableTransaction? = null
        for (record in data) {
            val transaction = MutableTransaction()
            if (columnIndexSplit != -1) {
                val split = saveGetFromRecord(record, columnIndexSplit)
                isSplitPart = split == CSV_PART_INDICATOR
                isSplitParent = split == CSV_INDICATOR
            }

            transaction.amount = try {
                if (columnIndexAmount != -1) {
                    QifUtils.parseMoney(saveGetFromRecord(record, columnIndexAmount), currency)
                } else {
                    val income = if (columnIndexIncome != -1) QifUtils.parseMoney(
                        saveGetFromRecord(
                            record,
                            columnIndexIncome
                        ), currency
                    ).abs() else BigDecimal(0)
                    val expense = if (columnIndexExpense != -1) QifUtils.parseMoney(
                        saveGetFromRecord(
                            record,
                            columnIndexExpense
                        ), currency
                    ).abs() else BigDecimal(0)
                    income.subtract(expense)
                }
            } catch (_: IllegalArgumentException) {
                BigDecimal.ZERO
            }

            if (!isSplitParent && columnIndexCategory != -1) {
                val category: String = saveGetFromRecord(record, columnIndexCategory)
                if (category != "") {
                    val subCategory = if (columnIndexSubcategory != -1) saveGetFromRecord(
                        record,
                        columnIndexSubcategory
                    ) else ""
                    if (category == context.getString(R.string.transfer) &&
                        subCategory != "" &&
                        QifUtils.isTransferCategory(subCategory)
                    ) {
                        transaction.toAccount = subCategory.substring(1, subCategory.length - 1)
                    } else if (QifUtils.isTransferCategory(category)) {
                        transaction.toAccount = category.substring(1, category.length - 1)
                    } else {
                        val category1 = category +
                                (subCategory.takeIf { it.isNotEmpty() }?.let { ":$it" } ?: "")
                        categories.add(CategoryInfo(category1))
                        transaction.category = category1
                    }
                }
            }
            val dateRecord =
                columnIndexDate.takeIf { it != -1 }?.let { saveGetFromRecord(record, it) }

            val timeRecord =
                columnIndexTime.takeIf { it != -1 }?.let { saveGetFromRecord(record, it) }
            val baseDate = dateRecord?.let { QifUtils.parseDate(it, dateFormat) } ?: Instant.now()
            if (timeRecord != null) {
                val time = try {
                    LocalTime.parse(timeRecord, context.timeFormatter)
                } catch (_: Exception) {
                    null
                }
                transaction.date = if (time != null) {
                    baseDate.atZone(ZoneId.systemDefault())
                        .withHour(time.hour)
                        .withMinute(time.minute)
                        .withSecond(time.second)
                        .toInstant()
                } else {
                    baseDate
                }
            } else if (dateRecord != null) {
                transaction.date = baseDate
            }
            if (columnIndexValueDate != -1) {
                transaction.valueDate = QifUtils.parseDate(
                    saveGetFromRecord(
                        record,
                        columnIndexValueDate
                    ), dateFormat
                )
            }
            if (columnIndexPayee != -1) {
                saveGetFromRecord(record, columnIndexPayee).takeIf { it.isNotEmpty() }?.let {
                    payees.add(it)
                    transaction.payee = it
                }
            }
            if (columnIndexNotes != -1) {
                transaction.memo = saveGetFromRecord(record, columnIndexNotes)
            }
            if (columnIndexMethod != -1) {
                transaction.method = saveGetFromRecord(record, columnIndexMethod)
            }
            if (columnIndexStatus != -1) {
                transaction.status = saveGetFromRecord(record, columnIndexStatus)
            }
            if (columnIndexNumber != -1) {
                transaction.number = saveGetFromRecord(record, columnIndexNumber)
            }
            columnToFieldMap.indexOf(R.string.tags).takeIf { it != -1 }?.let { it ->
                saveGetFromRecord(record, it).takeIf { it.isNotEmpty() }?.let { tagList ->
                    val parsedTags = parseTagList(tagList)
                    transaction.tags.addAll(parsedTags)
                    tags.addAll(parsedTags)
                }
            }
            if (isSplitParent) {
                splitParent = transaction
            }
            if (isSplitPart) {
                transaction.date = splitParent?.date ?: transaction.date
                splitParent?.splits?.add(transaction)
            } else {
                (if (columnIndexAccount != -1) {
                    requireAccount(saveGetFromRecord(record, columnIndexAccount))
                } else defaultAccount)
                    .transactions.add(transaction)
            }
        }
        if (columnIndexAccount == -1) {
            accountList.add(defaultAccount)
        }
    }

    private fun parseTagList(input: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < input.length) {
            val c = input[i]
            when {
                c == '"' -> {
                    if (inQuotes && i + 1 < input.length && input[i + 1] == '"') {
                        current.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                c == ',' && !inQuotes -> {
                    val token = current.toString().trim()
                    if (token.isNotEmpty()) {
                        result.add(token)
                    }
                    current.clear()
                }
                else -> current.append(c)
            }
            i++
        }
        val token = current.toString().trim()
        if (token.isNotEmpty()) {
            result.add(token)
        }
        return result
    }
}