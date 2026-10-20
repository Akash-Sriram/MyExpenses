package org.totschnig.myexpenses.activity

import android.annotation.SuppressLint
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.Menu
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.Preference
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import org.totschnig.myexpenses.MyApplication
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.dialog.ConfirmationDialogFragment
import org.totschnig.myexpenses.dialog.DialogUtils
import org.totschnig.myexpenses.dialog.SortUtilityDialogFragment
import org.totschnig.myexpenses.dialog.progress.NewProgressDialogFragment
import org.totschnig.myexpenses.feature.Feature
import org.totschnig.myexpenses.fragment.TwoPanePreference
import org.totschnig.myexpenses.fragment.TwoPanePreference.Companion.KEY_INITIAL_SCREEN
import org.totschnig.myexpenses.fragment.preferences.BasePreferenceFragment
import org.totschnig.myexpenses.fragment.preferences.PreferenceDataFragment
import org.totschnig.myexpenses.fragment.preferences.PreferencesAdvancedFragment
import org.totschnig.myexpenses.fragment.preferences.PreferencesBackupRestoreFragment
import org.totschnig.myexpenses.fragment.preferences.PreferencesBackupRestoreFragment.Companion.KEY_CHECKED_FILES
import org.totschnig.myexpenses.fragment.preferences.PreferencesOcrFragment
import org.totschnig.myexpenses.injector
import org.totschnig.myexpenses.model.ContribFeature
import org.totschnig.myexpenses.preference.PrefKey
import org.totschnig.myexpenses.provider.TransactionProvider.Companion.ACCOUNTS_URI
import org.totschnig.myexpenses.provider.TransactionProvider.Companion.DYNAMIC_CURRENCIES_URI
import org.totschnig.myexpenses.provider.TransactionProvider.Companion.TRANSACTIONS_URI
import org.totschnig.myexpenses.service.AutoBackupWorker
import org.totschnig.myexpenses.sync.GenericAccountService
import org.totschnig.myexpenses.export.pdf.FontType
import org.totschnig.myexpenses.util.PermissionHelper
import org.totschnig.myexpenses.util.TextUtils
import org.totschnig.myexpenses.util.config.Configurator
import org.totschnig.myexpenses.util.config.get
import org.totschnig.myexpenses.util.crashreporting.CrashHandler
import org.totschnig.myexpenses.util.ui.setNightMode
import org.totschnig.myexpenses.viewmodel.ModalProgressViewModel
import org.totschnig.myexpenses.viewmodel.PriceCalculationViewModel
import org.totschnig.myexpenses.viewmodel.SettingsViewModel
import org.totschnig.myexpenses.viewmodel.SyncViewModel
import org.totschnig.myexpenses.widget.AccountWidget
import org.totschnig.myexpenses.widget.BudgetWidget
import org.totschnig.myexpenses.widget.TemplateWidget
import org.totschnig.myexpenses.widget.WIDGET_CONTEXT_CHANGED
import org.totschnig.myexpenses.widget.updateWidgets
import timber.log.Timber
import java.io.Serializable

private const val KEY_RESULT_CODE = "key_result_code"

