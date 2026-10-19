package org.totschnig.myexpenses.activity

import android.os.Bundle

class SimpleToastActivity : ProtectedFragmentActivity() {
    companion object {
        const val KEY_MESSAGE = "message_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val message = intent?.getStringExtra(KEY_MESSAGE)
        if (message != null) {
            showMessage(message)
        } else {
            finish()
        }
    }

    override fun reportMissingSnackBarContainer() { /* is expected */ }

    override fun onMessageDialogDismissOrCancel() {
        finish()
    }
}
