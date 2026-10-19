/*   This file is part of My Expenses.
 *   My Expenses is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   My Expenses is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with My Expenses.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.totschnig.myexpenses.dialog

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.text.TextUtils
import android.widget.Button
import androidx.annotation.NonNull
import org.totschnig.myexpenses.ui.ScrollableProgressDialog
import org.totschnig.myexpenses.util.crashreporting.CrashHandler

class ProgressDialogFragment : BaseDialogFragment() {

    companion object {
        const val STYLE_SPINNER = 0
        const val STYLE_HORIZONTAL = 1

        private const val KEY_PROGRESS_STYLE = "progressStyle"
        private const val KEY_MESSAGE = "message"
        private const val KEY_WITH_BUTTON = "withButton"
        private const val KEY_TITLE = "title"
        private const val KEY_TASK_COMPLETED = "taskCompleted"
        private const val KEY_PROGRESS = "progress"
        private const val KEY_MAX = "max"

        @JvmStatic
        fun newInstance(message: String, withButton: Boolean): ProgressDialogFragment {
            return newInstance(null, message, STYLE_SPINNER, withButton)
        }

        /**
         * @param message       the dialog's message
         * @param progressStyle [STYLE_SPINNER] or [STYLE_HORIZONTAL]
         * @param withButton    if true dialog is rendered with an OK button that is initially disabled
         */
        @JvmStatic
        fun newInstance(
            title: String?,
            message: CharSequence?,
            progressStyle: Int,
            withButton: Boolean
        ): ProgressDialogFragment {
            return ProgressDialogFragment().apply {
                arguments = Bundle().apply {
                    putCharSequence(KEY_MESSAGE, message)
                    putString(KEY_TITLE, title)
                    putInt(KEY_PROGRESS_STYLE, progressStyle)
                    putBoolean(KEY_WITH_BUTTON, withButton)
                }
                isCancelable = false
            }
        }
    }

    private lateinit var mDialog: ScrollableProgressDialog
    private var mTaskCompleted = false
    private var progress = 0
    var max = 0
        set(value) {
            field = value
            if (::mDialog.isInitialized) {
                mDialog.max = value
            }
        }
    private var title: String? = null
    private var message: CharSequence? = null
    private val dialogButton = DialogInterface.BUTTON_POSITIVE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.let {
            mTaskCompleted = it.getBoolean(KEY_TASK_COMPLETED, false)
            message = it.getCharSequence(KEY_MESSAGE)
            title = it.getString(KEY_TITLE)
            progress = it.getInt(KEY_PROGRESS)
            max = it.getInt(KEY_MAX)
        }
    }

    override fun onResume() {
        super.onResume()
        setProgress(progress)
        this.max = max
        mDialog.setTitle(title)
        if (!TextUtils.isEmpty(message)) {
            mDialog.setMessage(message)
        }
        if (mTaskCompleted) {
            unsetIndeterminateDrawable()
        } else {
            val b: Button? = mDialog.getButton(dialogButton)
            b?.isEnabled = false
        }
    }

    @NonNull
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val arguments = requireArguments()
        val progressStyle = arguments.getInt(KEY_PROGRESS_STYLE)
        val messageFromArguments = arguments.getString(KEY_MESSAGE)
        val titleFromArguments = arguments.getString(KEY_TITLE)

        mDialog = ScrollableProgressDialog(
            requireActivity(),
            isHorizontal = (progressStyle == STYLE_HORIZONTAL)
        )

        val withButton = arguments.getBoolean(KEY_WITH_BUTTON)
        if (messageFromArguments != null) {
            if (message == null) {
                message = "$messageFromArguments …"
                mDialog.setMessage(message)
            }
        } else {
            if (titleFromArguments != null) {
                if (title == null) title = titleFromArguments
            } else {
                if (title == null) title = "..."
            }
            mDialog.setTitle(title)
        }
        if (withButton) {
            mDialog.setButton(dialogButton, getString(android.R.string.ok)) { _, _ ->
                activity ?: return@setButton
                (activity as ProgressDialogListener).onProgressDialogDismiss()
            }
        }
        return mDialog
    }

    fun setProgress(progress: Int) {
        this.progress = progress
        if (::mDialog.isInitialized) {
            mDialog.progress = progress
        }
    }

    fun setTitle(title: String) {
        this.title = title
        if (::mDialog.isInitialized) {
            mDialog.setTitle(title)
        }
    }

    fun appendToMessage(@NonNull newMessage: CharSequence) {
        this.message = if (TextUtils.isEmpty(this.message)) newMessage
        else TextUtils.concat(this.message, "\n", newMessage)
        if (::mDialog.isInitialized) {
            mDialog.setMessage(this.message)
        }
    }

    fun onTaskCompleted() {
        mTaskCompleted = true
        try {
            unsetIndeterminateDrawable()
        } catch (e: NullPointerException) {
            // seen on samsung SM-G900F
            CrashHandler.report(e)
        }
        mDialog.getButton(dialogButton).isEnabled = true
    }

    private fun unsetIndeterminateDrawable() {
        if (::mDialog.isInitialized) {
            mDialog.unsetIndeterminateDrawable()
        }
    }

    override fun onSaveInstanceState(@NonNull outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_TASK_COMPLETED, mTaskCompleted)
        outState.putString(KEY_TITLE, title)
        outState.putCharSequence(KEY_MESSAGE, message)
        outState.putInt(KEY_PROGRESS, progress)
        outState.putInt(KEY_MAX, max)
    }

    interface ProgressDialogListener {
        fun onProgressDialogDismiss()
    }
}
