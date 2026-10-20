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
                        cont.resume(visionText.text)
                    }
                    .addOnFailureListener { e ->
                        cont.resumeWithException(e)
                    }
            }
            val parsed = UpiReceiptParser.parse(textResult)
            Timber.d(
                "UPI receipt parsed successfully: hasAmount=%b, hasPayee=%b, hasUpiId=%b, hasUtr=%b",
                parsed.amount != null,
                parsed.payeeName != null,
                parsed.upiId != null,
                parsed.referenceNumber != null
            )
            Result.success(parsed)
        } catch (e: Exception) {
            Timber.e(e, "Error scanning UPI receipt from %s", uri)
            Result.failure(e)
        }
    }
}
