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

package org.totschnig.myexpenses.preference

import android.os.Bundle
import android.view.View
import android.widget.EditText
import androidx.preference.EditTextPreferenceDialogFragmentCompat
import org.totschnig.myexpenses.MyApplication
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.util.Utils

class SecurityQuestionDialogFragmentCompat : EditTextPreferenceDialogFragmentCompat() {
    private lateinit var answer: EditText

    override fun onBindDialogView(view: View) {
        super.onBindDialogView(view)
        answer = view.findViewById(R.id.answer)
    }

    override fun onDialogClosed(positiveResult: Boolean) {
        super.onDialogClosed(positiveResult)
        if (positiveResult) {
            (requireContext().applicationContext as MyApplication)
                .appComponent.prefHandler()
                .putString(PrefKey.SECURITY_ANSWER, Utils.md5(answer.text.toString()))
        }
    }

    companion object {
        @JvmStatic
        fun newInstance(key: String): SecurityQuestionDialogFragmentCompat {
            return SecurityQuestionDialogFragmentCompat().apply {
                arguments = Bundle(1).also { it.putString("key", key) }
            }
        }
    }
}
