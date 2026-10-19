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

package org.totschnig.myexpenses.activity

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.annotation.NonNull
import androidx.annotation.Nullable
import org.totschnig.myexpenses.MyApplication
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.activity.CALCULATOR_REQUEST
import org.totschnig.myexpenses.dialog.ProgressDialogFragment
import org.totschnig.myexpenses.model.ContribFeature
import org.totschnig.myexpenses.model.CurrencyUnit
import org.totschnig.myexpenses.preference.PrefKey.UI_FONT_SIZE
import org.totschnig.myexpenses.provider.KEY_AMOUNT
import org.totschnig.myexpenses.ui.AmountInput
import java.math.BigDecimal
import javax.inject.Inject

abstract class ProtectedFragmentActivity : BaseActivity(),
    ProgressDialogFragment.ProgressDialogListener {

    companion object {
        const val EDIT_COLOR_DIALOG = "editColorDialog"
        const val BUTTON_POSITIVE = eltos.simpledialogfragment.SimpleDialog.OnDialogResultListener.BUTTON_POSITIVE
        const val BUTTON_NEGATIVE = eltos.simpledialogfragment.SimpleDialog.OnDialogResultListener.BUTTON_NEGATIVE
        const val BUTTON_NEUTRAL = eltos.simpledialogfragment.SimpleDialog.OnDialogResultListener.BUTTON_NEUTRAL
    }

    protected lateinit var textColorSecondary: ColorStateList

    @Inject
    lateinit var settings: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings.registerOnSharedPreferenceChangeListener(this)
        val themeArray = theme.obtainStyledAttributes(intArrayOf(android.R.attr.textColorSecondary))
        textColorSecondary = themeArray.getColorStateList(0)!!
        themeArray.recycle()
    }

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(newBase)
        val application = (newBase?.applicationContext as? MyApplication) ?: return
        val customFontScale = application.appComponent.prefHandler().getInt(UI_FONT_SIZE, 0)
        if (customFontScale > 0) {
            val config = Configuration()
            config.fontScale = getFontScale(customFontScale, application.contentResolver)
            applyOverrideConfiguration(config)
        }
        featureManager.initActivity(this)
    }

    private fun getFontScale(customFontScale: Int, contentResolver: ContentResolver): Float {
        return Settings.System.getFloat(contentResolver, Settings.System.FONT_SCALE, 1.0f) *
                (1 + customFontScale / 10f)
    }

    override fun injectDependencies() {
        (applicationContext as MyApplication).appComponent.inject(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        settings.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onResume() {
        super.onResume()
        crashHandler.addBreadcrumb(javaClass.simpleName)
    }

    override fun onProgressDialogDismiss() {}

    override fun onCreateOptionsMenu(@NonNull menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        inflateHelpMenu(menu)
        return true
    }

    protected open fun inflateHelpMenu(menu: Menu) {
        val inflater: MenuInflater = menuInflater
        inflater.inflate(R.menu.help, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val itemId = item.itemId
        if (itemId != 0) {
            // isChecked reports the value at the moment the item is clicked, we dispatch command
            // with the requested new value
            if (dispatchCommand(itemId, if (item.isCheckable) !item.isChecked else null)) {
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK && requestCode == CALCULATOR_REQUEST && data != null) {
            val target = findViewById<View>(data.getIntExtra(CalculatorInput.EXTRA_KEY_INPUT_ID, 0))
            if (target is AmountInput) {
                target.setAmount(BigDecimal(data.getStringExtra(KEY_AMOUNT)), false, false)
            } else {
                showSnackBar("CALCULATOR_REQUEST launched with incorrect EXTRA_KEY_INPUT_ID")
            }
        }
    }

    override fun onCurrencySelectionChanged(@NonNull currencyUnit: CurrencyUnit) {}

    enum class ThemeType {
        dark, light
    }
}
