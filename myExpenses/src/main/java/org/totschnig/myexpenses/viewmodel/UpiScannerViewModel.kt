package org.totschnig.myexpenses.viewmodel

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.totschnig.myexpenses.util.upi.UpiAppHelper
import org.totschnig.myexpenses.util.upi.UpiAppInfo
import org.totschnig.myexpenses.util.upi.UpiQrData
import org.totschnig.myexpenses.util.upi.UpiQrParser
import timber.log.Timber

sealed interface UpiUiState {
    object Idle : UpiUiState
    data class AppSelection(val qrData: UpiQrData, val apps: List<UpiAppInfo>) : UpiUiState
    data class AwaitingPayment(val qrData: UpiQrData, val attemptedApp: UpiAppInfo) : UpiUiState
    data class PaymentFailed(
        val qrData: UpiQrData,
        val failedApp: UpiAppInfo,
        val availableApps: List<UpiAppInfo>,
        val reason: String? = null
    ) : UpiUiState
    data class PaymentCompleted(val qrData: UpiQrData, val appUsed: UpiAppInfo?) : UpiUiState
    data class ScanError(val message: String) : UpiUiState
}

class UpiScannerViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) : BaseViewModel(application) {

    private val _uiState = MutableStateFlow<UpiUiState>(UpiUiState.Idle)
    val uiState: StateFlow<UpiUiState> = _uiState.asStateFlow()

    private var currentQrData: UpiQrData? = null
    private var currentAttemptedApp: UpiAppInfo? = null

    fun onQrScanned(rawContent: String) {
        val parsed = UpiQrParser.parse(rawContent)
        if (parsed == null || !parsed.isValid) {
            _uiState.value = UpiUiState.ScanError("Scanned QR is not a valid UPI payment code.")
            return
        }

        currentQrData = parsed
        val installedApps = UpiAppHelper.getInstalledUpiApps(getApplication())
        if (installedApps.isEmpty()) {
            _uiState.value = UpiUiState.ScanError("No UPI apps found on this device.")
            return
        }

        _uiState.value = UpiUiState.AppSelection(parsed, installedApps)
    }

    fun selectAppToPay(app: UpiAppInfo, customAmount: java.math.BigDecimal? = null) {
        val qr = currentQrData ?: return
        val updatedQr = if (customAmount != null && customAmount > java.math.BigDecimal.ZERO) {
            qr.copy(amount = customAmount)
        } else {
            qr
        }
        currentQrData = updatedQr
        currentAttemptedApp = app
        _uiState.value = UpiUiState.AwaitingPayment(updatedQr, app)
    }

    fun handlePaymentActivityResult(resultCode: Int, data: Intent?) {
        val qr = currentQrData ?: return
        val app = currentAttemptedApp

        Timber.d("UPI Activity Result: resultCode=%d, data=%s", resultCode, data?.extras)

        val statusFromResponse = extractPaymentStatus(data)
        val isExplicitSuccess = statusFromResponse?.equals("SUCCESS", ignoreCase = true) == true
        val isExplicitSubmitted = statusFromResponse?.equals("SUBMITTED", ignoreCase = true) == true

        if (resultCode == Activity.RESULT_OK && (isExplicitSuccess || isExplicitSubmitted)) {
            _uiState.value = UpiUiState.PaymentCompleted(qr, app)
        } else {
            // Either cancelled, failed, or bank app didn't return a proper success bundle
            val allApps = UpiAppHelper.getInstalledUpiApps(getApplication())
            val remainingApps = allApps.filter { it.packageName != app?.packageName }
            val failureReason = if (statusFromResponse != null) "Status: $statusFromResponse" else null

            _uiState.value = UpiUiState.PaymentFailed(
                qrData = qr,
                failedApp = app ?: UpiAppInfo("unknown", "UPI App"),
                availableApps = remainingApps,
                reason = failureReason
            )
        }
    }

    fun markAsPaidAnyway() {
        val qr = currentQrData ?: return
        _uiState.value = UpiUiState.PaymentCompleted(qr, currentAttemptedApp)
    }

    fun retryWithApp(app: UpiAppInfo) {
        selectAppToPay(app)
    }

    fun reset() {
        currentQrData = null
        currentAttemptedApp = null
        _uiState.value = UpiUiState.Idle
    }

    private fun extractPaymentStatus(data: Intent?): String? {
        if (data == null) return null

        // 1. Check intent extras
        val statusExtra = data.getStringExtra("Status")
            ?: data.getStringExtra("status")
            ?: data.extras?.keySet()?.firstOrNull { it.equals("Status", ignoreCase = true) }
                ?.let { data.getStringExtra(it) }

        if (!statusExtra.isNullOrBlank()) return statusExtra

        // 2. Check response query params if response came in Uri
        val responseUri = data.data
        if (responseUri != null) {
            val statusParam = responseUri.getQueryParameter("Status")
                ?: responseUri.getQueryParameter("status")
            if (!statusParam.isNullOrBlank()) return statusParam
        }

        // 3. Fallback check for raw response string in extras
        val rawResponse = data.getStringExtra("response")
        if (!rawResponse.isNullOrBlank()) {
            val parts = rawResponse.split("&")
            for (part in parts) {
                val kv = part.split("=")
                if (kv.size == 2 && kv[0].equals("Status", ignoreCase = true)) {
                    return kv[1]
                }
            }
        }

        return null
    }

    fun createExpenseEditIntent(context: Context, qrData: UpiQrData, appUsed: UpiAppInfo?): Intent {
        return Intent(context, org.totschnig.myexpenses.activity.ExpenseEdit::class.java).apply {
            action = Intent.ACTION_INSERT
            data = org.totschnig.myexpenses.contract.TransactionsContract.Transactions.CONTENT_URI
            if (qrData.amount != null && qrData.amount > java.math.BigDecimal.ZERO) {
                putExtra(
                    org.totschnig.myexpenses.contract.TransactionsContract.Transactions.AMOUNT_MICROS,
                    -(qrData.amount.toDouble() * 1_000_000).toLong()
                )
            }
            qrData.payeeName?.takeIf { it.isNotBlank() }?.let {
                putExtra(org.totschnig.myexpenses.contract.TransactionsContract.Transactions.PAYEE_NAME, it)
            }
            putExtra(org.totschnig.myexpenses.contract.TransactionsContract.Transactions.METHOD_LABEL, "UPI")
            putExtra(org.totschnig.myexpenses.contract.TransactionsContract.Transactions.CURRENCY, qrData.currency.ifBlank { "INR" })
            qrData.transactionRef?.takeIf { it.isNotBlank() }?.let {
                putExtra(org.totschnig.myexpenses.contract.TransactionsContract.Transactions.REFERENCE_NUMBER, it)
            }
            val commentParts = mutableListOf<String>()
            commentParts.add("UPI: ${qrData.vpa}")
            if (!qrData.transactionNote.isNullOrBlank()) {
                commentParts.add("Note: ${qrData.transactionNote}")
            }
            if (appUsed != null) {
                commentParts.add("Via: ${appUsed.appName}")
            }
            putExtra(org.totschnig.myexpenses.contract.TransactionsContract.Transactions.COMMENT, commentParts.joinToString(" • "))
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
    }
}