class PreferenceActivity : SyncBackendSetupActivity(), ContribIFace,
    SortUtilityDialogFragment.OnConfirmListener {

    var resultCode: Int = RESULT_OK

    val viewModel: SettingsViewModel?
        get() = twoPanePreference.getDetailFragment<BasePreferenceFragment>()
            ?.viewModel

    private val progressViewModel: ModalProgressViewModel by viewModels()

    private val priceCalculationViewModel: PriceCalculationViewModel by viewModels()

    private val confirmCredentialsProtectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                twoPanePreference.startPerformProtection()
                requireApplication().unlock()
            }
        }

    private var initialPrefToShow: String? = null

    val calledFromSystemSettings
        get() = intent.action == Intent.ACTION_APPLICATION_PREFERENCES

    override fun onCreate(savedInstanceState: Bundle?) {
        if (savedInstanceState != null) {
            resultCode = savedInstanceState.getInt(KEY_RESULT_CODE, RESULT_OK)
        }
        super.onCreate(savedInstanceState)
        injector.inject(priceCalculationViewModel)
        setupWithFragment(savedInstanceState == null, false) {
            TwoPanePreference.newInstance(intent.getStringExtra(KEY_INITIAL_SCREEN))
        }
        setupToolbar()
        title = getString(R.string.settings_label)
        if (calledFromSystemSettings) {
            // Get the Application instance and call the method
            (applicationContext as? MyApplication)?.signalInitialLaunchForSystemPreferences()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_RESULT_CODE, resultCode)
    }

    override fun injectDependencies() {
        injector.inject(this)
    }

    override fun setTitle(title: CharSequence?) {
        supportActionBar!!.title = title
    }

    override fun onCreateOptionsMenu(menu: Menu) = false

    val twoPanePreference: TwoPanePreference
        get() = supportFragmentManager.findFragmentById(R.id.fragment_container) as TwoPanePreference

    override fun doHome() {
        if (!twoPanePreference.doHome()) {
            setResult(resultCode)
            finish()
        }
    }

    override fun dispatchCommand(command: Int, tag: Any?) =
        if (super.dispatchCommand(command, tag)) true
        else when (command) {

            R.id.CHANGE_COMMAND -> {
                val currencyCode = tag as String
                supportFragmentManager.beginTransaction()
                    .add(
                        NewProgressDialogFragment.newInstance(
                            getString(R.string.pref_home_currency_title) + ": " + currencyContext[currencyCode].description
                        ).apply { isCancelable = false },
                        PROGRESS_TAG
                    )
                    .commitNow()
                lifecycleScope.launch {
                    progressViewModel.appendToMessage(
                        TextUtils.concatResStrings(
                            this@PreferenceActivity,
                            ": ",
                            R.string.progress_recalculating,
                            R.string.price_history
                        )
                    )
                    val updatedPrices = priceCalculationViewModel.reCalculatePrices(currencyCode)
                    progressViewModel.appendToMessage(updatedPrices.toString())
                    progressViewModel.appendToMessage(
                        TextUtils.concatResStrings(
                            this@PreferenceActivity,
                            ": ",
                            R.string.progress_recalculating,
                            R.string.equivalent_amount_plural
                        )
                    )
                    val updatedTransactions =
                        priceCalculationViewModel.reCalculateEquivalentAmounts(currencyCode)
                    progressViewModel.appendToMessage(updatedTransactions.let { (it.first + it.second) }
                        .toString())
                    val dataFragment: PreferenceDataFragment? =
                        twoPanePreference.getDetailFragment()
                    if (dataFragment != null) {
                        dataFragment.updateHomeCurrency(currencyCode)
                    } else {
                        prefHandler.putString(PrefKey.HOME_CURRENCY, currencyCode)
                    }
                    requireApplication().invalidateHomeCurrency()
                    contentResolver.notifyChange(DYNAMIC_CURRENCIES_URI, null)

                    progressViewModel.appendToMessage(getString(R.string.done_label))
                    progressViewModel.onTaskCompleted(emptyList())
                }
                true
            }

            R.id.DELETE_CALENDAR_COMMAND -> {
                //noinspection MissingPermission
                viewModel?.deleteLocalCalendar()?.observe(this) {
                    when (it) {
                        1 -> twoPanePreference.getDetailFragment<PreferencesAdvancedFragment>()
                            ?.configureDeleteCalendarPreference(false)

                        else -> {
                            val message = if (it == 0) "Deletion of local calendar failed" else
                                "PANIC: DeleteLocalCalendar returned $it"
                            CrashHandler.report(Exception(message))
                            showSnackBar(message)
                        }
                    }
                }
                true
            }

            else -> false
        }

    private fun getKey(prefKey: PrefKey) = prefHandler.getKey(prefKey)

    private fun updateAllWidgets() {
        updateWidgetsForClass(AccountWidget::class.java)
        updateWidgetsForClass(TemplateWidget::class.java)
    }

    private fun updateWidgetsForClass(provider: Class<out AppWidgetProvider>) {
        updateWidgets(this, provider, WIDGET_CONTEXT_CHANGED)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        super.onSharedPreferenceChanged(sharedPreferences, key)
        when (key) {
            getKey(PrefKey.UI_THEME) -> {
                setNightMode(prefHandler, this)
                updateAllWidgets()
            }

            getKey(PrefKey.UI_FONT_SIZE) -> {
                updateAllWidgets()
                recreate()
            }

            getKey(PrefKey.AUTO_BACKUP) -> {
                val autoBackup = sharedPreferences.getBoolean(key, false)
                if (autoBackup &&
                    (prefHandler.getBoolean(PrefKey.PROTECTION_LEGACY, false) ||
                            prefHandler.getBoolean(PrefKey.PROTECTION_DEVICE_LOCK_SCREEN, false)
                            )
                ) {
                    showUnencryptedBackupWarning()
                }
                if (autoBackup) {
                    checkNotificationPermissionForAutoBackup()
                }
                AutoBackupWorker.enqueueOrCancel(this, prefHandler)
            }

            getKey(PrefKey.AUTO_BACKUP_TIME) -> AutoBackupWorker.enqueueOrCancel(this, prefHandler)

            getKey(PrefKey.TRACKING) ->
                tracker.setEnabled(sharedPreferences.getBoolean(key, false))

            getKey(PrefKey.CRASHREPORT_USEREMAIL) ->
                crashHandler.setUserEmail(sharedPreferences.getString(key, null))

            getKey(PrefKey.CRASHREPORT_ENABLED) -> {
                crashHandler.setEnabled(sharedPreferences.getBoolean(key, false))
                showSnackBar(R.string.app_restart_required)
            }

            getKey(PrefKey.UI_MAIN_SCREEN_VERSION) -> {
                showSnackBar(R.string.app_restart_required)
            }

            getKey(PrefKey.OCR_ENGINE) -> checkOcrFeature()

            getKey(PrefKey.TESSERACT_LANGUAGE) -> checkTessDataDownload()

            getKey(PrefKey.MLKIT_SCRIPT) -> checkOcrFeature()

            getKey(PrefKey.SYNC_FREQUCENCY) ->
                for (account in GenericAccountService.getAccounts(this)) {
                    GenericAccountService.configureAutomaticAndPeriod(account, prefHandler)
                }

            getKey(PrefKey.PROTECTION_LEGACY), getKey(PrefKey.PROTECTION_DEVICE_LOCK_SCREEN) -> {
                if (sharedPreferences.getBoolean(key, false)) {
                    if (prefHandler.getBoolean(PrefKey.AUTO_BACKUP, false)) {
                        showUnencryptedBackupWarning()
                    }
                }
                updateAllWidgets()
            }

            getKey(PrefKey.CUSTOM_DECIMAL_FORMAT) -> currencyFormatter.invalidate(contentResolver)

            getKey(PrefKey.PROTECTION_ENABLE_ACCOUNT_WIDGET) ->
                updateWidgetsForClass(AccountWidget::class.java)

            getKey(PrefKey.PROTECTION_ENABLE_TEMPLATE_WIDGET) ->
                updateWidgetsForClass(TemplateWidget::class.java)

            getKey(PrefKey.PROTECTION_ENABLE_BUDGET_WIDGET) ->
                updateWidgetsForClass(BudgetWidget::class.java)

            getKey(PrefKey.PLANNER_EXECUTION_TIME) -> enqueuePlanner(false)

            getKey(PrefKey.UNMAPPED_TRANSACTION_AS_TRANSFER) -> {
                contentResolver.notifyChange(TRANSACTIONS_URI, null)
                contentResolver.notifyChange(ACCOUNTS_URI, null)
            }

            getKey(PrefKey.PRINT_FONT_SIZE) -> FontType.clearCache()
        }
    }

    /**
     * checks and requests OCR + engine + (script for mlkit)
     */
    private fun checkOcrFeature() {
        if (!featureManager.isFeatureInstalled(Feature.OCR, this)) {
            featureManager.requestFeature(Feature.OCR, this)
        }
    }

    override fun onFeatureAvailable(feature: Feature) {
        super.onFeatureAvailable(feature)
        when (feature) {
            Feature.OCR, Feature.MLKIT, Feature.TESSERACT ->
                twoPanePreference.getDetailFragment<PreferencesOcrFragment>()
                    ?.configureOcrEnginePrefs()

            else -> {}
        }
    }

    private fun showUnencryptedBackupWarning() {
        if (prefHandler.getString(PrefKey.EXPORT_PASSWORD, null) == null) showMessage(
            unencryptedBackupWarning
        )
    }

    override fun onPositive(args: Bundle, checked: Boolean) {
        super.onPositive(args, checked)
        when (args.getInt(ConfirmationDialogFragment.KEY_COMMAND_POSITIVE)) {
            R.id.DELETE_FILES_COMMAND -> twoPanePreference.headerFragment.viewModel
                .deleteAppFiles(args.getStringArray(KEY_CHECKED_FILES)!!)
                .observe(this) {
                    showSnackBar(resources.getQuantityString(R.plurals.delete_success, it, it))
                }
        }
    }

    override fun onPermissionsGranted(requestCode: Int, perms: List<String>) {
        super.onPermissionsGranted(requestCode, perms)
        if (requestCode == PermissionHelper.PERMISSIONS_REQUEST_WRITE_CALENDAR) {
            initialPrefToShow = prefHandler.getKey(PrefKey.PLANNER_CALENDAR_ID)
        }
    }

    override fun onReceiveSyncAccountData(data: SyncViewModel.SyncAccountData) {
        twoPanePreference.getDetailFragment<PreferencesBackupRestoreFragment>()
            ?.loadSyncAccountData(data.accountName)
    }

    override fun onResumeFragments() {
        super.onResumeFragments()
        initialPrefToShow?.let {
            twoPanePreference.getDetailFragment<BasePreferenceFragment>()?.showPreference(it)
            initialPrefToShow = null
        }
    }

    override fun contribFeatureCalled(feature: ContribFeature, tag: Serializable?) {
        when (feature) {
            ContribFeature.CSV_IMPORT -> {
                startActivity(Intent(this, CsvImportActivity::class.java))
            }

            else -> super.contribFeatureCalled(feature, tag)
        }
    }

    fun protectionCheck(pref: Preference) =
        if (pref.key == prefHandler.getKey(PrefKey.CATEGORY_SECURITY) && prefHandler.isProtected) {
            confirmCredentials(
                launcher = confirmCredentialsProtectionLauncher,
                legacyUnlockCallback = { twoPanePreference.startPerformProtection() },
                shouldHideWindow = false,
                shouldLock = false
            )
            false
        } else true

    override val createAccountTaskShouldQueryRemoteAccounts = false
    override val offerEncryption = false

    override fun onSortOrderConfirmed(sortedIds: LongArray) {
        viewModel?.sortAccounts(sortedIds)
    }

    companion object {
        fun getIntent(context: Context, initialScreen: String? = null) =
            Intent(context, PreferenceActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(KEY_INITIAL_SCREEN, initialScreen)
    }
}