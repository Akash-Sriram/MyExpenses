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
import android.os.Environment
import java.io.File
import java.io.FileInputStream
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
            val prefix = prefHandler.requireString(PrefKey.BACKUP_FILE_PREFIX, "myexpenses-backup")
            val fullRegex = """$prefix-\d\d\d\d\d\d\d\d-\d\d\d\d\d\d\..+""".toRegex()
            val dbRegex = """${org.totschnig.myexpenses.util.IncrementalBackupHelper.SNAPSHOT_PREFIX}-\d\d\d\d\d\d\d\d-\d\d\d\d\d\d\..+""".toRegex()
            val dirsToScan = mutableListOf<DocumentFile>()

            // 1. Configured / Default appDir and its snapshots/ subfolder
            AppDirHelper.checkAppDir(getApplication()).getOrNull()?.let { appDir ->
                dirsToScan.add(appDir)
                appDir.findFile(org.totschnig.myexpenses.util.IncrementalBackupHelper.DIR_SNAPSHOTS)?.let { dirsToScan.add(it) }
            }

            // 2. Fallback common storage directories (Music, Download, Documents)
            listOf(
                Environment.DIRECTORY_MUSIC,
                Environment.DIRECTORY_DOWNLOADS,
                Environment.DIRECTORY_DOCUMENTS
            ).forEach { dirType ->
                try {
                    val publicDir = Environment.getExternalStoragePublicDirectory(dirType)
                    if (publicDir.exists() && publicDir.isDirectory) {
                        DocumentFile.fromFile(publicDir)?.let { docDir ->
                            dirsToScan.add(docDir)
                            val snapshotsSub = File(publicDir, org.totschnig.myexpenses.util.IncrementalBackupHelper.DIR_SNAPSHOTS)
                            if (snapshotsSub.exists() && snapshotsSub.isDirectory) {
                                DocumentFile.fromFile(snapshotsSub)?.let { dirsToScan.add(it) }
                            }
                        }
                    }
                } catch (_: Exception) {
                }
            }

            val matchingFiles = dirsToScan.flatMap { dir ->
                try {
                    dir.listFiles().filter {
                        it.name?.matches(fullRegex) == true || it.name?.matches(dbRegex) == true
                    }
                } catch (_: Exception) {
                    emptyList()
                }
            }.distinctBy { it.name ?: it.uri.toString() }
                .sortedByDescending { it.lastModified() }
                .take(15)

            _backupsList.value = matchingFiles.map { file ->
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

    fun doBackup(withSync: Boolean, lenientMode: Boolean, mode: org.totschnig.myexpenses.provider.BackupMode = org.totschnig.myexpenses.provider.BackupMode.FULL_ZIP) {
        viewModelScope.launch(coroutineDispatcher) {
            _backupState.value = BackupState.Running
            _backupState.value = BackupState.Completed(
                doBackup(
                    getApplication(),
                    prefHandler,
                    withSync,
                    lenientMode,
                    mode
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
            val stream = if (uri.scheme == "file") {
                FileInputStream(File(uri.path!!))
            } else {
                getApplication<MyApplication>().contentResolver.openInputStream(uri)
            }
            stream.use {
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