package org.totschnig.myexpenses.ui.animation

import android.view.View
import android.view.animation.Animation
import android.view.animation.Transformation
import android.widget.LinearLayout

/**
 * This animation class animates the expanding and reducing the size of a view.
 * The animation toggles between Expand and Reduce, depending on the current state of the view.
 *
 * @author Udinic
 */
class ExpandAnimation(view: View, duration: Int) : Animation() {
    private val mAnimatedView: View = view
    private val mViewLayoutParams: LinearLayout.LayoutParams =
        view.layoutParams as LinearLayout.LayoutParams
    private val mMarginStart: Int
    private val mMarginEnd: Int
    private val mIsVisibleAfter: Boolean
    private var mWasEndedAlready = false

    init {
        setDuration(duration.toLong())
        mIsVisibleAfter = view.visibility == View.VISIBLE
        mMarginStart = mViewLayoutParams.bottomMargin
        mMarginEnd = if (mMarginStart == 0) (-view.height) else 0
        view.visibility = View.VISIBLE
    }

    override fun applyTransformation(interpolatedTime: Float, t: Transformation) {
        super.applyTransformation(interpolatedTime, t)
        if (interpolatedTime < 1.0f) {
            mViewLayoutParams.bottomMargin =
                mMarginStart + ((mMarginEnd - mMarginStart) * interpolatedTime).toInt()
            mAnimatedView.requestLayout()
        } else if (!mWasEndedAlready) {
            mViewLayoutParams.bottomMargin = mMarginEnd
            mAnimatedView.requestLayout()
            if (mIsVisibleAfter) {
                mAnimatedView.visibility = View.GONE
            }
            mWasEndedAlready = true
        }
    }
}
