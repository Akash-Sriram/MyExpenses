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
        val upiId = UpiReceiptParser.extractUpiId(lines, effectiveUserPhones, fullText)
        val payee = UpiReceiptParser.extractPayee(lines, upiId, fullText)

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
        val sentFromRegex = Regex("""(?i)(?:sent\s+from|paid\s+from|debited\s+from)\s*[:\-]?\s*([A-Za-z0-9\s]+?Bank)""")
        for (line in lines) {
            if (line.contains("Samsung Wallet", ignoreCase = true)) continue
            val m = sentFromRegex.find(line)
            if (m != null) {
                val candidate = m.groupValues[1].trim()
                if (!candidate.contains("Samsung", ignoreCase = true) && !candidate.contains("@") && candidate.length >= 3) {
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

        val paymentMethod = UpiReceiptParser.detectPaymentMethod(fullText)

        return UpiReceiptData(
            amount = amount,
            payeeName = payee,
            recipientName = null,
            upiId = upiId,
            dateTime = dateTime,
            referenceNumber = utr,
            comment = comment,
            accountOrBankHint = bankHint,
            paymentMethodHint = paymentMethod,
            isExpense = true,
            rawText = fullText
        )
    }
}
