package org.totschnig.myexpenses.util.upi

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.regex.Pattern

object UpiReceiptParser {

    private val RUPEE_AMOUNT_PATTERN = Pattern.compile(
        """(?<![a-zA-Z0-9#])(?:[₹\u20B9\u09B9]|Rs\.?|INR|R(?=[\s\d|:JỊ]))[\s|:JỊ\-]*([0-9]{1,3}(?:,[0-9]{2,3})*(?:\.[0-9]{1,2})?|[0-9]+(?:\.[0-9]{1,2})?)(?!\d)""",
        Pattern.CASE_INSENSITIVE
    )

    private val LABELED_AMOUNT_PATTERN = Pattern.compile(
        """(?i)(?:grand\s+total|net\s+(?:payable|amount)|total(?:\s+amount|\s+payable|\s+bill)?|bill\s+amount|paid|sent|payment(?:\s+of)?|amount(?:\s*[:=])?|transfer(?:\s+of)?|card\s+payment|amount\s+received)\s*[:=]?\s*(?:Rs\.?|INR|Re\.?|[₹\u20B9\u0969\u09B9\u0930\u0939?zZrR#*F:|\-])*\s*([0-9]{1,3}(?:,[0-9]{2,3})*(?:\.[0-9]{1,2})?|[0-9]+(?:\.[0-9]{1,2})?)(?!\d)"""
    )

    private val STANDALONE_AMOUNT_PATTERN = Pattern.compile(
        """^([0-9]{1,3}(?:,[0-9]{2,3})*(?:\.[0-9]{1,2})?|[0-9]+(?:\.[0-9]{1,2})?)$"""
    )

    private val UPI_VPA_PATTERN = Pattern.compile(
        """(?i)\b([a-zA-Z0-9.\-_\u0966-\u096F\u09E6-\u09EF]{2,}(?:\s+[a-zA-Z0-9.\-_\u0966-\u096F\u09E6-\u09EF]+)*\s*@\s*[a-zA-Z0-9.\-_]{2,})\b"""
    )

    private val UTR_LABEL_LINE_PATTERN = Regex(
        """(?i)^\s*(?:b-connect(?:\s*txn)?\s*id|transaction\s*reference\s*(?:no\.?|num(?:ber)?|id)?|transaction\s*id|txn\s*id|tr\.?\s*id|upi\s*(?:ref(?:erence)?|txn|transaction)\s*(?:no\.?|id|num(?:ber)?)?|(?:bank\s*)?ref(?:erence)?\s*(?:#|no\.?|id)?|utr|rrn(?:\s*no)?|urn)\s*[:\-#]?\s*${'$'}"""
    )

    private val UTR_EXPLICIT_PATTERN = Pattern.compile(
        """(?i)(?:b-connect(?:\s*txn)?\s*id|transaction\s*reference\s*(?:no\.?|num(?:ber)?|id)?|transaction\s*id|txn\s*id|tr\.?\s*id|upi\s*(?:ref(?:erence)?|txn|transaction)\s*(?:no\.?|id|num(?:ber)?)?|(?:bank\s*)?ref(?:erence)?\s*(?:#|no\.?|id)?|utr|rrn(?:\s*no)?|urn)[ \t]*[:\-#]?[ \t]*([0-9A-Za-z]{10,24})"""
    )

    private val UTR_STANDALONE_PATTERN = Pattern.compile(
        """\b([0-9]{12})\b"""
    )

    private val BANK_HINT_PATTERNS = listOf(
        Pattern.compile("""(?i)(?:sent\s+from|paid\s+from|debited\s+from)\s*[:\-]?\s*([1li]?c[1li]c[1li](?:\s*bank)?|HDFC(?:\s+Bank)?|State Bank of India|SBI(?:[\s_]+Payments)?|Axis(?:\s+Bank)?|Kotak(?:\s+Bank)?|Yes Bank|Canara(?:\s+Bank)?|Bank of Baroda|PNB|Punjab National Bank|Union Bank|[A-Za-z ]+?Bank)"""),
        Pattern.compile("""(?i)from[:\s]+.*?\(([A-Za-z ]+?Bank)\)"""),
        Pattern.compile("""(?i)\b(?:i\s*)?([1li]?c[1li]c[1li](?:\s*bank)?|HDFC(?:\s+Bank)?|State Bank of India|SBI(?:[\s_]+Payments)?|Axis(?:\s+Bank)?|Kotak(?:\s+Bank)?|Yes Bank|Canara(?:\s+Bank)?|Bank of Baroda|PNB|Punjab National Bank|Union Bank|Paytm Payments Bank|Airtel Payments Bank|iMobile|Samsung Walet|Samsung Wallet)\b"""),
        Pattern.compile("""(?i)powered\s+by\s+([A-Za-z ]+?)\s*\|""")
    )

    private val PAYEE_LINE_PATTERNS = listOf(
        Pattern.compile("""(?i)^\s*(?:biller\s+name|paid\s+to|payment\s+to|sent\s+to|received\s+from)\s*[:\-]?\s*(.+)"""),
        Pattern.compile("""(?i)^\s*to\s*[:\-]\s*(.+)"""),
        Pattern.compile("""(?i)^\s*to\s+(.+)"""),
        Pattern.compile("""(?i)^\s*customer\s+name\s*[:\-]?\s*(.+)"""),
        Pattern.compile("""(?i)^\s*beneficiary(?:\s+name)?\s*[:\-]?\s*(.+)"""),
        Pattern.compile("""(?i)^\s*notes?\s*[:\-]\s*(.+)""")
    )

    private val PROMINENT_MERCHANTS = listOf(
        "Avenue Supermarts", "DMart", "BookMyShow", "Kafa Clothing", "Apollo Pharmacy",
        "Medplus", "Zomato", "Swiggy", "Amazon", "Flipkart", "Indian Oil", "Bharat Petroleum",
        "Bharat Gas (BPCL)", "Bharat Gas", "Hathway Broadband", "Hathway",
        "Tamil Nadu Electricity Board", "TNEB", "Life Insurance Corporation of India", "LIC",
        "HPCL", "Reliance", "BigBasket", "Blinkit", "Zepto", "Aavin"
    )

    private val IGNORE_PAYEE_WORDS = setOf(
        "sender", "sender:", "sent from", "sent from:", "paid from", "paid from:", "payer",
        "to", "to:", "paid to", "payment to", "sent to", "biller", "biller name", "beneficiary",
        "beneficiary name", "customer name", "notes", "notes:",
        "paid from", "payment completed", "transaction successful", "payment successful",
        "bill payment successful", "bill payment successful!", "bill paymentsuccessful!", "bill paymentsuccessful",
        "money sent", "money sent!", "biller id", "biller ld", "biller name", "bill amount", "transaction status",
        "payment mode", "transaction reference number", "registered contact number", "convenience fee", "total amount",
        "bill paid date and time", "pay from", "enable auto bill pay", "quick pay summary", "transaction id",
        "amount paid", "sent", "completed", "powered by", "upi", "axis bank", "icici bank", "1cici bank", "cici bank",
        "samsung wallet", "samsung walet", "google pay", "phonepe", "paytm", "bhim", "tax invoice", "retail invoice",
        "customer copy", "thank you", "date", "time", "amount", "total", "vpa", "upi id", "upiid",
        "tovpa", "to vpa", "to upi id", "from upi id", "from vpa", "from account", "remarks", "when",
        "reference", "ref", "ref.", "ref no", "ref no.", "ref #", "tr id", "tr. id", "tr id:", "tr. id:",
        "success", "success!", "home", "once in a while", "shopping desires", "give in to",
        "treat your family", "good time", "savings", "open iwish", "apply now", "cashback",
        "special offer", "discount", "download app", "personal loan", "applyfor", "imobile pay",
        "neft", "imps", "expiring soon", "you won a scratch card", "send again", "date & time", "date and time"
    )

