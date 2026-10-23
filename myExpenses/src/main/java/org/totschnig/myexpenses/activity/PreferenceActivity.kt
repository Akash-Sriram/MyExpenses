package org.totschnig.myexpenses.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import org.totschnig.myexpenses.MyApplication
import org.totschnig.myexpenses.compose.AppTheme
import org.totschnig.myexpenses.compose.settings.SettingsScreen
import org.totschnig.myexpenses.injector
import org.totschnig.myexpenses.viewmodel.SettingsViewModel2

class PreferenceActivity : SyncBackendSetupActivity() {

    private val viewModel: SettingsViewModel2 by viewModels()

    val calledFromSystemSettings
        get() = intent.action == Intent.ACTION_APPLICATION_PREFERENCES

    override fun injectDependencies() {
        injector.inject(this)
    }

    val twoPanePreference: org.totschnig.myexpenses.fragment.TwoPanePreference?
        get() = null

    fun protectionCheck(pref: androidx.preference.Preference): Boolean = true

    override fun onReceiveSyncAccountData(data: org.totschnig.myexpenses.viewmodel.SyncViewModel.SyncAccountData) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (calledFromSystemSettings) {
            (applicationContext as? MyApplication)?.signalInitialLaunchForSystemPreferences()
        }
        setContent {
            AppTheme {
                SettingsScreen(
                    viewModel = viewModel,
                    onBack = { finish() }
                )
            }
        }
    }

    companion object {
        const val KEY_INITIAL_SCREEN = "initial_screen"

        fun getIntent(context: Context, initialScreen: String? = null) =
            Intent(context, PreferenceActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(KEY_INITIAL_SCREEN, initialScreen)
    }
}