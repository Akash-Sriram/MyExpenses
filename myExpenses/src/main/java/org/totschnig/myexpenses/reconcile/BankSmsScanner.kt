package org.totschnig.myexpenses.reconcile

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat
import timber.log.Timber
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.regex.Pattern

data class SmsScanResult(
    val detectedBank: String?,
    val detectedDigits: String?,
    val checkpoints: List<BalanceCheckpoint>,
    val latestCheckpoint: BalanceCheckpoint?,
    val hasPermission: Boolean
)

object BankSmsScanner {

    private val KNOWN_BANKS = listOf(
        "ICICI", "HDFC", "SBI", "AXIS", "KOTAK", "PNB", "BOB", "CANARA",
        "INDUSIND", "YES", "CITI", "HSBC", "SCB", "FEDERAL", "IDFC",
        "UNION", "PAYTM", "JUPITER", "FI"
    )

    private val DIGITS_PATTERN = Pattern.compile("""\b(?:[Xx*#]{1,4})?([0-9]{3,4})\b""")

    fun scanInbox(context: Context, accountLabel: String): SmsScanResult {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED

        val detectedBank = detectBank(accountLabel)
        val detectedDigits = detectDigits(accountLabel)

        if (!hasPermission) {
            return SmsScanResult(
                detectedBank = detectedBank,
                detectedDigits = detectedDigits,
                checkpoints = emptyList(),
                latestCheckpoint = null,
                hasPermission = false
            )
        }

        val checkpoints = mutableListOf<BalanceCheckpoint>()

        try {
            val contentResolver = context.contentResolver
            val projection = arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE
            )

            // Query recent 300 SMS messages sorted by newest first
            val cursor = contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                projection,
                null,
                null,
                "${Telephony.Sms.DATE} DESC"
            )

            cursor?.use {
                val addressIdx = it.getColumnIndex(Telephony.Sms.ADDRESS)
                val bodyIdx = it.getColumnIndex(Telephony.Sms.BODY)
                val dateIdx = it.getColumnIndex(Telephony.Sms.DATE)

                var count = 0
                while (it.moveToNext() && count < 300) {
                    count++
                    val address = if (addressIdx != -1) it.getString(addressIdx) ?: "" else ""
                    val body = if (bodyIdx != -1) it.getString(bodyIdx) ?: "" else ""
                    val dateMillis = if (dateIdx != -1) it.getLong(dateIdx) else 0L

                    if (body.isBlank()) continue

                    val matchesBank = detectedBank != null && (
                        address.contains(detectedBank, ignoreCase = true) ||
                        body.contains(detectedBank, ignoreCase = true)
                    )

                    val matchesDigits = detectedDigits != null && body.contains(detectedDigits)

                    // If neither bank nor digits were detected in account label, consider any bank sender
                    val isCandidateBank = matchesBank || matchesDigits || isBankSender(address)

                    if (!isCandidateBank) continue

                    val parsed = BankStatementParser.parseSmsBalance(body) ?: continue

                    // If we have specific account digits, filter out messages that belong to another account
                    if (detectedDigits != null && parsed.accountDigits != null) {
                        if (!parsed.accountDigits.endsWith(detectedDigits) &&
                            !detectedDigits.endsWith(parsed.accountDigits)
                        ) {
                            continue
                        }
                    }

                    // Fill in date from SMS metadata if not parsed from body text
                    val resolvedDate = parsed.transactionDate ?: if (dateMillis > 0L) {
                        Instant.ofEpochMilli(dateMillis).atZone(ZoneId.systemDefault()).toLocalDate()
                    } else LocalDate.now()

                    val enrichedCheckpoint = parsed.copy(transactionDate = resolvedDate)
                    checkpoints.add(enrichedCheckpoint)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error scanning SMS inbox for account: %s", accountLabel)
        }

        return SmsScanResult(
            detectedBank = detectedBank,
            detectedDigits = detectedDigits,
            checkpoints = checkpoints,
            latestCheckpoint = checkpoints.firstOrNull(),
            hasPermission = true
        )
    }

    fun detectBank(accountLabel: String): String? {
        val upper = accountLabel.uppercase()
        return KNOWN_BANKS.firstOrNull { upper.contains(it) }
    }

    fun detectDigits(accountLabel: String): String? {
        val matcher = DIGITS_PATTERN.matcher(accountLabel)
        return if (matcher.find()) matcher.group(1) else null
    }

    private fun isBankSender(address: String): Boolean {
        val upper = address.uppercase()
        return KNOWN_BANKS.any { upper.contains(it) } ||
               upper.contains("BANK") ||
               upper.contains("BNK") ||
               upper.endsWith("BK") ||
               upper.endsWith("NB")
    }
}
