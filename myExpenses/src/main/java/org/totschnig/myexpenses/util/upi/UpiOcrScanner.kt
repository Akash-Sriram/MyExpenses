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
            android.util.Log.d("UPI_OCR", "Raw OCR text:\n$textResult")
            android.util.Log.d("UPI_OCR", "Parsed UPI receipt: amount=${parsed.amount}, payee=${parsed.payeeName}, upiId=${parsed.upiId}, dateTime=${parsed.dateTime}, utr=${parsed.referenceNumber}, bank=${parsed.accountOrBankHint}")
            Timber.i("Parsed UPI receipt: %s", parsed)
            Result.success(parsed)
        } catch (e: Exception) {
            android.util.Log.e("UPI_OCR", "Error scanning UPI receipt from $uri", e)
            Timber.e(e, "Error scanning UPI receipt from %s", uri)
            Result.failure(e)
        }
    }
}
