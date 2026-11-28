package org.totschnig.myexpenses.util.upi.rules

import org.totschnig.myexpenses.util.upi.ReceiptParserRule
import org.totschnig.myexpenses.util.upi.UpiReceiptData
import org.totschnig.myexpenses.util.upi.UpiReceiptParser
import java.util.Locale

object SamsungWalletReceiptRule : ReceiptParserRule {

    override val ruleName: String = "SamsungWallet"

    override fun matches(fullText: String, lines: List<String>): Boolean {
        val lower = fullText.lowercase(Locale.ROOT)
        return lower.contains("samsung wallet") || lower.contains("samsung walet")
    }

    override fun parse(
        lines: List<String>,
        fullText: String,
        effectiveUserNames: List<String>,
        effectiveUserPhones: List<String>
    ): UpiReceiptData? {
        val amount = UpiReceiptParser.extractAmount(lines, fullText)
        val dateTime = UpiReceiptParser.extractDateTime(lines, fullText)

        // Payee & UPI ID: In Samsung Wallet, payee is above the payee's VPA
        // e.g.:
        //   ZEPTO
        //   zeptogrocery.payu@hdfcbank
        var payee: String? = null
        var upiId: String? = null

        val vpaRegex = Regex("""(?i)\b([a-zA-Z0-9.\-_]{2,}@[a-zA-Z]{2,})\b""")
        val vpaLineIndex = lines.indexOfFirst { line ->
            !line.contains("Sender", ignoreCase = true) && vpaRegex.containsMatchIn(line)
        }

        if (vpaLineIndex >= 0) {
            val vpaMatch = vpaRegex.find(lines[vpaLineIndex])
            upiId = vpaMatch?.value

            // Candidate payee is the non-empty line above the VPA
            for (idx in (vpaLineIndex - 1) downTo 0) {
                val candidate = lines[idx].trim()
                if (candidate.isEmpty()) continue
                val lowerCand = candidate.lowercase(Locale.ROOT)
                if (lowerCand.contains("samsung wallet") || lowerCand.contains("paid from") || lowerCand.contains("success")) {
                    continue
                }
                if (!UpiReceiptParser.isValidPayee(candidate)) continue
                payee = UpiReceiptParser.cleanPayee(candidate)
                break
            }
        }

        // If not found from VPA relative position, fall back to UpiReceiptParser payee extraction
        if (payee == null) {
            payee = UpiReceiptParser.extractPayee(lines, upiId, fullText)
        }
        if (upiId == null) {
            upiId = UpiReceiptParser.extractUpiId(lines, effectiveUserPhones, fullText)
        }

        // Reference / Transaction ID: labeled under "Transaction ID: ..."
        var utr: String? = null
        val txnRegex = Regex("""(?i)(?:transaction\s*id|txn\s*id|tr\.?\s*id|ref\s*no\.?)\s*[:\-#]?\s*([0-9A-Za-z]{10,24})""")
        for (line in lines) {
            val m = txnRegex.find(line)
            if (m != null) {
                utr = m.groupValues[1].trim()
                break
            }
        }
        if (utr == null) {
            utr = UpiReceiptParser.extractUtr(lines, fullText)
        }

        // Bank / Debit account: "Sent from: ICICI Bank"
        var bankHint: String? = null
        val sentFromRegex = Regex("""(?i)(?:sent\s+from|paid\s+from|debited\s+from)\s*[:\-]?\s*([A-Za-z0-9\s]+?Bank|[A-Za-z0-9\s]+)""")
        for (line in lines) {
            if (line.contains("Samsung Wallet", ignoreCase = true)) continue
            val m = sentFromRegex.find(line)
            if (m != null) {
                val candidate = m.groupValues[1].trim()
                if (!candidate.contains("Samsung", ignoreCase = true) && candidate.length >= 3) {
                    bankHint = candidate
                    break
                }
            }
        }
        if (bankHint == null) {
            bankHint = UpiReceiptParser.extractBankHint(lines, fullText)
        }

        // Comment / Note: Explicitly labeled with "Notes: burger"
        var comment: String? = null
        val notesRegex = Regex("""(?i)^\s*notes?\s*[:\-]\s*(.+)""")
        for (line in lines) {
            val m = notesRegex.find(line)
            if (m != null) {
                val candidate = UpiReceiptParser.cleanComment(m.groupValues[1])
                if (UpiReceiptParser.isValidUserComment(candidate, payee)) {
                    comment = candidate
                    break
                }
            }
        }

        return UpiReceiptData(
            amount = amount,
            payeeName = payee,
            recipientName = null,
            upiId = upiId,
            dateTime = dateTime,
            referenceNumber = utr,
            comment = comment,
            accountOrBankHint = bankHint,
            paymentMethodHint = "UPI",
            isExpense = true,
            rawText = fullText
        )
    }
}
