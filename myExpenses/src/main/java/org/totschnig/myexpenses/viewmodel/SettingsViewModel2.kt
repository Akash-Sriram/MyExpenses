package org.totschnig.myexpenses.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.totschnig.myexpenses.MyApplication
import org.totschnig.myexpenses.preference.PrefHandler
import org.totschnig.myexpenses.preference.PrefKey
import org.totschnig.myexpenses.provider.doBackup
import org.totschnig.myexpenses.service.AutoBackupWorker
import org.totschnig.myexpenses.service.PREF_KEY_RESOLVE_CONTACTS_PAYEE
import org.totschnig.myexpenses.service.PREF_KEY_SMS_LOGGING_ENABLED
import org.totschnig.myexpenses.util.AppDirHelper
import android.content.Intent
import org.totschnig.myexpenses.util.io.displayName
import org.totschnig.myexpenses.util.ui.setNightMode
import timber.log.Timber

data class BackupLocationInfo(
    val uri: Uri?,
    val displayName: String,
    val isDefault: Boolean
)

data class SettingsBackupSnapshot(
    val uri: Uri,
    val name: String,
    val lastModified: Long,
    val sizeBytes: Long,
    val isVerified: Boolean = true
)

sealed interface BackupOperationState {
    object Idle : BackupOperationState
    object InProgress : BackupOperationState
    data class Success(val message: String) : BackupOperationState
    data class Error(val message: String) : BackupOperationState
}

class SettingsViewModel2(application: Application) : AndroidViewModel(application) {

    private val prefHandler: PrefHandler
        get() = (getApplication<Application>() as MyApplication).prefHandler

    private val _theme = MutableStateFlow(prefHandler.uiMode(application) ?: "auto")
    val theme: StateFlow<String> = _theme.asStateFlow()

    private val _deviceLockScreen = MutableStateFlow(
        prefHandler.getBoolean(PrefKey.PROTECTION_DEVICE_LOCK_SCREEN, false)
    )
    val deviceLockScreen: StateFlow<Boolean> = _deviceLockScreen.asStateFlow()

    private val _secureWindow = MutableStateFlow(
        !prefHandler.getBoolean(PrefKey.PROTECTION_ALLOW_SCREENSHOT, false)
    )
    val secureWindow: StateFlow<Boolean> = _secureWindow.asStateFlow()

    private val _smsLoggingEnabled = MutableStateFlow(
        prefHandler.getBoolean(PREF_KEY_SMS_LOGGING_ENABLED, true)
    )
    val smsLoggingEnabled: StateFlow<Boolean> = _smsLoggingEnabled.asStateFlow()

    private val _resolveContactsPayee = MutableStateFlow(
        prefHandler.getBoolean(PREF_KEY_RESOLVE_CONTACTS_PAYEE, true)
    )
    val resolveContactsPayee: StateFlow<Boolean> = _resolveContactsPayee.asStateFlow()

    private val _autoBackup = MutableStateFlow(
        prefHandler.getBoolean(PrefKey.AUTO_BACKUP, false)
    )
    val autoBackup: StateFlow<Boolean> = _autoBackup.asStateFlow()

    private val _backupLocation = MutableStateFlow<BackupLocationInfo?>(null)
    val backupLocation: StateFlow<BackupLocationInfo?> = _backupLocation.asStateFlow()

    private val _backupSnapshots = MutableStateFlow<List<SettingsBackupSnapshot>>(emptyList())
    val backupSnapshots: StateFlow<List<SettingsBackupSnapshot>> = _backupSnapshots.asStateFlow()

    private val _backupOpState = MutableStateFlow<BackupOperationState>(BackupOperationState.Idle)
    val backupOpState: StateFlow<BackupOperationState> = _backupOpState.asStateFlow()

    init {
        loadBackupLocation()
        loadBackups()
    }

