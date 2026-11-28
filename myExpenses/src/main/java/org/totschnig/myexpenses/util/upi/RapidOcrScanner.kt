package org.totschnig.myexpenses.util.upi

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import io.github.hzkitty.RapidOCR
import io.github.hzkitty.entity.OcrResult
import io.github.hzkitty.entity.RecResult
import timber.log.Timber
import java.io.InputStream
import java.math.BigDecimal
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

object RapidOcrScanner {

    @Volatile
    private var rapidOcrInstance: RapidOCR? = null
    private val initLock = Any()

    private fun getRapidOcr(context: Context): RapidOCR {
        rapidOcrInstance?.let { return it }
        synchronized(initLock) {
            rapidOcrInstance?.let { return it }
            val instance = RapidOCR.create(context.applicationContext)
            rapidOcrInstance = instance
            return instance
        }
    }

    fun scan(context: Context, uri: Uri): Result<UpiReceiptData> {
        return try {
            val bitmap = loadOptimizedBitmap(context, uri)
                ?: return Result.failure(IllegalArgumentException("Could not decode bitmap from URI: $uri"))

            val ocr = getRapidOcr(context)
            val ocrResult: OcrResult = ocr.run(bitmap)
            val recList: List<RecResult> = ocrResult.recRes.orEmpty()

            val lines = recList.map { it.text.trim() }.filter { it.isNotEmpty() }
            val fullText = ocrResult.strRes?.takeIf { it.isNotBlank() } ?: lines.joinToString("\n")

            Timber.i("RapidOCR Raw Extracted Lines (%d):\n%s", lines.size, fullText)

            val app = context.applicationContext as? org.totschnig.myexpenses.MyApplication
            val prefHandler = app?.prefHandler
            val userName = prefHandler?.getString(UpiReceiptParser.PREF_KEY_USER_PROFILE_NAME, UpiReceiptParser.DEFAULT_USER_NAME) ?: UpiReceiptParser.DEFAULT_USER_NAME
            val userPhone = prefHandler?.getString(UpiReceiptParser.PREF_KEY_USER_PROFILE_PHONE, UpiReceiptParser.DEFAULT_USER_PHONE) ?: UpiReceiptParser.DEFAULT_USER_PHONE
            val userVpa = prefHandler?.getString(UpiReceiptParser.PREF_KEY_USER_PROFILE_VPA, UpiReceiptParser.DEFAULT_USER_VPA) ?: UpiReceiptParser.DEFAULT_USER_VPA

            val baseParsed = UpiReceiptParser.parse(
                lines = lines,
                fullText = fullText,
                userName = userName,
                userPhone = userPhone,
                userVpa = userVpa
            )

            // 2D Spatial & Sequential Key-Value Pairing
            val pairedData = extract2DKeyValues(lines, baseParsed)
            Result.success(pairedData)
        } catch (e: Exception) {
            Timber.e(e, "RapidOCR execution failed for uri: %s", uri)
            Result.failure(e)
        }
    }

