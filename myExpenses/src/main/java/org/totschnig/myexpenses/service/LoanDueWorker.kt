package org.totschnig.myexpenses.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.totschnig.myexpenses.MyApplication
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.activity.LoansActivity
import org.totschnig.myexpenses.util.NotificationBuilderWrapper
import timber.log.Timber
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

class LoanDueWorker(
    context: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(context, workerParameters) {

    override suspend fun doWork(): Result {
        val app = applicationContext as MyApplication
        val repositoryLoan = app.appComponent.repositoryLoan()
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // End of today in millis
        val todayEnd = LocalDate.now()
            .atTime(LocalTime.MAX)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        try {
            val dueList = repositoryLoan.getDueInstallments(todayEnd)
            Timber.i("LoanDueWorker checked dues: found ${dueList.size} installments")

            for (item in dueList) {
                val installment = item.installment
                val loan = item.loan
                val accountLabel = item.accountLabel ?: "Linked Bank Account"
                val notificationId = NOTIFICATION_LOAN_BASE + installment.id.toInt()
                val formattedAmount = "₹%.2f".format(installment.totalAmount / 100.0)

                if (loan.autoDebit && loan.linkedAccountId != null && loan.linkedAccountId > 0) {
                    val result = repositoryLoan.markInstallmentPaid(
                        installmentId = installment.id,
                        paidDate = System.currentTimeMillis(),
                        createTransaction = true
                    )
                    if (result.isSuccess) {
                        val openAppIntent = Intent(applicationContext, LoansActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            putExtra(LoansActivity.EXTRA_INITIAL_TAB, LoansActivity.TAB_LOANS)
                        }
                        val contentPendingIntent = PendingIntent.getActivity(
                            applicationContext,
                            notificationId,
                            openAppIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )

                        val notification = NotificationBuilderWrapper.bigTextStyleBuilder(
                            applicationContext,
                            NotificationBuilderWrapper.CHANNEL_ID_LOANS,
                            "${applicationContext.getString(R.string.emi_auto_debited)}: ${loan.label}",
                            "$formattedAmount was automatically debited from $accountLabel for EMI #${installment.installmentNumber}."
                        ).setContentIntent(contentPendingIntent)
                         .setAutoCancel(true)

                        notificationManager.notify(notificationId, notification.build())
                    }
                } else {
                    val openAppIntent = Intent(applicationContext, LoansActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        putExtra(LoansActivity.EXTRA_INITIAL_TAB, LoansActivity.TAB_LOANS)
                    }
                    val contentPendingIntent = PendingIntent.getActivity(
                        applicationContext,
                        notificationId,
                        openAppIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )

                    val payIntent = Intent(applicationContext, LoanNotificationReceiver::class.java).apply {
                        action = LoanNotificationReceiver.ACTION_PAY_EMI
                        putExtra(LoanNotificationReceiver.EXTRA_INSTALLMENT_ID, installment.id)
                        putExtra(LoanNotificationReceiver.EXTRA_NOTIFICATION_ID, notificationId)
                        putExtra(LoanNotificationReceiver.EXTRA_LOAN_LABEL, loan.label)
                        putExtra(LoanNotificationReceiver.EXTRA_AMOUNT, installment.totalAmount)
                        putExtra(LoanNotificationReceiver.EXTRA_ACCOUNT_LABEL, accountLabel)
                    }
                    val payPendingIntent = PendingIntent.getBroadcast(
                        applicationContext,
                        notificationId,
                        payIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )

                    val notificationBuilder = NotificationBuilderWrapper.bigTextStyleBuilder(
                        applicationContext,
                        NotificationBuilderWrapper.CHANNEL_ID_LOANS,
                        "${applicationContext.getString(R.string.emi_due_today)}: ${loan.label}",
                        "EMI #${installment.installmentNumber} of $formattedAmount is due today from $accountLabel."
                    ).setContentIntent(contentPendingIntent)
                     .addAction(0, applicationContext.getString(R.string.pay_now), payPendingIntent)
                     .setAutoCancel(true)

                    notificationManager.notify(notificationId, notificationBuilder.build())
                }
            }

            val repositoryCommitment = app.appComponent.repositoryCommitment()
            val dueCommitments = repositoryCommitment.getDueCommitments(todayEnd)
            Timber.i("LoanDueWorker checked commitments: found ${dueCommitments.size} items")

            for (item in dueCommitments) {
                val payment = item.payment
                val accountLabel = item.accountLabel ?: "Linked Bank Account"
                val notificationId = NOTIFICATION_COMMITMENT_BASE + payment.id.toInt()
                val formattedAmount = "₹%.2f".format(item.amount / 100.0)

                if (item.autoDebit && item.linkedAccountId != null && item.linkedAccountId > 0) {
                    val result = repositoryCommitment.markCommitmentPaid(
                        paymentId = payment.id,
                        paidDate = System.currentTimeMillis(),
                        createTransaction = true
                    )
                    if (result.isSuccess) {
                        val openAppIntent = Intent(applicationContext, LoansActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
                        val contentPendingIntent = PendingIntent.getActivity(
                            applicationContext,
                            notificationId,
                            openAppIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )

                        val notification = NotificationBuilderWrapper.bigTextStyleBuilder(
                            applicationContext,
                            NotificationBuilderWrapper.CHANNEL_ID_LOANS,
                            "Auto-debited: ${item.title}",
                            "$formattedAmount was automatically debited from $accountLabel."
                        ).setContentIntent(contentPendingIntent)
                         .setAutoCancel(true)

                        notificationManager.notify(notificationId, notification.build())
                    }
                } else {
                    val openAppIntent = Intent(applicationContext, LoansActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    val contentPendingIntent = PendingIntent.getActivity(
                        applicationContext,
                        notificationId,
                        openAppIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )

                    val payIntent = Intent(applicationContext, LoanNotificationReceiver::class.java).apply {
                        action = LoanNotificationReceiver.ACTION_PAY_COMMITMENT
                        putExtra(LoanNotificationReceiver.EXTRA_PAYMENT_ID, payment.id)
                        putExtra(LoanNotificationReceiver.EXTRA_NOTIFICATION_ID, notificationId)
                        putExtra(LoanNotificationReceiver.EXTRA_LOAN_LABEL, item.title)
                        putExtra(LoanNotificationReceiver.EXTRA_AMOUNT, item.amount)
                        putExtra(LoanNotificationReceiver.EXTRA_ACCOUNT_LABEL, accountLabel)
                    }
                    val payPendingIntent = PendingIntent.getBroadcast(
                        applicationContext,
                        notificationId,
                        payIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )

                    val notificationBuilder = NotificationBuilderWrapper.bigTextStyleBuilder(
                        applicationContext,
                        NotificationBuilderWrapper.CHANNEL_ID_LOANS,
                        "Payment Due: ${item.title}",
                        "$formattedAmount is due today from $accountLabel."
                    ).setContentIntent(contentPendingIntent)
                     .addAction(0, applicationContext.getString(R.string.pay_now), payPendingIntent)
                     .setAutoCancel(true)

                    notificationManager.notify(notificationId, notificationBuilder.build())
                }
            }

            return Result.success()
        } catch (e: Exception) {
            Timber.e(e, "Error executing LoanDueWorker")
            return Result.failure()
        }
    }

    companion object {
        private const val WORK_NAME = "LoanDueWorker"
        private const val NOTIFICATION_LOAN_BASE = 80000
        private const val NOTIFICATION_COMMITMENT_BASE = 90000

        fun enqueue(context: Context) {
            val workManager = WorkManager.getInstance(context)
            val periodicWork = PeriodicWorkRequestBuilder<LoanDueWorker>(12, TimeUnit.HOURS)
                .build()
            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicWork
            )

            val oneTimeWork = OneTimeWorkRequestBuilder<LoanDueWorker>().build()
            workManager.enqueueUniqueWork(
                WORK_NAME + "_immediate",
                ExistingWorkPolicy.REPLACE,
                oneTimeWork
            )
        }
    }
}
