package org.totschnig.myexpenses.util.ui

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.widget.TextView
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar
import org.totschnig.myexpenses.R

object UiUtils {
    @JvmStatic
    fun increaseSnackbarMaxLines(snackbar: Snackbar) {
        val snackbarView = snackbar.view
        val textView = snackbarView.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
        textView?.maxLines = 10
    }

    @JvmStatic
    fun getTintedBitmapForTheme(context: Context, drawableResId: Int, themeResId: Int): Bitmap? {
        val d = getTintedDrawableForContext(ContextThemeWrapper(context, themeResId), drawableResId)
        return d?.let { drawableToBitmap(it) }
    }

    @JvmStatic
    fun getTintedDrawableForContext(context: Context, drawableResId: Int): Drawable? {
        return AppCompatResources.getDrawable(context, drawableResId)
    }

    @JvmStatic
    fun drawableToBitmap(d: Drawable): Bitmap {
        val width = if (d.intrinsicWidth > 0) d.intrinsicWidth else 1
        val height = if (d.intrinsicHeight > 0) d.intrinsicHeight else 1
        val b = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        d.setBounds(0, 0, c.width, c.height)
        d.draw(c)
        return b
    }

    @JvmStatic
    fun configureAmountTextViewForHebrew(amount: TextView) {
        val layoutDirection = amount.context.resources.getInteger(R.integer.amount_layout_direction)
        if (layoutDirection == 0) { // hebrew
            amount.layoutDirection = layoutDirection
            amount.setEms(5)
            amount.ellipsize = TextUtils.TruncateAt.MARQUEE
            amount.isSingleLine = true
            amount.marqueeRepeatLimit = -1
            amount.setHorizontallyScrolling(true)
            amount.isSelected = true
        }
    }

    enum class DateMode {
        DATE, DATE_TIME, BOOKING_VALUE
    }

    @JvmStatic
    fun dp2Px(dp: Float, resources: Resources): Int {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics).toInt()
    }

    @JvmStatic
    fun sp2Px(sp: Float, resources: Resources): Int {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, resources.displayMetrics).toInt()
    }

    @JvmStatic
    fun px2Dp(px: Int): Int {
        return (px / Resources.getSystem().displayMetrics.density).toInt()
    }

    @JvmStatic
    @ColorInt
    fun getColor(context: Context, @AttrRes attr: Int): Int {
        val typedValue = TypedValue()
        context.theme.resolveAttribute(attr, typedValue, true)
        return ContextCompat.getColor(context, typedValue.resourceId)
    }
}
