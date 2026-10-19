package org.totschnig.myexpenses.ui

import android.os.Bundle
import android.view.View
import com.google.android.material.slider.Slider
import eltos.simpledialogfragment.CustomViewDialog

class SimpleSeekBarDialog : CustomViewDialog<SimpleSeekBarDialog>() {
    private lateinit var slider: Slider

    companion object {
        const val TAG = "SimpleSeekBarDialog."
        protected const val SEEKBAR_MINIMUM = "Seekbar.maximum"
        protected const val SEEKBAR_MAXIMUM = "Seekbar.minimum"
        const val SEEKBAR_VALUE = "Seekbar.value"

        @JvmStatic
        fun build(): SimpleSeekBarDialog = SimpleSeekBarDialog()
    }

    /**
     * Sets the seekbar's maximum range
     * @param max the maximum range
     */
    fun max(max: Int): SimpleSeekBarDialog = setArg(SEEKBAR_MAXIMUM, max)

    /**
     * Sets the seekbar's minimum range
     * @param min the minimum range
     */
    fun min(min: Int): SimpleSeekBarDialog = setArg(SEEKBAR_MINIMUM, min)

    /**
     * Sets the seekbar's current value
     * @param value the current value
     */
    fun value(value: Int): SimpleSeekBarDialog = setArg(SEEKBAR_VALUE, value)

    override fun onCreateContentView(savedInstanceState: Bundle?): View {
        slider = Slider(requireContext())
        slider.valueFrom = requireArguments().getInt(SEEKBAR_MINIMUM).toFloat()
        slider.valueTo = requireArguments().getInt(SEEKBAR_MAXIMUM).toFloat()
        slider.stepSize = 1f
        restoreValue(savedInstanceState ?: requireArguments())
        return slider
    }

    override fun onResult(which: Int): Bundle {
        val result = Bundle()
        storeValue(result)
        return result
    }

    private fun restoreValue(bundle: Bundle) {
        slider.value = bundle.getInt(SEEKBAR_VALUE).toFloat()
    }

    private fun storeValue(bundle: Bundle) {
        bundle.putInt(SEEKBAR_VALUE, slider.value.toInt())
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        storeValue(outState)
    }
}
