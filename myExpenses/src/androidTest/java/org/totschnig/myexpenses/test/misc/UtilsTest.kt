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

package org.totschnig.myexpenses.test.misc

import junit.framework.TestCase
import org.totschnig.myexpenses.util.PdfHelper
import org.totschnig.myexpenses.util.Utils
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols

class UtilsTest : TestCase() {
    fun testValidateNumber() {
        var nfDLocal: DecimalFormat
        val symbols = DecimalFormatSymbols()
        symbols.decimalSeparator = '.'
        nfDLocal = DecimalFormat("#0.###", symbols)
        assertEquals(0, Utils.validateNumber(nfDLocal, "4.7")!!.compareTo(BigDecimal("4.7")))
        assertNull(Utils.validateNumber(nfDLocal, "4,7"))
        symbols.decimalSeparator = ','
        nfDLocal = DecimalFormat("#0.###", symbols)
        assertEquals(0, Utils.validateNumber(nfDLocal, "4,7")!!.compareTo(BigDecimal("4.7")))
        assertNull(Utils.validateNumber(nfDLocal, "4.7"))
        nfDLocal = DecimalFormat("#0")
        nfDLocal.isParseIntegerOnly = true
        assertEquals(0, Utils.validateNumber(nfDLocal, "470")!!.compareTo(BigDecimal(470)))
        assertNull(Utils.validateNumber(nfDLocal, "470.123"))
    }

    fun testPdfHelper() {
        assertFalse(PdfHelper.hasAnyRtl("test"))
        assertTrue(PdfHelper.hasAnyRtl("مصروفاتي"))
        assertTrue(PdfHelper.hasAnyRtl("הנושאים שלי"))
    }
}
