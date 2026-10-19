package org.totschnig.myexpenses.compose.commitments

import androidx.compose.runtime.Composable
import org.totschnig.myexpenses.compose.loans.LoansScreen
import org.totschnig.myexpenses.viewmodel.LoanViewModel

@Composable
fun CommitmentsScreen(
    viewModel: LoanViewModel,
    initialTab: Int = 0,
    onClose: () -> Unit
) {
    LoansScreen(
        viewModel = viewModel,
        initialTab = initialTab,
        onClose = onClose
    )
}
