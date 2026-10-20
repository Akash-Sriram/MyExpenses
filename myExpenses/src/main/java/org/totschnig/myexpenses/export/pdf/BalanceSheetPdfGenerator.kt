package org.totschnig.myexpenses.export.pdf

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.export.createFileFailure
import org.totschnig.myexpenses.injector
import org.totschnig.myexpenses.model.CurrencyUnit
import org.totschnig.myexpenses.model.Money
import org.totschnig.myexpenses.util.AppDirHelper.timeStampedFile
import org.totschnig.myexpenses.util.formatMoney
import org.totschnig.myexpenses.util.io.displayName
import org.totschnig.myexpenses.viewmodel.data.BalanceData
import org.totschnig.myexpenses.viewmodel.data.BalanceSection
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.abs

class BalanceSheetPdfGenerator(private val context: Context) {

    private val currencyFormatter = context.injector.currencyFormatter()
    private val homeCurrencyUnit = context.injector.currencyContext().homeCurrencyUnit
    private val prefHandler = context.injector.prefHandler()

    fun generatePdf(
        destDir: DocumentFile,
        data: BalanceData,
        debts: Long,
        date: LocalDate
    ): Pair<Uri, String> {
        val fileName = "BalanceSheet"
        val outputFile = timeStampedFile(
            destDir,
            fileName,
            "application/pdf", "pdf"
        ) ?: throw createFileFailure(context, destDir, fileName)

        val (assets, totalAssets, liabilities, totalLiabilities) = data
        val writer = PdfDocumentWriter(context, prefHandler)

        try {
            writer.drawParagraph(
                context.getString(R.string.balance_sheet),
                FontType.TITLE,
                CellAlignment.CENTER,
                spacingAfter = 4f
            )

            val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)
            writer.drawParagraph(
                date.format(formatter),
                FontType.NORMAL,
                CellAlignment.CENTER,
                spacingAfter = 12f
            )

            val columnWeights = floatArrayOf(3f, 1f)
            val rows = mutableListOf<PdfRow>()

            fun addLine(
                label: String?,
                amount: Long,
                isAbsolute: Boolean = true,
                labelFontType: FontType = FontType.NORMAL,
                expenseFontType: FontType = FontType.EXPENSE,
                incomeFontType: FontType = FontType.INCOME,
                paddingTop: Float = 4f,
                paddingBottom: Float = 4f
            ) {
                val formattedAmount = formatCurrency(if (isAbsolute) abs(amount) else amount)
                val font = if (amount < 0) expenseFontType else incomeFontType
                rows.add(
                    PdfRow(
                        listOf(
                            PdfCell(
                                text = label ?: "",
                                fontType = labelFontType,
                                alignment = CellAlignment.LEFT,
                                paddingTop = paddingTop,
                                paddingBottom = paddingBottom
                            ),
                            PdfCell(
                                text = formattedAmount,
                                fontType = font,
                                alignment = CellAlignment.RIGHT,
                                paddingTop = paddingTop,
                                paddingBottom = paddingBottom
                            )
                        )
                    )
                )
            }

            fun addSeparator() {
                rows.add(
                    PdfRow(
                        listOf(
                            PdfCell(
                                text = "",
                                borderBottom = true,
                                colSpan = 2,
                                paddingTop = 2f,
                                paddingBottom = 2f
                            )
                        )
                    )
                )
            }

            fun addAccountTypeSection(section: BalanceSection) {
                addLine(
                    label = section.type.title(context),
                    amount = section.total,
                    isAbsolute = true,
                    labelFontType = FontType.BALANCE_SECTION,
                    paddingTop = 4f,
                    paddingBottom = 4f
                )
                for (account in section.accounts) {
                    addLine(
                        label = account.label,
                        amount = account.currentBalance,
                        isAbsolute = true,
                        paddingTop = 1f,
                        paddingBottom = 2f
                    )
                }
            }

            addLine(
                label = context.getString(R.string.balance_sheet_section_assets),
                amount = totalAssets,
                labelFontType = FontType.BALANCE_CHAPTER,
                expenseFontType = FontType.EXPENSE_BOLD,
                incomeFontType = FontType.INCOME_BOLD,
                paddingTop = 8f,
                paddingBottom = 6f
            )
            for (section in assets) {
                addAccountTypeSection(section)
            }

            addSeparator()

            addLine(
                label = context.getString(R.string.balance_sheet_section_liabilities),
                amount = totalLiabilities,
                labelFontType = FontType.BALANCE_CHAPTER,
                expenseFontType = FontType.EXPENSE_BOLD,
                incomeFontType = FontType.INCOME_BOLD,
                paddingTop = 8f,
                paddingBottom = 6f
            )
            for (section in liabilities) {
                addAccountTypeSection(section)
            }

            addSeparator()

            if (debts != 0L) {
                addLine(
                    label = context.getString(R.string.debts),
                    amount = debts,
                    isAbsolute = false,
                    labelFontType = FontType.BALANCE_CHAPTER,
                    expenseFontType = FontType.EXPENSE_BOLD,
                    incomeFontType = FontType.INCOME_BOLD,
                    paddingTop = 6f,
                    paddingBottom = 4f
                )
                addSeparator()
            }

            addLine(
                label = context.getString(R.string.balance_sheet_net_worth),
                amount = totalAssets + totalLiabilities + debts,
                isAbsolute = false,
                labelFontType = FontType.BALANCE_CHAPTER,
                expenseFontType = FontType.EXPENSE_BOLD,
                incomeFontType = FontType.INCOME_BOLD,
                paddingTop = 8f,
                paddingBottom = 8f
            )

            writer.drawTable(columnWeights, rows)

            context.contentResolver.openOutputStream(outputFile.uri)?.use { out ->
                writer.writeTo(out)
            }
        } finally {
            writer.close()
        }

        return outputFile.uri to outputFile.displayName
    }

    private fun formatCurrency(
        amount: Long,
        currency: CurrencyUnit = homeCurrencyUnit
    ): String {
        return currencyFormatter.formatMoney(Money(currency, amount))
    }
}
