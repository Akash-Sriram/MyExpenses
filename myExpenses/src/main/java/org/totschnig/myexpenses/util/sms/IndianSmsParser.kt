package org.totschnig.myexpenses.util.sms

import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

data class ParsedDebitSms(
    val amountRupees: Double,
    val bankHint: String?,
    val accountDigits: String?,
    val payee: String?,
    val upiId: String?,
    val utrNumber: String?,
    val rawMessage: String,
    val timestamp: Long = System.currentTimeMillis(),
    val transactionDate: LocalDate? = null
)

object IndianSmsParser {

    private val DEBIT_KEYWORDS = listOf(
        "debited", "debit", "sent", "paid", "spent", "withdrawn", "transferred", "trf to"
    )

    private val CREDIT_IGNORE_KEYWORDS = listOf(
        "credited", "credit", "refund", "salary", "received rs", "deposited"
    )

    private val AMOUNT_PATTERNS = listOf(
        """(?:Rs\.?|INR|₹)\s*([0-9,]+(?:\.[0-9]{1,2})?)""".toRegex(RegexOption.IGNORE_CASE),
        """(?:debited|spent|paid|withdrawn)\s*(?:by|for|with)?\s*(?:Rs\.?|INR|₹)?\s*([0-9,]+(?:\.[0-9]{1,2})?)""".toRegex(RegexOption.IGNORE_CASE)
    )

    private val VPA_PATTERN = """\b([a-zA-Z0-9.\-_]{2,64}@[a-zA-Z]{2,64})\b""".toRegex()

    private val UTR_PATTERNS = listOf(
        """(?:Ref(?: No|erence)?|UTR|UPI(?:\s*Ref(?: No)?)?|txn(?: id)?|RRN)\s*[:#\s]*([0-9]{9,16})""".toRegex(RegexOption.IGNORE_CASE),
        """UPI/(?:P2[MP]/)?(?:[0-9]+/)?([0-9]{9,16})""".toRegex(RegexOption.IGNORE_CASE)
    )

    private val BANK_PATTERN = """\b(HDFC|ICICI|SBI|Axis|Kotak|PNB|Bank of Baroda|BOB|Canara|Paytm|IndusInd|IDFC|Federal|Yes Bank|Union Bank)\b""".toRegex(RegexOption.IGNORE_CASE)
    private val SENDER_BANK_PATTERN = """(HDFC|ICICI|SBI|Axis|Kotak|PNB|BOB|Canara|Paytm|IndusInd|IDFC|Federal|Yes\s?Bank|Union\s?Bank)""".toRegex(RegexOption.IGNORE_CASE)

    private val ACCOUNT_DIGITS_PATTERN = """(?:A/C|Acct|Account|card|a/c no\.?)\s*(?:no\.?\s*)?(?:[X*.]*|ending\s*(?:with|in)?\s*)(\d{3,4})""".toRegex(RegexOption.IGNORE_CASE)

    private val CREDITED_PAYEE_PATTERN = """(?:;\s*|\.\s*|\bto\s+)([A-Za-z0-9 &._-]{2,64}?)\s+credited\b""".toRegex(RegexOption.IGNORE_CASE)
    private val CREDITED_TO_PATTERN = """credited\s+to\s+([A-Za-z0-9 &._-]{2,64}?)(?:\s+(?:on|Ref|UPI|via|from)|\.|\z)""".toRegex(RegexOption.IGNORE_CASE)
    private val TOWARDS_PATTERN = """towards\s+(?:UPI/(?:P2[MP]/)?(?:[0-9]+/)?)([A-Za-z0-9 _-]+)""".toRegex(RegexOption.IGNORE_CASE)
    private val TRANSFER_TO_PATTERN = """transfer(?:red)?\s+to\s+([A-Za-z0-9 ._-]+?)(?:\s+(?:on|Ref|UPI|via|from)|\.|\z)""".toRegex(RegexOption.IGNORE_CASE)
    private val PAID_TO_PATTERN = """paid\s+to\s+([A-Za-z0-9 ._-]+?)(?:\s+(?:on|Ref|UPI|via|from)|\.|\z)""".toRegex(RegexOption.IGNORE_CASE)
    private val INFO_UPI_PATTERN = """Info:\s*UPI\*([^*]+)\*([^*.\n]+)""".toRegex(RegexOption.IGNORE_CASE)

    private val DATE_PATTERNS = listOf(
        """\b(\d{1,2})[-/ ]([A-Za-z]{3})[-/ ](\d{2,4})\b""".toRegex(RegexOption.IGNORE_CASE),
        """\b(\d{1,2})([A-Za-z]{3})(\d{2,4})\b""".toRegex(RegexOption.IGNORE_CASE),
        """\b(\d{1,2})[-/](\d{1,2})[-/](\d{2,4})\b""".toRegex()
    )

    private val MONTH_MAP = mapOf(
        "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4,
        "may" to 5, "jun" to 6, "jul" to 7, "aug" to 8,
        "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12
    )