    /**
     * Resolves labels and values directly from text elements.
     * Prevents app header noise (e.g. "Paid from Samsung Wallet") from polluting transaction notes.
     */
    private fun extract2DKeyValues(lines: List<String>, base: UpiReceiptData): UpiReceiptData {
        var explicitComment: String? = null
        var explicitUtr: String? = null
        var explicitSenderBank: String? = null
        var explicitSenderVpa: String? = null

        for (i in lines.indices) {
            val line = lines[i]

            // 1. Notes / Remarks pairing
            if (explicitComment == null) {
                val notesMatch = Regex("""(?i)^\s*(?:notes?|remarks?|message|comments?|purpose)\s*[:\-]\s*(.+)""").find(line)
                if (notesMatch != null) {
                    val candidate = notesMatch.groupValues[1].trim()
                    if (isCleanUserNote(candidate)) {
                        explicitComment = candidate
                    }
                } else if (line.matches(Regex("""(?i)^\s*(?:notes?|remarks?|message|comments?|purpose)\s*[:\-]?\s*$""")) && i + 1 < lines.size) {
                    val candidate = lines[i + 1].trim()
                    if (isCleanUserNote(candidate)) {
                        explicitComment = candidate
                    }
                }
            }

            // 2. Transaction ID / UTR pairing
            if (explicitUtr == null) {
                val utrMatch = Regex("""(?i)^\s*(?:transaction\s*id|txn\s*id|upi\s*ref|utr|rrn)\s*[:\-]?\s*([0-9A-Za-z]{10,24})""").find(line)
                if (utrMatch != null) {
                    explicitUtr = utrMatch.groupValues[1].trim()
                } else if (line.matches(Regex("""(?i)^\s*(?:transaction\s*id|txn\s*id|upi\s*ref|utr|rrn)\s*[:\-]?\s*$""")) && i + 1 < lines.size) {
                    val candidate = lines[i + 1].trim()
                    if (candidate.matches(Regex("""^[0-9A-Za-z]{10,24}$"""))) {
                        explicitUtr = candidate
                    }
                }
            }

            // 3. Sender Bank ("Sent from: ICICI Bank")
            if (explicitSenderBank == null) {
                val bankMatch = Regex("""(?i)^\s*(?:sent\s+from|paid\s+from|debited\s+from)\s*[:\-]\s*(.+)""").find(line)
                if (bankMatch != null) {
                    val candidate = bankMatch.groupValues[1].trim()
                    if (!candidate.contains("Wallet", ignoreCase = true) && !candidate.contains("Google", ignoreCase = true)) {
                        explicitSenderBank = candidate
                    }
                } else if (line.matches(Regex("""(?i)^\s*(?:sent\s+from|debited\s+from)\s*[:\-]?\s*$""")) && i + 1 < lines.size) {
                    val candidate = lines[i + 1].trim()
                    if (!candidate.contains("Wallet", ignoreCase = true)) {
                        explicitSenderBank = candidate
                    }
                }
            }

            // 4. Sender VPA ("Sender: asg98@pingpay")
            if (explicitSenderVpa == null) {
                val senderMatch = Regex("""(?i)^\s*sender\s*[:\-]\s*([a-zA-Z0-9.\-_@]+)""").find(line)
                if (senderMatch != null) {
                    explicitSenderVpa = senderMatch.groupValues[1].trim()
                } else if (line.matches(Regex("""(?i)^\s*sender\s*[:\-]?\s*$""")) && i + 1 < lines.size) {
                    val candidate = lines[i + 1].trim()
                    if (candidate.contains("@")) {
                        explicitSenderVpa = candidate
                    }
                }
            }
        }

        return base.copy(
            comment = explicitComment ?: base.comment?.takeIf { isCleanUserNote(it) },
            referenceNumber = explicitUtr ?: base.referenceNumber,
            accountOrBankHint = explicitSenderBank ?: base.accountOrBankHint
        )
    }

    private fun isCleanUserNote(note: String): Boolean {
        if (note.length < 2) return false
        val lower = note.lowercase(Locale.ROOT)
        val systemBlacklist = listOf(
            "samsung wallet", "wallet", "paid from", "sent from", "powered by",
            "axis bank", "icici bank", "hdfc bank", "upi", "google pay", "phonepe",
            "transaction successful", "payment successful", "sent"
        )
        return systemBlacklist.none { lower == it || lower.contains(it) }
    }

    private fun loadOptimizedBitmap(context: Context, uri: Uri): Bitmap? {
        return try {
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, boundsOptions)
            }

            val maxDimension = 1800
            var sampleSize = 1
            while (boundsOptions.outWidth / sampleSize > maxDimension || boundsOptions.outHeight / sampleSize > maxDimension) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOptions)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error decoding bitmap from %s", uri)
            null
        }
    }
}
