package org.totschnig.myexpenses.util.upi.rules

import org.totschnig.myexpenses.util.upi.ReceiptParserRule
import org.totschnig.myexpenses.util.upi.UpiReceiptData
import org.totschnig.myexpenses.util.upi.UpiReceiptParser
import java.util.Locale

object IMobileReceiptRule : ReceiptParserRule {

    override val ruleName: String = "IMobile"

    override fun matches(fullText: String, lines: List<String>): Boolean {
        val lower = fullText.lowercase(Locale.ROOT)
        return lower.contains("imobile") ||
                lower.contains("imobile pay") ||
                (lower.contains("paid successfully") && lower.contains("icici")) ||
                (lower.contains("payment successful") && lower.contains("icici"))
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

        // 1. Payee: Look for explicit "To <Payee>" line
        var payee: String? = null
        val toRegex = Regex("""(?i)^\s*to\s*[:\-]?\s*(.+)""")
        for (line in lines) {
            val m = toRegex.find(line)
            if (m != null) {
                val candidate = UpiReceiptParser.cleanPayee(m.groupValues[1])
                if (UpiReceiptParser.isValidPayee(candidate) && !candidate.contains("bank", ignoreCase = true)) {
                    payee = candidate
                    break
                }
            }
        }
        if (payee == null) {
            payee = UpiReceiptParser.extractPayee(lines, upiId, fullText)
        }

        // 2. Reference / URN: iMobile uses "URN: ..." or "Ref No.: ..."
        var utr: String? = null
        val urnRegex = Regex("""(?i)(?:urn|ref\s*no\.?|reference\s*no\.?|rrn)\s*[:\-#]?\s*([0-9A-Za-z]{10,24})""")
        for (line in lines) {
            val m = urnRegex.find(line)
            if (m != null) {
                utr = m.groupValues[1].trim()
                break
            }
        }
        if (utr == null) {
            utr = UpiReceiptParser.extractUtr(lines, fullText)
        }

        // 3. User Note / Comment:
        // iMobile often has a remark pill: "for potluck" or a line between amount and "To <Payee>"
        var comment: String? = null
        val pillRegex = Regex("""(?i)^\s*for\s+([a-zA-Z0-9\s.,'#\-]{2,50})$""")
        for (line in lines) {
            val m = pillRegex.find(line)
            if (m != null) {
                val raw = m.groupValues[1].trim()
                if (UpiReceiptParser.isValidUserComment(raw, payee)) {
                    comment = line.trim()
                    break
                }
            }
        }

        if (comment == null) {
            // Check line between amount and "To <Payee>"
            val toIdx = lines.indexOfFirst { it.matches(toRegex) }
            if (toIdx > 0) {
                for (idx in (toIdx - 1) downTo 0) {
                    val cand = lines[idx].trim()
                    if (cand.isEmpty()) continue
                    if (cand.contains("Paid successfully", ignoreCase = true)) break
                    val cleaned = UpiReceiptParser.cleanComment(cand)
                    if (UpiReceiptParser.isValidUserComment(cleaned, payee)) {
                        comment = cleaned
                        break
                    }
                }
            }
        }
        if (comment == null) {
            comment = UpiReceiptParser.extractComment(lines, payee, fullText)
        }

        // 4. Debit Account: Default to "ICICI Bank" for iMobile
        val bankHint = "ICICI Bank"

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
