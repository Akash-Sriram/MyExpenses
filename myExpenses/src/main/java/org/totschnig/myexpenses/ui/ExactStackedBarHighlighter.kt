package org.totschnig.myexpenses.ui

import android.graphics.RectF
import androidx.annotation.NonNull
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.BarLineScatterCandleBubbleData
import com.github.mikephil.charting.data.ChartData
import com.github.mikephil.charting.data.DataSet
import com.github.mikephil.charting.highlight.BarHighlighter
import com.github.mikephil.charting.highlight.ChartHighlighter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.interfaces.dataprovider.BarDataProvider
import com.github.mikephil.charting.interfaces.dataprovider.CombinedDataProvider
import com.github.mikephil.charting.interfaces.datasets.IBarDataSet
import com.github.mikephil.charting.utils.MPPointD

class ExactStackedBarHighlighter(chart: BarDataProvider) : BarHighlighter(chart) {

    private val barBoundsBuffer = RectF()

    override fun getHighlight(x: Float, y: Float): Highlight? {
        val pos = getValsForTouch(x, y)
        val xVal = pos.x.toFloat()
        MPPointD.recycleInstance(pos)
        return getHighlightForX(xVal, x, y)
    }

    override fun getHighlightForX(xVal: Float, x: Float, y: Float): Highlight? {
        val barDataSet: IBarDataSet = mChart.barData.getDataSetByIndex(0)
        val barEntry: BarEntry = barDataSet.getEntryForXValue(xVal, Float.NaN)
        for (i in barEntry.yVals.indices) {
            getBarBounds(barEntry, i)
            if (barBoundsBuffer.contains(x, y)) {
                val yVal = barEntry.yVals[i]
                val pixels = mChart.getTransformer(barDataSet.axisDependency)
                    .getPixelForValues(xVal, yVal)
                return Highlight(
                    barEntry.x, barEntry.y,
                    pixels.x.toFloat(), pixels.y.toFloat(),
                    0, i, barDataSet.axisDependency
                )
            }
        }
        return null
    }

    fun getBarBounds(e: BarEntry, stackIndex: Int) {
        val barData: BarData = mChart.barData
        val set: IBarDataSet = barData.getDataSetForEntry(e)

        val y = if (stackIndex == -1) e.y else e.yVals[stackIndex]
        val x = e.x
        val barWidth = barData.barWidth

        val left = x - barWidth / 2f
        val right = x + barWidth / 2f
        val top = if (y >= 0) y else 0f
        val bottom = if (y <= 0) y else 0f

        barBoundsBuffer.set(left, top, right, bottom)
        mChart.getTransformer(set.axisDependency).rectValueToPixel(barBoundsBuffer)
    }

    class CombinedHighlighter(chart: CombinedDataProvider) : ChartHighlighter<CombinedDataProvider>(chart) {

        private val barHighlighter: BarHighlighter = ExactStackedBarHighlighter(chart)

        override fun getHighlightsAtXValue(xVal: Float, x: Float, y: Float): List<Highlight> {
            mHighlightBuffer.clear()

            // if we have a match on a bar, we are only interested in this match
            val high = barHighlighter.getHighlight(x, y)

            if (high != null) {
                high.dataIndex = mChart.combinedData.getDataIndex(mChart.barData)
                mHighlightBuffer.add(high)
            } else {
                val dataObjects: List<BarLineScatterCandleBubbleData<*>> =
                    mChart.combinedData.allData

                for (i in dataObjects.indices) {
                    val dataObject: ChartData<*> = dataObjects[i]
                    if (dataObject is BarData) continue

                    for (j in 0 until dataObject.dataSetCount) {
                        val dataSet = dataObjects[i].getDataSetByIndex(j)
                        if (!dataSet.isHighlightEnabled) continue

                        val highs = buildHighlights(dataSet, j, xVal, DataSet.Rounding.CLOSEST)
                        for (highlight in highs) {
                            highlight.dataIndex = i
                            mHighlightBuffer.add(highlight)
                        }
                    }
                }
            }

            return mHighlightBuffer
        }
    }
}
