package org.totschnig.myexpenses.export.pdf

import android.content.Context
import android.database.Cursor
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import androidx.documentfile.provider.DocumentFile
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.db2.FLAG_EXPENSE
import org.totschnig.myexpenses.db2.FLAG_INCOME
import org.totschnig.myexpenses.db2.FLAG_NEUTRAL
import org.totschnig.myexpenses.db2.FLAG_TRANSFER
import org.totschnig.myexpenses.db2.tagMap
import org.totschnig.myexpenses.export.createFileFailure
import org.totschnig.myexpenses.injector
import org.totschnig.myexpenses.model.CrStatus
import org.totschnig.myexpenses.model.CurrencyContext
import org.totschnig.myexpenses.model.CurrencyUnit
import org.totschnig.myexpenses.model.Grouping
import org.totschnig.myexpenses.model.Money
import org.totschnig.myexpenses.preference.ColorSource
import org.totschnig.myexpenses.preference.PrefHandler
import org.totschnig.myexpenses.preference.PrefKey
import org.totschnig.myexpenses.preference.printLayout
import org.totschnig.myexpenses.preference.printLayoutColumnWidths
import org.totschnig.myexpenses.provider.KEY_AMOUNT
import org.totschnig.myexpenses.provider.KEY_PARENTID
import org.totschnig.myexpenses.provider.SPLIT_CATID
import org.totschnig.myexpenses.provider.asSequence
import org.totschnig.myexpenses.provider.filter.Criterion
import org.totschnig.myexpenses.util.AppDirHelper.timeStampedFile
import org.totschnig.myexpenses.util.ICurrencyFormatter
import org.totschnig.myexpenses.util.Utils
import org.totschnig.myexpenses.util.convAmount
import org.totschnig.myexpenses.util.formatMoney
import org.totschnig.myexpenses.util.io.displayName
import org.totschnig.myexpenses.util.ui.dateTimeFormatterLegacy
import org.totschnig.myexpenses.viewmodel.Account
import org.totschnig.myexpenses.viewmodel.Amount
import org.totschnig.myexpenses.viewmodel.Category
import org.totschnig.myexpenses.viewmodel.CombinedField
import org.totschnig.myexpenses.viewmodel.Date
import org.totschnig.myexpenses.viewmodel.Field
import org.totschnig.myexpenses.viewmodel.Notes
import org.totschnig.myexpenses.viewmodel.OriginalAmount
import org.totschnig.myexpenses.viewmodel.Payee
import org.totschnig.myexpenses.viewmodel.PrintLayoutConfigurationViewModel.Companion.asColumns
import org.totschnig.myexpenses.viewmodel.ReferenceNumber
import org.totschnig.myexpenses.viewmodel.Tags
import org.totschnig.myexpenses.viewmodel.data.DateInfo
import org.totschnig.myexpenses.viewmodel.data.HeaderData
import org.totschnig.myexpenses.viewmodel.data.PageAccount
import org.totschnig.myexpenses.viewmodel.data.Transaction2
import org.totschnig.myexpenses.viewmodel.data.getIndicatorPrefixForLabel
import org.totschnig.myexpenses.viewmodel.data.mergeTransfers
import java.io.IOException
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.sign

object PdfPrinter {

    enum class HorizontalPosition {
        LEFT, CENTER, RIGHT;
    }

    enum class VerticalPosition {
        TOP, BOTTOM;
    }

    fun defaultPaperSize(context: Context) = when (Utils.getCountryFromTelephonyManager(context)) {
        "ph", "us", "bz", "ca", "pr", "cl", "co", "cr", "gt", "mx", "ni", "pa", "sv", "ve" -> "LETTER"
        else -> "A4"
    }

