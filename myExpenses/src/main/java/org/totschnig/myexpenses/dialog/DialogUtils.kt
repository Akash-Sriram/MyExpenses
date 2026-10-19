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
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import org.totschnig.myexpenses.MyApplication
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.activity.BaseActivity
import org.totschnig.myexpenses.activity.IMPORT_FILENAME_REQUEST_CODE
import org.totschnig.myexpenses.activity.ProtectedFragmentActivity
import org.totschnig.myexpenses.preference.PrefHandler
import org.totschnig.myexpenses.preference.PrefKey
import org.totschnig.myexpenses.provider.KEY_UUID
import org.totschnig.myexpenses.util.Utils
import org.totschnig.myexpenses.util.distrib.DistributionHelper
import java.util.Arrays

object DialogUtils {

    @JvmStatic
    fun showPasswordDialog(
        ctx: BaseActivity,
        dialog: AlertDialog?,
        callback: PasswordDialogUnlockedCallback?
    ) {
        if (dialog == null) return
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.show()
        val resolvedCallback = callback ?: PasswordDialogUnlockedCallback {
            (ctx.applicationContext as MyApplication).unlock()
            ctx.showWindow()
        }
        val passwordDialogListener = PasswordDialogListener(ctx, dialog, resolvedCallback)
        var b: Button? = dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE)
        b?.setOnClickListener(passwordDialogListener)
        b = dialog.getButton(android.content.DialogInterface.BUTTON_NEUTRAL)
        b?.setOnClickListener(passwordDialogListener)
    }

    @JvmStatic
    fun passwordDialog(ctx: Activity, cancelable: Boolean): AlertDialog {
        val prefHandler = (ctx.application as MyApplication).appComponent.prefHandler()
        val securityQuestion = prefHandler.getString(PrefKey.SECURITY_QUESTION, "")
        val builder = MaterialAlertDialogBuilder(ctx)
        val view = LayoutInflater.from(builder.context).inflate(R.layout.password_check, null)
        view.findViewById<View>(R.id.password).tag = java.lang.Boolean.FALSE
        builder.setTitle(R.string.password_prompt)
            .setView(view)
            .setOnCancelListener { dialog ->
                if (cancelable) dialog.dismiss() else ctx.moveTaskToBack(true)
            }
        if (!securityQuestion.isNullOrEmpty()) {
            builder.setNeutralButton(R.string.password_lost) { _, _ -> }
        }
        builder.setPositiveButton(android.R.string.ok) { _, _ -> }
        if (cancelable) {
            builder.setNegativeButton(android.R.string.cancel) { dialog, _ -> dialog.dismiss() }
        }
        return builder.create()
    }

    fun interface PasswordDialogUnlockedCallback {
        fun onPasswordDialogUnlocked()
    }

    class PasswordDialogListener(
        private val ctx: BaseActivity,
        private val dialog: AlertDialog,
        private val callback: PasswordDialogUnlockedCallback
    ) : View.OnClickListener {

        override fun onClick(v: View) {
            val prefHandler = (ctx.application as MyApplication).appComponent.prefHandler()
            val securityQuestion = prefHandler.getString(PrefKey.SECURITY_QUESTION, "")
            val input = dialog.findViewById<EditText>(R.id.password)!!
            val error = dialog.findViewById<TextView>(R.id.passwordInvalid)!!
            if (v == dialog.getButton(AlertDialog.BUTTON_NEUTRAL)) {
                if (input.tag as Boolean) {
                    input.tag = java.lang.Boolean.FALSE
                    (v as Button).setText(R.string.password_lost)
                    dialog.setTitle(R.string.password_prompt)
                } else {
                    input.tag = java.lang.Boolean.TRUE
                    dialog.setTitle(securityQuestion)
                    (v as Button).setText(android.R.string.cancel)
                }
            } else {
                val value = input.text.toString()
                val isInSecurityQuestion = input.tag as Boolean
                if (Utils.md5(value) == prefHandler.getString(
                        if (isInSecurityQuestion) PrefKey.SECURITY_ANSWER else PrefKey.SET_PASSWORD, ""
                    )
                ) {
                    input.setText("")
                    error.setText("")
                    callback.onPasswordDialogUnlocked()
                    if (isInSecurityQuestion) {
                        prefHandler.putBoolean(PrefKey.PROTECTION_LEGACY, false)
                        ctx.showDismissibleSnackBar(R.string.password_disabled_reenable)
                        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setText(R.string.password_lost)
                        dialog.setTitle(R.string.password_prompt)
                        input.tag = java.lang.Boolean.FALSE
                    }
                    dialog.dismiss()
                } else {
                    input.setText("")
                    error.setText(
                        if (isInSecurityQuestion) R.string.password_security_answer_not_valid
                        else R.string.password_not_valid
                    )
                }
            }
        }
    }

    @JvmStatic
    fun configureEncoding(spinner: Spinner, context: Context?, prefHandler: PrefHandler, prefName: String) {
        if (context == null) return
        spinner.setSelection(
            context.resources.getStringArray(R.array.pref_qif_export_file_encoding).toList()
                .indexOf(prefHandler.getString(prefName, "UTF-8"))
        )
    }

    @JvmStatic
    fun configureDelimiter(spinner: Spinner, context: Context?, prefHandler: PrefHandler, prefName: String) {
        if (context == null) return
        spinner.setSelection(
            context.resources.getStringArray(R.array.pref_csv_import_delimiter_values).toList()
                .indexOf(prefHandler.getString(prefName, ","))
        )
    }

    @JvmStatic
    @Suppress("DEPRECATION")
    fun openBrowse(uri: Uri?, fragment: Fragment) {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            uri?.let { data = it }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            fragment.startActivityForResult(intent, IMPORT_FILENAME_REQUEST_CODE)
        } catch (e: ActivityNotFoundException) {
            (fragment.activity as ProtectedFragmentActivity)
                .showSnackBar(R.string.no_filemanager_installed, Snackbar.LENGTH_SHORT)
        } catch (ex: SecurityException) {
            (fragment.activity as ProtectedFragmentActivity)
                .showSnackBar(
                    "Sorry, this destination does not accept ${intent.action} request. Please select a different one.",
                    Snackbar.LENGTH_SHORT
                )
        }
    }

    @JvmStatic
    fun showSyncUnlinkConfirmationDialog(context: FragmentActivity, syncAccountName: String?, uuid: String?) {
        val b = Bundle().apply {
            putString(
                ConfirmationDialogFragment.KEY_MESSAGE,
                context.getString(R.string.dialog_confirm_sync_unlink, syncAccountName ?: "")
            )
            putString(KEY_UUID, uuid)
            putInt(ConfirmationDialogFragment.KEY_COMMAND_POSITIVE, R.id.SYNC_UNLINK_COMMAND)
            putInt(ConfirmationDialogFragment.KEY_POSITIVE_BUTTON_LABEL, R.string.menu_sync_unlink)
        }
        ConfirmationDialogFragment.newInstance(b)
            .show(context.supportFragmentManager, "SYNC_UNLINK")
    }
}
