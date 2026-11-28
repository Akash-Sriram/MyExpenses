package org.totschnig.myexpenses.util.upi.rules

import org.totschnig.myexpenses.util.upi.ReceiptParserRule
import org.totschnig.myexpenses.util.upi.UpiReceiptData
import org.totschnig.myexpenses.util.upi.UpiReceiptParser
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.Locale

object MerchantRefundRule : ReceiptParserRule {

    override val ruleName: String = "MerchantRefund"

    override fun matches(fullText: String, lines: List<String>): Boolean {
        val lower = fullText.lowercase(Locale.ROOT)
        return lower.contains("refund") && (
            lower.contains("order") ||
            lower.contains("refund ref") ||
            lower.contains("has been processed") ||
            lower.contains("has been initiated") ||
            lower.contains("has been issued")
        )
    }

    override fun parse(
        lines: List<String>,
        fullText: String,
        effectiveUserNames: List<String>,
        effectiveUserPhones: List<String>
    ): UpiReceiptData? {
        // 1. Amount: e.g. "Refund of Rs 160.40", "refund of ₹160.40", "refund of Rs. 160.40"
        val amtRegex = Regex("""(?i)(?:refund(?:\s+of)?|amount(?:\s+of)?)\s*[:=]?\s*(?:rs\.?|inr|₹)?\s*([0-9]+(?:\.[0-9]{1,2})?)""")
        var amount: BigDecimal? = amtRegex.find(fullText)?.groupValues?.get(1)?.toBigDecimalOrNull()
        if (amount == null) {
            amount = UpiReceiptParser.extractAmount(lines, fullText)
        }
        if (amount == null) return null

        // 2. Merchant: e.g. "for your Zepto order", "for your Swiggy order", "Zomato order", "Blinkit order"
        var merchant: String? = null
        val merchantOrderRegex = Regex("""(?i)(?:for\s+your\s+|for\s+)([A-Za-z0-9\s]+?)\s+order\b""")
        val mMatch = merchantOrderRegex.find(fullText)
        if (mMatch != null) {
            val candidate = mMatch.groupValues[1].trim()
            if (candidate.length in 2..30 && !candidate.contains("recent", ignoreCase = true)) {
                merchant = UpiReceiptParser.cleanPayee(candidate)
            }
        }
        if (merchant.isNullOrBlank()) {
            merchant = UpiReceiptParser.extractPayee(lines, null, fullText)
        }

        // 3. Order ID: e.g. "order OIUVJCSRB78772", "order #12345", "order: 12345"
        val orderRegex = Regex("""(?i)\border\s*[:#]?\s*([A-Za-z0-9_-]{5,30})\b""")
        val orderId = orderRegex.find(fullText)?.groupValues?.get(1)?.trim()

        // 4. Refund Reference: e.g. "Refund Ref. num. 104562090814", "Refund Ref: 104562090814", "Ref no: 104562090814"
        val refRegex = Regex("""(?i)(?:refund\s+ref(?:\.?\s*num(?:ber)?|\.?)?|ref(?:\.?\s*num(?:ber)?|\.?)?)\s*[:#.]?\s*([A-Za-z0-9_-]{6,30})\b""")
        val refundRef = refRegex.find(fullText)?.groupValues?.get(1)?.trim()

        val comment = buildString {
            append("Refund")
            if (!orderId.isNullOrBlank()) {
                append(" for order $orderId")
            }
            if (!refundRef.isNullOrBlank()) {
                append(" (Ref: $refundRef)")
            }
        }

        return UpiReceiptData(
            amount = amount,
            payeeName = merchant,
            dateTime = LocalDateTime.now(),
            referenceNumber = refundRef,
            comment = comment,
            isExpense = false,
            suggestedTags = listOf("refund")
        )
    }
}
