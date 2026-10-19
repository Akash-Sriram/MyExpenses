package org.totschnig.myexpenses.util.upi

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.math.BigDecimal
import java.time.LocalDateTime

@Parcelize
data class UpiReceiptData(
    val amount: BigDecimal? = null,
    val payeeName: String? = null,
    val upiId: String? = null,
    val dateTime: LocalDateTime? = null,
    val referenceNumber: String? = null,
    val accountOrBankHint: String? = null,
    val paymentMethodHint: String = "UPI",
    val isExpense: Boolean = true,
    val rawText: String? = null
) : Parcelable {
    val isValid: Boolean
        get() = amount != null || !payeeName.isNullOrBlank() || !referenceNumber.isNullOrBlank()
}
