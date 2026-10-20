package org.totschnig.myexpenses.export.pdf

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.View
import androidx.core.text.layoutDirection
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.preference.PrefHandler
import org.totschnig.myexpenses.preference.PrefKey
import java.io.OutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.max

enum class FontType(
    val factor: Float,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isUnderline: Boolean = false,
    val color: Int = Color.BLACK,
) {
    NORMAL(1f),
    SMALL(0.85f),
    TITLE(1.5f, isBold = true),
    HEADER(1.2f, isUnderline = true),
    BOLD(1f, isBold = true),
    ITALIC(1f, isItalic = true),
    UNDERLINE(1f, isUnderline = true),
    INCOME(1f, color = 0xFF006800.toInt()),
    EXPENSE(1f, color = 0xFF800000.toInt()),
    TRANSFER(1f, color = 0xFF000080.toInt()),
    INCOME_SMALL(0.85f, color = 0xFF006800.toInt()),
    EXPENSE_SMALL(0.85f, color = 0xFF800000.toInt()),
    TRANSFER_SMALL(0.85f, color = 0xFF000080.toInt()),
    INCOME_BOLD(1f, isBold = true, color = 0xFF006800.toInt()),
    EXPENSE_BOLD(1f, isBold = true, color = 0xFF800000.toInt()),
    BALANCE_CHAPTER(1.2f, isBold = true),
    BALANCE_SECTION(1f, isBold = true);

    companion object {
        fun clearCache() {}
    }
}

enum class CellAlignment {
    LEFT, CENTER, RIGHT
}

data class CellTextSpan(
    val text: CharSequence,
    val fontType: FontType = FontType.NORMAL,
)

data class PdfCell(
    val spans: List<CellTextSpan>,
    val alignment: CellAlignment = CellAlignment.LEFT,
    val paddingTop: Float = 3f,
    val paddingBottom: Float = 3f,
    val paddingLeft: Float = 4f,
    val paddingRight: Float = 4f,
    val backgroundColor: Int? = null,
    val borderTop: Boolean = false,
    val borderBottom: Boolean = false,
    val borderLeft: Boolean = false,
    val borderRight: Boolean = false,
    val isStrikeThrough: Boolean = false,
    val colSpan: Int = 1,
) {
    constructor(
        text: CharSequence,
        fontType: FontType = FontType.NORMAL,
        alignment: CellAlignment = CellAlignment.LEFT,
        paddingTop: Float = 3f,
        paddingBottom: Float = 3f,
        paddingLeft: Float = 4f,
        paddingRight: Float = 4f,
        backgroundColor: Int? = null,
        borderTop: Boolean = false,
        borderBottom: Boolean = false,
        borderLeft: Boolean = false,
        borderRight: Boolean = false,
        isStrikeThrough: Boolean = false,
        colSpan: Int = 1,
    ) : this(
        spans = if (text.isEmpty()) emptyList() else listOf(CellTextSpan(text, fontType)),
        alignment = alignment,
        paddingTop = paddingTop,
        paddingBottom = paddingBottom,
        paddingLeft = paddingLeft,
        paddingRight = paddingRight,
        backgroundColor = backgroundColor,
        borderTop = borderTop,
        borderBottom = borderBottom,
        borderLeft = borderLeft,
        borderRight = borderRight,
        isStrikeThrough = isStrikeThrough,
        colSpan = colSpan,
    )
}

data class PdfRow(
    val cells: List<PdfCell>,
    val isHeader: Boolean = false,
)

