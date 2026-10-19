package org.totschnig.myexpenses.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.totschnig.myexpenses.provider.KEY_SYNC_ACCOUNT_NAME
import org.totschnig.myexpenses.sync.GenericAccountService.Companion.getAccount
import org.totschnig.myexpenses.sync.SyncAdapter

class SyncNotificationDismissHandler : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent != null) {
            val accountName = intent.getStringExtra(KEY_SYNC_ACCOUNT_NAME)
            if (accountName != null) {
                val account = getAccount(accountName)
                SyncAdapter.clearNotificationContent(account)
            }
        }
    }
}