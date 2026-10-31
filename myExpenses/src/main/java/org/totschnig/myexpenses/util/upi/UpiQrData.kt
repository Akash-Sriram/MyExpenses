package org.totschnig.myexpenses.util.upi

import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.math.BigDecimal
import java.math.RoundingMode

@Parcelize
data class UpiQrData(
    val vpa: String,
    val payeeName: String? = null,
    val amount: BigDecimal? = null,
    val currency: String = "INR",
    val transactionRef: String? = null,
    val transactionNote: String? = null,
    val merchantCode: String? = null,
    val transactionId: String? = null,
    val url: String? = null,
    val rawUri: String = ""
) : Parcelable {

    val isValid: Boolean
        get() = vpa.isNotBlank() && vpa.contains("@")

    /**
     * Builds a clean, sanitized standard `upi://pay` URI that strips out bloated
     * gateway telemetry, callback URLs, and nested signatures that crash buggy bank apps like iMobile.
     */
    fun toCleanUri(includeRef: Boolean = true): Uri {
        val isRealMerchant = !merchantCode.isNullOrBlank() &&
                merchantCode != "0000" &&
                !merchantCode.equals("null", ignoreCase = true)

        val builder = Uri.Builder()
            .scheme("upi")
            .authority("pay")
            .appendQueryParameter("pa", vpa)

        payeeName?.takeIf { it.isNotBlank() }?.let {
            builder.appendQueryParameter("pn", it)
        }

        amount?.takeIf { it > BigDecimal.ZERO }?.let {
            builder.appendQueryParameter("am", it.setScale(2, RoundingMode.HALF_UP).toPlainString())
        }

        builder.appendQueryParameter("cu", currency.ifBlank { "INR" })

        transactionNote?.takeIf { it.isNotBlank() }?.let {
            builder.appendQueryParameter("tn", it)
        }

        if (isRealMerchant) {
            if (includeRef) {
                transactionRef?.takeIf { it.isNotBlank() }?.let {
                    builder.appendQueryParameter("tr", it)
                }
            }
            builder.appendQueryParameter("mc", merchantCode)
        }

        return builder.build()
    }
}
