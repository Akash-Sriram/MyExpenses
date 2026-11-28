package org.totschnig.myexpenses.util.upi.rules

import org.totschnig.myexpenses.util.upi.ReceiptParserRule
import org.totschnig.myexpenses.util.upi.UpiReceiptData
import org.totschnig.myexpenses.util.upi.UpiReceiptParser
import java.util.Locale

object BhimReceiptRule : ReceiptParserRule {

    override val ruleName: String = "BHIM"

    override fun matches(fullText: String, lines: List<String>): Boolean {
        val lower = fullText.lowercase(Locale.ROOT)
        return lower.contains("bhim") ||
                lower.contains("bharat's own") ||
                (lower.contains("banking name") && (lower.contains("to upi id") || lower.contains("debited account")))
    }

    override fun parse(
        lines: List<String>,
        fullText: String,
        effectiveUserNames: List<String>,
        effectiveUserPhones: List<String>
    ): UpiReceiptData? {
        val amount = UpiReceiptParser.extractAmount(lines, fullText)
        val dateTime = UpiReceiptParser.extractDateTime(lines, fullText)

        val rightColumnNoise = Regex("""(?i)\b(?:no\s+remarks?|remarks?|date\s*&\s*time|date\s+and\s+time|transaction\s+id)\b""")
        val stopLabels = Regex("""(?i)(?:transaction\s+id|date\s*&\s*time|to\s+upi\s+id|debited\s+account|process\s+details|remarks?)""")

        // 1. Payee: Lines between "Banking Name" and the next section header
        val bnIdx = lines.indexOfFirst { it.contains("banking name", ignoreCase = true) }
        var payee: String? = null
        if (bnIdx >= 0) {
            val parts = mutableListOf<String>()
            val sameLine = lines[bnIdx].replace(Regex("""(?i).*banking\s+name\s*[:\-]?"""), "").trim()
            if (sameLine.isNotEmpty()) parts += sameLine
            for (i in (bnIdx + 1) until minOf(lines.size, bnIdx + 4)) {
                val l = lines[i]
                val m = stopLabels.find(l)
                if (m != null) {
                    val before = l.substring(0, m.range.first).trim()
                    if (before.isNotEmpty()) parts += before
                    break
                }
                parts += l.trim()
            }
            payee = parts.joinToString(" ").replace(Regex("""\s+"""), " ").trim().takeIf { it.length >= 2 }
        }
        if (payee == null) {
            payee = UpiReceiptParser.extractPayee(lines, null, fullText)
        }

        // 2. UPI ID: Multi-line wrapped text under "To UPI ID"
        val upiIdx = lines.indexOfFirst { it.contains("to upi id", ignoreCase = true) }
        var upiId: String? = null
        if (upiIdx >= 0) {
            val sb = StringBuilder()
            sb.append(lines[upiIdx].replace(Regex("""(?i).*to\s+upi\s+id"""), ""))
            for (i in (upiIdx + 1) until minOf(lines.size, upiIdx + 7)) {
                val l = lines[i]
                if (Regex("""(?i)debited\s+account|process\s+details|icici|hdfc|sbi\b|axis""").containsMatchIn(l) && !l.contains("@")) break
                sb.append(l)
                if (l.contains("@")) break
            }
            val stitched = rightColumnNoise.replace(sb.toString(), "").replace(Regex("""\s+"""), "")
            upiId = Regex("""[A-Za-z0-9][A-Za-z0-9._\-]*@[A-Za-z]{2,}""").find(stitched)?.value
        }
        if (upiId == null) {
            upiId = UpiReceiptParser.extractUpiId(lines, effectiveUserPhones, fullText)
        }

        // 3. Reference / Transaction ID: Labeled under "Transaction ID"
        var utr: String? = null
        val txnIdx = lines.indexOfFirst { it.matches(Regex("""(?i)^\s*transaction\s+id\s*$""")) }
        if (txnIdx >= 0 && txnIdx + 1 < lines.size) {
            val cand = lines[txnIdx + 1].trim().split(" ", "\t").firstOrNull { it.matches(Regex("""\d{10,24}""")) }
            if (cand != null) utr = cand
        }
        if (utr == null) {
            utr = UpiReceiptParser.extractUtr(lines, fullText)
        }

        // 4. Notes / Remarks: Under "Remarks". Ignore "NO REMARK" or empty
        var comment: String? = null
        val hasNoRemark = Regex("""(?i)\bno\s+remarks?\b""").containsMatchIn(fullText)
        if (!hasNoRemark) {
            val remarksIdx = lines.indexOfFirst { it.contains("remarks", ignoreCase = true) }
            if (remarksIdx >= 0) {
                for (i in remarksIdx until minOf(lines.size, remarksIdx + 3)) {
                    val raw = lines[i].replace(Regex("""(?i).*remarks?\s*[:\-]?"""), "").trim()
                    if (raw.isNotEmpty() && !raw.contains("NO REMARK", ignoreCase = true)) {
                        val cleaned = UpiReceiptParser.cleanComment(raw)
                        if (UpiReceiptParser.isValidUserComment(cleaned, payee)) {
                            comment = cleaned
                            break
                        }
                    }
                }
            }
        }

        // 5. Account: Under "Debited account"
        var bankHint: String? = null
        val debitIdx = lines.indexOfFirst { it.contains("debited account", ignoreCase = true) }
        if (debitIdx >= 0) {
            for (i in (debitIdx + 1) until minOf(lines.size, debitIdx + 3)) {
                val line = lines[i].trim()
                if (line.isNotEmpty() && !line.contains("process details", ignoreCase = true)) {
                    bankHint = line
                    break
                }
            }
        }
        if (bankHint == null) {
            bankHint = UpiReceiptParser.extractBankHint(lines, fullText)
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