    private fun normalizeOcrText(text: String): String {
        val monthTokens = "jan|feb|mar|apr|may|jun|jul|aug|sep|oct|0ct|nov|dec"
        var t = text
            .replace('\u202F', ' ')
            .replace('\u00A0', ' ')
            .replace(Regex("""(?i)\bpaidat(?=\d|\b)"""), "Paid at ")
            .replace(Regex("""(?i)'?(\d{2,4})IST\b"""), " $1 IST")
            .replace(Regex("""(?i)'(\d{2,4})"""), " $1 ")
            .replace(Regex("""(?i)\b[oO\u09e6\u0966](\d:[0-5]\d)"""), "0$1")
            .replace(Regex("""(?i)\b([0-2])[oO\u09e6\u0966]:([0-5]\d)"""), "$10:$2")
            .replace(Regex("""(?i)\b(\d{1,2}):[oO\u09e6\u0966](\d)\b"""), "$1:0$2")
            .replace(Regex("""(?i)\b(\d{1,2})[;.](\d{2})\s*(AM|PM)\b"""), "$1:$2 $3")
            .replace(Regex("""(?i)\b(\d{1,2}):(\d{2})\s*([ap]m)(?=[,\s]|\b|$)"""), "$1:$2 $3")
            .replace(Regex("""(?i)\b(mon|tue|wed|thu|fri|sat|sun)\.(\d{1,2})"""), "$1. $2 ")
            .replace(Regex("""(?i)[\u09e6\u09660]ct\b"""), "Oct")
            .replace(Regex("""(?i),(\d{1,2})([a-z]{3})"""), ", $1 $2")
            .replace(Regex("""(?i)\b(\d{1,2})(?:st|nd|rd|th)?($monthTokens)"""), "$1 $2")
            .replace(Regex("""(?i)($monthTokens)(\d{2,4})\b"""), "$1 $2")
            .replace(Regex("""(?i)\b(?:inr|rs)\.?[\s|JỊ:\-]+(?=\d)"""), "INR ")
            .replace(Regex("""(?i)\b(inr|rs)\.?(\d+)"""), "$1 $2")
            .replace(Regex("""(\d{2,4})\.(\d{1,2}:\d{2})"""), "$1 $2")
            .replace(Regex("""(\d{2,4})(\d{1,2}:\d{2})"""), "$1 $2")
            .replace(Regex("""\b(\d{2})(\d{2})(\d{2})\s*(AM|PM)\b"""), "$1:$2:$3 $4")
            .replace(Regex("""\b(\d{2}):(\d{2})(\d{2})\s*(AM|PM)\b"""), "$1:$2:$3 $4")
        return t
    }

    const val PREF_KEY_USER_PROFILE_NAME = "pref_user_profile_name"
    const val PREF_KEY_USER_PROFILE_PHONE = "pref_user_profile_phone"
    const val PREF_KEY_USER_PROFILE_VPA = "pref_user_profile_vpa"

    const val DEFAULT_USER_NAME = "Akash Sriram"
    const val DEFAULT_USER_PHONE = "8680878355"
    const val DEFAULT_USER_VPA = "8680878355@icici"

    @JvmOverloads
    fun parse(
        rawText: String,
        userName: String? = null,
        userPhone: String? = null,
        userVpa: String? = null
    ): UpiReceiptData {
        val lines = rawText.lines()
        return parse(lines, rawText, userName, userPhone, userVpa)
    }

    @JvmOverloads
    fun parse(
        lines: List<String>,
        fullText: String? = null,
        userName: String? = null,
        userPhone: String? = null,
        userVpa: String? = null
    ): UpiReceiptData {
        val rawJoined = fullText ?: lines.joinToString("\n")
        val joined = normalizeOcrText(rawJoined)
        val normalizedLines = lines.map { normalizeOcrText(it).trim() }.filter { it.isNotEmpty() }

        val effectiveUserNames = buildList {
            add(userName ?: DEFAULT_USER_NAME)
            add("Akash Sriram")
            add("AkashSriram")
            add("MRG AKASH SRIRAM")
            add("G AKASH SRIRAM")
            add("AKASH SRIRAM G")
            add("Akash")
        }.distinct()

        val effectiveUserPhones = buildList {
            add(userPhone ?: DEFAULT_USER_PHONE)
            add("8680878355")
        }.distinct()

        val amount = extractAmount(normalizedLines, joined)
        val upiId = extractUpiId(normalizedLines, effectiveUserPhones, joined)
        val rawPayee = extractPayee(normalizedLines, upiId, joined)
        val dateTime = extractDateTime(normalizedLines, joined)
        val utr = extractUtr(normalizedLines, joined)
        val bankHint = extractBankHint(normalizedLines, joined)
        val paymentMethod = detectPaymentMethod(joined)
        val comment = extractComment(normalizedLines, rawPayee, joined)

        val isRecipientMe = isUserRecipient(rawPayee, upiId, effectiveUserNames, effectiveUserPhones)
        val isIncome = containsRefundOrIncome(joined) || isRecipientMe
        val isExpense = !isIncome

        val payeeName = if (isRecipientMe) {
            extractSender(normalizedLines, joined)?.takeIf { !isUserRecipient(it, null, effectiveUserNames, effectiveUserPhones) }
        } else {
            rawPayee
        }
        val recipientName = if (isRecipientMe) rawPayee else null

        return UpiReceiptData(
            amount = amount,
            payeeName = payeeName,
            recipientName = recipientName,
            upiId = upiId,
            dateTime = dateTime,
            referenceNumber = utr,
            comment = comment,
            accountOrBankHint = bankHint,
            paymentMethodHint = paymentMethod,
            isExpense = isExpense,
            rawText = joined
        )
    }

    private fun detectPaymentMethod(text: String): String {
        val lower = text.lowercase(Locale.ROOT)
        return when {
            lower.contains("auto-pay") || lower.contains("auto debit") || lower.contains("standing instruction") || lower.contains("mandate") || lower.contains("nach") || (lower.contains("remarks") && lower.contains("auto")) -> "Auto Debit"
            lower.contains("internet banking") || lower.contains("net banking") || lower.contains("neft") || lower.contains("imps") -> "Net Banking"
            lower.contains("b-connect") || lower.contains("bill payment") || lower.contains("billpayment") -> "Bill Payment"
            lower.contains("visacredit") || lower.contains("cardtype") || lower.contains("eft-sale") || lower.contains("credit card") -> "Credit Card"
            lower.contains("paylater") || lower.contains("pay later") -> "Paylater"
            lower.contains("debit card") -> "Debit Card"
            lower.contains("wallet balance") || lower.contains("upi lite") -> "Wallet Balance"
            else -> "UPI"
        }
    }

    private fun isReferenceOrAccountLine(line: String): Boolean {
        val lower = line.lowercase(Locale.ROOT).trim()
        return lower.contains("@") || lower.contains("reference") || lower.contains("ref #") ||
               lower.contains("ref no") || lower.contains("ref.") || lower.startsWith("ref") ||
               lower.contains("utr") || lower.contains("rrn") || lower.contains("txn id") ||
               lower.contains("transaction id") || lower.contains("tr. id") || lower.contains("tr id") ||
               lower.contains("sba-") || lower.contains("a/c") ||
               lower.contains("acc no") || lower.contains("account no") || lower.contains("sent from") ||
               lower.contains("paid from") || lower.contains("debited from") ||
               lower.contains("loan") || lower.contains("lakh") || lower.contains("|akh") ||
               lower.contains("crore") || lower.contains("pre-approved") || lower.contains("preapproved") ||
               lower.contains("apply for") || lower.contains("apply now") || lower.contains("up to") ||
               lower.contains("upto") || lower.contains("cashback up to") || lower.contains("win up to") ||
               lower.contains("insurance")
    }

