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

package org.totschnig.myexpenses.ui

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import org.totschnig.myexpenses.databinding.ScrollableProgressDialogBinding

@Deprecated("Use modern progress UI instead")
class ScrollableProgressDialog(context: Context) : AlertDialog(context) {
    private var binding: ScrollableProgressDialogBinding? = null
    private var message: CharSequence? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        binding = ScrollableProgressDialogBinding.inflate(LayoutInflater.from(context))
        message?.let { setMessage(it) }
        setView(binding!!.root)
        super.onCreate(savedInstanceState)
    }

    override fun setMessage(message: CharSequence?) {
        binding?.message?.text = message
        this.message = message
    }

    fun unsetIndeterminateDrawable() {
        binding?.progress?.visibility = android.view.View.INVISIBLE
    }
}
