package org.totschnig.myexpenses.ui

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import androidx.annotation.ColorInt

class FontDrawable(
    val char: Char,
    private val typeface: Typeface?,
    private var sizePx: Int,
    @ColorInt private var color: Int
) : Drawable() {

    @JvmField
    val fontCode: Char = char

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.typeface = this@FontDrawable.typeface
        this.color = this@FontDrawable.color
        this.textAlign = Paint.Align.CENTER
        this.textSize = sizePx.toFloat()
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        val x = b.centerX().toFloat()
        val y = b.centerY().toFloat() - ((paint.descent() + paint.ascent()) / 2f)
        canvas.drawText(char.toString(), x, y, paint)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun getIntrinsicWidth(): Int = sizePx
    override fun getIntrinsicHeight(): Int = sizePx

    class Builder(private val context: Context, private val char: Char, private val typeface: Typeface?) {
        private var sizePx = (24 * context.resources.displayMetrics.density).toInt()
        private var color = Color.BLACK

        fun setSizeDp(dp: Int) = apply {
            this.sizePx = (dp * context.resources.displayMetrics.density).toInt()
        }

        fun setColor(@ColorInt color: Int) = apply {
            this.color = color
        }

        fun build(): FontDrawable {
            val drawable = FontDrawable(char, typeface, sizePx, color)
            drawable.setBounds(0, 0, sizePx, sizePx)
            return drawable
        }
    }
}
