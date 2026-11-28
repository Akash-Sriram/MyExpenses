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
                // If deposit column has a non-zero value
                val depositStr = parts.getOrNull(dateColIdx + 4)?.replace(",", "")?.toDoubleOrNull() ?: 0.0
                depositStr > 0.0
            } else {
                false
            }

            val withdrawal = if (!isDeposit) txnAmt else BigDecimal.ZERO
            val deposit = if (isDeposit) txnAmt else BigDecimal.ZERO
            val netAmount = if (isDeposit) deposit else withdrawal.negate()

            val utr = extractUtr(remarks)
            val ref = extractRef(remarks)

            results.add(
                StatementTransaction(
                    sNo = sNoCounter++,
                    date = date,
                    payeeOrRemarks = cleanRemarks(remarks),
                    withdrawal = withdrawal,
                    deposit = deposit,
                    amount = netAmount,
                    balance = balance,
                    utr = utr,
                    ref = ref,
                    rawText = trimmed
                )
            )
        }
        return results
    }

    private fun parseStructuredText(lines: List<String>): List<StatementTransaction> {
        val results = mutableListOf<StatementTransaction>()
        val startLinePattern = Pattern.compile("""^\s*(\d+)\s+(\d{2}[./-]\d{2}[./-]\d{4})\s+(.*)$""")

        var currentTxn: MutableStatementTxn? = null

        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val matcher = startLinePattern.matcher(line)

            if (matcher.matches()) {
                // Save previous
                currentTxn?.let { results.add(it.toStatementTransaction()) }

                val sNo = matcher.group(1)?.toIntOrNull() ?: (results.size + 1)
                val dateStr = matcher.group(2) ?: ""
                val rest = matcher.group(3) ?: ""
                val date = parseDate(dateStr) ?: LocalDate.now()

                // Check 1-2 lines above for payee header if present
                val payeeHeader = if (i > 0 && lines[i - 1].trim().isNotEmpty() && !startLinePattern.matcher(lines[i - 1]).matches()) {
                    lines[i - 1].trim()
                } else ""

                // Extract decimal amounts
                val amtMatches = Regex("""(\d+\.\d{2})""").findAll(rest).map { it.value }.toList()
                var withdrawal = BigDecimal.ZERO
                var deposit = BigDecimal.ZERO
                var balance = BigDecimal.ZERO

                if (amtMatches.size >= 2) {
                    balance = amtMatches.last().toBigDecimalOrNull() ?: BigDecimal.ZERO
                    val txnAmt = amtMatches[amtMatches.size - 2].toBigDecimalOrNull() ?: BigDecimal.ZERO
                    
                    // Column index in line
                    val amtIdx = line.lastIndexOf(amtMatches[amtMatches.size - 2])
                    if (amtIdx < 112) {
                        withdrawal = txnAmt
                    } else {
                        deposit = txnAmt
                    }
                } else if (amtMatches.size == 1) {
                    balance = amtMatches[0].toBigDecimalOrNull() ?: BigDecimal.ZERO
                }

                currentTxn = MutableStatementTxn(
                    sNo = sNo,
                    date = date,
                    payeeHeader = payeeHeader,
                    withdrawal = withdrawal,
                    deposit = deposit,
                    balance = balance,
                    remarks = mutableListOf()
                )
            } else if (currentTxn != null) {
                val trimmed = line.trim()
                if (trimmed.isNotEmpty() &&
                    !trimmed.startsWith("Statement of Transactions") &&
                    !trimmed.startsWith("Your Base Branch") &&
                    !trimmed.startsWith("Please call") &&
                    !trimmed.startsWith("Never share") &&
                    !trimmed.startsWith("www.icici") &&
                    !trimmed.startsWith("S No.")
                ) {
                    if (line.length > 30) {
                        val remarkPart = line.substring(minOf(30, line.length), minOf(95, line.length)).trim()
                        if (remarkPart.isNotEmpty()) {
                            currentTxn.remarks.add(remarkPart)
                        }
                    }
                }
            }
            i++
        }

        currentTxn?.let { results.add(it.toStatementTransaction()) }
        return results
    }

    private class MutableStatementTxn(
        val sNo: Int,
        val date: LocalDate,
        val payeeHeader: String,
        val withdrawal: BigDecimal,
        val deposit: BigDecimal,
        val balance: BigDecimal,
        val remarks: MutableList<String>
    ) {
        fun toStatementTransaction(): StatementTransaction {
            val fullText = (listOf(payeeHeader) + remarks).filter { it.isNotBlank() }.joinToString(" ")
            val isDeposit = deposit > BigDecimal.ZERO
            val netAmount = if (isDeposit) deposit else withdrawal.negate()
            return StatementTransaction(
                sNo = sNo,
                date = date,
                payeeOrRemarks = cleanRemarks(if (payeeHeader.isNotBlank()) payeeHeader else fullText),
                withdrawal = withdrawal,
                deposit = deposit,
                amount = netAmount,
                balance = balance,
                utr = extractUtr(fullText),
                ref = extractRef(fullText),
                rawText = fullText
            )
        }
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

    private fun cleanRemarks(text: String): String {
        return text
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
