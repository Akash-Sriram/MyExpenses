package org.totschnig.myexpenses.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.totschnig.myexpenses.MyApplication
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.preference.PrefKey
import org.totschnig.myexpenses.provider.doBackup
import org.totschnig.myexpenses.util.AppDirHelper
import org.totschnig.myexpenses.util.crypt.EncryptionHelper
import org.totschnig.myexpenses.util.io.displayName
import java.io.IOException

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class BackupViewModel(application: Application) : ContentResolvingAndroidViewModel(application) {

    sealed interface BackupPurgeInfo {
        data class PendingConfirmation(val files: List<DocumentFile>) : BackupPurgeInfo
        data class AlreadyPurged(val results: List<Boolean>) : BackupPurgeInfo
    }

    data class BackupSuccessData(
        val file: DocumentFile,
        val path: String,
        val purgeInfo: BackupPurgeInfo
    )

    sealed class BackupState {
        class Prepared(val appDir: Result<DocumentFile>) : BackupState()
        object Running : BackupState()
        class Completed(val result: Result<BackupSuccessData>) : BackupState()

        class Purged(val result: Result<Int>) : BackupState()
    }

    data class BackupSnapshot(
        val uri: Uri,
        val name: String,
        val lastModified: Long,
        val sizeBytes: Long,
        val isVerified: Boolean = true
    )

    private val _backupsList = MutableStateFlow<List<BackupSnapshot>>(emptyList())
    val backupsList: StateFlow<List<BackupSnapshot>> = _backupsList.asStateFlow()

    fun loadBackups() {
        viewModelScope.launch(coroutineDispatcher) {
            val appDir = AppDirHelper.checkAppDir(getApplication()).getOrNull() ?: return@launch
            val prefix = prefHandler.requireString(PrefKey.BACKUP_FILE_PREFIX, "myexpenses-backup")
            val files = appDir.listFiles()
                .filter { it.name?.matches("""$prefix-\d\d\d\d\d\d\d\d-\d\d\d\d\d\d\..+""".toRegex()) == true }
                .sortedByDescending { it.lastModified() }
            _backupsList.value = files.map { file ->
                BackupSnapshot(
                    uri = file.uri,
                    name = file.name ?: "backup.zip",
                    lastModified = file.lastModified(),
                    sizeBytes = file.length(),
                    isVerified = true
                )
            }
        }
    }

    private val _backupState = MutableStateFlow<BackupState?>(null)
    val backupState: StateFlow<BackupState?> = _backupState.asStateFlow()

    fun doBackup(withSync: Boolean, lenientMode: Boolean) {
        viewModelScope.launch(coroutineDispatcher) {
            _backupState.value = BackupState.Running
            _backupState.value = BackupState.Completed(
                doBackup(
                    getApplication(),
                    prefHandler,
                    withSync,
                    lenientMode
                ).mapCatching { (backupFile, oldBackups) ->
                    loadBackups()
                    val requireConfirmation =
                        prefHandler.getBoolean(PrefKey.PURGE_BACKUP_REQUIRE_CONFIRMATION, false)
                    val extraData = if (requireConfirmation && oldBackups.isNotEmpty())
                        BackupPurgeInfo.PendingConfirmation(oldBackups)
                    else
                        BackupPurgeInfo.AlreadyPurged(oldBackups.map { true })
                    BackupSuccessData(
                        backupFile,
                        backupFile.displayName,
                        extraData
                    )
                }
            )
        }
    }

    suspend fun checkIsEncrypted(uri: Uri): Result<Boolean> = withContext(coroutineContext()) {
        runCatching {
            getApplication<MyApplication>().contentResolver.openInputStream(uri).use {
                if (it == null) throw IOException("Unable to open file $uri")
                EncryptionHelper.isEncrypted(it)
            }
        }
    }

    fun prepare() {
        viewModelScope.launch(coroutineDispatcher) {
            _backupState.value = BackupState.Prepared(AppDirHelper.checkAppDir(getApplication()))
        }
    }

    fun purgeBackups() {
        viewModelScope.launch(coroutineDispatcher) {
            (_backupState.value as? BackupState.Completed)?.let {
                _backupState.value = BackupState.Purged(
                    runCatching {
                        when (val purgeInfo = it.result.getOrThrow().purgeInfo) {
                            is BackupPurgeInfo.PendingConfirmation -> {
                                purgeInfo.files.sumOf { file ->
                                    if (file.delete()) 1 else 0
                                }
                            }
                            is BackupPurgeInfo.AlreadyPurged -> {
                                throw IllegalStateException("Already purged")
                            }
                        }
                    }
                )
            }
        }
    }

    companion object {
        fun purgeResult2Message(context: Context, result: List<Boolean>): String {
            val deleted = result.count { it }
            val failed = result.count { !it }
            return buildList {
                if (deleted > 0) {
                    add(
                        context.resources.getQuantityString(
                            R.plurals.purge_backup_success,
                            deleted,
                            deleted
                        )
                    )
                }
                if (failed > 0) {
                    add(
                        context.resources.getQuantityString(
                            R.plurals.purge_backup_failure,
                            failed,
                            failed
                        )
                    )
                }
            }.joinToString(" ")
        }
    }
}