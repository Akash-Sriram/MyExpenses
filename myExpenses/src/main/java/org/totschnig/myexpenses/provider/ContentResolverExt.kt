package org.totschnig.myexpenses.provider

import android.content.ContentResolver
import org.totschnig.myexpenses.provider.TransactionProvider.Companion.ACCOUNTS_URI

fun ContentResolver.triggerAccountListRefresh() {
    notifyChange(ACCOUNTS_URI, null, false)
}