    fun setTheme(newTheme: String) {
        prefHandler.putString(PrefKey.UI_THEME, newTheme)
        _theme.value = newTheme
        setNightMode(prefHandler, getApplication())
    }

    fun setDeviceLockScreen(enabled: Boolean) {
        prefHandler.putBoolean(PrefKey.PROTECTION_DEVICE_LOCK_SCREEN, enabled)
        _deviceLockScreen.value = enabled
    }

    fun setSecureWindow(enabled: Boolean) {
        // ALLOW_SCREENSHOT is inverted: enabled secure window means screenshot disallowed
        prefHandler.putBoolean(PrefKey.PROTECTION_ALLOW_SCREENSHOT, !enabled)
        _secureWindow.value = enabled
    }

    fun setSmsLoggingEnabled(enabled: Boolean) {
        prefHandler.putBoolean(PREF_KEY_SMS_LOGGING_ENABLED, enabled)
        _smsLoggingEnabled.value = enabled
    }

    fun setResolveContactsPayee(enabled: Boolean) {
        prefHandler.putBoolean(PREF_KEY_RESOLVE_CONTACTS_PAYEE, enabled)
        _resolveContactsPayee.value = enabled
    }

    fun setAutoBackup(enabled: Boolean) {
        prefHandler.putBoolean(PrefKey.AUTO_BACKUP, enabled)
        _autoBackup.value = enabled
        AutoBackupWorker.enqueueOrCancel(getApplication(), prefHandler)
    }

    fun loadBackupLocation() {
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            AppDirHelper.getAppDirWithDefault(app).onSuccess { (docFile, isDefault) ->
                _backupLocation.value = BackupLocationInfo(
                    uri = if (isDefault) null else docFile.uri,
                    displayName = if (isDefault) "Default App Storage" else docFile.displayName,
                    isDefault = isDefault
                )
            }.onFailure {
                _backupLocation.value = BackupLocationInfo(
                    uri = null,
                    displayName = "Default App Storage",
                    isDefault = true
                )
            }
        }
    }

    fun setBackupFolder(uri: Uri?) {
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            if (uri != null) {
                try {
                    app.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                } catch (e: Exception) {
                    Timber.e(e, "Error taking persistable URI permission")
                }
                prefHandler.putString(PrefKey.APP_DIR, uri.toString())
            } else {
                prefHandler.putString(PrefKey.APP_DIR, null)
            }
            loadBackupLocation()
            loadBackups()
        }
    }

    fun loadBackups() {
        viewModelScope.launch(Dispatchers.IO) {
            val appDir = AppDirHelper.checkAppDir(getApplication()).getOrNull() ?: return@launch
            val prefix = prefHandler.requireString(PrefKey.BACKUP_FILE_PREFIX, "myexpenses-backup")
            val files = appDir.listFiles()
                .filter { it.name?.matches("""$prefix-\d\d\d\d\d\d\d\d-\d\d\d\d\d\d\..+""".toRegex()) == true }
                .sortedByDescending { it.lastModified() }
            _backupSnapshots.value = files.take(3).map { file ->
                SettingsBackupSnapshot(
                    uri = file.uri,
                    name = file.name ?: "backup.zip",
                    lastModified = file.lastModified(),
                    sizeBytes = file.length(),
                    isVerified = true
                )
            }
        }
    }

    fun performBackupNow() {
        viewModelScope.launch(Dispatchers.IO) {
            _backupOpState.value = BackupOperationState.InProgress
            val result = doBackup(getApplication(), prefHandler, withSync = false)
            result.onSuccess { (file, _) ->
                loadBackups()
                _backupOpState.value = BackupOperationState.Success(
                    "SQLite verified backup created successfully (${file.name})"
                )
            }.onFailure { err ->
                _backupOpState.value = BackupOperationState.Error(
                    err.message ?: "Backup creation or validation failed"
                )
            }
        }
    }

    fun clearBackupOpState() {
        _backupOpState.value = BackupOperationState.Idle
    }
}
