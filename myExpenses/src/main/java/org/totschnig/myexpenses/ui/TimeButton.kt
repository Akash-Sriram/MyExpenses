package org.totschnig.myexpenses.ui

import android.content.Context
import android.os.Bundle
import android.util.AttributeSet
import com.google.android.material.timepicker.MaterialTimePicker
import org.totschnig.myexpenses.injector
import org.totschnig.myexpenses.preference.PrefKey
import org.totschnig.myexpenses.util.timeFormatter
import org.totschnig.myexpenses.util.ui.preferredTimePickerBuilder
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class TimeButton @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : ButtonWithDialog<MaterialTimePicker>(context, attrs, defStyleAttr) {

    var time: LocalTime = LocalTime.now()
        set(value) {
            field = value
            update()
        }

    private val timeFormatter: DateTimeFormatter by lazy { context.timeFormatter }

    override fun buildDialog() = preferredTimePickerBuilder(context)
        .setHour(time.hour)
        .setMinute(time.minute)
        .build()

    override fun update() {
        text = time.format(timeFormatter)
    }

    override val fragmentTag: String
        get() = "date_button"

    override fun attachListener(dialogFragment: MaterialTimePicker) {
        dialogFragment.addOnPositiveButtonClickListener {
            context.injector.prefHandler().putInt(PrefKey.TIME_PICKER_INPUT_MODE, dialogFragment.inputMode)
            time = LocalTime.of(dialogFragment.hour, dialogFragment.minute)
        }
        dialogFragment.addOnDismissListener {
            dialogShown = false
        }
    }

    override fun onSaveCustomState(bundle: Bundle) {
        bundle.putInt(KEY_TIME, time.toSecondOfDay())
    }

    override fun onRestoreCustomState(bundle: Bundle) {
        if (bundle.containsKey(KEY_TIME)) {
            time = LocalTime.ofSecondOfDay(bundle.getInt(KEY_TIME).toLong())
        }
    }

    companion object {
        private const val KEY_TIME = "time"
    }
}