class PdfDocumentWriter(
    val context: Context,
    val prefHandler: PrefHandler,
) {
    val baseFontSize: Float = prefHandler.getFloat(PrefKey.PRINT_FONT_SIZE, 12f)
    val isRtl: Boolean = Locale.getDefault().layoutDirection == View.LAYOUT_DIRECTION_RTL

    val paperWidth: Int
    val paperHeight: Int
    val marginLeft: Float
    val marginRight: Float
    val marginTop: Float
    val marginBottom: Float

    val contentLeft: Float
    val contentRight: Float
    val contentTop: Float
    val contentBottom: Float
    val contentWidth: Float

    private val pdfDocument = PdfDocument()
    private var currentPage: PdfDocument.Page? = null
    var currentCanvas: Canvas? = null
        private set
    var pageNumber = 0
        private set
    var currentY = 0f

    private val headerLeft = prefHandler.getString(PrefKey.PRINT_HEADER_LEFT)
    private val headerCenter = prefHandler.getString(PrefKey.PRINT_HEADER_CENTER)
    private val headerRight = prefHandler.getString(PrefKey.PRINT_HEADER_RIGHT)
    private val footerLeft = prefHandler.getString(PrefKey.PRINT_FOOTER_LEFT)
    private val footerCenter = prefHandler.getString(PrefKey.PRINT_FOOTER_CENTER)
    private val footerRight = prefHandler.getString(PrefKey.PRINT_FOOTER_RIGHT)

    private val linePaint = Paint().apply {
        color = Color.LTGRAY
        strokeWidth = 0.75f
        style = Paint.Style.STROKE
    }

    private val strikePaint = Paint().apply {
        color = Color.RED
        strokeWidth = 1f
        style = Paint.Style.STROKE
    }

    private val bgPaint = Paint().apply {
        style = Paint.Style.FILL
    }

    private val textPaints = mutableMapOf<Pair<FontType, Float>, TextPaint>()

    init {
        val paperFormatStr = prefHandler.requireString(
            PrefKey.PRINT_PAPER_FORMAT,
            PdfPrinter.defaultPaperSize(context)
        )
        val isLandscape = prefHandler.getString(
            PrefKey.PRINT_PAPER_ORIENTATION,
            context.getString(R.string.orientation_portrait)
        ) == context.getString(R.string.orientation_landscape)

        val (rawW, rawH) = when (paperFormatStr.uppercase()) {
            "LETTER" -> 612 to 792
            else -> 595 to 842 // A4
        }
        paperWidth = if (isLandscape) rawH else rawW
        paperHeight = if (isLandscape) rawW else rawH

        val hasHeader = !(headerLeft.isNullOrEmpty() && headerCenter.isNullOrEmpty() && headerRight.isNullOrEmpty())
        val hasFooter = !(footerLeft.isNullOrEmpty() && footerCenter.isNullOrEmpty() && footerRight.isNullOrEmpty())

        marginTop = (paperHeight * prefHandler.getFloat(PrefKey.PRINT_MARGIN_TOP, 0.04f))
            .coerceAtLeast(if (hasHeader) baseFontSize * 2.2f else baseFontSize)
        marginRight = paperWidth * prefHandler.getFloat(PrefKey.PRINT_MARGIN_RIGHT, 0.06f)
        marginBottom = (paperHeight * prefHandler.getFloat(PrefKey.PRINT_MARGIN_BOTTOM, 0.04f))
            .coerceAtLeast(if (hasFooter) baseFontSize * 2.2f else baseFontSize)
        marginLeft = paperWidth * prefHandler.getFloat(PrefKey.PRINT_MARGIN_LEFT, 0.06f)

        contentLeft = marginLeft
        contentRight = paperWidth - marginRight
        contentTop = marginTop
        contentBottom = paperHeight - marginBottom
        contentWidth = contentRight - contentLeft

        startNewPage()
    }

    fun getTextPaint(fontType: FontType, textSize: Float = baseFontSize * fontType.factor): TextPaint {
        return textPaints.getOrPut(fontType to textSize) {
            TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                this.textSize = textSize
                this.color = fontType.color
                this.isUnderlineText = fontType.isUnderline
                val style = when {
                    fontType.isBold && fontType.isItalic -> Typeface.BOLD_ITALIC
                    fontType.isBold -> Typeface.BOLD
                    fontType.isItalic -> Typeface.ITALIC
                    else -> Typeface.NORMAL
                }
                this.typeface = Typeface.create(Typeface.DEFAULT, style)
            }
        }
    }

    fun startNewPage() {
        finishCurrentPage()
        pageNumber++
        val pageInfo = PdfDocument.PageInfo.Builder(paperWidth, paperHeight, pageNumber).create()
        val page = pdfDocument.startPage(pageInfo)
        currentPage = page
        currentCanvas = page.canvas
        currentY = contentTop
        drawHeader(page.canvas)
    }

    private fun finishCurrentPage() {
        currentPage?.let { page ->
            drawFooter(page.canvas)
            pdfDocument.finishPage(page)
            currentPage = null
            currentCanvas = null
        }
    }

    private fun formatMetaText(template: String?): String {
        if (template.isNullOrEmpty()) return ""
        val dateStr = LocalDate.now().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT))
        return template
            .replace("%PAGE%", pageNumber.toString())
            .replace("%DATE%", dateStr)
    }

    private fun drawHeader(canvas: Canvas) {
        val y = marginTop - baseFontSize * 0.6f
        val paint = getTextPaint(FontType.SMALL)
        formatMetaText(headerLeft).takeIf { it.isNotEmpty() }?.let {
            canvas.drawText(it, contentLeft, y, paint)
        }
        formatMetaText(headerCenter).takeIf { it.isNotEmpty() }?.let {
            val w = paint.measureText(it)
            canvas.drawText(it, contentLeft + (contentWidth - w) / 2f, y, paint)
        }
        formatMetaText(headerRight).takeIf { it.isNotEmpty() }?.let {
            val w = paint.measureText(it)
            canvas.drawText(it, contentRight - w, y, paint)
        }
    }

    private fun drawFooter(canvas: Canvas) {
        val y = paperHeight - marginBottom + baseFontSize * 1.2f
        val paint = getTextPaint(FontType.SMALL)
        formatMetaText(footerLeft).takeIf { it.isNotEmpty() }?.let {
            canvas.drawText(it, contentLeft, y, paint)
        }
        formatMetaText(footerCenter).takeIf { it.isNotEmpty() }?.let {
            val w = paint.measureText(it)
            canvas.drawText(it, contentLeft + (contentWidth - w) / 2f, y, paint)
        }
        formatMetaText(footerRight).takeIf { it.isNotEmpty() }?.let {
            val w = paint.measureText(it)
            canvas.drawText(it, contentRight - w, y, paint)
        }
    }

    fun ensureSpace(height: Float) {
        if (currentY + height > contentBottom) {
            startNewPage()
        }
    }

    fun drawParagraph(
        text: CharSequence,
        fontType: FontType = FontType.NORMAL,
        alignment: CellAlignment = CellAlignment.LEFT,
        spacingAfter: Float = 6f
    ) {
        if (text.isEmpty()) return
        val paint = getTextPaint(fontType)
        val textWidth = max(1, contentWidth.toInt())
        val layoutAlignment = when (alignment) {
            CellAlignment.CENTER -> Layout.Alignment.ALIGN_CENTER
            CellAlignment.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
            CellAlignment.LEFT -> Layout.Alignment.ALIGN_NORMAL
        }
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, textWidth)
            .setAlignment(layoutAlignment)
            .setIncludePad(false)
            .build()

        ensureSpace(layout.height + spacingAfter)
        val canvas = currentCanvas ?: return
        canvas.save()
        canvas.translate(contentLeft, currentY)
        layout.draw(canvas)
        canvas.restore()
        currentY += layout.height + spacingAfter
    }

    fun drawLineSeparator(
        color: Int = Color.LTGRAY,
        lineWidth: Float = 0.75f,
        spacingBefore: Float = 4f,
        spacingAfter: Float = 4f,
    ) {
        ensureSpace(spacingBefore + lineWidth + spacingAfter)
        currentY += spacingBefore
        linePaint.color = color
        linePaint.strokeWidth = lineWidth
        currentCanvas?.drawLine(contentLeft, currentY, contentRight, currentY, linePaint)
        currentY += lineWidth + spacingAfter
    }

    fun drawTable(
        columnWeights: FloatArray,
        rows: List<PdfRow>,
        headerRows: Int = 0,
    ) {
        if (rows.isEmpty()) return
        val totalWeight = columnWeights.sum().coerceAtLeast(1f)
        val actualColWidths = FloatArray(columnWeights.size) { i ->
            (columnWeights[i] / totalWeight) * contentWidth
        }

        val headerRowList = if (headerRows > 0) rows.take(headerRows) else emptyList()

        fun drawRow(row: PdfRow) {
            var colIndex = 0
            val cellLayouts = mutableListOf<Pair<PdfCell, List<StaticLayout>>>()
            var maxRowHeight = baseFontSize + 6f

            for (cell in row.cells) {
                var cellAvailableWidth = 0f
                for (c in 0 until cell.colSpan) {
                    if (colIndex + c < actualColWidths.size) {
                        cellAvailableWidth += actualColWidths[colIndex + c]
                    }
                }
                colIndex += cell.colSpan

                val textAvailableWidth = max(1, (cellAvailableWidth - cell.paddingLeft - cell.paddingRight).toInt())
                val layouts = cell.spans.map { span ->
                    val paint = getTextPaint(span.fontType)
                    val align = when (cell.alignment) {
                        CellAlignment.CENTER -> Layout.Alignment.ALIGN_CENTER
                        CellAlignment.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
                        CellAlignment.LEFT -> Layout.Alignment.ALIGN_NORMAL
                    }
                    StaticLayout.Builder.obtain(span.text, 0, span.text.length, paint, textAvailableWidth)
                        .setAlignment(align)
                        .setIncludePad(false)
                        .build()
                }
                val totalTextHeight = layouts.sumOf { it.height }
                val totalCellHeight = totalTextHeight + cell.paddingTop + cell.paddingBottom
                if (totalCellHeight > maxRowHeight) {
                    maxRowHeight = totalCellHeight
                }
                cellLayouts.add(cell to layouts)
            }

            if (currentY + maxRowHeight > contentBottom) {
                startNewPage()
                if (!row.isHeader && headerRowList.isNotEmpty()) {
                    headerRowList.forEach { drawRow(it) }
                }
            }

            val canvas = currentCanvas ?: return
            var cellX = contentLeft
            colIndex = 0

            for ((cell, layouts) in cellLayouts) {
                var cellWidth = 0f
                for (c in 0 until cell.colSpan) {
                    if (colIndex + c < actualColWidths.size) {
                        cellWidth += actualColWidths[colIndex + c]
                    }
                }
                colIndex += cell.colSpan

                cell.backgroundColor?.let { bg ->
                    bgPaint.color = bg
                    canvas.drawRect(cellX, currentY, cellX + cellWidth, currentY + maxRowHeight, bgPaint)
                }

                if (cell.borderTop) {
                    canvas.drawLine(cellX, currentY, cellX + cellWidth, currentY, linePaint)
                }
                if (cell.borderBottom) {
                    canvas.drawLine(cellX, currentY + maxRowHeight, cellX + cellWidth, currentY + maxRowHeight, linePaint)
                }
                if (cell.borderLeft) {
                    canvas.drawLine(cellX, currentY, cellX, currentY + maxRowHeight, linePaint)
                }
                if (cell.borderRight) {
                    canvas.drawLine(cellX + cellWidth, currentY, cellX + cellWidth, currentY + maxRowHeight, linePaint)
                }

                var textY = currentY + cell.paddingTop
                for (layout in layouts) {
                    canvas.save()
                    canvas.translate(cellX + cell.paddingLeft, textY)
                    layout.draw(canvas)
                    canvas.restore()
                    textY += layout.height
                }

                if (cell.isStrikeThrough) {
                    val centerY = currentY + maxRowHeight / 2f
                    canvas.drawLine(cellX, centerY, cellX + cellWidth, centerY, strikePaint)
                }

                cellX += cellWidth
            }

            currentY += maxRowHeight
        }

        for (row in rows) {
            drawRow(row)
        }
    }

    fun writeTo(outputStream: OutputStream) {
        finishCurrentPage()
        pdfDocument.writeTo(outputStream)
    }

    fun close() {
        finishCurrentPage()
        pdfDocument.close()
    }
}
