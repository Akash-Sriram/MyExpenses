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
import android.view.View
import org.totschnig.myexpenses.databinding.ScrollableProgressDialogBinding

class ScrollableProgressDialog(
    context: Context,
    private val isHorizontal: Boolean = false
) : AlertDialog(context) {
    private var binding: ScrollableProgressDialogBinding? = null
    private var message: CharSequence? = null
    private var initialProgress = 0
    private var initialMax = 0

    var progress: Int
        get() = binding?.progressHorizontal?.progress ?: initialProgress
        set(value) {
            initialProgress = value
            binding?.progressHorizontal?.progress = value
        }

    var max: Int
        get() = binding?.progressHorizontal?.max ?: initialMax
        set(value) {
            initialMax = value
            binding?.progressHorizontal?.max = value
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        binding = ScrollableProgressDialogBinding.inflate(LayoutInflater.from(context))
        message?.let { setMessage(it) }
        if (isHorizontal) {
            binding?.progress?.visibility = View.GONE
            binding?.progressHorizontal?.visibility = View.VISIBLE
            binding?.progressHorizontal?.isIndeterminate = false
            binding?.progressHorizontal?.max = initialMax
            binding?.progressHorizontal?.progress = initialProgress
        }
        setView(binding!!.root)
        super.onCreate(savedInstanceState)
    }

    override fun setMessage(message: CharSequence?) {
        binding?.message?.text = message
        this.message = message
    }

    fun unsetIndeterminateDrawable() {
        binding?.progress?.visibility = View.INVISIBLE
        if (isHorizontal) {
            binding?.progressHorizontal?.visibility = View.GONE
        }
    }
}
