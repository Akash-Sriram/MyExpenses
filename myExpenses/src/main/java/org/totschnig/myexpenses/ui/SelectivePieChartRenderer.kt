package org.totschnig.myexpenses.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.interfaces.datasets.IPieDataSet
import com.github.mikephil.charting.renderer.PieChartRenderer
import com.github.mikephil.charting.utils.ColorTemplate
import com.github.mikephil.charting.utils.MPPointF
import com.github.mikephil.charting.utils.Utils
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class SelectivePieChartRenderer(
    chart: PieChart,
    private val selector: Selector
) : PieChartRenderer(chart, chart.animator, chart.viewPortHandler) {

    fun interface Selector {
        fun shouldDrawEntry(index: Int, pieEntry: PieEntry, value: Float): Boolean
    }

    override fun drawValues(c: Canvas) {
        val center = mChart.centerCircleBox
        val radius = mChart.radius
        var rotationAngle = mChart.rotationAngle
        val drawAngles = mChart.drawAngles
        val absoluteAngles = mChart.absoluteAngles

        val phaseX = mAnimator.phaseX
        val phaseY = mAnimator.phaseY

        val roundedRadius = (radius - radius * mChart.holeRadius / 100f) / 2f
        val holeRadiusPercent = mChart.holeRadius / 100f
        var labelRadiusOffset = radius / 10f * 3.6f

        if (mChart.isDrawHoleEnabled) {
            labelRadiusOffset = (radius - radius * holeRadiusPercent) / 2f
            if (!mChart.isDrawSlicesUnderHoleEnabled && mChart.isDrawRoundedSlicesEnabled) {
                rotationAngle += roundedRadius * 360 / (Math.PI * 2 * radius).toFloat()
            }
        }

        val labelRadius = radius - labelRadiusOffset
        val data: PieData = mChart.data
        val dataSets: List<IPieDataSet> = data.dataSets
        val yValueSum = data.yValueSum
        val drawEntryLabels = mChart.isDrawEntryLabelsEnabled

        var angle: Float
        var xIndex = 0

        c.save()
        val offset = Utils.convertDpToPixel(5f)

        for (i in dataSets.indices) {
            val dataSet = dataSets[i]
            val drawValues = dataSet.isDrawValuesEnabled
            if (!drawValues && !drawEntryLabels) continue

            val xValuePosition = dataSet.xValuePosition
            val yValuePosition = dataSet.yValuePosition

            applyValueTextStyle(dataSet)

            val lineHeight = Utils.calcTextHeight(mValuePaint, "Q") + Utils.convertDpToPixel(4f)
            val formatter = dataSet.valueFormatter
            val entryCount = dataSet.entryCount
            val isUseValueColorForLineEnabled = dataSet.isUseValueColorForLineEnabled
            val valueLineColor = dataSet.valueLineColor

            mValueLinePaint.strokeWidth = Utils.convertDpToPixel(dataSet.valueLineWidth)
            val sliceSpace = getSliceSpace(dataSet)

            val iconsOffset = MPPointF.getInstance(dataSet.iconsOffset)
            iconsOffset.x = Utils.convertDpToPixel(iconsOffset.x)
            iconsOffset.y = Utils.convertDpToPixel(iconsOffset.y)

            for (j in 0 until entryCount) {
                val entry = dataSet.getEntryForIndex(j)
                angle = if (xIndex == 0) 0f else absoluteAngles[xIndex - 1] * phaseX

                val sliceAngle = drawAngles[xIndex]
                val sliceSpaceMiddleAngle = sliceSpace / (Utils.FDEG2RAD * labelRadius)
                val angleOffset = (sliceAngle - sliceSpaceMiddleAngle / 2f) / 2f
                angle += angleOffset

                val transformedAngle = rotationAngle + angle * phaseY
                val value = if (mChart.isUsePercentValuesEnabled)
                    entry.y / yValueSum * 100f else entry.y

                if (selector.shouldDrawEntry(j, entry, value)) {
                    val entryLabel = entry.label
                    val sliceXBase = cos(transformedAngle * Utils.FDEG2RAD).toFloat()
                    val sliceYBase = sin(transformedAngle * Utils.FDEG2RAD).toFloat()

                    val drawXOutside = drawEntryLabels &&
                            xValuePosition == PieDataSet.ValuePosition.OUTSIDE_SLICE
                    val drawYOutside = drawValues &&
                            yValuePosition == PieDataSet.ValuePosition.OUTSIDE_SLICE
                    val drawXInside = drawEntryLabels &&
                            xValuePosition == PieDataSet.ValuePosition.INSIDE_SLICE
                    val drawYInside = drawValues &&
                            yValuePosition == PieDataSet.ValuePosition.INSIDE_SLICE

                    if (drawXOutside || drawYOutside) {
                        val valueLineLength1 = dataSet.valueLinePart1Length
                        val valueLineLength2 = dataSet.valueLinePart2Length
                        val valueLinePart1OffsetPercentage =
                            dataSet.valueLinePart1OffsetPercentage / 100f

                        val line1Radius = if (mChart.isDrawHoleEnabled)
                            (radius - radius * holeRadiusPercent) * valueLinePart1OffsetPercentage +
                                    radius * holeRadiusPercent
                        else radius * valueLinePart1OffsetPercentage

                        val polyline2Width = if (dataSet.isValueLineVariableLength)
                            labelRadius * valueLineLength2 *
                                    abs(sin(transformedAngle * Utils.FDEG2RAD)).toFloat()
                        else labelRadius * valueLineLength2

                        val pt0x = line1Radius * sliceXBase + center.x
                        val pt0y = line1Radius * sliceYBase + center.y
                        val pt1x = labelRadius * (1 + valueLineLength1) * sliceXBase + center.x
                        val pt1y = labelRadius * (1 + valueLineLength1) * sliceYBase + center.y

                        val pt2x: Float
                        val pt2y: Float
                        val labelPtx: Float
                        val labelPty: Float

                        if (transformedAngle % 360.0 >= 90.0 && transformedAngle % 360.0 <= 270.0) {
                            pt2x = pt1x - polyline2Width
                            pt2y = pt1y
                            mValuePaint.textAlign = Paint.Align.RIGHT
                            if (drawXOutside) getPaintEntryLabels().textAlign = Paint.Align.RIGHT
                            labelPtx = pt2x - offset
                            labelPty = pt2y
                        } else {
                            pt2x = pt1x + polyline2Width
                            pt2y = pt1y
                            mValuePaint.textAlign = Paint.Align.LEFT
                            if (drawXOutside) getPaintEntryLabels().textAlign = Paint.Align.LEFT
                            labelPtx = pt2x + offset
                            labelPty = pt2y
                        }

                        var lineColor = ColorTemplate.COLOR_NONE
                        if (isUseValueColorForLineEnabled) lineColor = dataSet.getColor(j)
                        else if (valueLineColor != ColorTemplate.COLOR_NONE) lineColor = valueLineColor

                        if (lineColor != ColorTemplate.COLOR_NONE) {
                            mValueLinePaint.color = lineColor
                            c.drawLine(pt0x, pt0y, pt1x, pt1y, mValueLinePaint)
                            c.drawLine(pt1x, pt1y, pt2x, pt2y, mValueLinePaint)
                        }

                        if (drawXOutside && drawYOutside) {
                            drawValue(c, formatter, value, entry, 0, labelPtx, labelPty,
                                dataSet.getValueTextColor(j))
                            if (j < data.entryCount && entryLabel != null) {
                                drawEntryLabel(c, entryLabel, labelPtx, labelPty + lineHeight)
                            }
                        } else if (drawXOutside) {
                            if (j < data.entryCount && entryLabel != null) {
                                drawEntryLabel(c, entryLabel, labelPtx, labelPty + lineHeight / 2f)
                            }
                        } else if (drawYOutside) {
                            drawValue(c, formatter, value, entry, 0, labelPtx,
                                labelPty + lineHeight / 2f, dataSet.getValueTextColor(j))
                        }
                    }

                    if (drawXInside || drawYInside) {
                        val x = labelRadius * sliceXBase + center.x
                        val y = labelRadius * sliceYBase + center.y
                        mValuePaint.textAlign = Paint.Align.CENTER

                        if (drawXInside && drawYInside) {
                            drawValue(c, formatter, value, entry, 0, x, y, dataSet.getValueTextColor(j))
                            if (j < data.entryCount && entryLabel != null) {
                                drawEntryLabel(c, entryLabel, x, y + lineHeight)
                            }
                        } else if (drawXInside) {
                            if (j < data.entryCount && entryLabel != null) {
                                drawEntryLabel(c, entryLabel, x, y + lineHeight / 2f)
                            }
                        } else if (drawYInside) {
                            drawValue(c, formatter, value, entry, 0, x, y + lineHeight / 2f,
                                dataSet.getValueTextColor(j))
                        }
                    }

                    if (entry.icon != null && dataSet.isDrawIconsEnabled) {
                        val icon: Drawable = entry.icon
                        val x = (labelRadius + iconsOffset.y) * sliceXBase + center.x
                        var y = (labelRadius + iconsOffset.y) * sliceYBase + center.y
                        y += iconsOffset.x
                        Utils.drawImage(c, icon, x.toInt(), y.toInt(),
                            icon.intrinsicWidth, icon.intrinsicHeight)
                    }
                }
                xIndex++
            }
            MPPointF.recycleInstance(iconsOffset)
        }
        MPPointF.recycleInstance(center)
        c.restore()
    }

    override fun drawExtras(c: Canvas) {
        if (mDrawBitmap != null) {
            super.drawExtras(c)
        }
    }

    override fun drawHighlighted(c: Canvas, indices: Array<Highlight>) {
        if (mBitmapCanvas != null) {
            super.drawHighlighted(c, indices)
        }
    }
}