    private fun cleanAmountCandidate(line: String): String {
        // Strip leading OCR symbol misreads when followed by space or digits (e.g. "₹30.00" -> "30.00", "7 618.00" -> "618.00", "? 1,250.00" -> "1,250.00", "३ 201.72" -> "201.72", "R600.00" -> "600.00")
        var cleaned = line.trim().replace(Regex("""^(?:[₹\u20B9\u0969\u09B9\u0930\u0939?zZrR#*F])\s*(?=\d)"""), "")
        cleaned = cleaned.replace(Regex("""^(?:Rs\.?|INR|Re\.?|R(?=[\d|:JỊ]))[\s|JỊ:\-]*"""), "")
        cleaned = cleaned.replace(Regex("""^(?:7)\s+(?=\d)"""), "")
        cleaned = cleaned.replace(Regex("""^[|:JỊ\-\s]+(?=\d)"""), "")
        return cleaned.trim()
    }

    private fun extractAmount(lines: List<String>, fullText: String): BigDecimal? {
        // Priority 0: Status line (Sent / Completed / Successful) preceded by amount line
        val statusIndex = lines.indexOfFirst {
            it.matches(Regex("""(?i)^\s*(?:sent|completed|payment\s+successful!?|transaction\s+successful!?|successful!?)\s*$"""))
        }
        if (statusIndex > 0) {
            val candidateLine = lines[statusIndex - 1].trim()
            if (!isReferenceOrAccountLine(candidateLine) && !candidateLine.matches(Regex("""^\d{10,24}$"""))) {
                var candidateCleaned = cleanAmountCandidate(candidateLine)
                // If OCR misread '₹' as '7' with zero space (e.g. "730.00" -> "30.00"):
                if (!candidateLine.contains("₹") && !candidateLine.contains("Rs", ignoreCase = true) && !candidateLine.contains("INR", ignoreCase = true)) {
                    if (candidateCleaned.matches(Regex("""^7\d{2,}(?:\.\d{1,2})?$"""))) {
                        val stripped = candidateCleaned.substring(1)
                        val strippedVal = stripped.replace(",", "").toBigDecimalOrNull()
                        if (strippedVal != null && strippedVal >= BigDecimal.ONE) {
                            candidateCleaned = stripped
                        }
                    }
                }
                val m = STANDALONE_AMOUNT_PATTERN.matcher(candidateCleaned)
                if (m.matches()) {
                    val parsed = m.group(1)?.replace(",", "")?.toBigDecimalOrNull()
                    if (parsed != null && isValidAmount(parsed)) {
                        return parsed
                    }
                }
            }
        }

        // Priority 1: Multi-line amount label followed by value on next line (e.g. ICICI: "Amount \n 618.00", "Amount \n ₹ 618.00", "Bill payment successful! \n 453")
        for (i in lines.indices) {
            val line = lines[i].trim()
            if (line.matches(Regex("""(?i)^\s*(?:amount|total(?:\s+amount|\s+payable|\s+bill)?|grand\s+total|net\s+(?:payable|amount)|bill\s+amount|transfer\s+amount|bill\s+payments?(?:\s+successful!?)?|amount\s+paid)\s*[:=]?$"""))) {
                for (offset in 1..2) {
                    if (i + offset < lines.size) {
                        val candidateLine = lines[i + offset].trim()
                        if (!isReferenceOrAccountLine(candidateLine) && !candidateLine.matches(Regex("""^\d{10,24}$"""))) {
                            val cleaned = cleanAmountCandidate(candidateLine)
                            val m = STANDALONE_AMOUNT_PATTERN.matcher(cleaned)
                            if (m.matches()) {
                                val parsed = m.group(1)?.replace(",", "")?.toBigDecimalOrNull()
                                if (parsed != null && isValidAmount(parsed)) {
                                    return parsed
                                }
                            }
                        }
                    }
                }
            }
        }

        // Priority 2: line with labeled amount on same line (Paid ..., Sent ..., Amount ..., Grand Total ...)
        for (line in lines) {
            if (isReferenceOrAccountLine(line)) continue
            val m = LABELED_AMOUNT_PATTERN.matcher(line)
            if (m.find()) {
                val parsed = m.group(1)?.replace(",", "")?.toBigDecimalOrNull()
                if (parsed != null && isValidAmount(parsed)) {
                    return parsed
                }
            }
        }

        // Priority 3: explicitly marked with ₹ / Rs / INR on lines that are NOT reference/account lines
        for (line in lines) {
            if (isReferenceOrAccountLine(line)) continue
            val rupeeMatcher = RUPEE_AMOUNT_PATTERN.matcher(line)
            if (rupeeMatcher.find()) {
                val raw = rupeeMatcher.group(1)?.replace(",", "")
                val parsed = raw?.toBigDecimalOrNull()
                if (parsed != null && isValidAmount(parsed)) {
                    return parsed
                }
            }
        }

        // Priority 4: Standalone decimal amounts (e.g. "7 618.00" -> "618.00", "215.94", "70.87")
        var bestDecimalAmount: BigDecimal? = null
        var fallbackIntegerAmount: BigDecimal? = null
        for (line in lines) {
            if (isReferenceOrAccountLine(line)) continue
            val cleaned = cleanAmountCandidate(line)
            val matcher = STANDALONE_AMOUNT_PATTERN.matcher(cleaned)
            if (matcher.matches()) {
                val raw = matcher.group(1)?.replace(",", "")
                val parsed = raw?.toBigDecimalOrNull()
                if (parsed != null && isValidAmount(parsed)) {
                    if (raw.contains(".")) {
                        bestDecimalAmount = parsed
                        break
                    } else if (fallbackIntegerAmount == null) {
                        fallbackIntegerAmount = parsed
                    }
                }
            }
        }
        if (bestDecimalAmount != null) return bestDecimalAmount
        if (fallbackIntegerAmount != null) return fallbackIntegerAmount

        return null
    }

    private fun isValidAmount(amount: BigDecimal): Boolean {
        // Exclude 12-digit UTRs or years or astronomical numbers
        if (amount >= BigDecimal("100000000")) return false // over 10 crore
        return amount > BigDecimal.ZERO
    }

    private fun extractUpiId(lines: List<String>, userPhones: List<String> = emptyList(), fullText: String? = null): String? {
        val isSamsung = fullText?.contains("Samsung Wallet", ignoreCase = true) == true ||
                fullText?.contains("Samsung Walet", ignoreCase = true) == true ||
                lines.any { it.contains("Samsung Wallet", ignoreCase = true) || it.contains("Samsung Walet", ignoreCase = true) }

        val amountIndex = if (isSamsung) {
            lines.indexOfFirst { line ->
                line.contains("₹") || line.contains("INR") || STANDALONE_AMOUNT_PATTERN.matcher(cleanAmountCandidate(line)).matches()
            }
        } else -1

        // In Samsung Wallet, payee VPA is strictly at the top inside the card, ABOVE the amount.
        // Lines after amount are sender details (Sender: asg98@pingpay) and must be excluded.
        val searchLines = if (isSamsung && amountIndex > 0) lines.subList(0, amountIndex) else lines

        val candidates = mutableListOf<String>()
        for (i in searchLines.indices) {
            val line = searchLines[i]
            // Skip lines that indicate sender / payer
            if (line.contains("sender", ignoreCase = true) || line.contains("sent from", ignoreCase = true) || line.contains("paid from", ignoreCase = true) || line.contains("debited from", ignoreCase = true)) {
                continue
            }
            // In Samsung Wallet receipts, ML Kit often produces stray left-column labels like "Sent from:" immediately before the merchant VPA.
            // Since searchLines is already restricted to lines before amount, do not reject merchant VPAs based on preceding "Sent from:" lines.
            if (!isSamsung && i > 0 && searchLines[i - 1].matches(Regex("""(?i)^\s*(?:sender|sent\s+from|paid\s+from|from|debited\s+from)\s*[:\-]?\s*$"""))) {
                continue
            }
            val matcher = UPI_VPA_PATTERN.matcher(line)
            if (matcher.find()) {
                val rawCandidate = matcher.group(1)?.replace(" ", "")?.trimEnd('.') ?: continue
                val normalizedCandidate = normalizeDevanagariDigits(rawCandidate)
                var candidate = repairIndianPhoneUpiId(normalizedCandidate, userPhones)
                // Normalize common OCR misreads in bank handles (e.g. "rn" for "m" in paytm or "n" in nsdlpb)
                candidate = candidate
                    .replace("@cashfreernsdlpb", "@cashfreensdlpb")
                    .replace("@paytrn", "@paytm")

                val isUserSenderVpa = candidate.equals("asg98@pingpay", ignoreCase = true) ||
                        candidate.contains("@pingpay", ignoreCase = true)

                if (!isUserSenderVpa) {
                    // Filter out non-UPI email addresses if possible
                    if (!candidate.endsWith(".com") || candidate.contains("@axb") || candidate.contains("@upi") || candidate.contains("@hdfcbank") || candidate.contains("@icici") || candidate.contains("@sbi") || candidate.contains("@okhdfcbank") || candidate.contains("@okaxis") || candidate.contains("@okicici") || candidate.contains("@oksbi") || candidate.contains("@paytm") || candidate.contains("@ybl") || candidate.contains("@ibl")) {
                        candidates.add(candidate)
                    }
                }
            }
        }
        return candidates.firstOrNull()
    }

