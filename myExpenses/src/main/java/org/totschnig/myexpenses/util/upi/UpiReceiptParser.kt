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
        """(?i)\b([a-zA-Z0-9.\-_\u0966-\u096F\u09E6-\u09EF]{2,}(?:\s+[a-zA-Z0-9.\-_\u0966-\u096F\u09E6-\u09EF]+)*\s*@\s*[a-zA-Z]{2,})\b"""
    )

    private val UTR_EXPLICIT_PATTERN = Pattern.compile(
        """(?i)(?:b-connect(?:\s*txn)?\s*id|transaction\s*reference\s*(?:no\.?|num(?:ber)?|id)?|transaction\s*id|upi\s*(?:ref(?:erence)?|txn|transaction)\s*(?:no\.?|id|num(?:ber)?)?|reference\s*(?:#|no\.?|id)?|utr|rrn(?:\s*no)?)\s*[:\-#]?\s*([0-9A-Za-z]{10,24})"""
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
        Pattern.compile("""(?i)^\s*(?:biller\s+name|paid\s+to|payment\s+to|sent\s+to)\s*[:\-]?\s*(.+)"""),
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
        "reference", "success", "success!", "home", "once in a while", "shopping desires", "give in to",
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
            .replace(Regex("""(?i)\b(\d{1,2})[;.](\d{2})\s*(AM|PM)\b"""), "$1:$2 $3")
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

    fun parse(rawText: String): UpiReceiptData {
        val normalized = normalizeOcrText(rawText)
        val lines = normalized.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        return parse(lines, normalized)
    }

    fun parse(lines: List<String>, fullText: String? = null): UpiReceiptData {
        val rawJoined = fullText ?: lines.joinToString("\n")
        val joined = normalizeOcrText(rawJoined)
        val normalizedLines = lines.map { normalizeOcrText(it).trim() }.filter { it.isNotEmpty() }

        val amount = extractAmount(normalizedLines, joined)
        val upiId = extractUpiId(normalizedLines)
        val payeeName = extractPayee(normalizedLines, upiId, joined)
        val dateTime = extractDateTime(normalizedLines, joined)
        val utr = extractUtr(normalizedLines, joined)
        val bankHint = extractBankHint(normalizedLines, joined)
        val paymentMethod = detectPaymentMethod(joined)
        val isExpense = !containsRefundOrIncome(joined)

        return UpiReceiptData(
            amount = amount,
            payeeName = payeeName,
            upiId = upiId,
            dateTime = dateTime,
            referenceNumber = utr,
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
        val lower = line.lowercase(Locale.ROOT)
        return lower.contains("@") || lower.contains("reference") || lower.contains("ref #") ||
               lower.contains("utr") || lower.contains("rrn") || lower.contains("txn id") ||
               lower.contains("transaction id") || lower.contains("sba-") || lower.contains("a/c") ||
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

    private fun extractUpiId(lines: List<String>): String? {
        val candidates = mutableListOf<String>()
        for (i in lines.indices) {
            val line = lines[i]
            // Skip lines that indicate sender / payer
            if (line.contains("sender", ignoreCase = true) || line.contains("sent from", ignoreCase = true) || line.contains("paid from", ignoreCase = true)) {
                continue
            }
            if (i > 0 && lines[i - 1].matches(Regex("""(?i)^\s*(?:sender|sent\s+from|paid\s+from|from)\s*[:\-]?\s*$"""))) {
                continue
            }
            val matcher = UPI_VPA_PATTERN.matcher(line)
            if (matcher.find()) {
                val rawCandidate = matcher.group(1)?.replace(" ", "") ?: continue
                val candidate = normalizeDevanagariDigits(rawCandidate)
                // Filter out non-UPI email addresses if possible
                if (!candidate.endsWith(".com") || candidate.contains("@axb") || candidate.contains("@upi") || candidate.contains("@hdfcbank") || candidate.contains("@icici") || candidate.contains("@sbi") || candidate.contains("@okhdfcbank") || candidate.contains("@okaxis") || candidate.contains("@okicici") || candidate.contains("@oksbi") || candidate.contains("@paytm") || candidate.contains("@ybl") || candidate.contains("@ibl")) {
                    candidates.add(candidate)
                }
            }
        }
        return candidates.firstOrNull { !it.contains("@pingpay", ignoreCase = true) } ?: candidates.firstOrNull()
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
        val months = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
        return months.any { lower.contains(it) } || text.matches(Regex(""".*\b\d{1,2}[/\-.]\d{1,2}[/\-.]\d{2,4}\b.*"""))
    }

    private fun extractPayee(lines: List<String>, foundUpiId: String?, fullText: String? = null): String? {
        val isSamsung = fullText?.contains("Samsung Wallet", ignoreCase = true) == true ||
                fullText?.contains("Samsung Walet", ignoreCase = true) == true ||
                fullText?.contains("Paid from Samsung Wallet", ignoreCase = true) == true

        // 0. Dedicated Samsung Wallet payee extraction:
        // In Samsung Wallet, the merchant name is directly above the recipient VPA inside the card.
        if (isSamsung && foundUpiId != null) {
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

        // 1. Multi-line "To", "Paid to", "Customer name", "Biller Name" followed by payee name on next line
        for (i in lines.indices) {
            val line = lines[i]
            if (line.matches(Regex("""(?i)^\s*(?:paid\s+to|payment\s+to|to|beneficiary(?:\s+name)?|customer\s*name|biller\s*name)\s*[:\-]?$"""))) {
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
        val vpaLineIndex = lines.indexOfFirst { it.contains("@") && !it.contains("sender", ignoreCase = true) && !it.contains("@pingpay", ignoreCase = true) }
        if (vpaLineIndex > 0) {
            var candidateIdx = vpaLineIndex - 1
            while (candidateIdx >= 0 && (lines[candidateIdx].matches(Regex("""(?i)^\s*(?:to\s*vpa|to\s*upi\s*id|vpa|from\s*upi\s*id|upi\s*id)\s*[:\-]?$""")) || !isValidPayee(cleanPayee(lines[candidateIdx])))) {
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
            line.contains("₹") || line.contains("INR") || STANDALONE_AMOUNT_PATTERN.matcher(line.trim()).matches()
        }
        if (amountIndex > 0) {
            for (i in (amountIndex - 1) downTo 0) {
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
            .replace(Regex("""(?i)\(verified merchant\)"""), "")
            .replace(Regex("""(?i)\[verified merchant\]"""), "")
            .replace(Regex("""(?i)\(verified\)"""), "")
            .replace(Regex("""(?i)\[verified\]"""), "")
            .replace(Regex("""(?i)\(upi id:.*\)"""), "")
            .replace(Regex("""[•*]+"""), "")
            .replace(Regex("""([a-z])([A-Z])"""), "$1 $2")
            .trim()

        // Strip leading prefixes: Sender:, To:, To , Paid to , Payment to , Sent to , Biller name:, Beneficiary:, etc.
        cleaned = cleaned.replace(
            Regex("""^(?i)\s*(?:sender\s*[:\-]?\s*|sent\s+from\s*[:\-]?\s*|paid\s+from\s*[:\-]?\s*|paid\s+to\s*[:\-]?\s*|payment\s+to\s*[:\-]?\s*|sent\s+to\s*[:\-]?\s*|to\s*[:\-]\s*|to\s+|biller\s+name\s*[:\-]?\s*|beneficiary(?:\s+name)?\s*[:\-]?\s*|customer\s+name\s*[:\-]?\s*|notes?\s*[:\-]\s*)"""),
            ""
        ).trim()

        // Strip any trailing punctuation or unwanted remnants
        cleaned = cleaned.trim(' ', ':', '-', ',', '.', '•', '*')

        // Strip embedded VPAs or emails if appended to the name (e.g. "Name (merchant@upi)" or "Name merchant@upi")
        cleaned = cleaned.replace(Regex("""(?i)\s*[(\[]?[a-zA-Z0-9.\-_]+@[a-zA-Z]{2,}[)\]]?"""), "").trim()

        return cleaned
    }

    private fun isValidPayee(candidate: String): Boolean {
        if (candidate.length < 2) return false
        val lower = candidate.lowercase(Locale.ROOT)
        if (IGNORE_PAYEE_WORDS.any { lower == it || lower.startsWith("$it ") || lower.endsWith(" $it") }) {
            return false
        }
        if (lower.contains("@")) return false
        if (candidate.matches(Regex("""(?i)^\s*(?:sender|sent\s+from|paid\s+from|paid\s+to|transaction\s+id|upi\s+ref|powered\s+by|notes?).*"""))) {
            return false
        }
        // If it's pure numbers or date, reject
        if (candidate.all { it.isDigit() || it.isWhitespace() || it == '-' || it == '/' || it == ':' || it == '.' }) {
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
        // Pattern 2: Time before Date "05:23 PM, 14 Oct 2026" or "10:34AM, 15 Mar '26 IST"
        val timeBeforeDate = Pattern.compile(
            """(?i)\b(\d{1,2}):(\d{2})(?::(\d{2}))?$WS(AM|PM)?$SEP(?:on[\s\u00a0\u202f]+)?(\d{1,2})(?:st|nd|rd|th)?$SEP([A-Za-z0-9]{3,9})(?:$SEP'?(\d{2,4}))?\b"""
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
        // Priority 1: explicitly labeled "Transaction ID", "UPI Ref No", "UTR"
        val m = UTR_EXPLICIT_PATTERN.matcher(fullText)
        if (m.find()) {
            return m.group(1)?.trim()
        }

        // Priority 2: ICICI specific transaction reference format (e.g. IC3143060000000XH7UP, IC31503200000010GBHN)
        val iciciRef = Regex("""\b(IC[0-9]{6,}[A-Za-z0-9]{4,})\b""").find(fullText)
        if (iciciRef != null) {
            return iciciRef.groupValues[1]
        }

        // Priority 3: standalone 12-digit number (NPCI standard UTR) excluding accounts
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
                lower.contains("credited to") ||
                lower.contains("refund") ||
                lower.contains("cashback received")
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
