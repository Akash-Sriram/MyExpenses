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
import org.totschnig.myexpenses.viewmodel.data.DisplayDebt

class DebtOverviewPdfGenerator(private val context: Context) {

    private val currencyFormatter = context.injector.currencyFormatter()
    private val homeCurrencyUnit = context.injector.currencyContext().homeCurrencyUnit
    private val prefHandler = context.injector.prefHandler()

    fun generatePdf(
        destDir: DocumentFile,
        groups: Collection<List<DisplayDebt>>,
    ): Pair<Uri, String> {
        val fileName = "DebtOverview"
        val outputFile = timeStampedFile(
            destDir,
            fileName,
            "application/pdf", "pdf"
        ) ?: throw createFileFailure(context, destDir, fileName)

        val writer = PdfDocumentWriter(context, prefHandler)

        try {
            writer.drawParagraph(
                context.getString(R.string.title_activity_debt_overview),
                FontType.TITLE,
                CellAlignment.CENTER,
                spacingAfter = 12f
            )

            val columnWeights = floatArrayOf(3f, 1f)
            val rows = mutableListOf<PdfRow>()

            groups.forEach { debts ->
                rows.add(
                    PdfRow(
                        listOf(
                            PdfCell(
                                text = debts.first().payeeName ?: "",
                                fontType = FontType.BALANCE_SECTION,
                                alignment = CellAlignment.LEFT,
                                paddingTop = 8f,
                                paddingBottom = 4f
                            ),
                            PdfCell(
                                text = formatCurrency(debts.sumOf { it.currentEquivalentBalance }),
                                fontType = if (debts.sumOf { it.currentEquivalentBalance } < 0) FontType.EXPENSE_BOLD else FontType.INCOME_BOLD,
                                alignment = CellAlignment.RIGHT,
                                paddingTop = 8f,
                                paddingBottom = 4f
                            )
                        )
                    )
                )

                debts.forEach { debt ->
                    rows.add(
                        PdfRow(
                            listOf(
                                PdfCell(
                                    text = debt.label ?: "",
                                    fontType = FontType.NORMAL,
                                    alignment = CellAlignment.LEFT,
                                    paddingTop = 1f,
                                    paddingBottom = 4f
                                ),
                                PdfCell(
                                    text = formatCurrency(debt.currentEquivalentBalance),
                                    fontType = if (debt.currentEquivalentBalance < 0) FontType.EXPENSE else FontType.INCOME,
                                    alignment = CellAlignment.RIGHT,
                                    paddingTop = 1f,
                                    paddingBottom = 4f
                                )
                            )
                        )
                    )
                }

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

            val totalAggregate = groups.flatten().sumOf { it.currentEquivalentBalance }
            rows.add(
                PdfRow(
                    listOf(
                        PdfCell(
                            text = context.getString(R.string.menu_aggregates),
                            fontType = FontType.BALANCE_CHAPTER,
                            alignment = CellAlignment.LEFT,
                            paddingTop = 8f,
                            paddingBottom = 8f
                        ),
                        PdfCell(
                            text = formatCurrency(totalAggregate),
                            fontType = if (totalAggregate < 0) FontType.EXPENSE_BOLD else FontType.INCOME_BOLD,
                            alignment = CellAlignment.RIGHT,
                            paddingTop = 8f,
                            paddingBottom = 8f
                        )
                    )
                )
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