    private fun normalizeDevanagariDigits(s: String): String {
        val sb = StringBuilder(s.length)
        for (ch in s) {
            when (ch) {
                in '\u0966'..'\u096F' -> sb.append('0' + (ch - '\u0966'))
                in '\u09E6'..'\u09EF' -> sb.append('0' + (ch - '\u09E6'))
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun containsDate(text: String): Boolean {
        val lower = text.lowercase(Locale.ROOT)
        val monthPattern = Regex("""(?i)\b(?:jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|jul(?:y)?|aug(?:ust)?|sep(?:tember)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)\b""")
        return (monthPattern.containsMatchIn(lower) && Regex("""\d""").containsMatchIn(lower)) ||
               lower.matches(Regex(""".*\b\d{1,2}[/\-.]\d{1,2}[/\-.]\d{2,4}\b.*"""))
    }

    private fun extractPayee(lines: List<String>, foundUpiId: String?, fullText: String? = null): String? {
        val isSamsung = fullText?.contains("Samsung Wallet", ignoreCase = true) == true ||
                fullText?.contains("Samsung Walet", ignoreCase = true) == true ||
                fullText?.contains("Paid from Samsung Wallet", ignoreCase = true) == true

        // 0. Dedicated Samsung Wallet payee extraction:
        // In Samsung Wallet, payee name and payee VPA are ALWAYS inside the card at the top, ABOVE the amount.
        if (isSamsung) {
            val walletIdx = lines.indexOfFirst { it.contains("Samsung Wallet", ignoreCase = true) || it.contains("Samsung Walet", ignoreCase = true) }
            val amountIdx = lines.indexOfFirst { line ->
                line.contains("₹") || line.contains("INR") || STANDALONE_AMOUNT_PATTERN.matcher(cleanAmountCandidate(line)).matches()
            }
            val startIdx = if (walletIdx >= 0) walletIdx + 1 else 0
            val endIdx = if (amountIdx > startIdx) amountIdx else lines.size

            // Step 1: Check if any line in the top card is a known prominent merchant (e.g. Zepto, DMart, BookMyShow)
            for (idx in startIdx until endIdx) {
                val candidate = cleanPayee(lines[idx])
                if (PROMINENT_MERCHANTS.any { candidate.equals(it, ignoreCase = true) }) {
                    return candidate
                }
            }

            // Step 2: If foundUpiId is known, check line immediately above it
            if (foundUpiId != null) {
                val vpaIdx = lines.indexOfFirst { it.contains(foundUpiId, ignoreCase = true) }
                if (vpaIdx > 0) {
                    var candidateIdx = vpaIdx - 1
                    while (candidateIdx >= 0 && (lines[candidateIdx].matches(Regex("""(?i)^\s*(?:to\s*vpa|to\s*upi\s*id|vpa|from\s*upi\s*id|upi\s*id)\s*[:\-]?$""")) || !isValidPayee(cleanPayee(lines[candidateIdx])))) {
                        candidateIdx--
                    }
                    if (candidateIdx >= 0) {
                        val candidate = cleanPayee(lines[candidateIdx])
                        if (isValidPayee(candidate)) {
                            return candidate
                        }
                    }
                }
            }

            // Step 3: Scan lines in top card for valid payee (rejects banks, dates, reference numbers)
            for (idx in startIdx until endIdx) {
                val candidate = cleanPayee(lines[idx])
                if (isValidPayee(candidate) && !isReferenceOrAccountLine(lines[idx]) && !containsDate(candidate)) {
                    return candidate
                }
            }
        }

        // 1. Multi-line "To", "Paid to", "Received from", "Customer name", "Biller Name" followed by payee name on next line
        for (i in lines.indices) {
            val line = lines[i]
            if (line.matches(Regex("""(?i)^\s*(?:paid\s+to|payment\s+to|received\s+from|to|beneficiary(?:\s+name)?|customer\s*name|biller\s*name)\s*[:\-]?$"""))) {
                if (i + 1 < lines.size) {
                    val candidate = cleanPayee(lines[i + 1])
                    if (isValidPayee(candidate) && !candidate.matches(Regex("""(?i)^\s*(?:to\s+vpa|to\s+upi\s+id|vpa|from\s+upi\s+id|upi\s*id|from|amount|when|date|remarks|biller|payment|transaction|registered).*"""))) {
                        return candidate
                    }
                }
            }
        }

        // 1b. Bill Payment / B-Connect: line immediately following the amount is the biller / utility provider
        val lowerText = fullText?.lowercase(Locale.ROOT) ?: ""
        if (lowerText.contains("bill payment") || lowerText.contains("billpayment") || lowerText.contains("b-connect")) {
            for (i in lines.indices) {
                val cleaned = cleanAmountCandidate(lines[i].trim())
                if (STANDALONE_AMOUNT_PATTERN.matcher(cleaned).matches()) {
                    if (i + 1 < lines.size) {
                        val candidate = cleanPayee(lines[i + 1])
                        if (isValidPayee(candidate) && !isReferenceOrAccountLine(candidate)) {
                            return candidate
                        }
                    }
                }
            }
        }

        // 1c. QuickPay / Internet Banking: line immediately following "Internet banking" is the biller / merchant
        val internetBankingIdx = lines.indexOfFirst {
            it.matches(Regex("""(?i)^\s*(?:internet\s*banking|net\s*banking)\s*$"""))
        }
        if (internetBankingIdx >= 0 && internetBankingIdx + 1 < lines.size) {
            val candidate = cleanPayee(lines[internetBankingIdx + 1])
            if (isValidPayee(candidate)) {
                return candidate
            }
        }

        // 1d. NEFT / IMPS transfer: line before masked beneficiary account (e.g. XXXXXXXXX5090)
        val maskedAccIdx = lines.indexOfFirst { it.matches(Regex("""(?i)^\s*[xX*]{4,}\d{3,}\s*$""")) }
        if (maskedAccIdx > 0) {
            val candidate = cleanPayee(lines[maskedAccIdx - 1])
            if (isValidPayee(candidate) && !candidate.contains("bank", ignoreCase = true) && !candidate.contains("remarks", ignoreCase = true)) {
                return candidate
            }
        }

        // 2. Explicit pattern "Paid to <Name>", "Payment to <Name>", "To: <Name>", "Notes: <Name>"
        for (line in lines) {
            for (p in PAYEE_LINE_PATTERNS) {
                val m = p.matcher(line)
                if (m.find()) {
                    val raw = cleanPayee(m.group(1))
                    if (isValidPayee(raw) && !raw.matches(Regex("""(?i)^\s*(?:to\s+vpa|to\s+upi\s+id|vpa|from\s+upi\s+id|upi\s*id).*"""))) {
                        return raw
                    }
                }
            }
        }

        // 3. Line immediately preceding UPI VPA (e.g. in Samsung Wallet: BIGTREE ENTERTAINMENT is right above bookmyshow@axb)
        val vpaLineIndex = lines.indexOfFirst { line ->
            line.contains("@") &&
                !line.contains("sender", ignoreCase = true) &&
                !line.contains("sent from", ignoreCase = true) &&
                !line.contains("paid from", ignoreCase = true) &&
                !line.contains("debited from", ignoreCase = true) &&
                !line.contains("@pingpay", ignoreCase = true)
        }
        if (vpaLineIndex > 0) {
            var candidateIdx = vpaLineIndex - 1
            while (candidateIdx >= 0 && (
                lines[candidateIdx].matches(Regex("""(?i)^\s*(?:to\s*vpa|to\s*upi\s*id|vpa|from\s*upi\s*id|upi\s*id)\s*[:\-]?$""")) ||
                isReferenceOrAccountLine(lines[candidateIdx]) ||
                containsDate(lines[candidateIdx]) ||
                !isValidPayee(cleanPayee(lines[candidateIdx]))
            )) {
                candidateIdx--
            }
            if (candidateIdx >= 0) {
                val candidate = cleanPayee(lines[candidateIdx])
                if (isValidPayee(candidate)) {
                    // Check if previous line is also part of the payee name (e.g. "PUMP 3 MAHAA MHERU \n ENTERPRISE" or "GRACEMARTPRIVATELTD \n KOLATHUR")
                    if (candidateIdx > 0) {
                        val prevCandidate = cleanPayee(lines[candidateIdx - 1])
                        if (isValidPayee(prevCandidate) &&
                            !prevCandidate.contains("bank", ignoreCase = true) &&
                            !BANK_HINT_PATTERNS.any { it.matcher(prevCandidate).find() } &&
                            !isReferenceOrAccountLine(lines[candidateIdx - 1]) &&
                            !prevCandidate.matches(Regex("""(?i)^\s*(?:paid\s+to|payment\s+to|to|amount|date|time|success|success!|from|txn|transaction|sent|bill|customer|retail|sender|notes?).*""")) &&
                            !containsDate(prevCandidate)
                        ) {
                            return "$prevCandidate $candidate"
                        }
                    }
                    return candidate
                }
            }
        }

        // 4. Line before the amount if it looks like a business name
        val amountIndex = lines.indexOfFirst { line ->
            line.contains("₹") || line.contains("INR") || STANDALONE_AMOUNT_PATTERN.matcher(cleanAmountCandidate(line)).matches()
        }
        if (amountIndex > 0) {
            for (i in (amountIndex - 1) downTo 0) {
                if (isReferenceOrAccountLine(lines[i])) continue
                val candidate = cleanPayee(lines[i])
                if (isValidPayee(candidate)) {
                    return candidate
                }
            }
        }

        // 5. Fallback: Prominent merchants in receipt header
        if (fullText != null) {
            val known = PROMINENT_MERCHANTS.firstOrNull { fullText.contains(it, ignoreCase = true) }
            if (known != null) return known
        }

        return null
    }

    @JvmStatic
    fun cleanPayee(raw: String?): String {
        if (raw == null) return ""
        var cleaned = raw
            .replace(Regex("""[\r\n]+"""), " ")
            .replace(Regex("""\s+"""), " ")
            .replace(Regex("""(?i)\(verified merchant\)"""), "")
            .replace(Regex("""(?i)\[verified merchant\]"""), "")
            .replace(Regex("""(?i)\(verified\)"""), "")
            .replace(Regex("""(?i)\[verified\]"""), "")
            .replace(Regex("""(?i)\(upi id:.*\)"""), "")
            .replace(Regex("""[•*]+"""), "")
            .replace(Regex("""([a-z])([A-Z])"""), "$1 $2")
            .trim()

        // Strip leading prefixes: Sender:, To:, To , Paid to , Payment to , Received from , Sent to , Biller name:, Beneficiary:, etc.
        cleaned = cleaned.replace(
            Regex("""^(?i)\s*(?:sender\s*[:\-]?\s*|sent\s+from\s*[:\-]?\s*|paid\s+from\s*[:\-]?\s*|paid\s+to\s*[:\-]?\s*|payment\s+to\s*[:\-]?\s*|received\s+from\s*[:\-]?\s*|sent\s+to\s*[:\-]?\s*|to\s*[:\-]\s*|to\s+|biller\s+name\s*[:\-]?\s*|beneficiary(?:\s+name)?\s*[:\-]?\s*|customer\s+name\s*[:\-]?\s*|notes?\s*[:\-]\s*)"""),
            ""
        ).trim()

        // Strip any trailing punctuation or unwanted remnants
        cleaned = cleaned.trim(' ', ':', '-', ',', '.', '•', '*', '৩', '₹', '✔', '✓', '☑')

        // Strip embedded VPAs or emails if appended to the name (e.g. "Name (merchant@upi)" or "Name merchant@upi")
        cleaned = cleaned.replace(Regex("""(?i)\s*[(\[]?[a-zA-Z0-9.\-_]+@[a-zA-Z0-9.\-_]+[)\]]?"""), "").trim()

        // Strip leading OCR noise / icon misreads: non-ASCII characters or symbols followed by whitespace or letters
        // (e.g. gas cylinder icon read as "Ö Bharat Gas" → "Bharat Gas", copyright icon "© Hathway" → "Hathway")
        cleaned = cleaned.replace(Regex("""^[^a-zA-Z0-9\s(]{1,3}\s*"""), "").trim()

        // Strip trailing non-word / isolated non-Latin symbols (e.g. OCR misreading checkmarks as Indic digits like "Zepto ৩")
        cleaned = cleaned.replace(Regex("""(?:\s+[\W\u0900-\u09FF\u2000-\u2BFF]+)+$"""), "").trim()

        // Strip trailing conjunctions from truncated receipt text (e.g. "TAMILNADU MUTTON AND" -> "TAMILNADU MUTTON")
        cleaned = cleaned.replace(Regex("""(?i)\s+(?:and|&)\s*$"""), "").trim()

        // Match against known prominent merchants
        val knownMerchant = PROMINENT_MERCHANTS.firstOrNull {
            cleaned.equals(it, ignoreCase = true) ||
            cleaned.startsWith("$it ", ignoreCase = true) ||
            cleaned.startsWith("${it}৩", ignoreCase = true) ||
            cleaned.endsWith(it, ignoreCase = true) ||
            cleaned.contains(it, ignoreCase = true)
        }
        if (knownMerchant != null) {
            return knownMerchant
        }

        return cleaned
    }

    private fun isValidPayee(candidate: String): Boolean {
        if (candidate.length < 2) return false
        val lower = candidate.lowercase(Locale.ROOT).trim()
        if (IGNORE_PAYEE_WORDS.any { lower == it || lower.startsWith("$it ") || lower.endsWith(" $it") }) {
            return false
        }
        if (lower.contains("@")) return false
        if (candidate.matches(Regex("""(?i)^\s*(?:sender|sent\s+from|paid\s+from|paid\s+to|transaction\s+id|tr\.?\s*id|upi\s+ref|ref\s*no\.?|ref\s*#|powered\s+by|notes?).*"""))) {
            return false
        }
        // Reject bank and payment provider names (e.g. "NSDL Payments Bank", "ICICI Bank", "State Bank of India", "Axis Bank")
        if (candidate.contains("bank", ignoreCase = true) ||
            candidate.contains("payments bank", ignoreCase = true) ||
            candidate.contains("payment bank", ignoreCase = true) ||
            BANK_HINT_PATTERNS.any { it.matcher(candidate).find() }) {
            return false
        }
        // If it contains a date/time string, reject
        if (containsDate(candidate)) {
            return false
        }
        // If it's pure numbers or date, reject
        if (candidate.all { it.isDigit() || it.isWhitespace() || it == '-' || it == '/' || it == ':' || it == '.' }) {
            return false
        }
        // Reject amounts (including formatted with commas or OCR symbols) like "₹3,260.10", "Z155.00", "R960.52", "957.50"
        val candidateCleaned = cleanAmountCandidate(candidate).replace(",", "")
        if (candidateCleaned.matches(Regex("""^[0-9]+(?:\.[0-9]+)?$"""))) {
            return false
        }
        return true
    }

    private fun extractDateTime(lines: List<String>, fullText: String): LocalDateTime? {
        val currentYear = LocalDate.now().year

        val SEP = """[,•|.\-\s\u00a0\u202f\n]+"""
        val WS = """[\s\u00a0\u202f]*"""

        // Pattern 1: Relative "Today, 5:23 PM" or "Yesterday at 5:23 PM"
        val relativeDateTime = Pattern.compile(
            """(?i)\b(today|yesterday)$SEP(?:at[\s\u00a0\u202f]+)?(\d{1,2}):(\d{2})(?::(\d{2}))?$WS(AM|PM)?\b"""
        )
        // Pattern 2: Time before Date "05:23 PM, 14 Oct 2026" or "10:34AM, 15 Mar '26 IST" or "Paid at 01:11PM, 02 Nov '26 IST"
        val timeBeforeDate = Pattern.compile(
            """(?i)(?:at[\s\u00a0\u202f]+)?\b(\d{1,2}):(\d{2})(?::(\d{2}))?$WS(AM|PM)?$SEP(?:on[\s\u00a0\u202f]+)?(\d{1,2})(?:st|nd|rd|th)?$SEP([A-Za-z0-9]{3,9})(?:$SEP'?(\d{2,4}))?\b"""
        )
        // Pattern 3: "14 OCT 2026, 05:23 PM" or "14 0CT 2026, 12:23 PM"
        val ddmmyyyyTime = Pattern.compile(
            """(?i)\b(\d{1,2})(?:st|nd|rd|th)?$SEP([A-Za-z0-9]{3,9})(?:$SEP'?(\d{2,4}))?$SEP(?:at[\s\u00a0\u202f]+)?(\d{1,2}):(\d{2})(?::(\d{2}))?$WS(AM|PM)?(?:[\s\u00a0\u202f]*hrs)?\b"""
        )
        // Pattern 4: "Oct 14, 2026 05:23 PM" or "0ct 14th, 05:23 PM"
        val mmmddyyyyTime = Pattern.compile(
            """(?i)\b([A-Za-z0-9]{3,9})$SEP(\d{1,2})(?:st|nd|rd|th)?(?:$SEP'?(\d{2,4}))?$SEP(?:at[\s\u00a0\u202f]+)?(\d{1,2}):(\d{2})(?::(\d{2}))?$WS(AM|PM)?\b"""
        )
        // Pattern 5: "15/03/2026 10:34:52" or "12/10/2024,17:37Hrs"
        val numericDateTime = Pattern.compile(
            """(?i)\b(\d{1,2})[/\-](\d{1,2})[/\-](\d{2,4})$SEP(?:at[\s\u00a0\u202f]+)?(\d{1,2}):(\d{2})(?::(\d{2}))?$WS(AM|PM)?(?:[\s\u00a0\u202f]*hrs)?\b"""
        )

        // Check individual lines first, and if not found, check the entire fullText (for multi-line receipts)
        val searchTargets = lines + listOf(fullText)

        for (target in searchTargets) {
            val mRel = relativeDateTime.matcher(target)
            if (mRel.find()) {
                val isYesterday = mRel.group(1)?.equals("yesterday", ignoreCase = true) == true
                val baseDate = if (isYesterday) LocalDate.now().minusDays(1) else LocalDate.now()
                val hour = parseHour(mRel.group(2)?.toIntOrNull() ?: 0, mRel.group(5))
                val min = mRel.group(3)?.toIntOrNull() ?: 0
                val sec = mRel.group(4)?.toIntOrNull() ?: 0
                return baseDate.atTime(hour, min, sec)
            }

            val mTimeFirst = timeBeforeDate.matcher(target)
            if (mTimeFirst.find()) {
                val month = parseMonth(mTimeFirst.group(6))
                if (month != null) {
                    val hour = parseHour(mTimeFirst.group(1)?.toIntOrNull() ?: 0, mTimeFirst.group(4))
                    val min = mTimeFirst.group(2)?.toIntOrNull() ?: 0
                    val sec = mTimeFirst.group(3)?.toIntOrNull() ?: 0
                    val day = (mTimeFirst.group(5)?.toIntOrNull() ?: 1).coerceIn(1, 31)
                    val year = parseYear(mTimeFirst.group(7), currentYear)
                    return LocalDateTime.of(year, month, day, hour, min, sec)
                }
            }

            val m1 = ddmmyyyyTime.matcher(target)
            if (m1.find()) {
                val month = parseMonth(m1.group(2))
                if (month != null) {
                    val day = (m1.group(1)?.toIntOrNull() ?: 1).coerceIn(1, 31)
                    val year = parseYear(m1.group(3), currentYear)
                    val hour = parseHour(m1.group(4)?.toIntOrNull() ?: 0, m1.group(7))
                    val min = m1.group(5)?.toIntOrNull() ?: 0
                    val sec = m1.group(6)?.toIntOrNull() ?: 0
                    return LocalDateTime.of(year, month, day, hour, min, sec)
                }
            }

            val m2 = mmmddyyyyTime.matcher(target)
            if (m2.find()) {
                val month = parseMonth(m2.group(1))
                if (month != null) {
                    val day = (m2.group(2)?.toIntOrNull() ?: 1).coerceIn(1, 31)
                    val year = parseYear(m2.group(3), currentYear)
                    val hour = parseHour(m2.group(4)?.toIntOrNull() ?: 0, m2.group(7))
                    val min = m2.group(5)?.toIntOrNull() ?: 0
                    val sec = m2.group(6)?.toIntOrNull() ?: 0
                    return LocalDateTime.of(year, month, day, hour, min, sec)
                }
            }

            val m3 = numericDateTime.matcher(target)
            if (m3.find()) {
                val d1 = m3.group(1)?.toIntOrNull() ?: 1
                val d2 = m3.group(2)?.toIntOrNull() ?: 1
                val year = parseYear(m3.group(3), currentYear)
                val day = d1.coerceIn(1, 31)
                val month = d2.coerceIn(1, 12)
                val hour = parseHour(m3.group(4)?.toIntOrNull() ?: 0, m3.group(7))
                val min = m3.group(5)?.toIntOrNull() ?: 0
                val sec = m3.group(6)?.toIntOrNull() ?: 0
                return LocalDateTime.of(year, month, day, hour, min, sec)
            }
        }

        // Fallback: search for standalone Date and standalone Time independently
        var foundDate: LocalDate? = null
        var foundTime: LocalTime? = null

        val dateOnly1 = Pattern.compile("""(?i)\b(\d{1,2})(?:st|nd|rd|th)?$SEP([A-Za-z0-9]{3,9})(?:$SEP(\d{2,4}))?\b""")
        val dateOnly2 = Pattern.compile("""(?i)\b([A-Za-z0-9]{3,9})$SEP(\d{1,2})(?:st|nd|rd|th)?(?:$SEP(\d{2,4}))?\b""")
        val dateOnly3 = Pattern.compile("""(?i)\b(\d{1,2})[/\-](\d{1,2})[/\-](\d{2,4})\b""")
        val timeOnly = Pattern.compile("""(?i)\b(\d{1,2}):(\d{2})(?::(\d{2}))?$WS(AM|PM)?\b""")

        for (target in searchTargets) {
            if (foundDate == null) {
                val m1 = dateOnly1.matcher(target)
                if (m1.find()) {
                    val month = parseMonth(m1.group(2))
                    if (month != null) {
                        val day = (m1.group(1)?.toIntOrNull() ?: 1).coerceIn(1, 31)
                        val year = parseYear(m1.group(3), currentYear)
                        foundDate = LocalDate.of(year, month, day)
                    }
                }
            }
            if (foundDate == null) {
                val m2 = dateOnly2.matcher(target)
                if (m2.find()) {
                    val month = parseMonth(m2.group(1))
                    if (month != null) {
                        val day = (m2.group(2)?.toIntOrNull() ?: 1).coerceIn(1, 31)
                        val year = parseYear(m2.group(3), currentYear)
                        foundDate = LocalDate.of(year, month, day)
                    }
                }
            }
            if (foundDate == null) {
                val m3 = dateOnly3.matcher(target)
                if (m3.find()) {
                    val day = (m3.group(1)?.toIntOrNull() ?: 1).coerceIn(1, 31)
                    val month = (m3.group(2)?.toIntOrNull() ?: 1).coerceIn(1, 12)
                    val year = parseYear(m3.group(3), currentYear)
                    foundDate = LocalDate.of(year, month, day)
                }
            }
            if (foundTime == null) {
                val mt = timeOnly.matcher(target)
                if (mt.find()) {
                    val rawH = mt.group(1)?.toIntOrNull() ?: 0
                    val amPm = mt.group(4)
                    val min = mt.group(2)?.toIntOrNull() ?: 0
                    val sec = mt.group(3)?.toIntOrNull() ?: 0
                    if (rawH in 0..23 && min in 0..59 && sec in 0..59) {
                        val hour = parseHour(rawH, amPm)
                        foundTime = LocalTime.of(hour, min, sec)
                    }
                }
            }
        }

        if (foundDate != null && foundTime != null) {
            return foundDate.atTime(foundTime)
        } else if (foundDate != null) {
            return foundDate.atTime(12, 0)
        } else if (foundTime != null) {
            return LocalDate.now().atTime(foundTime)
        }

        return null
    }

    private fun parseYear(rawYear: String?, fallbackYear: Int): Int {
        val y = rawYear?.replace("'", "")?.trim()?.toIntOrNull() ?: return fallbackYear
        return if (y < 100) 2000 + y else y
    }

    private fun parseMonth(name: String?): Int? {
        if (name == null) return null
        val n = name.lowercase(Locale.ROOT)
            .replace('0', 'o')
            .replace('1', 'l')
            .replace('5', 's')
            .replace('3', 'e')
        return when {
            n.startsWith("jan") -> 1
            n.startsWith("feb") -> 2
            n.startsWith("mar") -> 3
            n.startsWith("apr") -> 4
            n.startsWith("may") -> 5
            n.startsWith("jun") -> 6
            n.startsWith("jul") -> 7
            n.startsWith("aug") -> 8
            n.startsWith("sep") -> 9
            n.startsWith("oct") -> 10
            n.startsWith("nov") -> 11
            n.startsWith("dec") -> 12
            else -> null
        }
    }

    private fun parseHour(rawHour: Int, amPm: String?): Int {
        if (amPm == null) return rawHour.coerceIn(0, 23)
        return when (amPm.uppercase(Locale.ROOT)) {
            "PM" -> if (rawHour < 12) rawHour + 12 else rawHour
            "AM" -> if (rawHour == 12) 0 else rawHour
            else -> rawHour
        }.coerceIn(0, 23)
    }

    private fun extractUtr(lines: List<String>, fullText: String): String? {
        // Priority 1: line-by-line sequential scan: check each line for inline explicit label or multi-line label followed by reference number
        for (i in lines.indices) {
            val line = lines[i].trim()
            val lineWithoutSpacesInId = line.replace(Regex("""(?<=[0-9A-Za-z])[ \t\-]+(?=[0-9A-Za-z])"""), "")

            // 1a. Inline explicit label on this line (e.g. "Transaction ID: 301966032876", "Tr. ID: 111057198606", "Ref No. 429812345678")
            val m = UTR_EXPLICIT_PATTERN.matcher(lineWithoutSpacesInId)
            if (m.find()) {
                val candidate = m.group(1)?.trim()
                if (candidate != null && candidate.length in 10..26 && candidate.count { it.isDigit() } >= 6 && !candidate.matches(Regex("""(?i)^[xX*]+.*"""))) {
                    return candidate
                }
            }

            // 1b. Multi-line label followed by reference number on next line (e.g. ICICI: "Ref No." \n "429712345678" or PhonePe: "Transaction ID" \n "T2411020824381969639267")
            if (UTR_LABEL_LINE_PATTERN.matches(line)) {
                if (i + 1 < lines.size) {
                    val nextLine = lines[i + 1].trim().replace(" ", "").replace("-", "")
                    // Only combine next line with line+2 if nextLine starts with IC (ICICI OCR line splits like IC3163050000 + 0022VYFE)
                    val combined = if (nextLine.startsWith("IC", ignoreCase = true) && i + 2 < lines.size) {
                        val line2 = lines[i + 2].trim().replace(" ", "").replace("-", "")
                        if (line2.matches(Regex("""^[0-9A-Za-z]{4,12}$""")) && !isReferenceOrAccountLine(lines[i + 2]) && !containsDate(lines[i + 2])) {
                            nextLine + line2
                        } else nextLine
                    } else nextLine
                    val candidateLine = if (combined.matches(Regex("""^[0-9A-Za-z]{10,26}$"""))) combined else nextLine
                    if (candidateLine.matches(Regex("""^[0-9A-Za-z]{10,26}$""")) &&
                        candidateLine.count { it.isDigit() } >= 6 &&
                        !candidateLine.matches(Regex("""(?i)^[xX*]+.*""")) &&
                        !isReferenceOrAccountLine(lines[i + 1])) {
                        return candidateLine
                    }
                }
            }
        }

        // Priority 2: ICICI specific transaction reference format anywhere in receipt (e.g. IC3143060000000XH7UP, IC31503200000010GBHN)
        val iciciRef = Regex("""\bIC[0-9]{6,20}[\s\-]*[A-Za-z0-9]{4,}\b""").find(fullText)
        if (iciciRef != null) {
            return iciciRef.value.replace(" ", "").replace("\n", "").replace("-", "").take(26)
        }

        // Priority 4: standalone 12-digit number (NPCI standard UTR) excluding accounts
        for (i in lines.indices) {
            val line = lines[i].trim()
            if (line.contains("pay from", ignoreCase = true) || line.contains("a/c", ignoreCase = true) || line.contains("account", ignoreCase = true)) continue
            if (i > 0 && lines[i - 1].contains("pay from", ignoreCase = true)) continue
            val sm = UTR_STANDALONE_PATTERN.matcher(line)
            if (sm.matches()) {
                return sm.group(1)
            }
        }
        return null
    }

    private fun extractBankHint(lines: List<String>, fullText: String): String? {
        for (p in BANK_HINT_PATTERNS) {
            val m = p.matcher(fullText)
            if (m.find()) {
                val match = m.group(1)?.trim() ?: ""
                if (match.isNotEmpty()) {
                    if (match.contains("cici", ignoreCase = true) ||
                        match.contains("clcl", ignoreCase = true) ||
                        match.contains("c1c1", ignoreCase = true) ||
                        match.startsWith("1cici", ignoreCase = true) ||
                        match.startsWith("lcici", ignoreCase = true) ||
                        match.equals("iMobile", ignoreCase = true)) {
                        return "ICICI Bank"
                    }
                    if (match.equals("samsung walet", ignoreCase = true)) {
                        return "Samsung Wallet"
                    }
                    return match
                }
            }
        }
        return null
    }

    private fun containsRefundOrIncome(text: String): Boolean {
        val lower = text.lowercase(Locale.ROOT)
        return lower.contains("received from") ||
                lower.contains("payment received") ||
                lower.contains("money received") ||
                lower.contains("amount received") ||
                lower.contains("credited to") ||
                lower.contains("credited") ||
                lower.contains("credit to") ||
                lower.contains("deposit") ||
                lower.contains("cashback received") ||
                lower.contains("cashback") ||
                lower.contains("refund") ||
                lower.contains("reward") ||
                lower.contains("received with google pay") ||
                lower.contains("received on phonepe") ||
                lower.contains("received in paytm") ||
                lower.contains("incoming payment")
    }

    fun isUserRecipient(
        payeeName: String?,
        upiId: String?,
        userNames: List<String>,
        userPhones: List<String>
    ): Boolean {
        if (payeeName != null) {
            val clean = payeeName.lowercase(Locale.ROOT).replace(Regex("""[^a-z0-9]"""), "")
            for (uName in userNames) {
                val uClean = uName.lowercase(Locale.ROOT).replace(Regex("""[^a-z0-9]"""), "")
                if (uClean.length >= 4 && (clean.contains(uClean) || uClean.contains(clean))) {
                    return true
                }
            }
        }
        if (upiId != null) {
            val userPart = upiId.substringBefore("@").filter { it.isDigit() }
            for (uPhone in userPhones) {
                val digitsOnly = uPhone.filter { it.isDigit() }
                if (digitsOnly.length >= 8 && userPart.contains(digitsOnly)) {
                    return true
                }
            }
        }
        return false
    }

    private fun extractSender(lines: List<String>, fullText: String): String? {
        val senderPatterns = listOf(
            Regex("""(?i)^\s*(?:received\s+from|from|sender|payer|sent\s+by)\s*[:\-]?\s*(.+)"""),
            Regex("""(?i)^\s*from\s+[:\-]\s*(.+)""")
        )
        for (i in lines.indices) {
            val line = lines[i]
            for (pattern in senderPatterns) {
                val match = pattern.find(line)
                if (match != null) {
                    val candidate = cleanPayee(match.groupValues[1])
                    if (isValidPayee(candidate)) return candidate
                }
            }
            if (line.matches(Regex("""(?i)^\s*(?:received\s+from|from|sender|payer)\s*[:\-]?\s*$""")) && i + 1 < lines.size) {
                val candidate = cleanPayee(lines[i + 1])
                if (isValidPayee(candidate)) return candidate
            }
        }
        return null
    }

    fun repairIndianPhoneUpiId(candidate: String, userPhones: List<String>): String {
        val parts = candidate.split("@")
        if (parts.size != 2) return candidate
        var userPart = parts[0]
        val domain = parts[1]

        // IDFC Bank merchant UPI IDs are 8-digit numbers starting with 88.
        // OCR frequently misreads 88 as 44, 48, or 84 due to thin strokes.
        if (domain.equals("idfcbank", ignoreCase = true) && userPart.all { it.isDigit() }) {
            if (userPart.startsWith("44") || userPart.startsWith("48") || userPart.startsWith("84")) {
                userPart = "88" + userPart.substring(2)
                return "$userPart@$domain"
            }
        }

        if (userPart.length == 10 && userPart.all { it.isDigit() }) {
            for (uPhone in userPhones) {
                val uDigits = uPhone.filter { it.isDigit() }
                if (uDigits.length == 10) {
                    var diffCount = 0
                    var allDiffsAre4and8 = true
                    for (i in 0 until 10) {
                        if (userPart[i] != uDigits[i]) {
                            diffCount++
                            if (!((userPart[i] == '4' && uDigits[i] == '8') || (userPart[i] == '8' && uDigits[i] == '4'))) {
                                allDiffsAre4and8 = false
                            }
                        }
                    }
                    if ((allDiffsAre4and8 && diffCount in 1..4) || diffCount in 1..3) {
                        return "$uDigits@$domain"
                    }
                }
            }
            if (userPart.startsWith("4")) {
                userPart = "8" + userPart.substring(1)
                return "$userPart@$domain"
            }
        }
        return candidate
    }

    private fun extractComment(lines: List<String>, payeeName: String?, fullText: String): String? {
        // 1. Explicit labeled comments: "Notes: ...", "Remarks: ...", "Message: ..."
        for (i in lines.indices) {
            val line = lines[i]
            val m = Regex("""(?i)^\s*(?:notes?|remarks?|message|comments?|purpose)\s*[:\-]\s*(.+)""").find(line)
            if (m != null) {
                val candidate = cleanComment(m.groupValues[1])
                if (isValidUserComment(candidate, payeeName)) return candidate
            }
            if (line.matches(Regex("""(?i)^\s*(?:notes?|remarks?|message|comments?|purpose)\s*[:\-]?\s*$""")) && i + 1 < lines.size) {
                val candidate = cleanComment(lines[i + 1])
                if (isValidUserComment(candidate, payeeName)) return candidate
            }
        }

        // 2. ICICI / UPI pill comment: "for potluck" or "for <remark>"
        for (line in lines) {
            val pillMatch = Regex("""(?i)^\s*for\s+([a-zA-Z0-9\s.,'#\-]{2,50})$""").find(line)
            if (pillMatch != null) {
                val raw = pillMatch.groupValues[1].trim()
                if (isValidUserComment(raw, payeeName) && !isSystemForText(raw)) {
                    return line.trim()
                }
            }
        }
        return null
    }

    private fun isSystemForText(text: String): Boolean {
        val lower = text.lowercase(Locale.ROOT)
        val systemWords = listOf(
            "business", "queries", "assistance", "support", "help",
            "more details", "terms", "customer care", "loan", "personal loan",
            "further", "any", "feedback", "cashback", "savings"
        )
        return systemWords.any { lower == it || lower.startsWith("$it ") }
    }

    private fun isValidUserComment(candidate: String, payeeName: String?): Boolean {
        if (candidate.length < 2) return false
        val lower = candidate.lowercase(Locale.ROOT).trim()

        // Ignore generic default bank remark placeholders
        val placeholders = listOf(
            "upi payment", "upi txn", "upi transfer", "payment", "upi",
            "na", "nil", "none", "n/a", "ok", "yes", "no", "success"
        )
        if (placeholders.contains(lower)) return false

        // Must not match payee name
        if (payeeName != null) {
            val cleanPayeeLower = payeeName.lowercase(Locale.ROOT).trim()
            if (cleanPayeeLower.isNotEmpty() && (lower == cleanPayeeLower || cleanPayeeLower.contains(lower))) {
                return false
            }
        }

        // Must not match transaction ID / UTR or date
        if (candidate.matches(Regex("""(?i)^\d{10,24}$"""))) return false
        if (candidate.matches(Regex("""(?i)^\d{1,2}[/\-]\d{1,2}[/\-]\d{2,4}$"""))) return false

        // Reject if it matches common UI button noise
        if (IGNORE_PAYEE_WORDS.any { lower == it }) return false

        return true
    }


    @JvmStatic
    fun cleanComment(comment: String?): String {
        if (comment.isNullOrBlank()) return ""
        return comment
            .replace(Regex("""(?i)\s*(?:[•/,\-]\s*)?UTR\s*:\s*[^\s)\]]+"""), "")
            .replace(Regex("""(?i)\s*(?:[•/,\-]\s*)?UPI\s*:\s*[^\s)\]]+"""), "")
            .replace(Regex("""\(\s*\)"""), "")
            .replace(Regex("""\[\s*\]"""), "")
            .trim(' ', '•', '-', ',', '/', '(', ')')
            .trim()
    }
}
