package org.totschnig.myexpenses.util.upi

import android.content.Context
import android.net.Uri
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
        try {
            val image = InputImage.fromFilePath(context, uri)
            val recognizer = TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
            val textResult = suspendCoroutine { cont ->
                recognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        Timber.d("Raw ML Kit OCR Text:\n%s", visionText.text)
                        cont.resume(visionText.text)
                    }
                    .addOnFailureListener { e ->
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
            Timber.d(
                "UPI receipt parsed: isExpense=%b, amount=%s, payee=%s, recipient=%s, upiId=%s, utr=%s",
                parsed.isExpense,
                parsed.amount,
                parsed.payeeName,
                parsed.recipientName,
                parsed.upiId,
                parsed.referenceNumber
            )
            Result.success(parsed)
        } catch (e: Exception) {
            Timber.e(e, "Error scanning UPI receipt from %s", uri)
            Result.failure(e)
        }
    }
}