    @Throws(IOException::class)
    fun print(
        context: Context,
        account: PageAccount,
        destDir: DocumentFile,
        filter: Criterion?,
        colorSource: ColorSource,
    ): Pair<Uri, String> {
        val currencyFormatter = context.injector.currencyFormatter()
        val currencyContext = context.injector.currencyContext()
        val currencyUnit = currencyContext[account.currency]
        val prefHandler = context.injector.prefHandler()

        var selection = "$KEY_PARENTID is null"
        val selectionArgs: Array<String>
        if (filter != null) {
            selection += " AND " + filter.getSelectionForParents()
            selectionArgs = filter.getSelectionArgs(false)
        } else {
            selectionArgs = arrayOf()
        }
        val fileName = account.label.replace("\\W".toRegex(), "")
        val outputFile = timeStampedFile(
            destDir,
            fileName,
            "application/pdf", "pdf"
        ) ?: throw createFileFailure(context, destDir, fileName)

        val sortBy = if (KEY_AMOUNT == account.sortBy) {
            "abs($KEY_AMOUNT)"
        } else {
            account.sortBy
        }
        val uri = account.uriForTransactionList(
            shortenComment = false,
            extended = true
        )
        val projection = Transaction2.projection(
            account.isAggregate,
            account.grouping,
            prefHandler
        )
        context.contentResolver.query(
            uri,
            projection,
            selection, selectionArgs, sortBy + " " + account.sortDirection
        )!!.use { cursor ->
            if (cursor.count == 0) {
                throw Exception("No data")
            }

            val writer = PdfDocumentWriter(context, prefHandler)
            try {
                // Account Title
                writer.drawParagraph(account.label, FontType.TITLE, CellAlignment.LEFT, spacingAfter = 4f)

                // SubTitle: Current Balance
                val balanceColor = when (account.currentBalance.sign) {
                    1 -> 0xFF006800.toInt()
                    -1 -> 0xFF800000.toInt()
                    else -> Color.BLACK
                }
                val subTitle = SpannableStringBuilder().apply {
                    val label = context.getString(R.string.current_balance) + " : "
                    append(label)
                    setSpan(StyleSpan(Typeface.BOLD), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    val start = length
                    append(currencyFormatter.formatMoney(Money(currencyUnit, account.currentBalance)))
                    val end = length
                    setSpan(ForegroundColorSpan(balanceColor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                writer.drawParagraph(subTitle, FontType.BOLD, CellAlignment.LEFT, spacingAfter = 4f)

                // SubTitle 2: Filter if any
                filter?.prettyPrint(context)?.let {
                    writer.drawParagraph(it, FontType.BOLD, CellAlignment.LEFT, spacingAfter = 6f)
                }

                val itemDateFormat = dateTimeFormatterLegacy(account, prefHandler, context)?.first
                val columnsPreference = prefHandler.printLayout.asColumns()
                val columnWidthsPreference = prefHandler.printLayoutColumnWidths
                val (columns, columnWidths) = if (itemDateFormat == null) {
                    val finalColumns = mutableListOf<List<Field>>()
                    val finalColumnWidths = mutableListOf<Int>()
                    columnsPreference.map { column ->
                        column.mapNotNull { field -> if (field == Date) null else field }
                    }.forEachIndexed { index, list ->
                        if (list.isNotEmpty()) {
                            finalColumns.add(list)
                            finalColumnWidths.add(columnWidthsPreference[index])
                        }
                    }
                    finalColumns to finalColumnWidths
                } else columnsPreference to columnWidthsPreference

                if (columns.count { it.isNotEmpty() } == 0) {
                    throw Exception(context.getString(R.string.print_configuration_empty))
                }

                addTransactionList(
                    writer,
                    cursor,
                    context,
                    account,
                    filter,
                    currencyUnit,
                    currencyFormatter,
                    currencyContext,
                    columns,
                    columnWidths.toIntArray(),
                    colorSource,
                    itemDateFormat
                ) {
                    context.contentResolver.query(
                        uri,
                        projection,
                        "$KEY_PARENTID = ?",
                        arrayOf(it.toString()),
                        null
                    )!!
                }

                context.contentResolver.openOutputStream(outputFile.uri)?.use { out ->
                    writer.writeTo(out)
                }
            } finally {
                writer.close()
            }
            return outputFile.uri to outputFile.displayName
        }
    }

    private fun addTransactionList(
        writer: PdfDocumentWriter,
        transactionCursor: Cursor,
        context: Context,
        account: PageAccount,
        filter: Criterion?,
        currencyUnit: CurrencyUnit,
        currencyFormatter: ICurrencyFormatter,
        currencyContext: CurrencyContext,
        columns: List<List<Field>>,
        columnWidths: IntArray,
        colorSource: ColorSource,
        itemDateFormat: DateTimeFormatter?,
        splitCursor: (Long) -> Cursor,
    ) {
        val (builder, selection, selectionArgs) = account.groupingQuery(filter)
        val integerHeaderRowMap = context.contentResolver.query(
            builder.build(), null, selection, selectionArgs, null
        )!!.use {
            HeaderData.fromSequence(
                account.openingBalance,
                account.grouping,
                currencyUnit,
                it.asSequence
            )
        }

        var prevHeaderId: Int? = null
        val tagMap = context.contentResolver.tagMap

        fun columnAlignment(fields: List<Field>) =
            if (fields.all { it is Amount || it is OriginalAmount })
                CellAlignment.RIGHT else CellAlignment.LEFT

        val colWeights = columnWidths.map { it.toFloat() }.toFloatArray()
        val currentGroupRows = mutableListOf<PdfRow>()

        fun flushGroupTable() {
            if (currentGroupRows.isNotEmpty()) {
                writer.drawTable(colWeights, currentGroupRows, headerRows = 1)
                currentGroupRows.clear()
            }
        }

        fun buildHeaderRow(): PdfRow {
            val cells = columns.mapIndexed { index, fields ->
                val alignment = columnAlignment(fields)
                val extra = Bundle(1).apply {
                    putBoolean(Date.KEY_IS_TIME_FIELD, account.grouping == Grouping.DAY)
                }
                PdfCell(
                    spans = fields.map { CellTextSpan(it.toString(context, extra), FontType.BOLD) },
                    alignment = alignment,
                    borderTop = true,
                    borderBottom = true,
                    borderLeft = index == 0,
                    borderRight = index != columns.lastIndex,
                    paddingTop = 4f,
                    paddingBottom = 4f
                )
            }
            return PdfRow(cells, isHeader = true)
        }

        for (transaction in transactionCursor.asSequence.map {
            Transaction2.fromCursor(
                currencyContext = currencyContext,
                cursor = it,
                tags = tagMap,
                accountCurrency = account.currencyUnit,
                grouping = account.grouping
            )
        }.asIterable().let {
            if (account.isAggregate) it.mergeTransfers(
                account,
                currencyContext.homeCurrencyString
            ) else it
        }) {
            val currentHeaderId = account.grouping.calculateGroupId(transaction)
            if (currentHeaderId != prevHeaderId) {
                flushGroupTable()
                val headerRow = integerHeaderRowMap[currentHeaderId]!!

                if (account.grouping != Grouping.NONE) {
                    val groupTitle = account.grouping.getDisplayTitle(
                        ctx = context,
                        groupYear = transaction.year,
                        groupSecond = headerRow.second,
                        dateInfo = DateInfo.load(context.contentResolver),
                        weekStart = headerRow.weekStart,
                        weekRangeOnly = true,
                        relativeDay = false
                    )
                    writer.drawParagraph(groupTitle, FontType.HEADER, CellAlignment.LEFT, spacingAfter = 4f)
                }

                val sumExpense = headerRow.expenseSum
                val sumIncome = headerRow.incomeSum
                val sumTransfer = headerRow.transferSum
                val (_, amountMinor) = headerRow.delta
                val interimBalance = headerRow.interimBalance
                val formattedDelta = String.format(
                    "%s %s", if (amountMinor.sign > -1) "+" else "-",
                    currencyFormatter.convAmount(abs(amountMinor), currencyUnit)
                )

                val summaryRows = mutableListOf<PdfRow>()
                if (filter == null) {
                    summaryRows.add(
                        PdfRow(
                            listOf(
                                PdfCell(
                                    text = "${context.getString(R.string.at_start)}: ${currencyFormatter.formatMoney(headerRow.previousBalance)}",
                                    fontType = FontType.NORMAL,
                                    alignment = CellAlignment.LEFT,
                                    borderTop = true,
                                    borderLeft = true,
                                    paddingTop = 4f,
                                    paddingBottom = 2f
                                ),
                                PdfCell(
                                    text = "Δ: $formattedDelta",
                                    fontType = FontType.NORMAL,
                                    alignment = CellAlignment.CENTER,
                                    borderTop = true,
                                    paddingTop = 4f,
                                    paddingBottom = 2f
                                ),
                                PdfCell(
                                    text = "${context.getString(R.string.at_end)}: ${currencyFormatter.formatMoney(interimBalance)}",
                                    fontType = FontType.NORMAL,
                                    alignment = CellAlignment.RIGHT,
                                    borderTop = true,
                                    borderRight = true,
                                    paddingTop = 4f,
                                    paddingBottom = 2f
                                )
                            )
                        )
                    )
                } else {
                    summaryRows.add(
                        PdfRow(
                            listOf(
                                PdfCell(
                                    text = "Δ: $formattedDelta",
                                    fontType = FontType.NORMAL,
                                    alignment = CellAlignment.CENTER,
                                    borderTop = true,
                                    borderLeft = true,
                                    borderRight = true,
                                    colSpan = 3,
                                    paddingTop = 4f,
                                    paddingBottom = 2f
                                )
                            )
                        )
                    )
                }

                val line2Span = SpannableStringBuilder().apply {
                    append(context.getString(R.string.sum_income)).append(": + ")
                    val startInc = length
                    append(currencyFormatter.formatMoney(sumIncome))
                    setSpan(ForegroundColorSpan(0xFF006800.toInt()), startInc, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

                    append(" | ").append(context.getString(R.string.sum_expenses)).append(": - ")
                    val startExp = length
                    append(currencyFormatter.formatMoney(sumExpense.absolute()))
                    setSpan(ForegroundColorSpan(0xFF800000.toInt()), startExp, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

                    append(" | ").append(context.getString(R.string.sum_transfer)).append(": ")
                    val prefix = when (sumTransfer.amountMinor.sign) {
                        1 -> "+ "
                        -1 -> "- "
                        else -> ""
                    }
                    append(prefix)
                    val startTrans = length
                    append(currencyFormatter.formatMoney(sumTransfer.absolute()))
                    setSpan(ForegroundColorSpan(0xFF000080.toInt()), startTrans, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }

                summaryRows.add(
                    PdfRow(
                        listOf(
                            PdfCell(
                                text = line2Span,
                                alignment = CellAlignment.CENTER,
                                borderBottom = true,
                                borderLeft = true,
                                borderRight = true,
                                colSpan = 3,
                                paddingTop = 2f,
                                paddingBottom = 4f
                            )
                        )
                    )
                )

                writer.drawTable(floatArrayOf(1f, 1f, 1f), summaryRows)
                writer.ensureSpace(6f)
                writer.currentY += 6f

                currentGroupRows.add(buildHeaderRow())
                prevHeaderId = currentHeaderId
            }

            fun Transaction2.printField(field: Field): List<String> {
                return when (field) {
                    is CombinedField -> listOf(field.fields.flatMap { printField(it) }
                        .joinToString(" / "))

                    Amount -> listOf(
                        if (account.isHomeAggregate)
                            amount?.let {
                                currencyFormatter.formatMoney(
                                    if (type == FLAG_NEUTRAL) it.absolute() else it
                                )
                            } else null,
                        currencyFormatter.formatMoney(
                            if (type == FLAG_NEUTRAL) displayAmount.absolute() else displayAmount
                        )
                    )

                    OriginalAmount -> listOf(originalAmount?.let {
                        currencyFormatter.formatMoney(if (type == FLAG_NEUTRAL) it.absolute() else it)
                    })

                    Category -> listOf(categoryPath)
                    Date -> listOf(
                        if (isSplitPart) null else
                            itemDateFormat?.let {
                                Utils.convDateTime(_date, it)
                            }
                    )

                    Notes -> listOf(comment)
                    Payee -> listOf(party?.displayName)
                    Tags -> listOf(tagList.joinToString { it.label })

                    ReferenceNumber -> listOf(referenceNumber)
                    Account -> listOf(buildString {
                        if (account.id < 0 && !isSplitPart) {
                            append(accountLabel)
                        }
                        if (transferPeer != null) {
                            if (isNotEmpty()) {
                                append(" ")
                            }
                            append(getIndicatorPrefixForLabel(displayAmount.amountMinor) + transferAccountLabel)
                        }
                    })
                }
                    .filterNotNull()
                    .filter { it.isNotEmpty() }
            }

            fun Transaction2.createRow(
                isSplitPart: Boolean = false,
                isLastSplit: Boolean = false
            ): PdfRow {
                val isVoid = crStatus == CrStatus.VOID
                val cells = columns.mapIndexed { index, fields ->
                    val finalFields = if (isSplitPart) fields.filter { it != Payee } else fields
                    val borderRight = index != columns.lastIndex
                    val borderTop = !isSplitPart
                    val borderBottom = !isSplitPart || isLastSplit

                    val spans: List<CellTextSpan> = finalFields.flatMap { field ->
                        val fontType = when (field) {
                            is Amount, OriginalAmount -> {
                                if (account.id >= 0 || !isSameCurrency)
                                    (colorSource.transformType(type)
                                        ?: when (displayAmount.amountMinor.sign) {
                                            1 -> FLAG_INCOME
                                            -1 -> FLAG_EXPENSE
                                            else -> FLAG_NEUTRAL
                                        }).asFontType(isSplitPart) else FontType.NORMAL
                            }
                            else -> if (isSplitPart) FontType.SMALL else FontType.NORMAL
                        }
                        printField(field).map { CellTextSpan(it, fontType) }
                    }

                    PdfCell(
                        spans = spans,
                        alignment = columnAlignment(finalFields),
                        borderTop = borderTop,
                        borderBottom = borderBottom,
                        borderLeft = index == 0,
                        borderRight = borderRight,
                        paddingTop = if (isSplitPart) 1f else 3f,
                        paddingBottom = if (isSplitPart && !isLastSplit) 1f else 3f,
                        paddingLeft = 4f,
                        paddingRight = 4f,
                        isStrikeThrough = isVoid
                    )
                }
                return PdfRow(cells)
            }

            currentGroupRows.add(transaction.createRow())

            if (SPLIT_CATID == transaction.catId) {
                splitCursor(transaction.id).use { cursor ->
                    val list = cursor.asSequence.map {
                        Transaction2.fromCursor(
                            currencyContext = currencyContext,
                            cursor = it,
                            tags = tagMap,
                            accountCurrency = account.currencyUnit,
                            grouping = account.grouping
                        )
                    }.toList()
                    list.forEachIndexed { index, split ->
                        currentGroupRows.add(
                            split.createRow(
                                isSplitPart = true,
                                isLastSplit = index == list.lastIndex
                            )
                        )
                    }
                }
            }
        }

        flushGroupTable()
    }

    private fun Byte.asFontType(isSplitPart: Boolean) = when (this) {
        FLAG_INCOME -> if (isSplitPart) FontType.INCOME_SMALL else FontType.INCOME
        FLAG_EXPENSE -> if (isSplitPart) FontType.EXPENSE_SMALL else FontType.EXPENSE
        FLAG_TRANSFER -> if (isSplitPart) FontType.TRANSFER_SMALL else FontType.TRANSFER
        else -> if (isSplitPart) FontType.SMALL else FontType.NORMAL
    }
}