    fun parse(
        message: String,
        timestamp: Long = System.currentTimeMillis(),
        sender: String? = null
    ): ParsedDebitSms? {
        val lower = message.lowercase(Locale.ROOT)

        // Must be a debit message
        val hasDebit = DEBIT_KEYWORDS.any { lower.contains(it) }
        if (!hasDebit) return null

        // Must not be a pure credit notification
        val hasCredit = CREDIT_IGNORE_KEYWORDS.any { lower.contains(it) }
        if (hasCredit && !lower.contains("debited")) return null

        // Extract amount
        var amount: Double? = null
        for (pattern in AMOUNT_PATTERNS) {
            val match = pattern.find(message)
            if (match != null) {
                val rawAmount = match.groupValues[1].replace(",", "").trim()
                val parsed = rawAmount.toDoubleOrNull()
                if (parsed != null && parsed > 0.0) {
                    amount = parsed
                    break
                }
            }
        }
        if (amount == null) return null

        // Extract Date if present in message
        val transactionDate = extractDate(message)
        val finalTimestamp = if (transactionDate != null) {
            try {
                val originalTime = java.time.Instant.ofEpochMilli(timestamp)
                    .atZone(ZoneId.systemDefault()).toLocalTime()
                transactionDate.atTime(originalTime)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
            } catch (e: Exception) {
                timestamp
            }
        } else {
            timestamp
        }

        // Extract UPI ID (VPA)
        val vpa = VPA_PATTERN.find(message)?.groupValues?.get(1)

        // Extract UTR / Reference number
        var utr: String? = null
        for (pattern in UTR_PATTERNS) {
            val match = pattern.find(message)
            if (match != null) {
                utr = match.groupValues[1]
                break
            }
        }

        // Extract Bank Hint from message body or sender
        var bankHint = BANK_PATTERN.find(message)?.groupValues?.get(1)?.uppercase(Locale.ROOT)
        if (bankHint == null && sender != null) {
            bankHint = SENDER_BANK_PATTERN.find(sender)?.groupValues?.get(1)?.uppercase(Locale.ROOT)
        }

        // Extract Account Digits
        val accountDigits = ACCOUNT_DIGITS_PATTERN.find(message)?.groupValues?.get(1)

        // Extract Payee
        var payee: String? = null

        // 1. Info: UPI*...*Payee (ICICI older style)
        val infoMatch = INFO_UPI_PATTERN.find(message)
        if (infoMatch != null) {
            payee = formatPayeeName(infoMatch.groupValues[2])
        }

        // 2. "<Payee> credited" (ICICI, HDFC standard) e.g. "; WHISK AND SIP C credited."
        if (payee.isNullOrBlank()) {
            val creditedMatch = CREDITED_PAYEE_PATTERN.find(message)
            if (creditedMatch != null) {
                val candidate = creditedMatch.groupValues[1].trim()
                if (!candidate.startsWith("VPA", ignoreCase = true) && !candidate.contains("@")) {
                    payee = formatPayeeName(candidate)
                }
            }
        }

        // 3. "credited to <Payee>"
        if (payee.isNullOrBlank()) {
            val creditedToMatch = CREDITED_TO_PATTERN.find(message)
            if (creditedToMatch != null) {
                val candidate = creditedToMatch.groupValues[1].trim()
                if (!candidate.startsWith("VPA", ignoreCase = true) && !candidate.contains("@")) {
                    payee = formatPayeeName(candidate)
                }
            }
        }

        // 4. "towards UPI/.../Payee" (Axis)
        if (payee.isNullOrBlank()) {
            val towardsMatch = TOWARDS_PATTERN.find(message)
            if (towardsMatch != null) {
                payee = formatPayeeName(towardsMatch.groupValues[1])
            }
        }

        // 5. "transfer to <Payee>" (SBI)
        if (payee.isNullOrBlank()) {
            val transferMatch = TRANSFER_TO_PATTERN.find(message)
            if (transferMatch != null) {
                val candidate = transferMatch.groupValues[1].trim()
                if (!candidate.startsWith("VPA", ignoreCase = true)) {
                    payee = formatPayeeName(candidate)
                }
            }
        }

        // 6. "paid to <Payee>"
        if (payee.isNullOrBlank()) {
            val paidToMatch = PAID_TO_PATTERN.find(message)
            if (paidToMatch != null) {
                payee = formatPayeeName(paidToMatch.groupValues[1])
            }
        }

        // 7. VPA handle fallback: e.g. "swiggy@icici" -> "Swiggy"
        if (payee.isNullOrBlank() && vpa != null) {
            val handle = vpa.substringBefore("@")
            payee = if (!handle.matches("""\d{10}""".toRegex())) {
                formatPayeeName(handle.replace(".", " "))
            } else {
                vpa
            }
        }

        return ParsedDebitSms(
            amountRupees = amount,
            bankHint = bankHint,
            accountDigits = accountDigits,
            payee = payee,
            upiId = vpa,
            utrNumber = utr,
            rawMessage = message,
            timestamp = finalTimestamp,
            transactionDate = transactionDate
        )
    }

    private fun extractDate(message: String): LocalDate? {
        for (pattern in DATE_PATTERNS) {
            val match = pattern.find(message) ?: continue
            try {
                val day = match.groupValues[1].toInt()
                val monthStr = match.groupValues[2].lowercase(Locale.ROOT)
                val month = MONTH_MAP[monthStr] ?: monthStr.toIntOrNull() ?: continue
                var year = match.groupValues[3].toInt()
                if (year < 100) {
                    year += 2000
                }
                return LocalDate.of(year, month, day)
            } catch (e: Exception) {
                // Ignore parsing errors and continue to next pattern
            }
        }
        return null
    }

    private fun formatPayeeName(name: String): String {
        val clean = name.trim().trimEnd('.', ',', ';')
        if (clean == clean.uppercase(Locale.ROOT)) {
            return clean.split(" ")
                .filter { it.isNotBlank() }
                .joinToString(" ") { word ->
                    if (word.length <= 2) word.uppercase(Locale.ROOT)
                    else word.lowercase(Locale.ROOT).replaceFirstChar { it.uppercaseChar() }
                }
        }
        return clean
    }
}
