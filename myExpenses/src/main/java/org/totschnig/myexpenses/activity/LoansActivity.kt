package org.totschnig.myexpenses.activity

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import org.totschnig.myexpenses.compose.AppTheme
import org.totschnig.myexpenses.compose.commitments.CommitmentsScreen
import org.totschnig.myexpenses.injector
import org.totschnig.myexpenses.viewmodel.LoanViewModel

class LoansActivity : ProtectedFragmentActivity() {

    private val viewModel: LoanViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initialTab = intent.getIntExtra(EXTRA_INITIAL_TAB, TAB_LOANS)
        injector.inject(viewModel)
        setContent {
            AppTheme {
                CommitmentsScreen(
                    viewModel = viewModel,
                    initialTab = initialTab,
                    onClose = { finish() }
                )
            }
        }
    }

    companion object {
        const val EXTRA_INITIAL_TAB = "EXTRA_INITIAL_TAB"
        const val TAB_LOANS = 0
        const val TAB_CREDIT_CARDS = 1
        const val TAB_PLANS = 2
        const val TAB_TEMPLATES = 3
    }
}
