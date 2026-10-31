package org.totschnig.myexpenses.util.upi

import android.content.Context
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import timber.log.Timber

object UpiQrScannerHelper {

    /**
     * Launches the Google Play Services Code Scanner to scan a high-density QR code.
     * Operates completely without needing the CAMERA runtime permission.
     */
    fun scanQrCode(
        context: Context,
        onSuccess: (String) -> Unit,
        onCanceled: () -> Unit = {},
        onFailure: (Exception) -> Unit = {}
    ) {
        try {
            val options = GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .enableAutoZoom()
                .build()

            val scanner = GmsBarcodeScanning.getClient(context, options)
            scanner.startScan()
                .addOnSuccessListener { barcode ->
                    val raw = barcode.rawValue
                    if (!raw.isNullOrBlank()) {
                        onSuccess(raw)
                    } else {
                        onCanceled()
                    }
                }
                .addOnCanceledListener {
                    Timber.d("UPI QR scan cancelled by user")
                    onCanceled()
                }
                .addOnFailureListener { e ->
                    Timber.e(e, "UPI QR scan failed")
                    onFailure(e)
                }
        } catch (e: Exception) {
            Timber.e(e, "Error initializing GmsBarcodeScanning")
            onFailure(e)
        }
    }
}
