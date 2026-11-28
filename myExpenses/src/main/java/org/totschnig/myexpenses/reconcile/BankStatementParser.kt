package org.totschnig.myexpenses.reconcile

import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.regex.Pattern

object BankStatementParser {

    private val DATE_PATTERNS = listOf(
        DateTimeFormatter.ofPattern("dd.MM.yyyy"),
        DateTimeFormatter.ofPattern("dd/MM/yyyy"),
        DateTimeFormatter.ofPattern("dd-MM-yyyy"),
        DateTimeFormatter.ofPattern("dd/MM/yy"),
        DateTimeFormatter.ofPattern("dd-MM-yy"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd"),
        DateTimeFormatter.ofPattern("dd-MMM-yy", java.util.Locale.ENGLISH),
        DateTimeFormatter.ofPattern("dd-MMM-yyyy", java.util.Locale.ENGLISH),
        DateTimeFormatter.ofPattern("dd MMM yyyy", java.util.Locale.ENGLISH),
        DateTimeFormatter.ofPattern("dd MMM yy", java.util.Locale.ENGLISH)
    )

    private val UTR_PATTERN = Pattern.compile("""\b([0-9]{12})\b""")
    private val REF_PATTERN = Pattern.compile("""\b([A-Z0-9]{8,18})\b""")

    /**
     * Parses statement text (CSV format or structured text lines).
     */
    fun parse(rawText: String): List<StatementTransaction> {
        val lines = rawText.lines()
        if (lines.isEmpty()) return emptyList()

        // Check if CSV format
        val isCsv = lines.any { it.contains(",") && (it.contains("Withdrawal", ignoreCase = true) || it.contains("Balance", ignoreCase = true) || it.contains("Transaction Date", ignoreCase = true)) }
        return if (isCsv) {
            parseCsv(lines)
        } else {
            parseStructuredText(lines)
        }
    }

    /**
     * Parses spatial elements extracted from a PDF statement.
     * Uses table column boundaries, line-level remarks grouping, and running-balance math.
     */
    fun parseSpatialElements(elements: List<SpatialPdfElement>): List<StatementTransaction> {
        if (elements.isEmpty()) return emptyList()

        val results = mutableListOf<StatementTransaction>()
        val pages = elements.groupBy { it.page }.toSortedMap()

        var expectedSNo = 1
        var isDownwardDirection = true // Default for PDFBox (Y increases downwards from top of page)
        var prevBalance: BigDecimal? = null

        for ((_, pageElements) in pages) {
            // Find candidate transaction S Nos: x < 55, pure digits
            val candidates = pageElements.filter { e ->
                e.x < 55f && e.text.all { it.isDigit() } && e.text.toIntOrNull() != null
            }.sortedBy { it.text.toInt() }

            val validSnos = mutableListOf<SpatialPdfElement>()
            for (cand in candidates) {
                val num = cand.text.toInt()
                if (num == expectedSNo) {
                    validSnos.add(cand)
                    expectedSNo++
                }
            }

            if (validSnos.isEmpty()) continue

            // Auto-detect coordinate system orientation
            if (validSnos.size >= 2) {
                isDownwardDirection = validSnos.first().y < validSnos.last().y
            }

            for (i in validSnos.indices) {
                val snoElement = validSnos[i]
                val sNo = snoElement.text.toInt()
                val yCurrent = snoElement.y

                val yTop: Float
                val yBottom: Float

                if (isDownwardDirection) {
                    yTop = yCurrent - 7.5f
                    yBottom = if (i + 1 < validSnos.size) validSnos[i + 1].y - 7.5f else yCurrent + 45.0f
                } else {
                    yTop = yCurrent + 7.5f
                    yBottom = if (i + 1 < validSnos.size) validSnos[i + 1].y + 7.5f else yCurrent - 45.0f
                }

                val txElements = pageElements.filter { e ->
                    if (isDownwardDirection) e.y >= yTop && e.y < yBottom
                    else e.y <= yTop && e.y > yBottom
                }.sortedWith { a, b ->
                    if (isDownwardDirection) {
                        val dy = a.y.compareTo(b.y)
                        if (dy != 0) dy else a.x.compareTo(b.x)
                    } else {
                        val dy = b.y.compareTo(a.y)
                        if (dy != 0) dy else a.x.compareTo(b.x)
                    }
                }

                var date: LocalDate? = null
                val remarksWords = mutableListOf<SpatialPdfElement>()
                var rawWithdrawal: BigDecimal? = null
                var rawDeposit: BigDecimal? = null
                var rawBalance: BigDecimal? = null

                for (e in txElements) {
                    when {
                        e.x in 55.0f..114.99f -> {
                            if (date == null) {
                                date = parseDate(e.text)
                            }
                        }
                        e.x in 115.0f..389.99f -> {
                            if (e.text != "-" && e.text != "0") {
                                remarksWords.add(e)
                            }
                        }
                        e.x in 390.0f..459.99f -> {
                            val clean = e.text.replace(",", "")
                            if (Regex("""^\d+(\.\d{2})?$""").matches(clean)) {
                                rawWithdrawal = clean.toBigDecimalOrNull()
                            }
                        }
                        e.x in 460.0f..529.99f -> {
                            val clean = e.text.replace(",", "")
                            if (Regex("""^\d+(\.\d{2})?$""").matches(clean)) {
                                rawDeposit = clean.toBigDecimalOrNull()
                            }
                        }
                        e.x >= 530.0f -> {
                            val clean = e.text.replace(",", "")
                            if (Regex("""^\d+(\.\d{2})?$""").matches(clean)) {
                                rawBalance = clean.toBigDecimalOrNull()
                            }
                        }
                    }
                }

                // Group remarks words into lines (same Y within 2.5 points)
                val remarksLines = mutableListOf<String>()
                var currentLine = mutableListOf<String>()
                var lineY: Float? = null

                for (w in remarksWords) {
                    if (lineY == null || kotlin.math.abs(lineY - w.y) > 2.5f) {
                        if (currentLine.isNotEmpty()) {
                            remarksLines.add(currentLine.joinToString(" "))
                            currentLine = mutableListOf()
                        }
                        lineY = w.y
                    }
                    currentLine.add(w.text)
                }
                if (currentLine.isNotEmpty()) {
                    remarksLines.add(currentLine.joinToString(" "))
                }

                val payeeCandidate = remarksLines.firstOrNull() ?: ""
                val fullRemarks = remarksLines.joinToString(" ")

                var withdrawal = rawWithdrawal ?: BigDecimal.ZERO
                var deposit = rawDeposit ?: BigDecimal.ZERO
                val balance = rawBalance ?: BigDecimal.ZERO

                // Running balance verification & computation
                if (prevBalance != null && balance > BigDecimal.ZERO) {
                    val diff = balance.subtract(prevBalance)
                    if (diff > BigDecimal.ZERO) {
                        deposit = diff
                        withdrawal = BigDecimal.ZERO
                    } else if (diff < BigDecimal.ZERO) {
                        withdrawal = diff.abs()
                        deposit = BigDecimal.ZERO
                    }
                } else if (prevBalance == null && balance > BigDecimal.ZERO) {
                    if (deposit > BigDecimal.ZERO) {
                        prevBalance = balance.subtract(deposit)
                    } else if (withdrawal > BigDecimal.ZERO) {
                        prevBalance = balance.add(withdrawal)
                    }
                }

                if (balance > BigDecimal.ZERO) {
                    prevBalance = balance
                }

                val isDeposit = deposit > BigDecimal.ZERO
                val netAmount = if (isDeposit) deposit else withdrawal.negate()

                val parsedRemark = parseRemarkDetails(fullRemarks, payeeCandidate)

                results.add(
                    StatementTransaction(
                        sNo = sNo,
                        date = date ?: LocalDate.now(),
                        payeeOrRemarks = parsedRemark.payee,
                        withdrawal = withdrawal,
                        deposit = deposit,
                        amount = netAmount,
                        balance = balance,
                        utr = parsedRemark.utr ?: extractUtr(fullRemarks),
                        ref = parsedRemark.ref ?: extractRef(fullRemarks),
                        comment = parsedRemark.comment,
                        isRefund = parsedRemark.isRefund || (isDeposit && fullRemarks.contains("RFND", ignoreCase = true)),
                        rawText = fullRemarks
                    )
                )
            }
        }

        return results
    }

    private fun parseCsv(lines: List<Line>): List<StatementTransaction> {
        val results = mutableListOf<StatementTransaction>()
        var sNoCounter = 1

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("S No", ignoreCase = true) || trimmed.startsWith("Transaction Date", ignoreCase = true)) {
                continue
            }

            val parts = parseCsvLine(trimmed)
            if (parts.size < 5) continue

            // Look for Date in the first 3 columns
            var date: LocalDate? = null
            var dateColIdx = -1
            for (col in 0 until minOf(4, parts.size)) {
                date = parseDate(parts[col])
                if (date != null) {
                    dateColIdx = col
                    break
                }
            }
            if (date == null) continue

            // Remarks usually follow the date/cheque number columns
            val remarks = parts.getOrNull(dateColIdx + 2)?.ifBlank { parts.getOrNull(dateColIdx + 1) } ?: parts.getOrElse(dateColIdx + 1) { "" }

            // Amounts are usually the last 3 numeric columns: Withdrawal, Deposit, Balance
            val numCols = parts.mapNotNull { it.replace(",", "").toDoubleOrNull() }
            if (numCols.size < 2) continue

            val balance = numCols.last().toBigDecimal()
            val txnAmt = numCols[numCols.size - 2].toBigDecimal()

            // Determine if withdrawal or deposit
            val isDeposit = if (parts.size >= 8) {
                val depositStr = parts.getOrNull(dateColIdx + 4)?.replace(",", "")?.toDoubleOrNull() ?: 0.0
                depositStr > 0.0
            } else {
                false
            }

            val withdrawal = if (!isDeposit) txnAmt else BigDecimal.ZERO
            val deposit = if (isDeposit) txnAmt else BigDecimal.ZERO
            val netAmount = if (isDeposit) deposit else withdrawal.negate()

            val parsedRemark = parseRemarkDetails(remarks)

            results.add(
                StatementTransaction(
                    sNo = sNoCounter++,
                    date = date,
                    payeeOrRemarks = parsedRemark.payee,
                    withdrawal = withdrawal,
                    deposit = deposit,
                    amount = netAmount,
                    balance = balance,
                    utr = parsedRemark.utr ?: extractUtr(remarks),
                    ref = parsedRemark.ref ?: extractRef(remarks),
                    comment = parsedRemark.comment,
                    isRefund = parsedRemark.isRefund || (isDeposit && remarks.contains("RFND", ignoreCase = true)),
                    rawText = trimmed
                )
            )
        }
        return results
    }

    private fun parseStructuredText(lines: List<String>): List<StatementTransaction> {
        val results = mutableListOf<StatementTransaction>()
        val startLinePattern = Pattern.compile("""^\s*(\d+)\s+(\d{2}[./-]\d{2}[./-]\d{4})\s*(.*)$""")

        val blocks = mutableListOf<MutableList<String>>()
        var currentBlock: MutableList<String>? = null

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            if (startLinePattern.matcher(trimmed).matches()) {
                currentBlock?.let { blocks.add(it) }
                currentBlock = mutableListOf(trimmed)
            } else if (currentBlock != null) {
                if (!trimmed.startsWith("Statement of Transactions") &&
                    !trimmed.startsWith("Your Base Branch") &&
                    !trimmed.startsWith("Please call") &&
                    !trimmed.startsWith("Never share") &&
                    !trimmed.startsWith("www.icici") &&
                    !trimmed.startsWith("Dial your") &&
                    !trimmed.startsWith("S No.")
                ) {
                    currentBlock.add(trimmed)
                }
            }
        }
        currentBlock?.let { blocks.add(it) }

        var prevBalance: BigDecimal? = null

        for (block in blocks) {
            val firstLine = block.first()
            val matcher = startLinePattern.matcher(firstLine)
            if (!matcher.matches()) continue

            val sNo = matcher.group(1)?.toIntOrNull() ?: (results.size + 1)
            val dateStr = matcher.group(2) ?: ""
            val rest = matcher.group(3) ?: ""
            val date = parseDate(dateStr) ?: LocalDate.now()

            val blockText = block.joinToString(" ")
            val payeeCandidate = rest.trim()

            // Extract decimal amounts
            val amtMatches = Regex("""\b(\d+\.\d{2})\b""").findAll(blockText)
                .map { it.groupValues[1] }
                .filter { !dateStr.contains(it) }
                .mapNotNull { it.replace(",", "").toBigDecimalOrNull() }
                .toList()

            var withdrawal = BigDecimal.ZERO
            var deposit = BigDecimal.ZERO
            var balance = BigDecimal.ZERO

            if (amtMatches.isNotEmpty()) {
                balance = amtMatches.last()
            }

            if (prevBalance != null && balance > BigDecimal.ZERO) {
                val diff = balance.subtract(prevBalance)
                if (diff > BigDecimal.ZERO) {
                    deposit = diff
                    withdrawal = BigDecimal.ZERO
                } else if (diff < BigDecimal.ZERO) {
                    withdrawal = diff.abs()
                    deposit = BigDecimal.ZERO
                }
            } else if (amtMatches.size >= 2) {
                val txnAmt = amtMatches[amtMatches.size - 2]
                val isCredit = blockText.contains("credit", ignoreCase = true) ||
                               blockText.contains("deposit", ignoreCase = true) ||
                               blockText.contains("salary", ignoreCase = true) ||
                               blockText.contains("neft", ignoreCase = true)
                if (isCredit) {
                    deposit = txnAmt
                } else {
                    withdrawal = txnAmt
                }
            }

            if (balance > BigDecimal.ZERO) {
                prevBalance = balance
            }

            val isDeposit = deposit > BigDecimal.ZERO
            val netAmount = if (isDeposit) deposit else withdrawal.negate()

            val textForParsing = if (payeeCandidate.isNotBlank()) "$payeeCandidate $blockText" else blockText
            val parsedRemark = parseRemarkDetails(textForParsing, payeeCandidate)

            results.add(
                StatementTransaction(
                    sNo = sNo,
                    date = date,
                    payeeOrRemarks = parsedRemark.payee,
                    withdrawal = withdrawal,
                    deposit = deposit,
                    amount = netAmount,
                    balance = balance,
                    utr = parsedRemark.utr ?: extractUtr(blockText),
                    ref = parsedRemark.ref ?: extractRef(blockText),
                    comment = parsedRemark.comment,
                    isRefund = parsedRemark.isRefund || (isDeposit && blockText.contains("RFND", ignoreCase = true)),
                    rawText = blockText
                )
            )
        }

        return results
    }

    private fun parseDate(str: String): LocalDate? {
        val trimmed = str.trim()
        for (formatter in DATE_PATTERNS) {
            try {
                return LocalDate.parse(trimmed, formatter)
            } catch (_: Exception) {}
        }
        return null
    }

    private fun parseCsvLine(line: String): List<String> {
        val tokens = mutableListOf<String>()
        var inQuotes = false
        val sb = StringBuilder()
        for (ch in line) {
            when (ch) {
                '"' -> inQuotes = !inQuotes
                ',' -> {
                    if (inQuotes) sb.append(ch)
                    else {
                        tokens.add(sb.toString().trim())
                        sb.clear()
                    }
                }
                else -> sb.append(ch)
            }
        }
        tokens.add(sb.toString().trim())
        return tokens
    }

    private fun extractUtr(text: String): String? {
        val matcher = UTR_PATTERN.matcher(text)
        return if (matcher.find()) matcher.group(1) else null
    }

    private fun extractRef(text: String): String? {
        val matcher = REF_PATTERN.matcher(text)
        while (matcher.find()) {
            val g = matcher.group(1)
            if (g != null && g.length in 8..16 && !g.all { it.isDigit() }) {
                return g
            }
        }
        return null
    }

    data class ParsedRemark(
        val payee: String,
        val comment: String? = null,
        val utr: String? = null,
        val ref: String? = null,
        val isRefund: Boolean = false
    )

    fun parseRemarkDetails(text: String, candidatePayee: String? = null): ParsedRemark {
        val trimmed = text.trim()
        val isRefund = trimmed.startsWith("RFND", ignoreCase = true) ||
                       trimmed.contains("/RFND", ignoreCase = true) ||
                       trimmed.startsWith("REFUND", ignoreCase = true) ||
                       trimmed.contains("/REFUND", ignoreCase = true)

        val utr = extractUtr(trimmed)
        val ref = extractRef(trimmed)

        // 1. Check for RFND format: RFNDZEPTO/XXpayu@hdfcban/burger/HDFC BANK/805834443326/PPPL...
        if (isRefund) {
            val tokens = trimmed.split("/").map { it.trim() }.filter { it.isNotEmpty() }
            if (tokens.isNotEmpty()) {
                val firstToken = tokens.first()
                val rawMerchant = firstToken.replace(Regex("""^(?i)(?:rfnd|refund)[\s/_-]*"""), "").trim()
                val cleanMerchant = org.totschnig.myexpenses.util.upi.UpiReceiptParser.cleanPayee(rawMerchant).ifBlank {
                    candidatePayee?.takeIf { it.isNotBlank() } ?: rawMerchant
                }

                // Look for item / note token: not bank name, not VPA (no @), not pure digits, not long hex/hash
                var note: String? = null
                for (token in tokens.drop(1)) {
                    if (token.contains("@")) continue
                    if (token.contains("BANK", ignoreCase = true)) continue
                    if (token.all { it.isDigit() }) continue
                    if (token.length > 20 && token.any { it.isDigit() } && token.any { it.isLetter() }) continue // hash/gateway id
                    if (token.length in 2..30) {
                        note = token
                        break
                    }
                }

                val comment = if (!note.isNullOrBlank()) {
                    if (note.contains("refund", ignoreCase = true)) note else "$note refund"
                } else {
                    "Refund"
                }

                return ParsedRemark(
                    payee = cleanMerchant,
                    comment = comment,
                    utr = utr,
                    ref = ref,
                    isRefund = true
                )
            }
        }

        // 2. Check for standard UPI slash-separated remarks: UPI/<UTR>/<NAME>/<VPA>/... or UPI/<NAME>/<VPA>/...
        if (trimmed.startsWith("UPI/", ignoreCase = true) || trimmed.contains("/UPI/", ignoreCase = true)) {
            val tokens = trimmed.split("/").map { it.trim() }.filter { it.isNotEmpty() }
            val rest = tokens.filter { !it.equals("UPI", ignoreCase = true) }
            var extractedPayee: String? = null
            var comment: String? = null
            for (token in rest) {
                if (token.all { it.isDigit() }) continue
                if (token.contains("@")) continue
                if (token.contains("BANK", ignoreCase = true)) continue
                if (token.equals("DR", ignoreCase = true) || token.equals("CR", ignoreCase = true)) continue
                if (extractedPayee == null && token.length >= 2) {
                    extractedPayee = org.totschnig.myexpenses.util.upi.UpiReceiptParser.cleanPayee(token)
                } else if (comment == null && token.length >= 2) {
                    comment = token
                }
            }
            if (!extractedPayee.isNullOrBlank()) {
                return ParsedRemark(
                    payee = extractedPayee,
                    comment = comment,
                    utr = utr,
                    ref = ref,
                    isRefund = false
                )
            }
        }

        // 3. Fallback
        val fallbackPayee = if (!candidatePayee.isNullOrBlank()) {
            org.totschnig.myexpenses.util.upi.UpiReceiptParser.cleanPayee(cleanRemarks(candidatePayee))
        } else {
            org.totschnig.myexpenses.util.upi.UpiReceiptParser.cleanPayee(cleanRemarks(trimmed))
        }

        return ParsedRemark(
            payee = fallbackPayee.ifBlank { cleanRemarks(trimmed) },
            comment = null,
            utr = utr,
            ref = ref,
            isRefund = false
        )
    }

    private fun cleanRemarks(text: String): String {
        return text
            .replace(Regex("""(?i)^RFND[A-Za-z0-9_-]+/?|RFND/|REFUND/"""), "")
            .replace(Regex("""UPI/[^/]+/"""), "")
            .replace(Regex("""BIL/INFT/[^/]+/"""), "")
            .replace(Regex("""NEFT-[^/]+-"""), "")
            .replace(Regex("""[0-9]{12}"""), "")
            .trim()
            .take(60)
            .ifBlank { text.take(60) }
    }

    /**
     * Parses bank SMS to extract account info, transaction amount, and available balance checkpoint.
     * Example: "ICICI Bank Acc XX407 debited Rs. 489.00 on 01-Nov-26 InfoBIL*INFT*FK15.Avl Bal Rs. 31,500.23."
     */
    fun parseSmsBalance(smsText: String): BalanceCheckpoint? {
        val trimmed = smsText.trim()
        if (trimmed.isEmpty()) return null

        val balPatterns = listOf(
            Pattern.compile("""(?i)(?:avl\s*bal(?:ance)?|available\s*balance|clear\s*bal|tot\s*bal|total\s*balance|balance(?:\s*is)?|bal)(?:[\w\s/.-]{0,35}?)(?:is|[:=])?\s*(?:rs\.?|inr|₹)?\s*([0-9,]+(?:\.[0-9]{2})?)"""),
            Pattern.compile("""(?i)(?:rs\.?|inr|₹)\s*([0-9,]+(?:\.[0-9]{2})?)\s*(?:is\s*)?(?:your\s*)?(?:avl\s*bal(?:ance)?|available\s*balance|clear\s*bal)"""),
            Pattern.compile("""(?i)balance\s*[:=]\s*(?:rs\.?|inr|₹)?\s*([0-9,]+(?:\.[0-9]{2})?)""")
        )

        var avlBal: BigDecimal? = null
        for (pattern in balPatterns) {
            val matcher = pattern.matcher(trimmed)
            if (matcher.find()) {
                val balStr = matcher.group(1)?.replace(",", "")
                avlBal = balStr?.toBigDecimalOrNull()
                if (avlBal != null) break
            }
        }
        if (avlBal == null) return null

        val accPattern = Pattern.compile("""(?i)(?:acc(?:ount)?|a\/c|acct)[\s\w/.-]{0,15}?(?:ending\s*(?:with)?)?[\s:\-.*X#]*([0-9]{3,4})\b""")
        val accMatcher = accPattern.matcher(trimmed)
        val accDigits = if (accMatcher.find()) accMatcher.group(1) else null

        val isDebit = trimmed.contains("debited", ignoreCase = true) ||
                      trimmed.contains("withdrawn", ignoreCase = true) ||
                      trimmed.contains("spent", ignoreCase = true) ||
                      trimmed.contains("paid", ignoreCase = true) ||
                      trimmed.contains("purchase of", ignoreCase = true) ||
                      trimmed.contains("dr", ignoreCase = true)

        val txnAmtPattern = Pattern.compile("""(?i)(?:debited|credited|spent|withdrawn|paid|received|transfer\s*of|txn\s*of)\s*(?:by|for|of)?\s*[:\s]*\s*(?:rs\.?|inr|₹)?\s*([0-9,]+(?:\.[0-9]{2})?)""")
        val txMatcher = txnAmtPattern.matcher(trimmed)
        val txnAmt = if (txMatcher.find()) {
            txMatcher.group(1)?.replace(",", "")?.toBigDecimalOrNull()
        } else null

        val datePattern = Pattern.compile("""\b(\d{1,2}[-/. ](?:[A-Za-z]{3}|\d{1,2})[-/. ]\d{2,4})\b""")
        val dateMatcher = datePattern.matcher(trimmed)
        val date = if (dateMatcher.find()) {
            parseDate(dateMatcher.group(1) ?: "")
        } else null

        val infoPattern = Pattern.compile("""(?i)(?:info[:\s*]*|remarks?[:\s*]*|ref[:\s*]*|upi\s*ref[:\s*]*)([A-Za-z0-9*_-]+)""")
        val infoMatcher = infoPattern.matcher(trimmed)
        val info = if (infoMatcher.find()) infoMatcher.group(1) else null

        return BalanceCheckpoint(
            accountDigits = accDigits,
            transactionAmount = txnAmt,
            isDebit = isDebit,
            transactionDate = date,
            info = info,
            availableBalance = avlBal,
            rawText = trimmed
        )
    }

    fun verifyBalance(appBalance: BigDecimal, checkpoint: BalanceCheckpoint): BalanceVerificationResult {
        val diff = appBalance.subtract(checkpoint.availableBalance)
        val isAligned = diff.compareTo(BigDecimal.ZERO) == 0
        return BalanceVerificationResult(
            appBalance = appBalance,
            bankBalance = checkpoint.availableBalance,
            difference = diff,
            isAligned = isAligned,
            checkpoint = checkpoint
        )
    }
}

private typealias Line = String
