package org.totschnig.myexpenses.activity

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import org.totschnig.myexpenses.compose.AppTheme
import org.totschnig.myexpenses.compose.insights.InsightsScreen
import org.totschnig.myexpenses.injector
import org.totschnig.myexpenses.viewmodel.InsightsViewModel

class InsightsActivity : ProtectedFragmentActivity() {

    private val viewModel: InsightsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        injector.inject(viewModel)
        setContent {
            AppTheme {
                InsightsScreen(
                    viewModel = viewModel,
                    onBack = { finish() }
                )
            }
        }
    }
}
