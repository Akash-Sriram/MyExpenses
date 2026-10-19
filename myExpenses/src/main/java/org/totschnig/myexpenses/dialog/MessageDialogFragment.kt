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

import android.app.Activity
import android.app.Dialog
import android.content.DialogInterface
import android.content.DialogInterface.BUTTON_NEGATIVE
import android.content.DialogInterface.BUTTON_NEUTRAL
import android.content.DialogInterface.BUTTON_POSITIVE
import android.content.DialogInterface.OnClickListener
import android.os.Bundle
import androidx.core.os.BundleCompat
import androidx.annotation.Nullable
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.totschnig.myexpenses.R
import java.io.Serializable

class MessageDialogFragment : BaseDialogFragment(), OnClickListener {

    companion object {
        private const val KEY_TITLE = "title"
        private const val KEY_MESSAGE = "message"
        private const val KEY_POSITIVE = "positive"
        private const val KEY_NEUTRAL = "neutral"
        private const val KEY_NEGATIVE = "negative"
        private const val KEY_ICON = "icon"

        @JvmStatic
        fun noButton(): Button = nullButton(android.R.string.cancel)

        @JvmStatic
        fun okButton(): Button = nullButton(android.R.string.ok)

        @JvmStatic
        fun nullButton(label: Int): Button = Button(label, R.id.NO_COMMAND, null)

        @JvmStatic
        fun newInstance(
            title: CharSequence?,
            message: CharSequence,
            positive: Button?,
            neutral: Button?,
            negative: Button?
        ): MessageDialogFragment = newInstance(title, message, positive, neutral, negative, 0)

        @JvmStatic
        fun newInstance(
            title: CharSequence?,
            message: CharSequence,
            positive: Button?,
            neutral: Button?,
            negative: Button?,
            icon: Int
        ): MessageDialogFragment {
            return MessageDialogFragment().apply {
                arguments = Bundle().apply {
                    putCharSequence(KEY_TITLE, title)
                    putCharSequence(KEY_MESSAGE, message)
                    putSerializable(KEY_POSITIVE, positive)
                    putSerializable(KEY_NEUTRAL, neutral)
                    putSerializable(KEY_NEGATIVE, negative)
                    putInt(KEY_ICON, icon)
                }
            }
        }
    }

    data class Button(
        @JvmField val label: Int,
        @JvmField val command: Int,
        @JvmField val tag: Serializable?,
        @JvmField val keepDialogOpen: Boolean = false
    ) : Serializable

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val bundle = requireArguments()
        val ctx: Activity = requireActivity()
        val builder = MaterialAlertDialogBuilder(ctx)
            .setMessage(bundle.getCharSequence(KEY_MESSAGE))
            .setTitle(bundle.getCharSequence(KEY_TITLE))

        val positive = BundleCompat.getSerializable(bundle, KEY_POSITIVE, Button::class.java)
        val neutral = BundleCompat.getSerializable(bundle, KEY_NEUTRAL, Button::class.java)
        val negative = BundleCompat.getSerializable(bundle, KEY_NEGATIVE, Button::class.java)

        positive?.let { builder.setPositiveButton(it.label, if (it.keepDialogOpen) null else this) }
        neutral?.let { builder.setNeutralButton(it.label, if (it.keepDialogOpen) null else this) }
        negative?.let { builder.setNegativeButton(it.label, if (it.keepDialogOpen) null else this) }

        val icon = bundle.getInt(KEY_ICON)
        if (icon != 0) builder.setIcon(icon)

        val alertDialog = builder.create()
        alertDialog.setOnShowListener {
            if (shouldKeepDialogOpen(positive)) setOnClickForward(alertDialog, BUTTON_POSITIVE)
            if (shouldKeepDialogOpen(negative)) setOnClickForward(alertDialog, BUTTON_NEGATIVE)
            if (shouldKeepDialogOpen(neutral)) setOnClickForward(alertDialog, BUTTON_NEUTRAL)
        }
        return alertDialog
    }

    private fun shouldKeepDialogOpen(button: Button?): Boolean = button?.keepDialogOpen == true

    /** prevent automatic dismiss on button click */
    private fun setOnClickForward(alertDialog: AlertDialog, which: Int) {
        alertDialog.getButton(which).setOnClickListener { onClick(alertDialog, which) }
    }

    override fun onCancel(dialog: DialogInterface) {
        activity ?: return
        (activity as MessageDialogListener).onMessageDialogDismissOrCancel()
    }

    override fun onClick(dialog: DialogInterface, which: Int) {
        activity ?: return
        val bundle = requireArguments()
        val clicked = BundleCompat.getSerializable(
            bundle,
            when (which) {
                BUTTON_POSITIVE -> KEY_POSITIVE
                BUTTON_NEUTRAL -> KEY_NEUTRAL
                BUTTON_NEGATIVE -> KEY_NEGATIVE
                else -> throw IllegalStateException("unknown button $which")
            },
            Button::class.java
        )
        if (clicked == null || clicked.command == R.id.NO_COMMAND) {
            onCancel(dialog)
        } else {
            (activity as MessageDialogListener).dispatchCommand(clicked.command, clicked.tag)
        }
    }

    interface MessageDialogListener {
        fun dispatchCommand(command: Int, @Nullable tag: Any?): Boolean
        fun onMessageDialogDismissOrCancel()
    }
}
