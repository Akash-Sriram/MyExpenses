package org.totschnig.myexpenses.util.upi

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

object UpiOcrScanner {

    suspend fun scanReceipt(context: Context, uri: Uri): Result<UpiReceiptData> = withContext(Dispatchers.IO) {
        // 1. Primary: RapidOCR (PaddleOCR PP-OCRv4 ONNX Runtime with 2D Key-Value Pairing)
        val rapidResult = try {
            RapidOcrScanner.scan(context, uri)
        } catch (e: Throwable) {
            Timber.w(e, "RapidOCR attempt failed, falling back to ML Kit")
            Result.failure(e)
        }

        if (rapidResult.isSuccess) {
            val data = rapidResult.getOrNull()
            if (data != null && data.isValid) {
                Timber.i(
                    "RapidOCR successfully scanned receipt: amount=%s, payee=%s, comment=%s, utr=%s",
                    data.amount,
                    data.payeeName,
                    data.comment,
                    data.referenceNumber
                )
                return@withContext rapidResult
            }
        }

        // 2. Graceful Fallback: Google ML Kit
        scanWithMlKit(context, uri)
    }

    private suspend fun scanWithMlKit(context: Context, uri: Uri): Result<UpiReceiptData> {
        return try {
            val image = InputImage.fromFilePath(context, uri)
            val recognizer = TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
            val textResult = suspendCoroutine { cont ->
                recognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        Log.i("UpiOcrScanner", "Raw ML Kit Devanagari OCR Text:\n${visionText.text}")
                        Timber.d("Raw ML Kit OCR Text:\n%s", visionText.text)
                        cont.resume(visionText.text)
                    }
                    .addOnFailureListener { e ->
                        Log.e("UpiOcrScanner", "ML Kit OCR failed", e)
                        cont.resumeWithException(e)
                    }
            }
            val app = context.applicationContext as? org.totschnig.myexpenses.MyApplication
            val prefHandler = app?.prefHandler
            val userName = prefHandler?.getString(UpiReceiptParser.PREF_KEY_USER_PROFILE_NAME, UpiReceiptParser.DEFAULT_USER_NAME) ?: UpiReceiptParser.DEFAULT_USER_NAME
            val userPhone = prefHandler?.getString(UpiReceiptParser.PREF_KEY_USER_PROFILE_PHONE, UpiReceiptParser.DEFAULT_USER_PHONE) ?: UpiReceiptParser.DEFAULT_USER_PHONE
            val userVpa = prefHandler?.getString(UpiReceiptParser.PREF_KEY_USER_PROFILE_VPA, UpiReceiptParser.DEFAULT_USER_VPA) ?: UpiReceiptParser.DEFAULT_USER_VPA

            val parsed = UpiReceiptParser.parse(
                rawText = textResult,
                userName = userName,
                userPhone = userPhone,
                userVpa = userVpa
            )

            Log.i("UpiOcrScanner", "UPI receipt parsed via ML Kit: amount=${parsed.amount}, payee=${parsed.payeeName}, dt=${parsed.dateTime}, utr=${parsed.referenceNumber}")
            Timber.d(
                "UPI receipt parsed via ML Kit: isExpense=%b, amount=%s, payee=%s, recipient=%s, upiId=%s, utr=%s",
                parsed.isExpense,
                parsed.amount,
                parsed.payeeName,
                parsed.recipientName,
                parsed.upiId,
                parsed.referenceNumber
            )
            Result.success(parsed)
        } catch (e: Exception) {
            Log.e("UpiOcrScanner", "Failed to scan UPI receipt via ML Kit", e)
            Timber.e(e, "Failed to scan UPI receipt via ML Kit")
            Result.failure(e)
        }
    }
}
