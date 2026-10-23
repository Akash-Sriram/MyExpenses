package org.totschnig.myexpenses.util.sms

import java.util.Locale

data class ParsedDebitSms(
    val amountRupees: Double,
    val bankHint: String?,
    val accountDigits: String?,
    val payee: String?,
    val upiId: String?,
    val utrNumber: String?,
    val rawMessage: String,
    val timestamp: Long = System.currentTimeMillis()
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
        """(?:Ref(?: No|erence)?|UTR|UPI Ref(?: No)?|txn(?: id)?)\s*[:#\s]*([0-9]{9,16})""".toRegex(RegexOption.IGNORE_CASE),
        """UPI/(?:P2[MP]/)?([0-9]{9,16})""".toRegex(RegexOption.IGNORE_CASE)
    )

    private val BANK_PATTERN = """\b(HDFC|ICICI|SBI|Axis|Kotak|PNB|Bank of Baroda|BOB|Canara|Paytm|IndusInd|IDFC|Federal|Yes Bank|Union Bank)\b""".toRegex(RegexOption.IGNORE_CASE)
    private val SENDER_BANK_PATTERN = """(HDFC|ICICI|SBI|Axis|Kotak|PNB|BOB|Canara|Paytm|IndusInd|IDFC|Federal|Yes\s?Bank|Union\s?Bank)""".toRegex(RegexOption.IGNORE_CASE)

    private val ACCOUNT_DIGITS_PATTERN = """(?:A/C|Acct|Account|card|a/c no\.?)\s*(?:no\.?\s*)?(?:[X*]*|ending\s*(?:with\s*)?)(\d{3,4})""".toRegex(RegexOption.IGNORE_CASE)

    private val TOWARDS_PATTERN = """towards\s+(?:UPI/(?:P2[MP]/)?(?:[0-9]+/)?)([A-Za-z0-9 _-]+)""".toRegex(RegexOption.IGNORE_CASE)
    private val TRANSFER_TO_PATTERN = """transfer(?:red)?\s+to\s+([A-Za-z0-9 ._-]+?)(?:\s+(?:on|Ref|UPI|via|from)|\.|\z)""".toRegex(RegexOption.IGNORE_CASE)
    private val PAID_TO_PATTERN = """paid\s+to\s+([A-Za-z0-9 ._-]+?)(?:\s+(?:on|Ref|UPI|via|from)|\.|\z)""".toRegex(RegexOption.IGNORE_CASE)
    private val INFO_UPI_PATTERN = """Info:\s*UPI\*([^*]+)\*([^*.\n]+)""".toRegex(RegexOption.IGNORE_CASE)

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
        val infoMatch = INFO_UPI_PATTERN.find(message)
        if (infoMatch != null) {
            payee = infoMatch.groupValues[2].trim()
        }
        if (payee.isNullOrBlank()) {
            val towardsMatch = TOWARDS_PATTERN.find(message)
            if (towardsMatch != null) {
                payee = towardsMatch.groupValues[1].trim()
            }
        }
        if (payee.isNullOrBlank()) {
            val transferMatch = TRANSFER_TO_PATTERN.find(message)
            if (transferMatch != null) {
                val candidate = transferMatch.groupValues[1].trim()
                if (!candidate.startsWith("VPA", ignoreCase = true)) {
                    payee = candidate
                }
            }
        }
        if (payee.isNullOrBlank()) {
            val paidToMatch = PAID_TO_PATTERN.find(message)
            if (paidToMatch != null) {
                payee = paidToMatch.groupValues[1].trim()
            }
        }
        if (payee.isNullOrBlank() && vpa != null) {
            // e.g. "swiggy@icici" -> "Swiggy"
            val handle = vpa.substringBefore("@")
            if (!handle.matches("""\d{10}""".toRegex())) {
                payee = handle.replace(".", " ").split(" ")
                    .filter { it.isNotBlank() }
                    .joinToString(" ") { it.replaceFirstChar { char -> char.uppercaseChar() } }
            } else {
                payee = vpa
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
            timestamp = timestamp
        )
    }
}
