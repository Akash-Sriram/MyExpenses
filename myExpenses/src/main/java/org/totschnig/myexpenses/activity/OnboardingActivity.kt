package org.totschnig.myexpenses.activity

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import org.totschnig.myexpenses.MyApplication
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.compose.AppTheme
import org.totschnig.myexpenses.compose.onboarding.ModernOnboardingScreen
import org.totschnig.myexpenses.dialog.RestoreFromCloudDialogFragment
import org.totschnig.myexpenses.model.AccountType
import org.totschnig.myexpenses.preference.PrefKey
import org.totschnig.myexpenses.provider.KEY_SYNC_ACCOUNT_NAME
import org.totschnig.myexpenses.service.PREF_KEY_RESOLVE_CONTACTS_PAYEE
import org.totschnig.myexpenses.service.PREF_KEY_SMS_LOGGING_ENABLED
import org.totschnig.myexpenses.sync.json.AccountMetaData
import org.totschnig.myexpenses.util.crashreporting.CrashHandler
import org.totschnig.myexpenses.util.distrib.DistributionHelper.versionNumber
import org.totschnig.myexpenses.util.safeMessage
import org.totschnig.myexpenses.viewmodel.OnBoardingDataViewModel
import org.totschnig.myexpenses.viewmodel.RestoreViewModel.Companion.KEY_BACKUP_FROM_SYNC
import org.totschnig.myexpenses.viewmodel.RestoreViewModel.Companion.KEY_PASSWORD
import org.totschnig.myexpenses.viewmodel.SyncViewModel.SyncAccountData

private const val KEY_ACCOUNT_NAME = "key_account_name"

class OnboardingActivity : SyncBackendSetupActivity() {

    private val viewModel: OnBoardingDataViewModel by viewModels()

    var accountName: String? = null

    override val drawToTopEdge = true
    override val drawToBottomEdge = false

    override fun onCreate(savedInstanceState: Bundle?) {
        if (savedInstanceState == null) {
            try {
                prefHandler.setDefaultValues(this)
            } catch (e: Exception) {
                CrashHandler.report(e)
            }
        } else {
            accountName = savedInstanceState.getString(KEY_ACCOUNT_NAME)
        }
        super.onCreate(savedInstanceState)

        (application as MyApplication).appComponent.inject(viewModel)

        viewModel.accountSave.observe(this) { result ->
            result.onSuccess {
                start()
            }.onFailure {
                CrashHandler.report(it)
                showSnackBar(it.safeMessage)
            }
        }

        setContent {
            AppTheme {
                val accountTypes by viewModel.accountTypes.collectAsState(AccountType.initialAccountTypes)
                ModernOnboardingScreen(
                    availableAccountTypes = accountTypes,
                    onSaveAndFinish = { account, enableSms, enableContacts ->
                        doWithEncryptionCheck {
                            prefHandler.putString(PrefKey.HOME_CURRENCY, "INR")
                            prefHandler.putBoolean(PREF_KEY_SMS_LOGGING_ENABLED, enableSms)
                            prefHandler.putBoolean(PREF_KEY_RESOLVE_CONTACTS_PAYEE, enableContacts)
                            viewModel.saveAccount(account)
                        }
                    },
                    onRestoreBackup = {
                        withResultCallbackLauncher.launch(
                            Intent(this@OnboardingActivity, BackupRestoreActivity::class.java).apply {
                                action = BackupRestoreActivity.ACTION_RESTORE
                            }
                        )
                    }
                )
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_ACCOUNT_NAME, accountName)
    }

    override fun onCreateOptionsMenu(menu: Menu) = false

    fun navigateNext() {}

    fun start() {
        prefHandler.putInt(PrefKey.CURRENT_VERSION, versionNumber)
        prefHandler.putInt(PrefKey.FIRST_INSTALL_VERSION, versionNumber)
        val intent = Intent(this, MyExpensesV2::class.java)
        startActivity(intent)
        finish()
    }

    override val createAccountTaskShouldReturnBackups = true
    override val createAccountTaskShouldQueryLocalAccounts = false

    override fun onReceiveSyncAccountData(data: SyncAccountData) {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                accountName = data.accountName
                if (data.backups.isNotEmpty() || data.remoteAccounts.isNotEmpty()) {
                    if (checkForDuplicateUuids(data.remoteAccounts)) {
                        showSnackBar("Found accounts with duplicate uuids")
                    } else {
                        RestoreFromCloudDialogFragment.newInstance(data.backups, data.remoteAccounts)
                            .show(supportFragmentManager, FRAGMENT_TAG_RESTORE)
                    }
                } else {
                    showSnackBar("Neither backups nor sync accounts found")
                }
            }
        }
    }

    override fun onPostRestoreTask(result: Result<Unit>) {
        super.onPostRestoreTask(result)
        result.onSuccess {
            restartAfterRestore()
        }
    }

    fun setupFromBackup(backup: String?, password: String?) {
        doRestore(Bundle(4).apply {
            putString(KEY_SYNC_ACCOUNT_NAME, accountName)
            putString(KEY_BACKUP_FROM_SYNC, backup)
            putString(KEY_PASSWORD, password)
        })
    }

    fun setupFromSyncAccounts(syncAccounts: List<AccountMetaData>) {
        doWithEncryptionCheck {
            showSnackBarIndefinite(R.string.progress_dialog_fetching_data_from_sync_backend)
            syncViewModel.setupFromSyncAccounts(syncAccounts.map { it.uuid }, accountName!!)
                .observe(this) { result ->
                    dismissSnackBar()
                    result.onSuccess {
                        start()
                    }.onFailure {
                        showDismissibleSnackBar(it.safeMessage)
                    }
                }
        }
    }

    override val snackBarContainerId: Int
        get() = android.R.id.content
}