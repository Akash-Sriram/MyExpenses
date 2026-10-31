package org.totschnig.myexpenses.util.upi

import android.net.Uri
import timber.log.Timber
import java.math.BigDecimal
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

object UpiQrParser {

    /**
     * Checks whether the given scanned QR string represents a UPI payment request.
     */
    fun isUpiQr(raw: String): Boolean {
        val trimmed = raw.trim()
        if (trimmed.startsWith("upi://pay", ignoreCase = true)) return true
        if (trimmed.contains("pa=") && trimmed.contains("@")) return true
        return false
    }

    /**
     * Parses a scanned QR string into a structured [UpiQrData].
     * Supports standard `upi://pay`, gateway wrapped links, and complex encoded query parameters.
     */
    fun parse(raw: String): UpiQrData? {
        if (!isUpiQr(raw)) return null

        val trimmed = raw.trim()
        return try {
            val normalizedUri = if (trimmed.startsWith("upi://", ignoreCase = true)) {
                trimmed
            } else if (trimmed.contains("pa=")) {
                // If the QR is just a query string or HTTP link, extract from the query part
                val queryPart = if (trimmed.contains("?")) trimmed.substringAfter("?") else trimmed
                "upi://pay?$queryPart"
            } else {
                trimmed
            }

            val uri = Uri.parse(normalizedUri)

            // Extract parameters using URI query or fallback manual parsing
            val params = extractParams(normalizedUri, uri)

            val vpa = params["pa"]?.trim() ?: return null
            if (!vpa.contains("@")) {
                Timber.w("Invalid VPA in scanned UPI QR: %s", vpa)
                return null
            }

            val payeeName = params["pn"]?.trim()?.takeIf { it.isNotEmpty() }
            val amountStr = params["am"]?.trim()?.replace(",", "")
            val amount = amountStr?.let {
                try {
                    BigDecimal(it).takeIf { amt -> amt > BigDecimal.ZERO }
                } catch (e: Exception) {
                    Timber.w(e, "Could not parse amount from UPI QR: %s", it)
                    null
                }
            }

            val currency = params["cu"]?.trim()?.uppercase() ?: "INR"
            val transactionRef = params["tr"]?.trim()?.takeIf { it.isNotEmpty() }
            val transactionNote = params["tn"]?.trim()?.takeIf { it.isNotEmpty() }
            val merchantCode = params["mc"]?.trim()?.takeIf { it.isNotEmpty() }
            val transactionId = params["tid"]?.trim()?.takeIf { it.isNotEmpty() }
            val url = params["url"]?.trim()?.takeIf { it.isNotEmpty() }

            UpiQrData(
                vpa = vpa,
                payeeName = payeeName,
                amount = amount,
                currency = currency,
                transactionRef = transactionRef,
                transactionNote = transactionNote,
                merchantCode = merchantCode,
                transactionId = transactionId,
                url = url,
                rawUri = trimmed
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse UPI QR code: %s", raw)
            null
        }
    }

    private fun extractParams(raw: String, parsedUri: Uri): Map<String, String> {
        val result = mutableMapOf<String, String>()

        // 1. Android Uri.getQueryParameterNames
        try {
            for (key in parsedUri.queryParameterNames) {
                parsedUri.getQueryParameter(key)?.let { value ->
                    result[key.lowercase()] = value
                }
            }
        } catch (e: Exception) {
            Timber.d(e, "Uri query extraction had issue, falling back to manual parser")
        }

        // 2. Manual parser fallback / supplement for raw encoded params (handles gateway-encoded characters like %3D)
        val queryString = raw.substringAfter("?", "")
        if (queryString.isNotEmpty()) {
            val pairs = queryString.split("&")
            for (pair in pairs) {
                val idx = pair.indexOf("=")
                if (idx > 0) {
                    val key = safeUrlDecode(pair.substring(0, idx)).lowercase().trim()
                    val value = safeUrlDecode(pair.substring(idx + 1)).trim()
                    if (key.isNotEmpty() && !result.containsKey(key)) {
                        result[key] = value
                    }
                }
            }
        }

        return result
    }

    private fun safeUrlDecode(value: String): String {
        return try {
            URLDecoder.decode(value, StandardCharsets.UTF_8.name())
        } catch (_: Exception) {
            value
        }
    }
}
