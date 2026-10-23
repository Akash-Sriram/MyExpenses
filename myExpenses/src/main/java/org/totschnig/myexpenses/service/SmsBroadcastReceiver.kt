package org.totschnig.myexpenses.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import org.totschnig.myexpenses.MyApplication
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.activity.ExpenseEdit
import org.totschnig.myexpenses.contract.TransactionsContract
import org.totschnig.myexpenses.util.CHANNEL_ID_SMS
import org.totschnig.myexpenses.util.contacts.ContactPayeeResolver
import org.totschnig.myexpenses.util.sms.IndianSmsParser
import timber.log.Timber
import java.util.Locale

const val PREF_KEY_SMS_LOGGING_ENABLED = "pref_sms_logging_enabled"
const val PREF_KEY_RESOLVE_CONTACTS_PAYEE = "pref_resolve_contacts_payee"

class SmsBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (Telephony.Sms.Intents.SMS_RECEIVED_ACTION != intent.action) return

        val myApp = context.applicationContext as? MyApplication
        val prefHandler = myApp?.prefHandler
        val isSmsLoggingEnabled = prefHandler?.getBoolean(PREF_KEY_SMS_LOGGING_ENABLED, true) ?: true
        if (!isSmsLoggingEnabled) return

        try {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            if (messages.isNullOrEmpty()) return

            val fullBody = messages.joinToString("") { it.displayMessageBody ?: "" }
            val timestamp = messages.firstOrNull()?.timestampMillis ?: System.currentTimeMillis()
            val sender = messages.firstOrNull()?.originatingAddress

            val parsed = IndianSmsParser.parse(fullBody, timestamp, sender) ?: return

            val resolveContacts = prefHandler?.getBoolean(PREF_KEY_RESOLVE_CONTACTS_PAYEE, true) ?: true
            val resolvedContactName = if (resolveContacts) {
                ContactPayeeResolver.resolvePayeeName(context, parsed.upiId)
                    ?: ContactPayeeResolver.resolvePayeeName(context, parsed.payee)
            } else null

            val finalPayee = resolvedContactName
                ?: parsed.payee
                ?: "UPI Payee"

            post1TapNotification(context, parsed, finalPayee)
        } catch (e: Exception) {
            Timber.e(e, "Error processing incoming SMS for 1-tap logging")
        }
    }

    private fun post1TapNotification(
        context: Context,
        parsed: org.totschnig.myexpenses.util.sms.ParsedDebitSms,
        payee: String
    ) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val notificationId = ((System.currentTimeMillis() % 800000) + 100000).toInt()

        val logIntent = Intent(context, ExpenseEdit::class.java).apply {
            action = Intent.ACTION_INSERT
            data = TransactionsContract.Transactions.CONTENT_URI
            // Expense is negative in MyExpenses micros
            putExtra(
                TransactionsContract.Transactions.AMOUNT_MICROS,
                -(parsed.amountRupees * 1_000_000).toLong()
            )
            putExtra(TransactionsContract.Transactions.PAYEE_NAME, payee)
            putExtra(TransactionsContract.Transactions.METHOD_LABEL, "UPI")
            putExtra(TransactionsContract.Transactions.CURRENCY, "INR")
            putExtra(TransactionsContract.Transactions.DATE, parsed.timestamp / 1000)
            if (!parsed.utrNumber.isNullOrEmpty()) {
                putExtra(TransactionsContract.Transactions.REFERENCE_NUMBER, parsed.utrNumber)
            }
            if (!parsed.bankHint.isNullOrEmpty()) {
                putExtra(TransactionsContract.Transactions.ACCOUNT_LABEL, parsed.bankHint)
            }
            putExtra(
                TransactionsContract.Transactions.COMMENT,
                "UPI: ${parsed.upiId ?: "VPA"} • Ref: ${parsed.utrNumber ?: "N/A"}"
            )
            putExtra(MyApplication.KEY_NOTIFICATION_ID, notificationId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            logIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val amountStr = String.format(Locale.ENGLISH, "₹%.2f", parsed.amountRupees)
        val title = "$amountStr debited to $payee"
        val subtitle = "UPI / ${parsed.bankHint ?: "Bank"} • Tap to log expense"

        val notification = Notification.Builder(context, CHANNEL_ID_SMS)
            .setSmallIcon(R.drawable.ic_stat_notification_sigma)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setStyle(
                Notification.BigTextStyle()
                    .bigText("$subtitle\n\nSMS: ${parsed.rawMessage}")
            )
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setWhen(parsed.timestamp)
            .addAction(
                Notification.Action.Builder(
                    null,
                    "[ 1-Tap Log ]",
                    pendingIntent
                ).build()
            )
            .build()

        notificationManager.notify(notificationId, notification)
    }
}
