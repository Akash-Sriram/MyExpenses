package org.totschnig.myexpenses.activity

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import org.totschnig.myexpenses.compose.AppTheme
import org.totschnig.myexpenses.compose.loans.LoansScreen
import org.totschnig.myexpenses.injector
import org.totschnig.myexpenses.viewmodel.LoanViewModel

class LoansActivity : ProtectedFragmentActivity() {

    private val viewModel: LoanViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        injector.inject(viewModel)
        setContent {
            AppTheme {
                LoansScreen(
                    viewModel = viewModel,
                    onClose = { finish() }
                )
            }
        }
    }
}
