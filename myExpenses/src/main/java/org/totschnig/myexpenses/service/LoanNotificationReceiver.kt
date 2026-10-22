package org.totschnig.myexpenses.service

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.totschnig.myexpenses.MyApplication
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.util.NotificationBuilderWrapper
import timber.log.Timber

/**
 * Handles interactive 1-tap payment directly from EMI due notifications.
 */
class LoanNotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        if (action == ACTION_PAY_EMI) {
            val installmentId = intent.getLongExtra(EXTRA_INSTALLMENT_ID, 0L)
            val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
            val loanLabel = intent.getStringExtra(EXTRA_LOAN_LABEL) ?: "Loan"
            val amount = intent.getLongExtra(EXTRA_AMOUNT, 0L)
            val accountLabel = intent.getStringExtra(EXTRA_ACCOUNT_LABEL) ?: "Account"

            if (installmentId <= 0L) return

            val app = context.applicationContext as MyApplication
            val repositoryLoan = app.appComponent.repositoryLoan()

            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val result = repositoryLoan.markInstallmentPaid(
                        installmentId = installmentId,
                        paidDate = System.currentTimeMillis(),
                        createTransaction = true
                    )

                    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    if (result.isSuccess) {
                        val formattedAmount = "₹%.2f".format(amount / 100.0)
                        val updatedNotification = NotificationBuilderWrapper.bigTextStyleBuilder(
                            context,
                            NotificationBuilderWrapper.CHANNEL_ID_LOANS,
                            "✓ $loanLabel EMI Paid",
                            "$formattedAmount debited from $accountLabel."
                        ).setAutoCancel(true)

                        notificationManager.notify(notificationId, updatedNotification.build())
                    } else {
                        Timber.e(result.exceptionOrNull(), "Failed to mark installment paid from notification")
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error processing loan notification payment")
                } finally {
                    pendingResult.finish()
                }
            }
        } else if (action == ACTION_PAY_COMMITMENT) {
            val paymentId = intent.getLongExtra(EXTRA_PAYMENT_ID, 0L)
            val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
            val label = intent.getStringExtra(EXTRA_LOAN_LABEL) ?: "Commitment"
            val amount = intent.getLongExtra(EXTRA_AMOUNT, 0L)
            val accountLabel = intent.getStringExtra(EXTRA_ACCOUNT_LABEL) ?: "Account"

            if (paymentId <= 0L) return

            val app = context.applicationContext as MyApplication
            val repositoryCommitment = app.appComponent.repositoryCommitment()

            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val result = repositoryCommitment.markCommitmentPaid(
                        paymentId = paymentId,
                        paidDate = System.currentTimeMillis(),
                        createTransaction = true
                    )

                    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    if (result.isSuccess) {
                        val formattedAmount = "₹%.2f".format(amount / 100.0)
                        val updatedNotification = NotificationBuilderWrapper.bigTextStyleBuilder(
                            context,
                            NotificationBuilderWrapper.CHANNEL_ID_LOANS,
                            "✓ $label Paid",
                            "$formattedAmount debited from $accountLabel."
                        ).setAutoCancel(true)

                        notificationManager.notify(notificationId, updatedNotification.build())
                    } else {
                        Timber.e(result.exceptionOrNull(), "Failed to mark commitment paid from notification")
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error processing commitment notification payment")
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    companion object {
        const val ACTION_PAY_EMI = "org.totschnig.myexpenses.ACTION_PAY_EMI"
        const val ACTION_PAY_COMMITMENT = "org.totschnig.myexpenses.ACTION_PAY_COMMITMENT"
        const val EXTRA_INSTALLMENT_ID = "EXTRA_INSTALLMENT_ID"
        const val EXTRA_PAYMENT_ID = "EXTRA_PAYMENT_ID"
        const val EXTRA_NOTIFICATION_ID = "EXTRA_NOTIFICATION_ID"
        const val EXTRA_LOAN_LABEL = "EXTRA_LOAN_LABEL"
        const val EXTRA_AMOUNT = "EXTRA_AMOUNT"
        const val EXTRA_ACCOUNT_LABEL = "EXTRA_ACCOUNT_LABEL"
    }
}
